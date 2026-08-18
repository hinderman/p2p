/**
 * Translation between domain aggregates and JPA entities.
 *
 * <p>Es la costura que mantiene los dos modelos independientes. Cada mapper
 * convierte en ambos sentidos y es el unico punto que conoce las dos formas,
 * de modo que un cambio de esquema queda contenido aqui.
 */
package com.project.backend.infrastructure.persistence.mapper;
