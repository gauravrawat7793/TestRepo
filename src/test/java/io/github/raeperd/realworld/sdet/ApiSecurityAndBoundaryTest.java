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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately probes authorization gaps, spec mismatches, and data integrity edges.
 * Several tests document known defects rather than ideal behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityAndBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("BUG: feed returns favorited articles, not articles from followed authors (RealWorld spec)")
    void feed_shouldShowFollowedAuthorsArticles_perSpec() throws Exception {
        String suffix = uniqueSuffix();
        RegisteredUser author = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "feed-author-" + suffix + "@test.com", "feed_author_" + suffix, "password123");
        RegisteredUser reader = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "feed-reader-" + suffix + "@test.com", "feed_reader_" + suffix, "password123");

        String slug = createArticle(author, "Feed Test Article " + suffix);

        mockMvc.perform(post("/profiles/" + author.username() + "/follow")
                        .header("Authorization", authHeader(reader.token())))
                .andExpect(status().isOk());

        String feedBody = mockMvc.perform(get("/articles/feed")
                        .header("Authorization", authHeader(reader.token())))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode articles = objectMapper.readTree(feedBody).get("articles");
        boolean containsFollowedAuthorArticle = false;
        if (articles != null && articles.isArray()) {
            for (JsonNode article : articles) {
                if (slug.equals(article.get("slug").textValue())) {
                    containsFollowedAuthorArticle = true;
                    break;
                }
            }
        }
        assertThat(containsFollowedAuthorArticle)
                .as("GET /articles/feed should include articles from followed authors without favoriting")
                .isTrue();
    }

    @Test
    @DisplayName("BUG: comment author cannot delete own comment on another user's article (wrong OR in auth check)")
    void commentAuthor_shouldDeleteOwnComment_onOthersArticle() throws Exception {
        String suffix = uniqueSuffix();
        RegisteredUser author = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "cmt-author-" + suffix + "@test.com", "cmt_author_" + suffix, "password123");
        RegisteredUser commenter = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "cmt-reader-" + suffix + "@test.com", "cmt_reader_" + suffix, "password123");

        String slug = createArticle(author, "Comment Boundary " + suffix);

        String body = mockMvc.perform(post("/articles/" + slug + "/comments")
                        .header("Authorization", authHeader(commenter.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":{\"body\":\"My comment\"}}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long commentId = objectMapper.readTree(body).get("comment").get("id").longValue();

        mockMvc.perform(delete("/articles/" + slug + "/comments/" + commentId)
                        .header("Authorization", authHeader(commenter.token())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Non-author updating article should return 403, not 500 (IllegalAccessError leaks)")
    void nonAuthor_updateArticle_shouldBeForbidden() throws Exception {
        String suffix = uniqueSuffix();
        RegisteredUser owner = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "owner-" + suffix + "@test.com", "owner_" + suffix, "password123");
        RegisteredUser intruder = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "intruder-" + suffix + "@test.com", "intruder_" + suffix, "password123");

        String slug = createArticle(owner, "Protected Article " + suffix);

        mockMvc.perform(put("/articles/" + slug)
                        .header("Authorization", authHeader(intruder.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"body\":\"hacked\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BUG: duplicate email registration is not rejected (no DB/JPA uniqueness)")
    void register_duplicateEmail_shouldRejectSecondSignup() throws Exception {
        String suffix = uniqueSuffix();
        String email = "dup-" + suffix + "@test.com";
        String password = "password123";

        ApiTestSupport.registerUser(mockMvc, objectMapper, email, "user_a_" + suffix, password);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"user":{"email":"%s","username":"user_b_%s","password":"%s"}}
                                """.formatted(email, suffix, password)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /articles without auth is rejected")
    void createArticle_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"article":{"title":"No Auth","description":"d","body":"b","tagList":[]}}
                                """))
                .andExpect(status().isUnauthorized());
    }

    private String createArticle(RegisteredUser author, String title) throws Exception {
        String response = mockMvc.perform(post("/articles")
                        .header("Authorization", authHeader(author.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"article":{"title":"%s","description":"d","body":"b","tagList":[]}}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("article").get("slug").textValue();
    }
}
