package com.khedma.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.khedma.TestcontainersConfig;
import com.khedma.TestcontainersConfig.CapturingPushGateway;
import com.khedma.points.PointsDtos.ManualKind;
import com.khedma.points.PointsDtos.ManualPointsRequest;
import com.khedma.points.PointsService;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

/**
 * The notification is sent after the points transaction commits, so this test must really commit
 * (it can't be rolled back like the others) and removes what it created afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class PointsPushTest {

    @Autowired PointsService pointsService;
    @Autowired StudentRepository students;
    @Autowired UserRepository users;
    @Autowired PushSubscriptionRepository subscriptions;
    @Autowired CapturingPushGateway gateway;
    @Autowired JdbcTemplate jdbc;

    Student student;
    AppUser kid;
    AppUser admin;

    @BeforeEach
    void setUp() {
        gateway.clear();
        Student s = new Student();
        s.setName("بوش تيست");
        s.setGrade(Grade.THIRD);
        student = students.save(s);

        kid = account("pushkid@points.test", Role.USER, UserStatus.APPROVED);
        kid.setStudent(student);
        users.save(kid);
        admin = account("pushadmin@points.test", Role.ADMIN, UserStatus.APPROVED);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from push_subscriptions where user_id in (?, ?)", kid.getId(), admin.getId());
        jdbc.update("delete from point_transactions where student_id = ?", student.getId());
        jdbc.update("delete from app_users where id in (?, ?)", kid.getId(), admin.getId());
        jdbc.update("delete from students where id = ?", student.getId());
        jdbc.update("update point_reasons set usage_count = 0 where label = 'سلوك'");
    }

    @Test
    void aBonusNotifiesTheLinkedAccountAfterTheCommit() {
        subscribe(kid, "https://fcm.googleapis.com/fcm/send/kid-points");

        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.ADD, 2, "سلوك", null), admin.getId());

        assertThat(gateway.sent()).hasSize(1);
        assertThat(gateway.sent().get(0).target().endpoint()).endsWith("kid-points");
        assertThat(gateway.sent().get(0).payload()).contains("نقط جديدة").contains("+٢ سلوك · رصيدك ٢").contains("\"url\":\"/me\"");
    }

    @Test
    void deductionsResetsAndUnlinkedOrUnsubscribedStudentsGetNothing() {
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.ADD, 5, "سلوك", null), admin.getId());
        assertThat(gateway.sent()).isEmpty(); // linked, but no device subscribed

        subscribe(kid, "https://fcm.googleapis.com/fcm/send/kid-points");
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.DEDUCT, 1, "خصم", null), admin.getId());
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.RESET, null, "تصفير", null), admin.getId());
        assertThat(gateway.sent()).isEmpty();

        kid.setStudent(null);
        users.save(kid);
        pointsService.manualForStudent(student.getId(), new ManualPointsRequest(ManualKind.ADD, 3, "سلوك", null), admin.getId());
        assertThat(gateway.sent()).isEmpty(); // nobody's account is linked to this student any more
    }

    private void subscribe(AppUser user, String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setUser(user);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh("x");
        subscription.setAuth("y");
        subscriptions.save(subscription);
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
}
