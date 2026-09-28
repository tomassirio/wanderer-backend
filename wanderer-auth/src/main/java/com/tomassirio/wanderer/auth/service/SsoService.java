package com.tomassirio.wanderer.auth.service;

import com.tomassirio.wanderer.auth.dto.LoginResponse;
import com.tomassirio.wanderer.auth.sso.ExternalIdentity;

/** Signs a user in from a verified external identity, linking or creating the account. */
public interface SsoService {

    /**
     * @throws IllegalArgumentException if the email is unverified or the account is disabled
     * @throws IllegalStateException if provisioning or user lookup fails
     */
    LoginResponse signIn(ExternalIdentity identity);
}
