package dev.ronaldofojr.monitor.service;

public final class Exceptions {

    private Exceptions() {
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String what, Object id) {
            super(what + " " + id + " nao encontrado(a)");
        }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) {
            super(message);
        }
    }
}
