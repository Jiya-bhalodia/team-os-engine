package com.placement.teama.service;

/** Raised when a caller reuses a key for different logical eligibility work. */
public class IdempotencyConflictException extends IllegalArgumentException {
    public IdempotencyConflictException(String message) { super(message); }
}
