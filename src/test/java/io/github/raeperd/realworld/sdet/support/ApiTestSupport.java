package io.github.raeperd.realworld.sdet.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared helpers for SDET API / E2E tests against the running Spring context.
 */
public final class ApiTestSupport {

    private ApiTestSupport() {
    }

    public static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static RegisteredUser registerUser(MockMvc mockMvc, ObjectMapper objectMapper,
                                              String email, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":{"email":"%s","username":"%s","password":"%s"}}
                                """.formatted(email, username, password)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).get("user");
        return new RegisteredUser(
                email,
                username,
                password,
                user.get("token").textValue()
        );
    }

    public static String loginAndGetToken(MockMvc mockMvc, ObjectMapper objectMapper,
                                          String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":{"email":"%s","password":"%s"}}
                                """.formatted(email, password)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("user")
                .get("token")
                .textValue();
    }

    public static String authHeader(String token) {
        return "Token " + token;
    }

    public record RegisteredUser(String email, String username, String password, String token) {
    }
}
