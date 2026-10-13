package com.khedma.followup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khedma.TestcontainersConfig;
import com.khedma.auth.JwtService;
import com.khedma.common.ArabicNames;
import com.khedma.student.Grade;
import com.khedma.student.Student;
import com.khedma.student.StudentRepository;
import com.khedma.user.AppUser;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

import jakarta.persistence.EntityManager;

/** Who can see and do what after the USER role was split into MAKHDOUM and SERVANT, plus follow-up reports. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@Transactional
class RolesAndFollowupsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired StudentRepository students;
    @Autowired JwtService jwt;
    @Autowired EntityManager entityManager;

    Student mina;
    Student maria;
    AppUser makhdoum;
    String makhdoumToken;
    String servantToken;
    String otherServantToken;
    String adminToken;

    @BeforeEach
    void setUp() {
        mina = student("مينا عادل جرجس");
        maria = student("ماريا فادي");
        makhdoum = account("kid@test.local", "Mina Adel", Role.MAKHDOUM);
        makhdoum.setStudent(mina);
        makhdoumToken = jwt.issue(makhdoum).token();
        servantToken = jwt.issue(account("servant@test.local", "Servant One", Role.SERVANT)).token();
        otherServantToken = jwt.issue(account("servant2@test.local", "Servant Two", Role.SERVANT)).token();
        adminToken = jwt.issue(account("admin@test.local", "Admin", Role.ADMIN)).token();
    }

    @Test
    void makhdoumSeesOnlyTheirOwnLinkedStudent() throws Exception {
        mvc.perform(get("/api/me/student").header("Authorization", bearer(makhdoumToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.id").value(mina.getId()))
                .andExpect(jsonPath("$.card.name").value("مينا عادل جرجس"));

        // Nothing else: not the dashboard, not another student, not follow-ups, not management.
        for (String path : new String[] {"/api/dashboard", "/api/dashboard/students", "/api/dashboard/students/" + maria.getId(),
                "/api/followups", "/api/followup-types", "/api/students", "/api/transactions"}) {
            mvc.perform(get(path).header("Authorization", bearer(makhdoumToken)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void makhdoumWithoutALinkGetsAClearError() throws Exception {
        String lonely = jwt.issue(account("lonely@test.local", "Lonely", Role.MAKHDOUM)).token();
        mvc.perform(get("/api/me/student").header("Authorization", bearer(lonely)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_LINKED"));
    }

    @Test
    void pendingAccountsGetNothing() throws Exception {
        AppUser pending = account("pending@test.local", "Pending", Role.MAKHDOUM);
        pending.setStatus(UserStatus.PENDING);
        pending.setStudent(mina);
        mvc.perform(get("/api/me/student").header("Authorization", bearer(jwt.issue(pending).token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void servantReadsEverythingButManagesNothing() throws Exception {
        mvc.perform(get("/api/dashboard/students/" + mina.getId()).header("Authorization", bearer(servantToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/students").header("Authorization", bearer(servantToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/students/" + mina.getId() + "/points").header("Authorization", bearer(servantToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"ADD\",\"amount\":5,\"notes\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void servantsWriteReportsAndSeeOnlyTheirOwnWhileAdminsSeeAll() throws Exception {
        long typeId = typeId("تعبان");
        long first = id(report(servantToken, mina, typeId, "مش جه من أسبوعين").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.typeLabel").value("تعبان")));
        report(otherServantToken, maria, typeId, "").andExpect(status().isCreated());

        mvc.perform(get("/api/followups").header("Authorization", bearer(servantToken)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(first));
        mvc.perform(get("/api/followups").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/followups").param("studentId", String.valueOf(maria.getId()))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void onlyAdminsHandleReportsAndEditTypes() throws Exception {
        long typeId = typeId("محتاج افتقاد");
        long id = id(report(servantToken, mina, typeId, "").andExpect(status().isCreated()));

        mvc.perform(patch("/api/followups/" + id + "/resolve").header("Authorization", bearer(servantToken)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/followups/" + id).header("Authorization", bearer(servantToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/followup-types").header("Authorization", bearer(servantToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"جديد\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/followups/" + id + "/resolve").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"كلمته وتمام\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.resolvedNote").value("كلمته وتمام"))
                .andExpect(jsonPath("$.resolvedByName").value("Admin"));
        mvc.perform(patch("/api/followups/" + id + "/reopen").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(delete("/api/followups/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        mvc.perform(patch("/api/followups/" + id + "/resolve").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLLOWUP_NOT_FOUND"));
    }

    @Test
    void typesAreEditableAndOldReportsKeepTheirLabel() throws Exception {
        mvc.perform(post("/api/followup-types").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"تعبان\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOLLOWUP_TYPE_ALREADY_EXISTS"));

        long typeId = typeId("عمل شقاوة");
        long id = id(report(servantToken, mina, typeId, "").andExpect(status().isCreated()));

        // Rename, then deactivate: servants stop seeing it, the old report keeps its original wording.
        mvc.perform(put("/api/followup-types/" + typeId).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"شقاوة كبيرة\",\"active\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/followup-types").header("Authorization", bearer(servantToken)))
                .andExpect(jsonPath("$[?(@.id == " + typeId + ")]").isEmpty());
        mvc.perform(get("/api/followup-types").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$[?(@.id == " + typeId + ")].active").value(false));
        report(servantToken, mina, typeId, "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOLLOWUP_TYPE_NOT_FOUND"));
        mvc.perform(get("/api/followups").header("Authorization", bearer(servantToken)))
                .andExpect(jsonPath("$.items[?(@.id == " + id + ")].typeLabel").value("عمل شقاوة"));

        // Each real request is its own transaction, so nothing is cached between them: mimic that here.
        entityManager.flush();
        entityManager.clear();
        mvc.perform(delete("/api/followup-types/" + typeId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear(); // the FK has set the report's type to null by now
        mvc.perform(get("/api/followups").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.items[?(@.id == " + id + ")].typeLabel").value("عمل شقاوة"));
    }

    @Test
    void suggestionsRankTheClosestUnlinkedStudentFirst() throws Exception {
        Student closer = student("مينا عادل بشرى");
        Student weaker = student("مينا سامي");
        AppUser signedUp = account("new@test.local", "مينا عادل", Role.MAKHDOUM);

        entityManager.flush();
        String owner = jwt.issue(account("owner2@test.local", "Owner", Role.SUPER_ADMIN)).token();
        JsonNode suggestions = json.readTree(mvc.perform(get("/api/users/" + signedUp.getId() + "/student-suggestions")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Two words in common beat one; students with nothing in common are left out;
        // "مينا عادل جرجس" is already linked to another account, so it isn't offered.
        assertThat(suggestions).extracting(s -> s.get("id").asLong()).containsExactly(closer.getId(), weaker.getId());
    }

    @Test
    void oneStudentCanOnlyBeLinkedToOneAccountAndRemovalUnlinks() throws Exception {
        String owner = jwt.issue(account("owner3@test.local", "Owner", Role.SUPER_ADMIN)).token();
        AppUser second = account("second@test.local", "Second", Role.MAKHDOUM);

        mvc.perform(put("/api/users/" + second.getId() + "/student").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + mina.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_LINKED"));

        mvc.perform(delete("/api/students/" + mina.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(users.findById(makhdoum.getId()).orElseThrow().getStudent()).isNull();
        mvc.perform(get("/api/me/student").header("Authorization", bearer(makhdoumToken)))
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_LINKED"));
    }

    @Test
    void arabicNamesMatchDespiteSpellingVariants() {
        assertThat(ArabicNames.similarity("أندرو هاني", "اندرو هانى")).isEqualTo(1);
        assertThat(ArabicNames.similarity("مينا عادل", "مينا عادل جرجس")).isGreaterThan(ArabicNames.similarity("مينا عادل", "مينا سامي"));
        assertThat(ArabicNames.similarity("مينا", "ماريا")).isZero();
    }

    // ---- helpers ----

    private Student student(String name) {
        Student s = new Student();
        s.setName(name);
        s.setGrade(Grade.FIRST);
        return students.save(s);
    }

    private AppUser account(String email, String name, Role role) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setDisplayName(name);
        user.setEmailVerified(true);
        user.setRole(role);
        user.setStatus(UserStatus.APPROVED);
        return users.save(user);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private ResultActions report(String token, Student student, long typeId, String note) throws Exception {
        return mvc.perform(post("/api/followups").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student.getId() + ",\"typeId\":" + typeId + ",\"note\":\"" + note + "\"}"));
    }

    private long typeId(String label) throws Exception {
        JsonNode types = json.readTree(mvc.perform(get("/api/followup-types").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode type : types) {
            if (type.get("label").asText().equals(label)) return type.get("id").asLong();
        }
        throw new AssertionError("no seeded type " + label);
    }

    private long id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
