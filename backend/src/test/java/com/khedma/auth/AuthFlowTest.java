package com.khedma.auth;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khedma.TestcontainersConfig;
import com.khedma.user.Role;
import com.khedma.user.UserRepository;
import com.khedma.user.UserStatus;

@SpringBootTest(properties = "app.super-admin-emails=owner@test.local")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuthFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @Test
    void newAccountIsPendingAndCannotUseAdminApis() throws Exception {
        String token = register("pending@test.local");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.role").value("USER"));
        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedAdminCanUseAdminApisButNotUserManagement() throws Exception {
        String token = register("admin2@test.local");
        var user = users.findByEmail("admin2@test.local").orElseThrow();
        user.setRole(Role.ADMIN);
        user.setStatus(UserStatus.APPROVED);
        users.save(user);

        mvc.perform(get("/api/students").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.massPoints").value(5))
                .andExpect(jsonPath("$.servicePoints").value(2));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void passwordSignupWithOwnerEmailDoesNotGrantSuperAdmin() throws Exception {
        String token = register("owner@test.local");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void wrongPasswordIsRejectedAndRequestsWithoutTokenAre401() throws Exception {
        register("login@test.local");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"login@test.local\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"LOGIN@test.local\",\"password\":\"secret-pass-1\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    private String register(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"secret-pass-1\",\"displayName\":\"Test\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("accessToken").asText();
    }
}
