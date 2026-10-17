package com.khedma.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

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
import com.khedma.auth.otp.CodePurpose;
import com.khedma.auth.otp.EmailVerificationCodeRepository;
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
    @Autowired EmailVerificationCodeRepository codes;

    @Test
    void registrationRequiresEmailVerificationBeforeLogin() throws Exception {
        register("new@test.local").andExpect(status().isAccepted());

        login("new@test.local").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        verify("new@test.local", wrongCode("new@test.local")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));

        ResultActions verified = verify("new@test.local", emails.lastCode("new@test.local")).andExpect(status().isOk());
        String token = token(verified);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // The browser that just proved the inbox is trusted; any other one gets the emailed code step.
        login("new@test.local", PASSWORD, field(verified, "deviceToken")).andExpect(status().isOk());
        login("new@test.local").andExpect(status().isAccepted());
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

    @Test
    void forgotPasswordResetsThePasswordWithAnEmailedCode() throws Exception {
        verifiedToken("forgot@test.local");

        forgot("forgot@test.local").andExpect(status().isAccepted());
        assertThat(emails.lastPurpose("forgot@test.local")).isEqualTo(CodePurpose.RESET_PASSWORD);

        reset("forgot@test.local", wrongCode("forgot@test.local"), "brand-new-pass")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        reset("forgot@test.local", emails.lastCode("forgot@test.local"), "brand-new-pass")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        login("forgot@test.local").andExpect(status().isUnauthorized()); // old password is gone
        login("forgot@test.local", "brand-new-pass", null).andExpect(status().isAccepted());
        // A used code can't be replayed.
        reset("forgot@test.local", emails.lastCode("forgot@test.local"), "another-pass-1")
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmails() throws Exception {
        forgot("nobody@test.local").andExpect(status().isAccepted());
        assertThat(emails.lastCode("nobody@test.local")).isNull();
    }

    @Test
    void importedUnverifiedAccountCanSetItsPasswordAndKeepsItsRole() throws Exception {
        var imported = new com.khedma.user.AppUser();
        imported.setEmail("imported@test.local");
        imported.setDisplayName("Imported Admin");
        imported.setRole(Role.ADMIN);
        imported.setStatus(UserStatus.APPROVED);
        users.save(imported);

        forgot("imported@test.local").andExpect(status().isAccepted());
        reset("imported@test.local", emails.lastCode("imported@test.local"), "first-password-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.status").value("APPROVED"))
                .andExpect(jsonPath("$.user.emailVerified").value(true));
    }

    @Test
    void signInFromANewBrowserNeedsTheEmailedCode() throws Exception {
        verifiedToken("twofa@test.local");

        ResultActions first = login("twofa@test.local").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.email").value("twofa@test.local"));
        String challenge = field(first, "challengeToken");
        assertThat(emails.lastPurpose("twofa@test.local")).isEqualTo(CodePurpose.LOGIN);

        // The challenge is not an access token.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + challenge)).andExpect(status().isUnauthorized());

        loginCode(challenge, wrongCode("twofa@test.local")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        ResultActions second = loginCode(challenge, emails.lastCode("twofa@test.local")).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        String device = field(second, "deviceToken");

        login("twofa@test.local", PASSWORD, device).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        login("twofa@test.local", PASSWORD, "made-up-device").andExpect(status().isAccepted());
    }

    @Test
    void aCodeOnlyWorksForTheFlowItWasSentFor() throws Exception {
        verifiedToken("purpose@test.local");
        String challenge = field(login("purpose@test.local").andExpect(status().isAccepted()), "challengeToken");
        String loginCode = emails.lastCode("purpose@test.local");

        reset("purpose@test.local", loginCode, "brand-new-pass").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        loginCode(challenge, loginCode).andExpect(status().isOk());
    }

    @Test
    void newCodesDoNotGiveMoreGuesses() throws Exception {
        register("guesser@test.local");
        String wrong = wrongCode("guesser@test.local");
        for (int i = 0; i < 5; i++) verify("guesser@test.local", wrong);

        rewindCooldown("guesser@test.local");
        resend("guesser@test.local").andExpect(status().isAccepted());
        String fresh = emails.lastCode("guesser@test.local");
        String wrongAgain = fresh.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            verify("guesser@test.local", wrongAgain).andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_CODE"));
        }
        // Ten wrong guesses this hour: even the right code is refused until the hour is over.
        verify("guesser@test.local", fresh).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void anAccountGetsAtMostTenCodesADay() throws Exception {
        register("spam@test.local");
        for (int i = 1; i < 10; i++) {
            rewindCooldown("spam@test.local");
            resend("spam@test.local").andExpect(status().isAccepted());
        }
        rewindCooldown("spam@test.local");
        resend("spam@test.local").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_CODES"));
    }

    @Test
    void resettingThePasswordSignsOutOtherSessions() throws Exception {
        String oldToken = verifiedToken("revoke@test.local");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isOk());

        forgot("revoke@test.local").andExpect(status().isAccepted());
        String newToken = token(reset("revoke@test.local", emails.lastCode("revoke@test.local"), "brand-new-pass")
                .andExpect(status().isOk()));

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + oldToken)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
    }

    @Test
    void signOutEverywhereRevokesTokensAndTrustedBrowsers() throws Exception {
        register("everywhere@test.local");
        ResultActions verified = verify("everywhere@test.local", emails.lastCode("everywhere@test.local"));
        String token = token(verified);

        mvc.perform(post("/api/me/sign-out-everywhere").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        login("everywhere@test.local", PASSWORD, field(verified, "deviceToken")).andExpect(status().isAccepted());
    }

    @Test
    void repeatedWrongPasswordsPauseSignInForThatEmail() throws Exception {
        verifiedToken("locked@test.local");
        for (int i = 0; i < 8; i++) {
            login("locked@test.local", "wrong-password", null).andExpect(status().isUnauthorized());
        }
        login("locked@test.local").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void unknownEmailGetsTheSameAnswerAsAWrongPassword() throws Exception {
        login("ghost@test.local", PASSWORD, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ---- helpers --------------------------------------------------------------------------

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String email, String code, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPassword + "\"}"));
    }

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
        return login(email, PASSWORD, null);
    }

    private ResultActions login(String email, String password, String deviceToken) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (deviceToken != null) body.put("deviceToken", deviceToken);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions loginCode(String challenge, String code) throws Exception {
        return mvc.perform(post("/api/auth/login/verify").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("challengeToken", challenge, "code", code))));
    }

    private ResultActions resend(String email) throws Exception {
        return mvc.perform(post("/api/auth/resend-code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    /** The test clock is frozen, so step past the 60s resend cooldown by moving the last send back. */
    private void rewindCooldown(String email) {
        Long id = users.findByEmail(email).orElseThrow().getId();
        codes.findById(id).ifPresent(code -> {
            code.setLastSentAt(code.getLastSentAt().minusSeconds(120));
            codes.save(code);
        });
    }

    private String wrongCode(String email) {
        return emails.lastCode(email).equals("000000") ? "111111" : "000000";
    }

    private String token(ResultActions result) throws Exception {
        return field(result, "accessToken");
    }

    private String field(ResultActions result, String name) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get(name).asText();
    }
}
