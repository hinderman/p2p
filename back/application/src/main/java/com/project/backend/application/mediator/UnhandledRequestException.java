package com.project.backend.application.mediator;

import java.util.Set;

/** Raised when a request reaches the mediator without an explicitly registered handler. */
public final class UnhandledRequestException extends IllegalArgumentException {

    public UnhandledRequestException(Class<?> requestType, Set<Class<?>> registeredRequestTypes) {
        super("No application handler is registered for " + requestType.getName()
                + ". Registered request types: " + registeredRequestTypes.stream()
                .map(Class::getSimpleName)
                .sorted()
                .toList());
    }
}
