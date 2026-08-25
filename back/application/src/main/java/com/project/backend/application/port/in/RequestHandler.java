package com.project.backend.application.port.in;

import com.project.backend.application.dto.Request;

/**
 * Typed application use case that can be registered with the mediator.
 *
 * <p>{@link #requestType()} is intentionally explicit. It avoids reflection on
 * erased generic signatures and lets application startup reject duplicate
 * request handlers deterministically.
 */
public interface RequestHandler<I extends Request<R>, R> extends UseCase<I, R> {

    Class<I> requestType();
}
