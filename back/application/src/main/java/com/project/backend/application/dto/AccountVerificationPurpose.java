package com.project.backend.application.dto;

/**
 * Why a single-use account token was issued.
 *
 * <p>A token is only ever accepted for the purpose it was issued for, so a link
 * that proves ownership of an address can never be replayed as, say, a password
 * reset.
 */
public enum AccountVerificationPurpose {
    EMAIL_VERIFICATION
}
