package com.project.backend.application.port.in.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.port.in.UseCase;

/** CQRS write side: receives an intent and may change state. */
@FunctionalInterface
public interface CommandHandler<C extends Command, R> extends UseCase<C, R> {
}
