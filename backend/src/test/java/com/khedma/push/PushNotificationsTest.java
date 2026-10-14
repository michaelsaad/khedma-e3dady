package com.khedma.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.khedma.TestcontainersConfig;
import com.khedma.TestcontainersConfig.CapturingPushGateway;
import com.khedma.attendance.AttendanceService;
import com.khedma.attendance.SkippedMeeting;
import com.khedma.attendance.SkippedMeetingRepository;
import com.khedma.auth.JwtService;
import com.khedma.common.ApiException;
import com.khedma.common.ErrorCode;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class PushNotificationsTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final String FCM = "https://fcm.googleapis.com/fcm/send/";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired SkippedMeetingRepository skippedMeetings;
    @Autowired AttendanceService attendanceService;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired PushScheduler scheduler;
    @Autowired CapturingPushGateway gateway;
    @Autowired JwtService jwt;
    @Autowired EntityManager entityManager;

    AppUser owner;
    AppUser admin;
    AppUser kid;
    AppUser pending;
    String ownerToken;
    String adminToken;
    String kidToken;
    String keys;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        owner = account("owner@push.test", Role.SUPER_ADMIN, UserStatus.APPROVED);
        admin = account("admin@push.test", Role.ADMIN, UserStatus.APPROVED);
        kid = account("kid@push.test", Role.USER, UserStatus.APPROVED);
        pending = account("pending@push.test", Role.USER, UserStatus.PENDING);
        ownerToken = jwt.issue(owner).token();
        adminToken = jwt.issue(admin).token();
        kidToken = jwt.issue(kid).token();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        String p256dh = B64.encodeToString(EcKeys.encode((ECPublicKey) generator.generateKeyPair().getPublic()));
        keys = "{\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + B64.encodeToString(new byte[16]) + "\"}";
    }

    // ---- Subscriptions ----

    @Test
    void configIsPublic() throws Exception {
        mvc.perform(get("/api/push/config")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void anyApprovedAccountCanSubscribeButPendingAndAnonymousCannot() throws Exception {
        subscribe(kidToken, FCM + "kid-phone").andExpect(status().isCreated());
        assertThat(subscriptions.findByEndpoint(FCM + "kid-phone")).get().extracting(s -> s.getUser().getId()).isEqualTo(kid.getId());

        subscribe(jwt.issue(pending).token(), FCM + "pending-phone").andExpect(status().isForbidden());
        mvc.perform(post("/api/push/subscribe").contentType(MediaType.APPLICATION_JSON).content(body(FCM + "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribingAgainMovesTheDeviceToTheCurrentAccount() throws Exception {
        subscribe(kidToken, FCM + "shared-phone").andExpect(status().isCreated());
        subscribe(adminToken, FCM + "shared-phone").andExpect(status().isCreated());

        assertThat(subscriptions.findAllByUserIdIn(List.of(kid.getId(), admin.getId()))).hasSize(1)
                .allSatisfy(s -> assertThat(s.getUser().getId()).isEqualTo(admin.getId()));
    }

    @Test
    void anAccountCanOnlyRemoveItsOwnDevices() throws Exception {
        subscribe(kidToken, FCM + "kid-phone").andExpect(status().isCreated());

        mvc.perform(delete("/api/push/subscribe").param("endpoint", FCM + "kid-phone").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(FCM + "kid-phone")).isPresent();

        mvc.perform(delete("/api/push/subscribe").param("endpoint", FCM + "kid-phone").header("Authorization", bearer(kidToken)))
                .andExpect(status().isNoContent());
        assertThat(subscriptions.findByEndpoint(FCM + "kid-phone")).isEmpty();
    }

    @Test
    void onlyTheRealPushServicesAreAccepted() throws Exception {
        for (String endpoint : new String[] {"http://fcm.googleapis.com/fcm/send/x", "https://evil.example.com/hook",
                "https://fcm.googleapis.com.evil.com/x", "https://user@fcm.googleapis.com/x", "https://127.0.0.1/x", "not a url"}) {
            subscribe(kidToken, endpoint).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PUSH_ENDPOINT_NOT_ALLOWED"));
        }
        for (String endpoint : new String[] {FCM + "ok", "https://updates.push.services.mozilla.com/wpush/v2/abc",
                "https://web.push.apple.com/abc", "https://wns2-par02p.notify.windows.com/w/?token=abc"}) {
            PushService.requireAllowedEndpoint(endpoint);
        }
        assertThatThrownBy(() -> PushService.requireAllowedEndpoint("https://push.example.org/x"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(ErrorCode.PUSH_ENDPOINT_NOT_ALLOWED);
    }

    @Test
    void malformedKeysAreRejected() throws Exception {
        mvc.perform(post("/api/push/subscribe").header("Authorization", bearer(kidToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"" + FCM + "bad\",\"keys\":{\"p256dh\":\"AAAA\",\"auth\":\"BBBB\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ---- Broadcast ----

    @Test
    void broadcastToEveryoneReachesApprovedDevicesOnly() throws Exception {
        subscribe(kidToken, FCM + "kid-phone");
        subscribe(adminToken, FCM + "admin-phone");
        subscribe(adminToken, FCM + "admin-laptop");
        // A pending account that somehow has a subscription must not receive anything.
        PushSubscription stray = new PushSubscription();
        stray.setUser(pending);
        stray.setEndpoint(FCM + "pending-phone");
        stray.setP256dh("x");
        stray.setAuth("y");
        subscriptions.save(stray);
        entityManager.flush();

        mvc.perform(get("/api/notifications/recipients").param("audience", "ALL").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.devices").value(3));

        broadcast(ownerToken, "ALL", "اجتماع بكرة", "الساعة ٦").andExpect(status().isCreated())
                .andExpect(jsonPath("$.devices").value(3)).andExpect(jsonPath("$.sentBy").value("Test"));

        assertThat(gateway.sent()).extracting(s -> s.target().endpoint())
                .containsExactlyInAnyOrder(FCM + "kid-phone", FCM + "admin-phone", FCM + "admin-laptop");
        assertThat(gateway.sent().get(0).payload()).contains("\"title\":\"اجتماع بكرة\"").contains("\"body\":\"الساعة ٦\"")
                .contains("\"url\":\"/\"");
    }

    @Test
    void broadcastToAdminsSkipsOrdinaryAccounts() throws Exception {
        subscribe(kidToken, FCM + "kid-phone");
        subscribe(adminToken, FCM + "admin-phone");
        subscribe(ownerToken, FCM + "owner-phone");

        broadcast(ownerToken, "ADMINS", "للأدمنز", "نص").andExpect(status().isCreated());

        assertThat(gateway.sent()).extracting(s -> s.target().endpoint())
                .containsExactlyInAnyOrder(FCM + "admin-phone", FCM + "owner-phone");
        mvc.perform(get("/api/notifications/broadcasts").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$[0].title").value("للأدمنز")).andExpect(jsonPath("$[0].audience").value("ADMINS"));
    }

    @Test
    void onlyTheSuperAdminMayBroadcast() throws Exception {
        broadcast(adminToken, "ALL", "x", "y").andExpect(status().isForbidden());
        broadcast(kidToken, "ALL", "x", "y").andExpect(status().isForbidden());
        mvc.perform(post("/api/notifications/broadcast").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"body\":\"y\",\"audience\":\"ALL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devicesThePushServiceReportsGoneAreRemoved() throws Exception {
        subscribe(kidToken, FCM + "alive");
        subscribe(kidToken, FCM + "gone-uninstalled");
        entityManager.flush();

        broadcast(ownerToken, "ALL", "x", "y").andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();

        assertThat(subscriptions.findByEndpoint(FCM + "gone-uninstalled")).isEmpty();
        assertThat(subscriptions.findByEndpoint(FCM + "alive")).get().extracting(PushSubscription::getLastSuccessAt).isNotNull();
    }

    // ---- Scheduled notifications ----

    @Test
    void meetingReminderGoesToAdminsOnTheEveningBeforeAndOnlyOnce() {
        subscribeDirectly(admin, FCM + "admin-phone");
        subscribeDirectly(kid, FCM + "kid-phone");

        scheduler.meetingReminder(LocalDate.of(2026, 10, 14)); // Wednesday: the meeting is on Friday the 16th, not tomorrow
        assertThat(gateway.sent()).isEmpty();

        scheduler.meetingReminder(LocalDate.of(2026, 10, 15)); // Thursday
        assertThat(gateway.sent()).extracting(s -> s.target().endpoint()).containsExactly(FCM + "admin-phone");
        assertThat(gateway.sent().get(0).payload()).contains("تذكير الحضور").contains("\"url\":\"/attendance\"");

        scheduler.meetingReminder(LocalDate.of(2026, 10, 15)); // a restart or a second instance
        assertThat(gateway.sent()).hasSize(1);
    }

    @Test
    void noReminderForASkippedMeeting() {
        subscribeDirectly(admin, FCM + "admin-phone");
        SkippedMeeting skipped = new SkippedMeeting();
        skipped.setMeetingDate(LocalDate.of(2026, 10, 16));
        skipped.setReason("");
        skipped.setCreatedAt(java.time.Instant.now());
        skippedMeetings.save(skipped);

        scheduler.meetingReminder(LocalDate.of(2026, 10, 15));

        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void absenceAlertNamesWhoMissedTheLastMeetingsAndSendsOnce() {
        subscribeDirectly(admin, FCM + "admin-phone");
        Student mina = student("مينا عادل");
        Student maria = student("ماريا فادي");
        Student kirollos = student("كيرلس ماهر");
        LocalDate oct2 = LocalDate.of(2026, 10, 2);
        LocalDate oct9 = LocalDate.of(2026, 10, 9);
        attendanceService.save(oct2, mina.getId(), true, false, admin.getId());
        attendanceService.save(oct2, maria.getId(), false, false, admin.getId());   // recorded, absent
        attendanceService.save(oct9, kirollos.getId(), false, true, admin.getId());
        attendanceService.save(oct9, maria.getId(), false, false, admin.getId());   // recorded, absent

        scheduler.absenceAlert(LocalDate.of(2026, 10, 10)); // the day after the last meeting

        // Mina came on the 2nd, Kirollos on the 9th; only Maria missed both.
        assertThat(gateway.sent()).hasSize(1);
        assertThat(gateway.sent().get(0).payload()).contains("غياب متتالي").contains("غابوا آخر اجتماعين: ماريا");
        assertThat(gateway.sent().get(0).payload()).doesNotContain("مينا").doesNotContain("كيرلس");

        scheduler.absenceAlert(LocalDate.of(2026, 10, 10));
        assertThat(gateway.sent()).hasSize(1);
    }

    @Test
    void absenceAlertIsSilentWhenAttendanceWasNeverTakenOrTheMeetingWasSkipped() {
        subscribeDirectly(admin, FCM + "admin-phone");
        Student maria = student("ماريا فادي");
        attendanceService.save(LocalDate.of(2026, 10, 9), maria.getId(), false, false, admin.getId());

        // Nothing recorded on 2 Oct: the alert would flag every student, so it stays quiet.
        scheduler.absenceAlert(LocalDate.of(2026, 10, 10));
        assertThat(gateway.sent()).isEmpty();

        attendanceService.save(LocalDate.of(2026, 10, 2), maria.getId(), false, false, admin.getId());
        attendanceService.skip(LocalDate.of(2026, 10, 9), "", admin.getId());
        // The last meeting is skipped, so there is nothing to report the day after it.
        scheduler.absenceAlert(LocalDate.of(2026, 10, 10));
        assertThat(gateway.sent()).isEmpty();
    }

    @Test
    void absenceMessageShowsAFewNamesAndCountsTheRest() {
        assertThat(PushScheduler.absenceBody(List.of("أ", "ب", "ج", "د", "هـ", "و"), 2))
                .isEqualTo("غابوا آخر اجتماعين: أ، ب، ج، د و٢ كمان");
        assertThat(PushScheduler.absenceBody(List.of("أ"), 3)).isEqualTo("غابوا آخر ٣ اجتماعات: أ");
    }

    // ---- helpers ----

    private ResultActions subscribe(String token, String endpoint) throws Exception {
        return mvc.perform(post("/api/push/subscribe").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body(endpoint)));
    }

    private String body(String endpoint) {
        return "{\"endpoint\":\"" + endpoint + "\",\"keys\":" + keys + "}";
    }

    private ResultActions broadcast(String token, String audience, String title, String text) throws Exception {
        return mvc.perform(post("/api/notifications/broadcast").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"body\":\"" + text + "\",\"audience\":\"" + audience + "\"}"));
    }

    private void subscribeDirectly(AppUser user, String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh("x");
        subscription.setAuth("y");
        subscriptions.save(subscription);
        entityManager.flush();
    }

    private AppUser account(String email, Role role, UserStatus status) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName("Test");
        user.setEmailVerified(true);
        user.setRole(role);
        user.setStatus(status);
        return users.save(user);
    }

    private Student student(String name) {
        Student student = new Student();
        student.setName(name);
        student.setGrade(Grade.FIRST);
        return students.save(student);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
