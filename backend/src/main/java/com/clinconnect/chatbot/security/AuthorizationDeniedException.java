package com.clinconnect.chatbot.security;

/** Thrown when a subject is not authorized for a tool's scope. Fails closed. */
public class AuthorizationDeniedException extends RuntimeException {

    public AuthorizationDeniedException(String message) {
        super(message);
    }
}
