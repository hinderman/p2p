package com.project.backend.application.port.in;

import com.project.backend.application.dto.Command;
import com.project.backend.application.dto.Query;

/**
 * Single typed entry point to application use cases.
 *
 * <p>Keeping {@link #send(Command)} and {@link #query(Query)} separate makes
 * the CQRS intent visible at every input adapter while allowing both paths to
 * share safe, framework-independent pipeline behavior.
 */
public interface ApplicationMediator {

    <R> R send(Command<R> command);

    <R> R query(Query<R> query);
}
