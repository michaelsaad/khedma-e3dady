package com.khedma.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khedma.TestcontainersConfig;
import com.khedma.TestcontainersConfig.CapturingEmailSender;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

@SpringBootTest(properties = "app.super-admin-emails=owner@test.local")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthFlowTest {

    private static final String PASSWORD = "secret-pass-1";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CapturingEmailSender emails;

    @Test
    void registrationRequiresEmailVerificationBeforeLogin() throws Exception {
        register("new@test.local").andExpect(status().isAccepted());

        login("new@test.local").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        verify("new@test.local", wrongCode("new@test.local")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));

        String token = token(verify("new@test.local", emails.lastCode("new@test.local")).andExpect(status().isOk()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));

        login("new@test.local").andExpect(status().isOk());
        register("new@test.local").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void codeIsLockedAfterTooManyWrongAttempts() throws Exception {
        register("brute@test.local");
        String wrong = wrongCode("brute@test.local");
        for (int i = 0; i < 5; i++) {
            verify("brute@test.local", wrong).andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        }
        verify("brute@test.local", emails.lastCode("brute@test.local")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void resendIsRateLimited() throws Exception {
        register("resend@test.local");
        // Within the 60s cooldown no new code is sent.
        mvc.perform(post("/api/auth/resend-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"resend@test.local\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESEND_TOO_SOON"));
    }

    @Test
    void ownerIsPromotedOnlyAfterVerifyingTheirEmail() throws Exception {
        register("owner@test.local");
        assertThat(users.findByEmail("owner@test.local").orElseThrow().getRole()).isEqualTo(Role.USER);

        verify("owner@test.local", emails.lastCode("owner@test.local"))
                .andExpect(jsonPath("$.user.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.user.status").value("APPROVED"));
    }

    @Test
    void pendingUserCannotSeeDashboardButApprovedUserCan() throws Exception {
        String token = verifiedToken("viewer@test.local");
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        setAccess("viewer@test.local", Role.USER, UserStatus.APPROVED);
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingsHeld").value(4));
        mvc.perform(get("/api/dashboard/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        // Regular users can't manage data.
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanManageButNotUsers() throws Exception {
        String token = verifiedToken("admin2@test.local");
        setAccess("admin2@test.local", Role.ADMIN, UserStatus.APPROVED);

        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/settings").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.massPoints").value(5))
                .andExpect(jsonPath("$.servicePoints").value(2));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestsGetUnauthenticatedCode() throws Exception {
        mvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void validationErrorsReturnFieldCodes() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"displayName\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')].code").value("Email"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')].code").value("Size"));
    }

    // ---- helpers --------------------------------------------------------------------------

    private String verifiedToken(String email) throws Exception {
        register(email).andExpect(status().isAccepted());
        return token(verify(email, emails.lastCode(email)).andExpect(status().isOk()));
    }

    private void setAccess(String email, Role role, UserStatus status) {
        var user = users.findByEmail(email).orElseThrow();
        user.setRole(role);
        user.setStatus(status);
        users.save(user);
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"Test\"}"));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private String wrongCode(String email) {
        return emails.lastCode(email).equals("000000") ? "111111" : "000000";
    }

    private String token(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
}
