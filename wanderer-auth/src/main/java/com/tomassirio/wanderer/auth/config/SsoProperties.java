package com.tomassirio.wanderer.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedReturnUris exact URIs the SSO flow may redirect back to; the first is the default
 * @param loginCodeTtl lifetime of the one-time code handed to the client
 */
@ConfigurationProperties(prefix = "app.sso")
public record SsoProperties(List<String> allowedReturnUris, Duration loginCodeTtl) {

    public SsoProperties {
        if (allowedReturnUris == null || allowedReturnUris.isEmpty()) {
            throw new IllegalStateException(
                    "app.sso.allowed-return-uris must list at least one URI");
        }
        if (loginCodeTtl == null) {
            loginCodeTtl = Duration.ofSeconds(60);
        }
    }

    public String defaultReturnUri() {
        return allowedReturnUris.get(0);
    }
}
