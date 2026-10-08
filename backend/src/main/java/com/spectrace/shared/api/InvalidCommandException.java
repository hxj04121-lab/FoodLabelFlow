package com.spectrace.shared.api;

public class InvalidCommandException extends IllegalArgumentException {
    public InvalidCommandException(String message) { super(message); }
}
