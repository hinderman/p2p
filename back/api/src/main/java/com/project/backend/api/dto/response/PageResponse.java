package com.project.backend.api.dto.response;

import java.util.List;

/**
 * Pagination envelope shared by every paginated endpoint.
 *
 * <p>It is a dedicated API type rather than the application {@code Page}: the HTTP
 * contract must stay stable even if the internal representation changes.
 * {@code totalPages} and {@code hasNext} are derived server-side so no client has
 * to reimplement that arithmetic.
 *
 * @param content       elements of the requested page
 * @param page          zero-based index of the returned page
 * @param size          maximum number of elements per page
 * @param totalElements elements matching the query across every page
 * @param totalPages    number of pages available for the current size
 * @param hasNext       whether another page follows this one
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
