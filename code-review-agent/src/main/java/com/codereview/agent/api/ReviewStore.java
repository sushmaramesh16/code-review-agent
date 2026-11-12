package com.codereview.agent.api;

import com.codereview.agent.model.ReviewResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory storage for finished reviews. Later replaced by DynamoDB. */
@Component
public class ReviewStore {

    private final Map<String, ReviewResult> reviews = new ConcurrentHashMap<>();

    public void save(ReviewResult review) {
        reviews.put(review.id(), review);
    }

    public Optional<ReviewResult> find(String id) {
        return Optional.ofNullable(reviews.get(id));
    }
}
