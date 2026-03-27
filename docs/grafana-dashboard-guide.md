# Wanderer Grafana Dashboard - Complete Setup Guide

**Project:** wanderer  
**Environments:** development, production  
**Services:** wanderer-auth, wanderer-command, wanderer-query  
**Date:** 2026-03-26

---

## Table of Contents

1. [Variables Setup](#variables-setup)
2. [Dashboard Structure](#dashboard-structure)
3. [Row 1: Overview Stats](#row-1-overview-stats)
4. [Row 2: Traffic Overview](#row-2-traffic-overview)
5. [Row 3: Performance](#row-3-performance)
6. [Row 4: Per-Service Details](#row-4-per-service-details)
7. [Row 5: Endpoints Analysis](#row-5-endpoints-analysis)
8. [Row 6: JVM & Database](#row-6-jvm--database)
9. [Alerts Configuration](#alerts-configuration)
10. [Best Practices](#best-practices)

---

## Variables Setup

### Variable 1: environment

```
Name: environment
Type: Query
Label: Environment
Query: label_values(up{project="wanderer"}, environment)
Multi-value: No
Include All: No
```

### Variable 2: service

```
Name: service
Type: Query
Label: Service
Query: label_values(up{project="wanderer", environment="$environment"}, component)
Multi-value: Yes
Include All: Yes
Custom all value: .*
```

**Note:** Make sure Multi-value is enabled and Format is default (not Regex).

---

## Dashboard Structure

```
┌─────────────────────────────────────────────────────────────┐
│ Row 1: Overview (4 stat panels)                             │
├─────────────────────────────────────────────────────────────┤
│ Row 2: Traffic Overview (2 time series)                     │
├─────────────────────────────────────────────────────────────┤
│ Row 3: Performance (1 time series with 3 queries)           │
├─────────────────────────────────────────────────────────────┤
│ Row 4: Per-Service Details (repeated panels)                │
├─────────────────────────────────────────────────────────────┤
│ Row 5: Endpoints Analysis (repeated tables)                 │
├─────────────────────────────────────────────────────────────┤
│ Row 6: JVM & Database (repeated panels, collapsed)          │
└─────────────────────────────────────────────────────────────┘
```

---

## Row 1: Overview Stats

**Purpose:** Quick at-a-glance metrics for all selected services  
**Repeat:** None (shows aggregated data)  
**Layout:** 4 panels side by side (6 columns each)

### Panel 1.1: Service Status

```yaml
Type: Stat
Title: "Service Status"
Width: 6
Height: 4

Query:
  up{project="wanderer", environment="$environment", component=~"$service"}

Standard Options:
  Unit: none
  Decimals: 0
  Color mode: Background

Value Mappings:
  0 → DOWN
  1 → UP

Thresholds:
  Base: null (gray)
  Red: 0 (DOWN)
  Green: 1 (UP)

Legend:
  Display: {{component}}
```

### Panel 1.2: Total Requests/sec

```yaml
Type: Stat
Title: "Total Requests/sec"
Width: 6
Height: 4

Query:
  sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component=~"$service"}[5m]))

Standard Options:
  Unit: reqps
  Decimals: 2
  Color mode: Value

Thresholds:
  Base: 0 (blue)
  Green: 10
  Yellow: 100
  Red: 1000
```

### Panel 1.3: Error Rate %

```yaml
Type: Stat
Title: "Error Rate %"
Width: 6
Height: 4

Query:
  (sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component=~"$service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component=~"$service"}[5m]))) * 100

Standard Options:
  Unit: percent (0-100)
  Decimals: 2
  Color mode: Background

Thresholds:
  Green: 0-1 (healthy)
  Yellow: 1-5 (warning)
  Red: 5-100 (critical)
```

### Panel 1.4: Avg Response Time

```yaml
Type: Stat
Title: "Avg Response Time"
Width: 6
Height: 4

Query:
  histogram_quantile(0.5, sum(rate(http_server_requests_seconds_bucket{project="wanderer", environment="$environment", component=~"$service"}[5m])) by (le)) * 1000

Standard Options:
  Unit: ms
  Decimals: 0
  Color mode: Value

Thresholds:
  Green: 0-500 (fast)
  Yellow: 500-2000 (acceptable)
  Red: 2000+ (slow)
```

---

## Row 2: Traffic Overview

**Purpose:** Compare traffic across all services  
**Repeat:** None (all services in same graph)  
**Layout:** 2 panels side by side or full width each

### Panel 2.1: Request Rate by Service

```yaml
Type: Time series
Title: "Request Rate by Service"
Width: 24 (full width) or 12 (half)
Height: 8

Query:
  sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component=~"$service"}[5m])) by (component)

Standard Options:
  Unit: reqps
  Decimals: 2
  Min: (blank)
  Max: (blank)

Graph Styles:
  Line width: 2
  Fill opacity: 10
  Point size: 5
  Show points: Auto
  Line interpolation: Linear

Legend:
  Mode: Table
  Placement: Bottom
  Values: Last, Max, Mean
  Display: {{component}}

Axis:
  Soft min: 0
  Label: req/s

Tooltip:
  Mode: All
  Sort: Descending
```

### Panel 2.2: Error Rate % by Service

```yaml
Type: Time series
Title: "Error Rate % by Service"
Width: 24 (full width) or 12 (half)
Height: 8

Query:
  (sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component=~"$service", status=~"5.."}[5m])) by (component) / sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component=~"$service"}[5m])) by (component)) * 100

Standard Options:
  Unit: percent (0-100)
  Decimals: 2
  Min: 0
  Max: (blank)

Graph Styles:
  Line width: 2
  Fill opacity: 30
  Point size: 5

Thresholds:
  Green: 0-1
  Yellow: 1-5
  Red: 5-100

Legend:
  Mode: Table
  Placement: Bottom
  Values: Last, Max
  Display: {{component}}

Axis:
  Soft min: 0
  Soft max: 10
  Label: Error %
```

---

## Row 3: Performance

**Purpose:** Detailed response time analysis  
**Repeat:** None (compare all services)  
**Layout:** 1 full-width panel

### Panel 3.1: Response Time Percentiles (P50, P95, P99)

```yaml
Type: Time series
Title: "Response Time Percentiles (P50, P95, P99)"
Width: 24
Height: 8

Query A - P50 (Median):
  histogram_quantile(0.5, sum(rate(http_server_requests_seconds_bucket{project="wanderer", environment="$environment", component=~"$service"}[5m])) by (component, le)) * 1000
  Legend: {{component}} - P50

Query B - P95:
  histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{project="wanderer", environment="$environment", component=~"$service"}[5m])) by (component, le)) * 1000
  Legend: {{component}} - P95

Query C - P99:
  histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{project="wanderer", environment="$environment", component=~"$service"}[5m])) by (component, le)) * 1000
  Legend: {{component}} - P99

Standard Options:
  Unit: ms
  Decimals: 0
  Min: (blank)
  Max: (blank)

Graph Styles:
  Fill opacity: 0 (no fill)

Overrides:
  Query A (P50):
    - Color: Blue
    - Line width: 1
    - Display name: {{component}} - P50
  
  Query B (P95):
    - Color: Orange
    - Line width: 3 (BOLD - most important!)
    - Display name: {{component}} - P95
  
  Query C (P99):
    - Color: Red
    - Line width: 2
    - Line style: Dashed
    - Display name: {{component}} - P99

Legend:
  Mode: Table
  Placement: Bottom
  Values: Current, Max, Mean

Axis:
  Soft min: 0
  Label: Response Time (ms)

Tooltip:
  Mode: All
  Sort: Descending
```

---

## Row 4: Per-Service Details

**Purpose:** Detailed metrics per service  
**Repeat:** Yes (by $service variable)  
**Layout:** Mixed (horizontal for gauges, vertical for graphs)

### Panel 4.1: Memory Usage Gauge

```yaml
Type: Gauge
Title: "Memory Usage - $service"
Width: 8 (3 per row)
Height: 6

Repeat:
  By variable: service
  Direction: Horizontal
  Max per row: 3

Query:
  (jvm_memory_used_bytes{project="wanderer", environment="$environment", component="$service", area="heap"} / jvm_memory_max_bytes{project="wanderer", environment="$environment", component="$service", area="heap"}) * 100

Standard Options:
  Unit: percent (0-100)
  Decimals: 1
  Min: 0
  Max: 100

Thresholds:
  Green: 0-70 (healthy)
  Yellow: 70-85 (warning)
  Red: 85-100 (critical)

Display:
  Show threshold labels: Yes
  Show threshold markers: Yes
```

### Panel 4.2: CPU Usage

```yaml
Type: Time series
Title: "CPU Usage - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  process_cpu_usage{project="wanderer", environment="$environment", component="$service"} * 100

Standard Options:
  Unit: percent (0-100)
  Decimals: 1
  Min: 0
  Max: 100

Graph Styles:
  Line width: 2
  Fill opacity: 20
  Gradient mode: Opacity

Legend:
  Display: {{component}}

Axis:
  Soft min: 0
  Label: CPU %

Thresholds (optional):
  Green: 0-70
  Yellow: 70-90
  Red: 90-100
```

### Panel 4.3: Request Rate (Detailed)

```yaml
Type: Time series
Title: "Request Rate - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component="$service"}[5m]))

Standard Options:
  Unit: reqps
  Decimals: 2

Graph Styles:
  Line width: 2
  Fill opacity: 10

Legend:
  Display: {{component}}

Axis:
  Soft min: 0
  Label: req/s
```

### Panel 4.4: Error Rate (Detailed)

```yaml
Type: Time series
Title: "Error Rate - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  (sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component="$service", status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component="$service"}[5m]))) * 100

Standard Options:
  Unit: percent (0-100)
  Decimals: 2

Graph Styles:
  Line width: 2
  Fill opacity: 30

Thresholds:
  Green: 0-1
  Yellow: 1-5
  Red: 5-100

Axis:
  Soft min: 0
  Label: Error %
```

---

## Row 5: Endpoints Analysis

**Purpose:** Identify hot paths and slow endpoints  
**Repeat:** Yes (by $service variable)  
**Layout:** Vertical (full width tables)

### Panel 5.1: Top 10 Endpoints by Traffic

```yaml
Type: Table
Title: "Top 10 Endpoints - $service"
Width: 24 (full width)
Height: 10

Repeat:
  By variable: service
  Direction: Vertical

Query:
  topk(10, sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component="$service"}[5m])) by (uri, method))

Standard Options:
  Unit: reqps
  Decimals: 2

Table Options:
  Show header: Yes
  Cell display mode: Auto

Column Overrides:
  method:
    - Display name: Method
    - Width: 100
  uri:
    - Display name: Endpoint
    - Width: Auto
  Value:
    - Display name: Requests/sec
    - Width: 150
    - Unit: reqps
```

### Panel 5.2: Top 10 Slowest Endpoints (P95)

```yaml
Type: Table
Title: "Slowest Endpoints (P95) - $service"
Width: 24 (full width)
Height: 10

Repeat:
  By variable: service
  Direction: Vertical

Query:
  topk(10, histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{project="wanderer", environment="$environment", component="$service"}[5m])) by (uri, method, le))) * 1000

Standard Options:
  Unit: ms
  Decimals: 0

Table Options:
  Show header: Yes
  Cell display mode: Auto

Column Overrides:
  method:
    - Display name: Method
    - Width: 100
  uri:
    - Display name: Endpoint
    - Width: Auto
  Value:
    - Display name: P95 Latency (ms)
    - Width: 150
    - Unit: ms

Thresholds:
  Green: 0-500
  Yellow: 500-2000
  Red: 2000+
```

### Panel 5.3: Status Code Distribution

```yaml
Type: Pie chart or Bar chart
Title: "Status Code Distribution - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component="$service"}[5m])) by (status)

Standard Options:
  Unit: reqps
  Decimals: 2

Legend:
  Mode: Table
  Placement: Right
  Values: Value, Percent
  Display: {{status}}

Pie Chart Options:
  Legend values: Value, Percent
  Display labels: Name, Percent
```

---

## Row 6: JVM & Database

**Purpose:** Deep JVM and database metrics  
**Repeat:** Yes (by $service variable)  
**Layout:** Vertical (full width)  
**Default:** Collapsed

### Panel 6.1: Database Connections

```yaml
Type: Time series
Title: "Database Connections - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query A - Active:
  hikaricp_connections_active{project="wanderer", environment="$environment", component="$service"}
  Legend: Active

Query B - Idle:
  hikaricp_connections_idle{project="wanderer", environment="$environment", component="$service"}
  Legend: Idle

Query C - Total (optional):
  hikaricp_connections{project="wanderer", environment="$environment", component="$service"}
  Legend: Total

Standard Options:
  Unit: short
  Decimals: 0

Graph Styles:
  Line width: 2
  Fill opacity: 0

Axis:
  Soft min: 0
  Label: Connections

Legend:
  Mode: Table
  Placement: Bottom
  Values: Current, Max, Mean
```

### Panel 6.2: DB Connection Wait Time

```yaml
Type: Time series
Title: "DB Connection Acquisition Time - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  rate(hikaricp_connections_acquire_seconds_sum{project="wanderer", environment="$environment", component="$service"}[5m]) / rate(hikaricp_connections_acquire_seconds_count{project="wanderer", environment="$environment", component="$service"}[5m]) * 1000

Standard Options:
  Unit: ms
  Decimals: 2

Graph Styles:
  Line width: 2
  Fill opacity: 20

Axis:
  Soft min: 0
  Label: Acquisition Time (ms)

Thresholds (optional):
  Green: 0-10
  Yellow: 10-100
  Red: 100+
```

### Panel 6.3: Heap Memory Usage

```yaml
Type: Time series
Title: "Heap Memory Usage - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query A - Used:
  jvm_memory_used_bytes{project="wanderer", environment="$environment", component="$service", area="heap"} / 1024 / 1024
  Legend: Used

Query B - Max:
  jvm_memory_max_bytes{project="wanderer", environment="$environment", component="$service", area="heap"} / 1024 / 1024
  Legend: Max

Standard Options:
  Unit: decmbytes
  Decimals: 0

Graph Styles:
  Line width: 2
  Fill opacity: 20

Axis:
  Soft min: 0
  Label: Memory (MB)

Legend:
  Mode: Table
  Placement: Bottom
  Values: Current, Max, Mean

Overrides:
  Query B (Max):
    - Line style: Dashed
    - Color: Gray
```

### Panel 6.4: Garbage Collection Time

```yaml
Type: Time series
Title: "GC Time - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  rate(jvm_gc_pause_seconds_sum{project="wanderer", environment="$environment", component="$service"}[5m])

Standard Options:
  Unit: s
  Decimals: 4

Graph Styles:
  Line width: 2
  Fill opacity: 20

Axis:
  Soft min: 0
  Label: GC Time (s/s)

Legend:
  Display: {{action}} - {{cause}}
```

### Panel 6.5: Thread Count

```yaml
Type: Time series
Title: "Thread Count - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query A - Live:
  jvm_threads_live_threads{project="wanderer", environment="$environment", component="$service"}
  Legend: Live

Query B - Daemon:
  jvm_threads_daemon_threads{project="wanderer", environment="$environment", component="$service"}
  Legend: Daemon

Standard Options:
  Unit: short
  Decimals: 0

Graph Styles:
  Line width: 2
  Fill opacity: 0

Axis:
  Soft min: 0
  Label: Threads

Legend:
  Mode: Table
  Placement: Bottom
  Values: Current, Max
```

### Panel 6.6: Class Loading

```yaml
Type: Time series
Title: "Loaded Classes - $service"
Width: 24 (full width)
Height: 8

Repeat:
  By variable: service
  Direction: Vertical

Query:
  jvm_classes_loaded_classes{project="wanderer", environment="$environment", component="$service"}

Standard Options:
  Unit: short
  Decimals: 0

Graph Styles:
  Line width: 2
  Fill opacity: 10

Axis:
  Soft min: 0
  Label: Classes
```

---

## Alerts Configuration

### Alert 1: Service Down

```yaml
Alert Name: Service Down - $environment
Query: up{project="wanderer", environment="$environment"} == 0
Condition: WHEN last() OF query(A) IS BELOW 1
Evaluate every: 1m
For: 2m
Severity: Critical
Message: "Service {{component}} is DOWN in {{environment}}"
```

### Alert 2: High Error Rate

```yaml
Alert Name: High Error Rate - $environment
Query: (sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", status=~"5.."}[5m])) by (component) / sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment"}[5m])) by (component)) * 100
Condition: WHEN last() OF query(A) IS ABOVE 5
Evaluate every: 1m
For: 5m
Severity: Warning
Message: "Error rate for {{component}} is {{value}}% in {{environment}}"
```

### Alert 3: High Memory Usage

```yaml
Alert Name: High Memory Usage - $environment
Query: (jvm_memory_used_bytes{project="wanderer", environment="$environment", area="heap"} / jvm_memory_max_bytes{project="wanderer", environment="$environment", area="heap"}) * 100
Condition: WHEN last() OF query(A) IS ABOVE 85
Evaluate every: 1m
For: 5m
Severity: Warning
Message: "Memory usage for {{component}} is {{value}}% in {{environment}}"
```

### Alert 4: Slow Response Time

```yaml
Alert Name: Slow Response Time - $environment
Query: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{project="wanderer", environment="$environment"}[5m])) by (component, le))
Condition: WHEN last() OF query(A) IS ABOVE 2
Evaluate every: 1m
For: 5m
Severity: Warning
Message: "P95 response time for {{component}} is {{value}}s in {{environment}}"
```

### Alert 5: Database Connection Pool Exhausted

```yaml
Alert Name: DB Pool Nearly Exhausted - $environment
Query: hikaricp_connections_active{project="wanderer", environment="$environment"} / hikaricp_connections_max{project="wanderer", environment="$environment"}
Condition: WHEN last() OF query(A) IS ABOVE 0.9
Evaluate every: 1m
For: 5m
Severity: Warning
Message: "DB connection pool for {{component}} is {{value | percentage}} full in {{environment}}"
```

---

## Best Practices

### Dashboard Settings

```yaml
Time Range: Last 1 hour
Refresh: 30s or 1m
Timezone: Browser Time or UTC
Theme: Dark (or Light)
```

### Panel Organization

1. **Overview First:** Most critical metrics at the top
2. **Comparison Next:** Compare services side-by-side
3. **Details Below:** Repeated panels for deep dives
4. **Heavy Metrics Last:** JVM/DB metrics collapsed by default

### Color Scheme

- **Green:** Healthy (< 70% utilization, < 1% errors)
- **Yellow:** Warning (70-85% utilization, 1-5% errors)
- **Red:** Critical (> 85% utilization, > 5% errors)
- **Blue:** Informational (request rates, counts)

### Legend Best Practices

- **Use Table mode** for better readability
- **Show Last, Max, Mean** for time series
- **Show Value and Percent** for pie charts
- **Keep legends at bottom** for full-width panels

### Query Optimization

- Use `[5m]` rate interval for most metrics
- Use `by (component)` to group by service
- Use `topk(10, ...)` to limit results
- Use `histogram_quantile` for percentiles

### Naming Conventions

- **Comparison panels:** "[Metric] by Service"
- **Repeated panels:** "[Metric] - $service"
- **Stats:** "[Metric] (short name)"
- **Tables:** "Top N [Metric] - $service"

---

## Quick Setup Checklist

- [ ] Create environment variable
- [ ] Create service variable (multi-value enabled)
- [ ] Create Row 1: Overview (4 stat panels)
- [ ] Create Row 2: Traffic (2 time series)
- [ ] Create Row 3: Performance (1 time series, 3 queries)
- [ ] Create Row 4: Per-Service (4 repeated panels)
- [ ] Create Row 5: Endpoints (3 repeated panels)
- [ ] Create Row 6: JVM & DB (6 repeated panels, collapsed)
- [ ] Configure alerts (5 critical alerts)
- [ ] Set dashboard refresh to 30s
- [ ] Save and test with different service selections
- [ ] Share with team!

---

## Additional Improvements

### Optional Panels

1. **Log Rate by Level:**
   ```promql
   sum(rate(logback_events_total{project="wanderer", environment="$environment", component="$service"}[5m])) by (level)
   ```

2. **System Load:**
   ```promql
   system_cpu_usage{project="wanderer", environment="$environment", component="$service"} * 100
   ```

3. **Request Duration Histogram:**
   ```promql
   sum(increase(http_server_requests_seconds_bucket{project="wanderer", environment="$environment", component="$service"}[5m])) by (le)
   ```

4. **Error Breakdown by Status:**
   ```promql
   sum(rate(http_server_requests_seconds_count{project="wanderer", environment="$environment", component="$service", status=~"5.."}[5m])) by (status)
   ```

### Dashboard Links

Add links to:
- Application logs (Kibana/Loki)
- Deployment history
- Related services
- Documentation

### Annotations

Configure annotations for:
- Deployments
- Alerts
- Incidents
- Configuration changes

---

## Troubleshooting

### No Data Appearing

1. Check Prometheus is scraping:
   ```promql
   up{project="wanderer", environment="$environment"}
   ```
2. Verify label names match (component vs application)
3. Check time range (try Last 15 minutes)
4. Verify environment variable has correct value

### Repeated Panels Not Working

1. Ensure Multi-value is enabled on $service variable
2. Use `component="$service"` (exact match) not `component=~"$service"`
3. Save dashboard after configuring repeat
4. Refresh browser

### Panels Resizing

1. Set "Max per row" for horizontal repeat
2. Manually resize panel before enabling repeat
3. Check gridPos width in JSON (w: 8 for 3 per row)

### Query Performance Issues

1. Reduce time range
2. Use longer rate intervals ([10m] instead of [5m])
3. Limit results with topk()
4. Add more specific label filters

---

## Resources

- **Prometheus Documentation:** https://prometheus.io/docs/
- **Grafana Documentation:** https://grafana.com/docs/
- **PromQL Cheat Sheet:** https://promlabs.com/promql-cheat-sheet/
- **Spring Boot Metrics:** https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics

---

**Last Updated:** 2026-03-26  
**Maintained By:** Platform Team  
**Questions:** Contact #platform-support

---

## Summary

This dashboard provides complete observability for the Wanderer microservices:

- ✅ **15+ essential panels** covering HTTP, JVM, and database metrics
- ✅ **5 critical alerts** for proactive monitoring
- ✅ **Multi-environment support** (dev/prod)
- ✅ **Per-service deep dives** with repeat functionality
- ✅ **Performance analysis** with P50/P95/P99 percentiles
- ✅ **Endpoint analysis** to identify hot paths and bottlenecks

Happy monitoring! 🎯📊
