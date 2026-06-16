package io.github.raeperd.realworld.sdet;

import io.github.raeperd.realworld.sdet.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static io.github.raeperd.realworld.sdet.support.ApiTestSupport.authHeader;
import static io.github.raeperd.realworld.sdet.support.ApiTestSupport.uniqueSuffix;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication API: happy paths and deliberate negative cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;
    private String username;
    private static final String PASSWORD = "password123";

    @BeforeEach
    void uniqueUser() {
        String suffix = uniqueSuffix();
        email = "sdet-" + suffix + "@test.com";
        username = "sdet_user_" + suffix;
    }

    @Test
    @DisplayName("POST /users registers and returns JWT token")
    void register_returnsToken() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(email, username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("user.email").value(email))
                .andExpect(jsonPath("user.username").value(username))
                .andExpect(jsonPath("user.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /users/login rejects wrong password with 404-style empty body")
    void login_wrongPassword_returnsNotFound() throws Exception {
        ApiTestSupport.registerUser(mockMvc, objectMapper, email, username, PASSWORD);

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":{"email":"%s","password":"wrong-password"}}
                                """.formatted(email)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /users rejects invalid email format")
    void register_invalidEmail_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("not-an-email", username, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /user without Authorization is rejected")
    void currentUser_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /user with malformed token is rejected")
    void currentUser_withGarbageToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/user")
                        .header("Authorization", authHeader("not.a.valid.jwt")))
                .andExpect(status().isUnauthorized());
    }

    private static String userJson(String email, String username, String password) {
        return """
                {"user":{"email":"%s","username":"%s","password":"%s"}}
                """.formatted(email, username, password);
    }
}
