package com.codereview.agent.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void createsReview() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"prNumber\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/reviews/")))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.findings.length()").value(4));
    }

    @Test
    void missingPrNumberIsBadRequest() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPullRequestIsNotFound() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content("{\"prNumber\": 99}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerWithoutRepoIsBadRequest() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"acme\", \"prNumber\": 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidOwnerNameIsBadRequest() throws Exception {
        mvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\": \"../evil\", \"repo\": \"x\", \"prNumber\": 1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownReviewIsNotFound() throws Exception {
        mvc.perform(get("/reviews/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
