package com.tomassirio.wanderer.auth.sso;

import com.tomassirio.wanderer.auth.client.WandererQueryClient;
import feign.FeignException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Derives a free username from an SSO email: local part, cleaned, suffixed on collision. */
@Component
@RequiredArgsConstructor
public class UsernameGenerator {

    static final int MIN_LENGTH = 3;
    static final int MAX_BASE_LENGTH = 40; // + "_1234" stays under the 50-char limit
    static final int MAX_ATTEMPTS = 5;

    private final WandererQueryClient wandererQueryClient;

    public String generate(String email) {
        String base = baseFrom(email);
        if (isAvailable(base)) {
            return base;
        }
        // ponytail: random suffix + availability check can race with a concurrent signup; the
        // command service's unique username constraint rejects the loser, which surfaces as a
        // failed SSO login that succeeds on retry.
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = base + "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (isAvailable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique username");
    }

    static String baseFrom(String email) {
        String local = email == null ? "" : email.split("@", 2)[0];
        String cleaned = local.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        if (cleaned.length() > MAX_BASE_LENGTH) {
            cleaned = cleaned.substring(0, MAX_BASE_LENGTH);
        }
        if (cleaned.length() < MIN_LENGTH) {
            cleaned = "wanderer" + cleaned;
        }
        return cleaned;
    }

    private boolean isAvailable(String username) {
        try {
            return wandererQueryClient.getUserByUsername(username, "basic") == null;
        } catch (FeignException e) {
            if (e.status() == 404) {
                return true;
            }
            throw new IllegalStateException("Failed to check username availability", e);
        }
    }
}
