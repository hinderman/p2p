package com.project.backend.domain.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/** Default calendar until a jurisdiction-specific holiday source is configured. */
public final class WeekendBusinessDayCalendar implements BusinessDayCalendar {

    @Override
    public boolean isBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "The date is required");
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
