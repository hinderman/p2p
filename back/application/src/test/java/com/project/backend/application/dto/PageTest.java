package com.project.backend.application.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTest {

    @Test
    void rejects_a_page_request_outside_the_supported_bounds() {
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(-1, 20));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageRequest(0, PageRequest.MAX_SIZE + 1));
    }

    @Test
    void applies_the_defaults_for_absent_query_parameters() {
        PageRequest request = PageRequest.of(null, null);

        assertEquals(0, request.page());
        assertEquals(PageRequest.DEFAULT_SIZE, request.size());
    }

    @Test
    void computes_the_offset_without_overflowing_an_int() {
        assertEquals(0L, new PageRequest(0, 20).offset());
        assertEquals(40L, new PageRequest(2, 20).offset());
        assertEquals(2_147_483_600L, new PageRequest(21_474_836, 100).offset());
    }

    @Test
    void derives_the_navigation_metadata_from_the_totals() {
        Page<String> page = Page.of(List.of("a", "b"), new PageRequest(0, 2), 5);

        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
        assertFalse(Page.of(List.of("e"), new PageRequest(2, 2), 5).hasNext());
    }

    @Test
    void reports_a_single_empty_page_when_nothing_matches() {
        Page<String> page = Page.empty(new PageRequest(0, 20), 0);

        assertEquals(0, page.totalPages());
        assertFalse(page.hasNext());
        assertTrue(page.content().isEmpty());
    }

    @Test
    void keeps_the_total_when_the_requested_page_is_past_the_end() {
        Page<String> page = Page.empty(new PageRequest(9, 20), 5);

        assertEquals(5L, page.totalElements());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    void copies_the_content_so_a_caller_cannot_mutate_a_returned_page() {
        List<String> mutable = new ArrayList<>(List.of("a"));
        Page<String> page = Page.of(mutable, new PageRequest(0, 20), 1);
        mutable.add("b");

        assertEquals(List.of("a"), page.content());
        assertThrows(UnsupportedOperationException.class, () -> page.content().add("c"));
    }

    @Test
    void maps_the_elements_while_preserving_the_pagination_metadata() {
        Page<Integer> mapped = Page.of(List.of("a", "bb"), new PageRequest(1, 2), 7).map(String::length);

        assertEquals(List.of(1, 2), mapped.content());
        assertEquals(1, mapped.page());
        assertEquals(2, mapped.size());
        assertEquals(7L, mapped.totalElements());
        assertEquals(4, mapped.totalPages());
    }
}
