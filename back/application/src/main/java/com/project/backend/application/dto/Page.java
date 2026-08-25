package com.project.backend.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable slice of a larger result set, together with the totals a client needs
 * to navigate it without holding the whole collection.
 *
 * @param content       elements of this page, never null and always copied
 * @param page          zero-based index of this page
 * @param size          maximum number of elements a page may hold
 * @param totalElements elements matching the query across every page
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {
    public Page {
        content = List.copyOf(Objects.requireNonNull(content, "The page content is required"));
        if (page < 0) {
            throw new IllegalArgumentException("The page index cannot be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("The page size must be positive");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("The total element count cannot be negative");
        }
    }

    public static <T> Page<T> of(List<T> content, PageRequest request, long totalElements) {
        return new Page<>(content, request.page(), request.size(), totalElements);
    }

    /** Page beyond the last one, or a query with no matches: the total still lets the client correct itself. */
    public static <T> Page<T> empty(PageRequest request, long totalElements) {
        return new Page<>(List.of(), request.page(), request.size(), totalElements);
    }

    /** Integer arithmetic on purpose: rounding a double division loses precision for large totals. */
    public int totalPages() {
        return (int) ((totalElements + size - 1) / size);
    }

    public boolean hasNext() {
        return page + 1L < totalPages();
    }

    /** Converts the elements while preserving the pagination metadata, so adapters never recompute it. */
    public <R> Page<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "The mapper is required");
        return new Page<>(content.stream().<R>map(mapper).toList(), page, size, totalElements);
    }
}
