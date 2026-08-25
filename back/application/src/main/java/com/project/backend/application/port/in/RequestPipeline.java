package com.project.backend.application.port.in;

import com.project.backend.application.dto.Request;

/**
 * Cross-cutting behavior around a dispatched command or query.
 *
 * <p>Implementations must not inspect or log request payloads because commands
 * may contain credentials, tokens, or financial data. The pipeline receives
 * only the request identity and a continuation.
 */
@FunctionalInterface
public interface RequestPipeline {

    <R> R invoke(Request<R> request, RequestExecution<R> next);
}
