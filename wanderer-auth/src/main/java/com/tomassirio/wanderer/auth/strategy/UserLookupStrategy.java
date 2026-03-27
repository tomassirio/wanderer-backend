package com.tomassirio.wanderer.auth.strategy;

import com.tomassirio.wanderer.commons.domain.User;
import java.util.Optional;

/** Strategy interface for looking up users by different identifiers. */
public interface UserLookupStrategy {

    /**
     * Checks if this strategy can handle the given identifier.
     *
     * @param identifier the user identifier (username, email, etc.)
     * @return true if this strategy can handle the identifier
     */
    boolean canHandle(String identifier);

    /**
     * Looks up a user by the given identifier.
     *
     * @param identifier the user identifier
     * @return Optional containing the user if found, empty otherwise
     */
    Optional<User> lookupUser(String identifier);
}
