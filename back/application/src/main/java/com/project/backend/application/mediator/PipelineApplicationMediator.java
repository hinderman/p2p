package com.project.backend.application.mediator;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.Query;
import com.project.backend.application.dto.Request;
import com.project.backend.application.port.in.ApplicationMediator;
import com.project.backend.application.port.in.RequestExecution;
import com.project.backend.application.port.in.RequestHandler;
import com.project.backend.application.port.in.RequestPipeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit, framework-free mediator for the application's command and query handlers.
 *
 * <p>Handlers are registered by their declared request type rather than inferred
 * through erased generic metadata. Exactly one handler is allowed per concrete
 * request type and pipelines execute in the order in which they are supplied.
 */
public final class PipelineApplicationMediator implements ApplicationMediator {
    private final Map<Class<?>, RequestHandler<?, ?>> handlers;
    private final List<RequestPipeline> pipelines;

    public PipelineApplicationMediator(
            List<? extends RequestHandler<?, ?>> handlers,
            List<? extends RequestPipeline> pipelines) {
        this.handlers = registerHandlers(handlers);
        this.pipelines = List.copyOf(Objects.requireNonNull(pipelines, "Pipelines are required"));
    }

    @Override
    public <R> R send(Command<R> command) {
        return dispatch(command);
    }

    @Override
    public <R> R query(Query<R> query) {
        return dispatch(query);
    }

    private <R> R dispatch(Request<R> request) {
        Objects.requireNonNull(request, "Application request is required");
        RequestHandler<?, ?> handler = handlers.get(request.getClass());
        if (handler == null) {
            throw new UnhandledRequestException(request.getClass(), handlers.keySet());
        }

        RequestExecution<R> execution = ignored -> execute(handler, ignored);
        for (int index = pipelines.size() - 1; index >= 0; index--) {
            RequestPipeline pipeline = pipelines.get(index);
            RequestExecution<R> next = execution;
            execution = interceptedRequest -> pipeline.invoke(interceptedRequest, next);
        }
        return execution.execute(request);
    }

    @SuppressWarnings("unchecked")
    private static <R> R execute(RequestHandler<?, ?> handler, Request<R> request) {
        return ((RequestHandler<Request<R>, R>) handler).execute(request);
    }

    private static Map<Class<?>, RequestHandler<?, ?>> registerHandlers(
            List<? extends RequestHandler<?, ?>> requestHandlers) {
        Objects.requireNonNull(requestHandlers, "Request handlers are required");
        Map<Class<?>, RequestHandler<?, ?>> registered = new LinkedHashMap<>();
        for (RequestHandler<?, ?> handler : requestHandlers) {
            Objects.requireNonNull(handler, "Request handler is required");
            Class<?> requestType = Objects.requireNonNull(handler.requestType(), "Request handler type is required");
            RequestHandler<?, ?> previous = registered.putIfAbsent(requestType, handler);
            if (previous != null) {
                throw new IllegalArgumentException("Multiple handlers are registered for " + requestType.getName());
            }
        }
        return Map.copyOf(registered);
    }
}
