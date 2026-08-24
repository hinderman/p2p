package com.project.backend.domain.service;

import java.time.LocalDate;

/**
 * Business-calendar policy used only to adjust contractual due dates. Holiday
 * data is deliberately supplied by the outer application, never hard-coded in
 * the amortization algorithm.
 */
@FunctionalInterface
public interface BusinessDayCalendar {

    boolean isBusinessDay(LocalDate date);
}
