package com.codereview.agent.api;

import com.codereview.agent.source.UpstreamServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** GitHub answered with an error (rate limit, server error): 502 Bad Gateway. */
    @ExceptionHandler(UpstreamServiceException.class)
    public ProblemDetail upstreamFailed(UpstreamServiceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** The LLM couldn't be reached: 503 Service Unavailable. */
    @ExceptionHandler(RestClientException.class)
    public ProblemDetail llmUnavailable(RestClientException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The language model is unavailable: " + e.getMessage());
    }
}
