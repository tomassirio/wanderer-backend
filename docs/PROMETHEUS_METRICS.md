# Prometheus Metrics Integration

This document describes how to use Prometheus metrics in the Wanderer Backend to create dashboards in Grafana.

## Overview

All three Wanderer Backend modules now expose Prometheus metrics through their `/actuator/prometheus` endpoint:

- **wanderer-auth** (port 8083): `http://localhost:8083/actuator/prometheus`
- **wanderer-command** (port 8081): `http://localhost:8081/actuator/prometheus`
- **wanderer-query** (port 8082): `http://localhost:8082/actuator/prometheus`

## Metrics Exposed

The following categories of metrics are automatically collected by Spring Boot Actuator and Micrometer:

### HTTP Metrics
- `http_server_requests_seconds_count` - Total number of HTTP requests
- `http_server_requests_seconds_sum` - Total time spent processing requests
- `http_server_requests_seconds_max` - Maximum request duration
- Tags: `method`, `uri`, `status`, `outcome`

### JVM Metrics
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_memory_max_bytes` - Maximum JVM memory
- `jvm_gc_pause_seconds_*` - Garbage collection metrics
- `jvm_threads_live` - Number of live threads
- `jvm_classes_loaded` - Number of loaded classes

### System Metrics
- `system_cpu_usage` - System CPU usage
- `process_cpu_usage` - Process CPU usage
- `process_uptime_seconds` - Process uptime

### Database Metrics (HikariCP)
- `hikaricp_connections_active` - Active database connections
- `hikaricp_connections_idle` - Idle database connections
- `hikaricp_connections_pending` - Pending database connections
- `hikaricp_connections_timeout_total` - Connection timeout count

### Application Metrics
- `application_ready_time_seconds` - Time taken to start the application
- `application_started_time_seconds` - Application startup timestamp

## Prometheus Configuration

Configure your Prometheus instance (from turing-pi-server) to scrape the metrics endpoints. Add the following to your `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'wanderer-auth'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['<wanderer-auth-host>:8083']
        labels:
          application: 'wanderer-auth'
          environment: 'production'

  - job_name: 'wanderer-command'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['<wanderer-command-host>:8081']
        labels:
          application: 'wanderer-command'
          environment: 'production'

  - job_name: 'wanderer-query'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['<wanderer-query-host>:8082']
        labels:
          application: 'wanderer-query'
          environment: 'production'
```

Replace `<wanderer-*-host>` with the actual hostname or IP address where each service is running.

## Grafana Dashboard Examples

### Request Rate Dashboard

Create a dashboard panel to monitor HTTP request rates:

```promql
# Requests per second by service
rate(http_server_requests_seconds_count[5m])

# Requests per second by endpoint
rate(http_server_requests_seconds_count{job=~"wanderer-.*"}[5m])

# Error rate (HTTP 4xx and 5xx)
rate(http_server_requests_seconds_count{status=~"[45].*"}[5m])
```

### Response Time Dashboard

Monitor response times across services:

```promql
# Average response time
rate(http_server_requests_seconds_sum[5m]) / rate(http_server_requests_seconds_count[5m])

# 95th percentile response time
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Maximum response time
http_server_requests_seconds_max
```

### JVM Memory Dashboard

Monitor JVM memory usage:

```promql
# Heap memory usage
jvm_memory_used_bytes{area="heap"}

# Non-heap memory usage
jvm_memory_used_bytes{area="nonheap"}

# Memory usage by pool
jvm_memory_used_bytes{id=~".*"}
```

### Database Connection Pool

Monitor database connection health:

```promql
# Active connections
hikaricp_connections_active

# Idle connections
hikaricp_connections_idle

# Connection pool usage percentage
(hikaricp_connections_active / hikaricp_connections_max) * 100
```

### System Resource Monitoring

Monitor system resources:

```promql
# CPU usage
process_cpu_usage
system_cpu_usage

# Thread count
jvm_threads_live

# Garbage collection time
rate(jvm_gc_pause_seconds_sum[5m])
```

## Recommended Grafana Dashboards

You can import pre-built Spring Boot dashboards from Grafana:

1. **Spring Boot 2.1 Statistics** (ID: 10280)
2. **JVM (Micrometer)** (ID: 4701)
3. **Spring Boot APM Dashboard** (ID: 12900)

To import:
1. Go to Grafana → Dashboards → Import
2. Enter the dashboard ID
3. Select your Prometheus data source
4. Click Import

## Custom Metrics

You can add custom metrics to your services using Micrometer's `MeterRegistry`. Example:

```java
@Service
public class MyService {
    private final Counter customCounter;
    private final Timer customTimer;
    
    public MyService(MeterRegistry meterRegistry) {
        this.customCounter = Counter.builder("custom_operation_total")
            .description("Total custom operations")
            .tag("service", "wanderer-command")
            .register(meterRegistry);
            
        this.customTimer = Timer.builder("custom_operation_duration")
            .description("Custom operation duration")
            .tag("service", "wanderer-command")
            .register(meterRegistry);
    }
    
    public void performOperation() {
        customTimer.record(() -> {
            // Your operation code
            customCounter.increment();
        });
    }
}
```

## Alerting Examples

Configure Prometheus alerts for critical conditions:

```yaml
groups:
  - name: wanderer_alerts
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.*"}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          
      - alert: HighMemoryUsage
        expr: (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High JVM heap usage"
          
      - alert: DatabaseConnectionPoolExhaustion
        expr: hikaricp_connections_idle == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool exhausted"
```

## Security Considerations

The `/actuator/prometheus` endpoint is currently exposed without authentication. For production deployments, consider:

1. **Network isolation**: Restrict access to Prometheus endpoints using firewall rules
2. **Spring Security**: Add authentication to actuator endpoints
3. **Reverse proxy**: Use a reverse proxy (nginx, traefik) to add authentication

Example Spring Security configuration:

```java
@Configuration
public class ActuatorSecurityConfig {
    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/prometheus").hasRole("METRICS")
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
```

## Testing Metrics Endpoint

Verify the metrics endpoint is working:

```bash
# Test wanderer-auth metrics
curl http://localhost:8083/actuator/prometheus

# Test wanderer-command metrics
curl http://localhost:8081/actuator/prometheus

# Test wanderer-query metrics
curl http://localhost:8082/actuator/prometheus
```

You should see Prometheus-formatted metrics output.

## Troubleshooting

### Metrics endpoint returns 404

Ensure the following properties are set in `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.metrics.export.prometheus.enabled=true
```

### No metrics appearing in Prometheus

1. Check Prometheus targets are up: `http://<prometheus-host>:9090/targets`
2. Verify network connectivity between Prometheus and services
3. Check Prometheus logs for scrape errors
4. Verify the metrics path and port configuration

### Missing specific metrics

Some metrics only appear after the application has received traffic or performed certain operations. Generate some load to see all metrics.
