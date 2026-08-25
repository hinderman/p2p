package com.project.backend.api.observability;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.Request;
import com.project.backend.application.port.in.RequestExecution;
import com.project.backend.application.port.in.RequestPipeline;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;

/**
 * Records application-use-case latency without exposing request payloads.
 *
 * <p>The request name is a bounded set of application classes, not user input.
 * Credentials, invitation tokens, financial values, and identifiers are never
 * used as metric tags or log values.
 */
public final class ApplicationRequestMetricsPipeline implements RequestPipeline {
    public static final String METRIC_NAME = "application.request.execution";

    private final MeterRegistry meterRegistry;

    public ApplicationRequestMetricsPipeline(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "Meter registry is required");
    }

    @Override
    public <R> R invoke(Request<R> request, RequestExecution<R> next) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            R result = next.execute(request);
            sample.stop(timerFor(request, "success"));
            return result;
        } catch (RuntimeException | Error failure) {
            sample.stop(timerFor(request, "failure"));
            throw failure;
        }
    }

    private Timer timerFor(Request<?> request, String outcome) {
        return Timer.builder(METRIC_NAME)
                .description("Application command and query execution time")
                .tags(
                        "request", request.getClass().getSimpleName(),
                        "kind", request instanceof Command<?> ? "command" : "query",
                        "outcome", outcome)
                .register(meterRegistry);
    }
}
