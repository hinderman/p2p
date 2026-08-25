package com.project.backend.application.port.in;

import com.project.backend.application.dto.Request;

/** Continuation supplied to a mediator pipeline behavior. */
@FunctionalInterface
public interface RequestExecution<R> {

    R execute(Request<R> request);
}
