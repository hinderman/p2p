package com.project.backend.application.dto;

/**
 * Intencion de <strong>leer</strong> status, sin efectos secundarios.
 *
 * <p>Queries are implemented as immutable {@code record}s (for example, {@code ListPayerLoansQuery}).
 * Separar consultas de comandos permite optimizar cada lado por separado: las
 * readModel pueden saltarse el aggregate y proyectar directamente si hace falta.
 */
public interface Query {
}
