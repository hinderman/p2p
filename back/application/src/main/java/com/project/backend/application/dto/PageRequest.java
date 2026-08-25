package com.project.backend.application.dto;

/**
 * Framework-free pagination request.
 *
 * <p>The application module deliberately has no Spring dependency, so it defines
 * its own contract instead of exposing {@code org.springframework.data.domain.Pageable}
 * to use cases.
 *
 * <p>Invalid values are rejected instead of silently clamped: a client asking for
 * a negative page or an unbounded size has a defect, and returning a quietly
 * corrected result makes that defect harder to find. The upper bound is a server
 * concern, not a client one: it is what stops a single request from materializing
 * an entire table.
 */
public record PageRequest(int page, int size) {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("The page index cannot be negative");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("The page size must be between 1 and " + MAX_SIZE);
        }
    }

    /** Applies the defaults for absent HTTP query parameters, keeping them defined in one place. */
    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    /** Widened on purpose: {@code page * size} overflows an int for large page indexes. */
    public long offset() {
        return (long) page * size;
    }
}
