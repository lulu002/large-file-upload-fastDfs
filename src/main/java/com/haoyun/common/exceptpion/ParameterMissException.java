package com.haoyun.common.exceptpion;

public class ParameterMissException extends Exception {

    public ParameterMissException() {
        super();
    }

    public ParameterMissException(String message) {
        super(message);
    }

    public ParameterMissException(String message, Throwable cause) {
        super(message, cause);
    }
}
