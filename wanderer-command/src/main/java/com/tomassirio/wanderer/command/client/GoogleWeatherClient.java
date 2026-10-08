package com.tomassirio.wanderer.command.client;

import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the Google Weather API (v1).
 *
 * <p>Handles the REST call to the {@code currentConditions:lookup} endpoint and returns the raw
 * response as a {@link Map}. Authentication and URL construction are encapsulated here so that the
 * service layer only deals with domain logic.
 *
 * @see <a href="https://developers.google.com/maps/documentation/weather/current-conditions">Google
 *     Weather API</a>
 */
@Slf4j
@Component
public class GoogleWeatherClient {

    private static final String WEATHER_API_URL =
            "https://weather.googleapis.com/v1/currentConditions:lookup"
                    + "?key={key}&location.latitude={lat}&location.longitude={lon}";

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String apiKey;
    private final RestClient restClient;

    public GoogleWeatherClient(
            @Value("${google.maps.api-key:}") String apiKey, RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    /**
     * Calls the Google Weather API to fetch current conditions at the given coordinates.
     *
     * @param latitude the latitude of the location
     * @param longitude the longitude of the location
     * @return the raw API response as a Map, or {@code null} if the response is empty
     * @throws org.springframework.web.client.RestClientException on HTTP errors
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCurrentConditions(double latitude, double longitude) {
        return restClient
                .get()
                .uri(WEATHER_API_URL, apiKey, latitude, longitude)
                .retrieve()
                .body(Map.class);
    }

    /** Returns whether the client has an API key configured. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Strips the API key from a message to avoid leaking secrets in logs. */
    public String sanitize(String message) {
        if (message == null) {
            return "unknown error";
        }
        String sanitized = message.replace(apiKey, "[REDACTED]");
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200) + "...";
        }
        return sanitized;
    }
}
