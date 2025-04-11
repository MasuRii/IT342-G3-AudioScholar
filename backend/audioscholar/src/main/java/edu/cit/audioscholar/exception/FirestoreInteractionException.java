package edu.cit.audioscholar.exception;

public class FirestoreInteractionException extends RuntimeException {

    public FirestoreInteractionException(String message) {
        super(message);
    }

    public FirestoreInteractionException(String message, Throwable cause) {
        super(message, cause);
    }
}