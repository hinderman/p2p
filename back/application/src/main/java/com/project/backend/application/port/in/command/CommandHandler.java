package com.project.backend.application.port.in.command;

import com.project.backend.application.dto.Command;
import com.project.backend.application.port.in.RequestHandler;

/** CQRS write side: receives an intent and may change state. */
public interface CommandHandler<C extends Command<R>, R> extends RequestHandler<C, R> {
}
