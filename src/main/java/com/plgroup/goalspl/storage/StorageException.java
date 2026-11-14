package com.plgroup.goalspl.storage;

/** Fallo de la base de datos con un mensaje que se entiende en el registro del servidor. */
public final class StorageException extends Exception {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
