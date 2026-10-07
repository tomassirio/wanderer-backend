package com.tomassirio.wanderer.command.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tomassirio.wanderer.command.analytics.TripStartFunnel;
import com.tomassirio.wanderer.commons.exception.GlobalExceptionHandler;
import com.tomassirio.wanderer.commons.utils.MockMvcTestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class AnalyticsControllerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcTestUtils.buildMockMvcWithCurrentUserResolver(
                        new AnalyticsController(new TripStartFunnel(registry)),
                        new GlobalExceptionHandler());
    }

    private double count(String event, String source) {
        var counter =
                registry.find(TripStartFunnel.METRIC)
                        .tag("event", event)
                        .tag("source", source)
                        .counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void recordEvent_shouldIncrementFunnelCounter() throws Exception {
        mockMvc.perform(
                        post("/api/1/analytics/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"event\":\"READY_SCREEN_VIEWED\",\"source\":\"PLAN\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        post("/api/1/analytics/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"event\":\"SAVED_AS_PLAN\"}"))
                .andExpect(status().isNoContent());

        assertThat(count("READY_SCREEN_VIEWED", "PLAN")).isEqualTo(1);
        assertThat(count("SAVED_AS_PLAN", "NONE")).isEqualTo(1);
    }

    @Test
    void recordEvent_tripStartedIsServerOnly() throws Exception {
        mockMvc.perform(
                        post("/api/1/analytics/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"event\":\"TRIP_STARTED\",\"source\":\"SCRATCH\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post("/api/1/analytics/events")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"event\":\"NOPE\"}"))
                .andExpect(status().isBadRequest());

        assertThat(count("TRIP_STARTED", "SCRATCH")).isZero();
    }
}
