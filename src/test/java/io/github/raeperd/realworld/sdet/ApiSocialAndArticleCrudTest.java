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
import tools.jackson.databind.ObjectMapper;

import static io.github.raeperd.realworld.sdet.support.ApiTestSupport.authHeader;
import static io.github.raeperd.realworld.sdet.support.ApiTestSupport.uniqueSuffix;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Profiles, favorites, and core article mutations with auth.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiSocialAndArticleCrudTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST/DELETE /profiles/{user}/follow toggles following flag")
    void followAndUnfollowProfile() throws Exception {
        String suffix = uniqueSuffix();
        RegisteredUser reader = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "reader-" + suffix + "@test.com", "reader_" + suffix, "password123");
        RegisteredUser celeb = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "celeb-" + suffix + "@test.com", "celeb_" + suffix, "password123");

        mockMvc.perform(post("/profiles/" + celeb.username() + "/follow")
                        .header("Authorization", authHeader(reader.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("profile.following", is(true)));

        mockMvc.perform(delete("/profiles/" + celeb.username() + "/follow")
                        .header("Authorization", authHeader(reader.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("profile.following", is(false)));
    }

    private String createArticle(RegisteredUser author, String title) throws Exception {
        String response = mockMvc.perform(post("/articles")
                        .header("Authorization", authHeader(author.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"article":{"title":"%s","description":"d","body":"b","tagList":["t"]}}
                                """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("article").get("slug").textValue();
    }

    @Test
    @DisplayName("POST/DELETE /articles/{slug}/favorite toggles favorited flag")
    void favoriteAndUnfavoriteArticle() throws Exception {
        String suffix = uniqueSuffix();
        RegisteredUser author = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "fav-" + suffix + "@test.com", "fav_author_" + suffix, "password123");

        String slug = createArticle(author, "Favorite Test Article " + suffix);

        mockMvc.perform(post("/articles/" + slug + "/favorite")
                        .header("Authorization", authHeader(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("article.favorited", is(true)));

        mockMvc.perform(delete("/articles/" + slug + "/favorite")
                        .header("Authorization", authHeader(author.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("article.favorited", is(false)));
    }

    @Test
    @DisplayName("PUT /articles/{slug} updates body for author")
    void authorCanUpdateOwnArticle() throws Exception {
        String suffix = uniqueSuffix();
        RegisteredUser author = ApiTestSupport.registerUser(mockMvc, objectMapper,
                "edit-" + suffix + "@test.com", "editor_" + suffix, "password123");

        String slug = createArticle(author, "Editable Article " + suffix);

        mockMvc.perform(put("/articles/" + slug)
                        .header("Authorization", authHeader(author.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"article\":{\"body\":\"updated body\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("article.body", is("updated body")));
    }
}
