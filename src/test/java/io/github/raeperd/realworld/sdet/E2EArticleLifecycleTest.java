package io.github.raeperd.realworld.sdet;

import io.github.raeperd.realworld.sdet.support.ApiTestSupport;
import io.github.raeperd.realworld.sdet.support.ApiTestSupport.RegisteredUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static io.github.raeperd.realworld.sdet.support.ApiTestSupport.authHeader;
import static io.github.raeperd.realworld.sdet.support.ApiTestSupport.uniqueSuffix;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: register → login → publish article → comment → delete article.
 */
@SpringBootTest
@AutoConfigureMockMvc
class E2EArticleLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Full author journey: register, publish, comment, delete")
    void register_login_publish_comment_delete() throws Exception {
        String suffix = uniqueSuffix();
        String email = "lifecycle-" + suffix + "@test.com";
        String username = "author_" + suffix;
        String password = "password123";

        RegisteredUser registered = ApiTestSupport.registerUser(mockMvc, objectMapper, email, username, password);
        String tokenFromLogin = ApiTestSupport.loginAndGetToken(mockMvc, objectMapper, email, password);

        mockMvc.perform(get("/user").header("Authorization", authHeader(tokenFromLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("user.username").value(username));

        String createResponse = mockMvc.perform(post("/articles")
                        .header("Authorization", authHeader(registered.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"article":{"title":"How to train your dragon","description":"Ever wonder how?",
                                "body":"Very carefully.","tagList":["dragons","training"]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("article.author.username").value(username))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String slug = objectMapper.readTree(createResponse).get("article").get("slug").textValue();

        mockMvc.perform(get("/articles/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("article.title").value("How to train your dragon"));

        String commentResponse = mockMvc.perform(post("/articles/" + slug + "/comments")
                        .header("Authorization", authHeader(registered.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":{\"body\":\"Great article!\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("comment.body", is("Great article!")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode comment = objectMapper.readTree(commentResponse).get("comment");
        long commentId = comment.get("id").longValue();

        mockMvc.perform(get("/articles/" + slug + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("comments[0].id").value(commentId));

        mockMvc.perform(delete("/articles/" + slug + "/comments/" + commentId)
                        .header("Authorization", authHeader(registered.token())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/articles/" + slug)
                        .header("Authorization", authHeader(registered.token())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/articles/" + slug))
                .andExpect(status().isNotFound());
    }
}
