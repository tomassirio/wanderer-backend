package com.tomassirio.wanderer.auth.domain;

import java.util.Locale;

/**
 * Normalizes email addresses so one account exists per email regardless of case or surrounding
 * whitespace. Used at every credential write and lookup in wanderer-auth.
 */
public final class EmailAddresses {

    private EmailAddresses() {}

    /** Trims and lowercases the email. Null-safe: null input returns null. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
