package com.almajd.accounting.backup;

/**
 * Thrown by {@link SimpleJson} when a JSON document cannot be parsed.
 * Kept dependency-free (no android / org.json) so it is unit-testable on a
 * plain JVM.
 */
public class JsonException extends Exception {

    public JsonException(String message) {
        super(message);
    }
}
