package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;
import java.util.UUID;

/**
 * Resolves a verified external identity to a Wanderer user (linking or creating the account), and
 * mints Wanderer tokens for a resolved user at code-exchange time.
 */
public interface SsoService {

    /**
     * Finds, links or provisions the account for a verified external identity. Issues no tokens.
     *
     * @throws IllegalArgumentException if the email is unverified or the account is disabled
     * @throws IllegalStateException if provisioning fails
     */
    UUID resolveUser(ExternalIdentity identity);

    /**
     * Mints access and refresh tokens for a previously resolved user, reading roles fresh.
     *
     * @throws IllegalArgumentException if the credential is missing or the account is disabled
     * @throws IllegalStateException if the user lookup fails
     */
    LoginResponse issueTokens(UUID userId);
}
