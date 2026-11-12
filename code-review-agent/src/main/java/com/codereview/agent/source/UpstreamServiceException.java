package com.codereview.agent.source;

/** Thrown when an outside service we depend on (like GitHub) fails or refuses the request. */
public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
