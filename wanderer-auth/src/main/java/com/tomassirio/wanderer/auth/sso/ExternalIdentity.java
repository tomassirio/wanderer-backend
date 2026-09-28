package com.tomassirio.wanderer.auth.sso;

/**
 * Provider-neutral identity produced by an {@link SsoIdentityMapper}.
 *
 * @param provider registration id, e.g. "google"
 * @param subject stable, provider-unique user id (never the email)
 * @param email email reported by the provider, may be null
 * @param emailVerified whether the provider vouches for the email
 * @param name display name, may be null
 */
public record ExternalIdentity(
        String provider, String subject, String email, boolean emailVerified, String name) {}
