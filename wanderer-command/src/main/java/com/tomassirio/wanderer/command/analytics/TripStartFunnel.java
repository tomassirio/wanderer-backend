package com.tomassirio.wanderer.command.analytics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Trip-start funnel analytics. There is no product-analytics vendor in the stack, so events are
 * Micrometer counters ({@code wanderer_trip_start_funnel_total{event,source}} on {@code
 * /actuator/prometheus}). Tags come from enums only, so cardinality stays bounded.
 */
@Component
@RequiredArgsConstructor
public class TripStartFunnel {

    public static final String METRIC = "wanderer.trip.start.funnel";

    public enum Event {
        READY_SCREEN_VIEWED,
        TRIP_STARTED,
        CLOSED_WITHOUT_STARTING,
        SAVED_AS_PLAN
    }

    public enum Source {
        SCRATCH,
        PLAN
    }

    private final MeterRegistry meterRegistry;

    public void record(Event event, Source source) {
        meterRegistry
                .counter(
                        METRIC,
                        "event",
                        event.name(),
                        "source",
                        source == null ? "NONE" : source.name())
                .increment();
    }
}
