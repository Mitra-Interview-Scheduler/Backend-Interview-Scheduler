package com.nemal.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Turns an exception into a message that is safe to return to API clients.
 *
 * Messages we write ourselves (plain RuntimeException / IllegalArgumentException /
 * IllegalStateException, ResponseStatusException reasons, and com.nemal exceptions) are
 * meant for users and pass through. Anything else (database, Hibernate, NPE, library
 * errors) can leak SQL, table names or stack details, so it is logged and replaced.
 */
public final class ApiErrors {

    public static final String GENERIC_MESSAGE = "Something went wrong. Please try again.";

    private static final Logger logger = LoggerFactory.getLogger(ApiErrors.class);

    private static final Set<Class<?>> USER_FACING_TYPES = Set.of(
            RuntimeException.class,
            IllegalArgumentException.class,
            IllegalStateException.class
    );

    private ApiErrors() {
    }

    public static String safeMessage(Throwable error) {
        if (error == null) {
            return GENERIC_MESSAGE;
        }
        String message = error instanceof ResponseStatusException statusException
                ? statusException.getReason()
                : error.getMessage();
        if (isUserFacing(error) && message != null && !message.isBlank()) {
            return message;
        }
        logger.error("Unexpected error returned to client as a generic message", error);
        return GENERIC_MESSAGE;
    }

    private static boolean isUserFacing(Throwable error) {
        return error instanceof ResponseStatusException
                || USER_FACING_TYPES.contains(error.getClass())
                || error.getClass().getName().startsWith("com.nemal.");
    }
}
