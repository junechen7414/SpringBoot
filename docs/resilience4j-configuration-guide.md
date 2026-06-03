# 🛡️ Resilience4j Configuration Guide

> **Last Updated**: 2026-06-03  
> **Author**: Bobby  
> **Project**: Spring Boot Demo Application

---

## 📋 Table of Contents

1. [Introduction & Overview](#-introduction--overview)
2. [Production Environment Configuration](#-production-environment-configuration)
3. [Learning/Demo Configuration](#-learningdemo-configuration)
4. [Configuration Comparison Tables](#-configuration-comparison-tables)
5. [JMeter Testing Guide](#-jmeter-testing-guide)
6. [Troubleshooting Guide](#-troubleshooting-guide)
7. [Quick Reference](#-quick-reference)

---

## 🎯 Introduction & Overview

### What is Resilience4j?

Resilience4j is a lightweight fault tolerance library designed for Java applications. This project uses three core patterns to protect our microservices:

### The Three Patterns

#### 1️⃣ **RateLimiter** - Request Rate Control
- **Purpose**: Limits the number of requests per time period (e.g., 1000 requests/second)
- **Use Case**: Prevent API abuse, protect backend from traffic spikes
- **Failure Response**: HTTP 429 (Too Many Requests)
- **Analogy**: Like a bouncer at a club - only allows X people per minute

#### 2️⃣ **Bulkhead** - Concurrent Call Control
- **Purpose**: Limits the number of parallel/concurrent executions
- **Use Case**: Prevent thread pool exhaustion, control resource usage
- **Failure Response**: HTTP 503 (Service Unavailable)
- **Analogy**: Like a parking lot - only X cars can park at the same time

#### 3️⃣ **CircuitBreaker** - Cascading Failure Protection
- **Purpose**: Stops calling a failing service to prevent cascading failures
- **Use Case**: Protect against downstream service failures
- **Failure Response**: HTTP 503 (Service Unavailable - Circuit Open)
- **Analogy**: Like an electrical circuit breaker - trips when too many failures occur

### 🔄 Configuration Hierarchy & Execution Order

```
Request → RateLimiter → Bulkhead → CircuitBreaker → Service Method
            ↓              ↓            ↓
          429 Error    503 Error    503 Error
```

**Execution Flow**:
1. **RateLimiter** checks first: "Have we exceeded requests/second limit?"
2. **Bulkhead** checks second: "Are too many requests running concurrently?"
3. **CircuitBreaker** checks third: "Is the downstream service healthy?"
4. If all pass → Execute the actual service method

### 🤔 When to Use Each Pattern

| Pattern | Use When | Don't Use When |
|---------|----------|----------------|
| **RateLimiter** | • Protecting public APIs<br>• Preventing abuse<br>• Enforcing SLA limits | • Internal services only<br>• Trusted clients only |
| **Bulkhead** | • Limited resources (DB connections, threads)<br>• Preventing resource exhaustion<br>• Isolating critical operations | • Unlimited resources<br>• Simple CRUD operations |
| **CircuitBreaker** | • Calling external services<br>• Downstream dependencies<br>• Network operations | • Local operations<br>• Database queries (use timeouts instead) |

### 📊 Pattern Relationships

**Critical Rule**: `Bulkhead max-concurrent-calls >= RateLimiter limit-for-period`

**Why?** 
- RateLimiter allows X requests per second
- Bulkhead must handle at least X concurrent requests
- Otherwise, valid requests will be rejected by Bulkhead

**Example**:
```yaml
ratelimiter:
  instances:
    api-read:
      limit-for-period: 1000        # Allow 1000 requests/second
bulkhead:
  instances:
    api-read:
      max-concurrent-calls: 1000    # Must be >= 1000 to handle them all
```

---

## 🏭 Production Environment Configuration

### Configuration Principles

#### 1. Calculate Based on System Capacity

**Step 1: Measure Your System's Capacity**
```bash
# Use load testing tools to find:
# - Maximum requests/second your server can handle
# - Average response time
# - Thread pool size
# - Database connection pool size
```

**Step 2: Apply Safety Margin (70-80% Rule)**
```
Production Limit = Measured Capacity × 0.75
```

**Example Calculation**:
- Load test shows server handles 1500 req/s at 95th percentile < 200ms
- Apply 75% safety margin: 1500 × 0.75 = **1125 req/s**
- Round down for safety: **1000 req/s**

#### 2. Parameter Relationships

**🔑 Critical Rule: Bulkhead ≥ RateLimiter**

The Bulkhead limit must be **greater than or equal to** the RateLimiter limit. This is because:

1. **Execution Order**: RateLimiter checks first, then Bulkhead
2. **If Bulkhead < RateLimiter**: Bulkhead becomes the bottleneck and will reject valid requests that passed RateLimiter
3. **RateLimiter Never Triggers**: You'll never see 429 errors, only 503 errors from Bulkhead
4. **Defeats the Purpose**: RateLimiter's rate control becomes meaningless

**Example of the Problem**:
```
RateLimiter: 1000 req/s
Bulkhead: 500 concurrent

Result: Only 500 requests can run concurrently
→ RateLimiter allows 1000/s, but Bulkhead blocks 500/s
→ You'll see 503 errors (Bulkhead full) instead of 429 (Rate limited)
→ RateLimiter configuration is wasted
```

```yaml
# ✅ CORRECT Configuration
resilience4j:
  ratelimiter:
    instances:
      api-read:
        limit-for-period: 1000              # 1000 requests/second
  bulkhead:
    instances:
      api-read:
        max-concurrent-calls: 1000          # >= RateLimiter limit ✅
        # Bulkhead won't interfere with RateLimiter's rate control
```

```yaml
# ❌ WRONG Configuration
resilience4j:
  ratelimiter:
    instances:
      api-read:
        limit-for-period: 1000              # 1000 requests/second
  bulkhead:
    instances:
      api-read:
        max-concurrent-calls: 500           # < RateLimiter limit ❌
        # Problem: Bulkhead becomes the bottleneck!
        # Valid requests that passed RateLimiter will be rejected by Bulkhead
        # You'll never see 429 errors, only 503 errors
```

**Best Practice**:
- Set Bulkhead = RateLimiter (or slightly higher)
- This ensures RateLimiter controls the rate, Bulkhead protects resources
- Both patterns work as intended without interfering with each other

#### 3. Different API Types Configuration

### 📖 Read Operations (High Throughput)

**Characteristics**:
- Fast response time (< 100ms)
- No data modification
- Can handle high concurrency
- Cacheable

**Configuration Strategy**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 1000              # High limit for reads
        limit-refresh-period: 1s
        timeout-duration: 0ms               # Fail-fast
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 1000          # Match RateLimiter
        max-wait-duration: 0ms              # Fail-fast
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 100            # Large window for stability
        minimum-number-of-calls: 50         # Need more data points
        failure-rate-threshold: 50          # 50% failure rate
        wait-duration-in-open-state: 60s    # 1 minute recovery time
```

**Reasoning**:
- **RateLimiter 1000/s**: Read operations are fast, can handle high volume
- **Bulkhead 1000**: Matches RateLimiter to avoid unnecessary rejections
- **CircuitBreaker window 100**: Larger window for more stable failure detection
- **Minimum calls 50**: Need sufficient data before opening circuit

### ✍️ Write Operations (Lower Throughput)

**Characteristics**:
- Slower response time (100-500ms)
- Data modification (DB writes)
- Transaction overhead
- Cannot be cached

**Configuration Strategy**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      product-write:
        limit-for-period: 200               # Lower limit for writes
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      product-write:
        max-concurrent-calls: 200           # Match RateLimiter
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 50             # Smaller window
        minimum-number-of-calls: 20         # Faster detection
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s    # Shorter recovery time
```

**Reasoning**:
- **RateLimiter 200/s**: Write operations are slower, need lower limit
- **Bulkhead 200**: Matches RateLimiter, prevents DB connection exhaustion
- **CircuitBreaker window 50**: Smaller window for faster failure detection
- **Minimum calls 20**: Detect write failures quickly

### 🔧 Critical Operations (Strict Control)

**Characteristics**:
- Complex business logic
- Multiple validations
- External service calls
- High resource consumption

**Configuration Strategy**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      order-create:
        limit-for-period: 100               # Very strict limit
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      order-create:
        max-concurrent-calls: 50            # Even stricter concurrency
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      OrderService:
        sliding-window-size: 30
        minimum-number-of-calls: 10         # Quick detection
        failure-rate-threshold: 40          # Lower threshold (40%)
        wait-duration-in-open-state: 60s
```

**Reasoning**:
- **RateLimiter 100/s**: Complex operations need strict rate limiting
- **Bulkhead 50**: Lower than RateLimiter to prevent resource exhaustion
- **CircuitBreaker threshold 40%**: More sensitive to failures
- **Minimum calls 10**: Detect critical operation failures quickly

### 📊 Real-World Production Examples

#### Example 1: E-commerce Product Catalog API

**System Specs**:
- Server: 8 CPU cores, 16GB RAM
- Database: PostgreSQL with 100 connection pool
- Load test result: 2000 req/s sustained

**Configuration**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      # Product listing (high traffic)
      product-list:
        limit-for-period: 1500              # 2000 × 0.75 = 1500
        limit-refresh-period: 1s
        timeout-duration: 0ms
      
      # Product detail (medium traffic)
      product-detail:
        limit-for-period: 1000              # Lower than list
        limit-refresh-period: 1s
        timeout-duration: 0ms
      
      # Product search (resource intensive)
      product-search:
        limit-for-period: 500               # More restrictive
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  bulkhead:
    instances:
      product-list:
        max-concurrent-calls: 1500          # Match RateLimiter
        max-wait-duration: 0ms
      
      product-detail:
        max-concurrent-calls: 1000
        max-wait-duration: 0ms
      
      product-search:
        max-concurrent-calls: 300           # Lower due to DB load
        max-wait-duration: 0ms
  
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 100
        minimum-number-of-calls: 50
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10
```

**Calculation Breakdown**:
- **Product List**: Simple query, fast response → High limit (1500/s)
- **Product Detail**: Single record fetch → Medium limit (1000/s)
- **Product Search**: Full-text search, DB intensive → Low limit (500/s)
- **Bulkhead for Search**: 300 < 500 to prevent DB connection exhaustion

#### Example 2: Payment Processing API

**System Specs**:
- Server: 4 CPU cores, 8GB RAM
- External payment gateway (3rd party)
- Average response time: 500ms

**Configuration**:
```yaml
resilience4j:
  ratelimiter:
    instances:
      payment-process:
        limit-for-period: 100               # Conservative for external calls
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  bulkhead:
    instances:
      payment-process:
        max-concurrent-calls: 50            # Limit concurrent external calls
        max-wait-duration: 0ms
  
  circuitbreaker:
    instances:
      PaymentService:
        sliding-window-size: 20             # Small window for quick detection
        minimum-number-of-calls: 10
        failure-rate-threshold: 30          # Sensitive to failures (30%)
        wait-duration-in-open-state: 120s   # 2 minutes for external service
        permitted-number-of-calls-in-half-open-state: 5
        slow-call-duration-threshold: 2s    # Consider >2s as slow
        slow-call-rate-threshold: 50        # 50% slow calls opens circuit
```

**Reasoning**:
- **Low RateLimiter (100/s)**: External service has rate limits
- **Lower Bulkhead (50)**: Prevent too many concurrent external calls
- **Sensitive CircuitBreaker (30%)**: Protect against payment gateway failures
- **Longer wait time (120s)**: Give external service more time to recover
- **Slow call detection**: Prevent timeout cascades

### ✅ Production Best Practices

#### 1. Start Conservative, Then Optimize
```yaml
# Phase 1: Initial deployment (conservative)
ratelimiter:
  instances:
    api:
      limit-for-period: 500                 # Start low

# Phase 2: After monitoring (optimized)
ratelimiter:
  instances:
    api:
      limit-for-period: 1000                # Increase based on metrics
```

#### 2. Monitor and Adjust
```yaml
# Add metrics monitoring
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus

# Monitor these metrics:
# - resilience4j.ratelimiter.available.permissions
# - resilience4j.bulkhead.available.concurrent.calls
# - resilience4j.circuitbreaker.state
```

#### 3. Use Different Configs for Different Environments
```yaml
# application-prod.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 1000              # Production limit

# application-staging.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 500               # Lower for staging

# application-dev.yml
resilience4j:
  ratelimiter:
    instances:
      api:
        limit-for-period: 100               # Very low for dev
```

#### 4. Document Your Calculations
```yaml
# Always add comments explaining your configuration
resilience4j:
  ratelimiter:
    instances:
      product-read:
        # Calculation: Load test showed 1500 req/s capacity
        # Applied 75% safety margin: 1500 × 0.75 = 1125
        # Rounded down to: 1000 req/s
        # Last updated: 2026-06-03
        # Load test date: 2026-05-15
        limit-for-period: 1000
```

#### 5. Fail-Fast Philosophy
```yaml
# ✅ RECOMMENDED: Fail-fast (timeout-duration: 0ms)
resilience4j:
  ratelimiter:
    instances:
      api:
        timeout-duration: 0ms               # Don't wait, fail immediately
  bulkhead:
    instances:
      api:
        max-wait-duration: 0ms              # Don't queue, fail immediately

# ❌ NOT RECOMMENDED: Waiting/queuing
resilience4j:
  ratelimiter:
    instances:
      api:
        timeout-duration: 5s                # Waiting causes cascading delays
```

**Why Fail-Fast?**
- Prevents cascading delays
- Better user experience (fast failure vs slow timeout)
- Easier to debug
- Prevents resource exhaustion

---

## 🎓 Learning/Demo Configuration

### Goal
Provide minimal configurations that make it **easy to demonstrate** each pattern with JMeter using small request counts (~20 requests).

---

### A. RateLimiter Demo Configuration

#### 🎯 Purpose
Demonstrate request rate limiting - only allow X requests per second.

#### ⚙️ Configuration
```yaml
# File: application.yml or application-demo.yml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10                # Allow only 10 requests per second
        limit-refresh-period: 1s            # Reset every 1 second
        timeout-duration: 0ms               # Fail immediately if limit exceeded
```

#### 📊 JMeter Test Plan

**Thread Group Configuration**:
```
Number of Threads (users): 20
Ramp-Up Period (seconds): 0                 # All threads start immediately
Loop Count: 1                               # Each thread sends 1 request
```

**HTTP Request**:
```
Method: GET
Path: /api/products
```

**Expected Results**:
- ✅ **~10 requests succeed** (HTTP 200)
- ❌ **~10 requests fail** (HTTP 429 - Too Many Requests)
- Response body for 429: `{"error": "Rate limit exceeded"}`

#### 🔍 How to Verify

1. **View Results Tree**:
   - Green requests (200): Successful
   - Red requests (429): Rate limited

2. **Summary Report**:
   - Look for "Error %" around 50%
   - Throughput should be ~10 requests/second

3. **Response Assertion**:
   - Add assertion to check for HTTP 429
   - Verify error message contains "Rate limit"

#### 📸 What You Should See

```
Request #1-10:  HTTP 200 ✅ (Allowed)
Request #11-20: HTTP 429 ❌ (Rate Limited)

Response Time: < 50ms (very fast rejection)
```

---

### B. Bulkhead Demo Configuration

#### 🎯 Purpose
Demonstrate concurrent call limiting - only allow X parallel executions.

#### ⚙️ Configuration
```yaml
# File: application.yml or application-demo.yml
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15            # Only 15 concurrent requests allowed
        max-wait-duration: 0ms              # Don't wait, reject immediately
```

#### 📊 JMeter Test Plan

**Thread Group Configuration**:
```
Number of Threads (users): 30
Ramp-Up Period (seconds): 0                 # All threads start simultaneously
Loop Count: 1
```

**HTTP Request**:
```
Method: GET
Path: /api/products
```

**⚠️ CRITICAL: Add Constant Timer**:
```
Timer: Constant Timer
Thread Delay (milliseconds): 2000           # Make each request take 2 seconds
```

**Why Timer is Needed?**
- Without timer: Requests complete too fast, all 30 might fit in the bulkhead
- With timer: Requests take 2 seconds, so only 15 can run concurrently

**Expected Results**:
- ✅ **~15 requests succeed** (HTTP 200) - Running concurrently
- ❌ **~15 requests fail** (HTTP 503 - Service Unavailable)
- Response body for 503: `{"error": "Bulkhead is full"}`

#### 🔍 How to Verify

1. **View Results Tree**:
   - First 15 requests: Green (200) with ~2000ms response time
   - Remaining 15: Red (503) with <50ms response time (immediate rejection)

2. **Response Time Graph**:
   - Successful requests: ~2000ms (slow)
   - Rejected requests: <50ms (fast rejection)

3. **Aggregate Report**:
   - Error % around 50% (15 out of 30)

#### 📸 What You Should See

```
Thread 1-15:  HTTP 200 ✅ (Running concurrently, ~2000ms)
Thread 16-30: HTTP 503 ❌ (Rejected immediately, <50ms)

Concurrent Calls: 15 (max)
Rejected Calls: 15
```

---

### C. CircuitBreaker Demo Configuration

#### 🎯 Purpose
Demonstrate circuit breaker states: CLOSED → OPEN → HALF_OPEN → CLOSED

#### ⚙️ Configuration
```yaml
# File: application.yml or application-demo.yml
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10             # Small window for quick demo
        minimum-number-of-calls: 5          # Only need 5 calls to activate
        failure-rate-threshold: 50          # 50% failure rate opens circuit
        wait-duration-in-open-state: 10s    # Wait 10 seconds before half-open
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

#### 📊 JMeter Test Plan - Three Phases

#### **Phase 1: Trigger Circuit to Open**

**Thread Group 1**:
```
Number of Threads: 10
Ramp-Up Period: 0
Loop Count: 1
```

**HTTP Request**:
```
Method: GET
Path: /api/products/999999                  # Non-existent product (causes 404)
```

**Expected Results**:
- First 5-10 requests: HTTP 404 (Not Found)
- Circuit opens after 50% failure rate
- Subsequent requests: HTTP 503 (Circuit Open)

#### **Phase 2: Wait for Half-Open**

**Wait 10 seconds** (wait-duration-in-open-state)

**What Happens**:
- Circuit automatically transitions to HALF_OPEN state
- Ready to test if service has recovered

#### **Phase 3: Close Circuit with Successful Requests**

**Thread Group 2**:
```
Number of Threads: 3
Ramp-Up Period: 0
Loop Count: 1
```

**HTTP Request**:
```
Method: GET
Path: /api/products/1                       # Valid product (causes 200)
```

**Expected Results**:
- All 3 requests: HTTP 200 (Success)
- Circuit closes after 3 successful calls
- Circuit is now CLOSED and healthy

#### 🔍 How to Verify

1. **Phase 1 - Circuit Opens**:
   ```
   Request 1-5:  HTTP 404 ❌ (Failures counted)
   Request 6:    HTTP 503 ⚠️ (Circuit OPEN)
   Request 7-10: HTTP 503 ⚠️ (Circuit still OPEN)
   ```

2. **Phase 2 - Wait**:
   ```
   Wait 10 seconds...
   Circuit State: OPEN → HALF_OPEN
   ```

3. **Phase 3 - Circuit Closes**:
   ```
   Request 1-3: HTTP 200 ✅ (Success in HALF_OPEN)
   Circuit State: HALF_OPEN → CLOSED
   ```

#### 📸 What You Should See

**Circuit States Timeline**:
```
Time 0s:    CLOSED (healthy)
Time 1s:    OPEN (too many failures)
Time 11s:   HALF_OPEN (testing recovery)
Time 12s:   CLOSED (recovered)
```

**Response Codes**:
```
Phase 1: 404, 404, 404, 404, 404, 503, 503, 503, 503, 503
Phase 2: (waiting...)
Phase 3: 200, 200, 200
```

---

### D. Combined Demo Configuration

#### 🎯 Purpose
Show all three patterns working together with proper parameter relationships.

#### ⚙️ Configuration
```yaml
# File: application-demo.yml
resilience4j:
  # Pattern 1: RateLimiter (checks first)
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 20                # Allow 20 requests/second
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  # Pattern 2: Bulkhead (checks second)
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 25            # Allow 25 concurrent (≥ RateLimiter)
        max-wait-duration: 0ms
  
  # Pattern 3: CircuitBreaker (checks third)
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
        permitted-number-of-calls-in-half-open-state: 5
```

#### 📊 JMeter Test Plan - Combined Test

**Thread Group**:
```
Number of Threads: 30
Ramp-Up Period: 0
Loop Count: 1
```

**HTTP Request**:
```
Method: GET
Path: /api/products
```

**Add Constant Timer**:
```
Thread Delay: 1000ms                        # 1 second per request
```

#### 🔍 Expected Results

**Request Flow**:
```
Request 1-20:  Pass RateLimiter ✅
               → All 20 pass Bulkhead ✅ (Bulkhead limit is 25)
               → All 20 succeed ✅ (HTTP 200)

Request 21-30: Rejected by RateLimiter ❌ (HTTP 429)
```

**Response Distribution**:
- ✅ **20 requests**: HTTP 200 (Success - passed both RateLimiter and Bulkhead)
- ❌ **10 requests**: HTTP 429 (Rate limited)

#### 📸 What You Should See

```
Execution Order:
┌─────────────┐
│ RateLimiter │ → 20 pass, 10 rejected (429)
└──────┬──────┘
       ↓
┌─────────────┐
│  Bulkhead   │ → All 20 pass (limit is 25)
└──────┬──────┘
       ↓
┌─────────────┐
│CircuitBreaker│ → All 20 pass (circuit closed)
└──────┬──────┘
       ↓
   Service Method

---

## 📊 Configuration Comparison Tables

### Table 1: Production vs Learning Configuration

| Parameter | Production Value | Learning Value | Reason for Learning Value |
|-----------|-----------------|----------------|---------------------------|
| **RateLimiter** |
| `limit-for-period` | 1000/s | 10/s | Easy to trigger with 20 requests |
| `limit-refresh-period` | 1s | 1s | Same (standard interval) |
| `timeout-duration` | 0ms | 0ms | Same (fail-fast) |
| **Bulkhead** |
| `max-concurrent-calls` | 1000 | 15 | See rejection quickly with 30 threads |
| `max-wait-duration` | 0ms | 0ms | Same (fail-fast) |
| **CircuitBreaker** |
| `sliding-window-size` | 100 | 10 | Fast state changes for demo |
| `minimum-number-of-calls` | 50 | 5 | Quick activation with few requests |
| `failure-rate-threshold` | 50% | 50% | Same (standard threshold) |
| `wait-duration-in-open-state` | 60s | 10s | Shorter wait for demo |
| `permitted-calls-in-half-open` | 10 | 3 | Fewer requests needed to close |

### Table 2: Read vs Write Operations

| Aspect | Read Operations | Write Operations | Reasoning |
|--------|----------------|------------------|-----------|
| **Typical Response Time** | 50-100ms | 200-500ms | Writes involve DB transactions |
| **RateLimiter Limit** | 1000/s | 200/s | Reads are faster, can handle more |
| **Bulkhead Concurrent** | 1000 | 200 | Match RateLimiter, prevent DB exhaustion |
| **CircuitBreaker Window** | 100 | 50 | Larger window for stable reads |
| **Minimum Calls** | 50 | 20 | More data for reads, faster detection for writes |
| **Failure Threshold** | 50% | 50% | Same (standard) |
| **Wait Duration** | 60s | 30s | Shorter for writes (faster recovery needed) |
| **Resource Impact** | Low (CPU) | High (DB, locks) | Writes consume more resources |
| **Cacheability** | Yes | No | Reads can be cached |

### Table 3: API Endpoint Types

| Endpoint Type | RateLimiter | Bulkhead | CircuitBreaker | Example |
|--------------|-------------|----------|----------------|---------|
| **Public List API** | 1500/s | 1500 | Window: 100 | `GET /api/products` |
| **Public Detail API** | 1000/s | 1000 | Window: 100 | `GET /api/products/{id}` |
| **Search API** | 500/s | 300 | Window: 50 | `GET /api/products/search` |
| **Create API** | 200/s | 200 | Window: 50 | `POST /api/products` |
| **Update API** | 200/s | 200 | Window: 50 | `PUT /api/products/{id}` |
| **Delete API** | 100/s | 100 | Window: 30 | `DELETE /api/products/{id}` |
| **Batch API** | 50/s | 20 | Window: 20 | `POST /api/products/batch` |
| **External API Call** | 100/s | 50 | Window: 20, Threshold: 30% | Payment gateway |

### Table 4: Environment-Specific Configuration

| Environment | RateLimiter | Bulkhead | CircuitBreaker | Purpose |
|-------------|-------------|----------|----------------|---------|
| **Production** | 1000/s | 1000 | Window: 100, Wait: 60s | Real traffic, high capacity |
| **Staging** | 500/s | 500 | Window: 50, Wait: 30s | Pre-production testing |
| **Development** | 100/s | 100 | Window: 20, Wait: 10s | Local development |
| **Load Testing** | 2000/s | 2000 | Window: 200, Wait: 5s | Stress testing |
| **Demo/Learning** | 10/s | 15 | Window: 10, Wait: 10s | Easy to demonstrate |

### Table 5: Failure Response Codes

| Pattern | HTTP Status | Response Body | When It Happens | Client Action |
|---------|-------------|---------------|-----------------|---------------|
| **RateLimiter** | 429 | `{"error": "Rate limit exceeded"}` | Too many requests/second | Retry after 1 second |
| **Bulkhead** | 503 | `{"error": "Bulkhead is full"}` | Too many concurrent calls | Retry immediately or later |
| **CircuitBreaker (Open)** | 503 | `{"error": "Circuit breaker is open"}` | Too many failures | Wait for circuit to close |
| **CircuitBreaker (Half-Open)** | 503 | `{"error": "Circuit breaker is half-open"}` | Testing recovery | Wait for circuit to close |
| **Service Error** | 500 | `{"error": "Internal server error"}` | Actual service failure | Report to support |

---

## 🧪 JMeter Testing Guide

### Prerequisites

1. **Install JMeter**: Download from [Apache JMeter](https://jmeter.apache.org/)
2. **Start Application**: `./gradlew bootRun`
3. **Verify Application**: `curl http://localhost:8080/actuator/health`

### General JMeter Setup

#### Create Test Plan
1. Open JMeter
2. Right-click "Test Plan" → Add → Threads (Users) → Thread Group
3. Right-click "Thread Group" → Add → Sampler → HTTP Request
4. Right-click "Thread Group" → Add → Listener → View Results Tree
5. Right-click "Thread Group" → Add → Listener → Summary Report

---

### Test 1: RateLimiter Testing

#### 🎯 Goal
Verify that only 10 requests per second are allowed.

#### 📋 Step-by-Step Instructions

**Step 1: Configure Application**
```yaml
# application.yml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0ms
```

**Step 2: Restart Application**
```bash
./gradlew bootRun
```

**Step 3: Configure JMeter Thread Group**
```
Name: RateLimiter Test
Number of Threads (users): 20
Ramp-Up Period (seconds): 0
Loop Count: 1
```

**Step 4: Configure HTTP Request**
```
Protocol: http
Server Name or IP: localhost
Port Number: 8080
HTTP Request Method: GET
Path: /api/products
```

**Step 5: Add Response Assertion (Optional)**
```
Right-click Thread Group → Add → Assertions → Response Assertion
Field to Test: Response Code
Pattern Matching Rules: Matches
Patterns to Test: 200|429
```

**Step 6: Run Test**
1. Click green "Start" button (▶️)
2. Watch "View Results Tree"

#### ✅ Expected Results

**View Results Tree**:
```
✅ Request 1:  HTTP 200 - Response Time: ~50ms
✅ Request 2:  HTTP 200 - Response Time: ~50ms
...
✅ Request 10: HTTP 200 - Response Time: ~50ms
❌ Request 11: HTTP 429 - Response Time: ~10ms
❌ Request 12: HTTP 429 - Response Time: ~10ms
...
❌ Request 20: HTTP 429 - Response Time: ~10ms
```

**Summary Report**:
```
Label         Samples  Average  Error %  Throughput
GET /products    20      30ms     50%     20/sec
```

**Response Body (429)**:
```json
{
  "timestamp": "2026-06-03T06:00:00.000+00:00",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded",
  "path": "/api/products"
}
```

#### 🔍 How to Verify Success

1. **Check Error Rate**: Should be ~50% (10 success, 10 failures)
2. **Check Response Codes**: Mix of 200 and 429
3. **Check Response Times**: 429 responses should be very fast (<50ms)
4. **Check Logs**: Look for "Rate limit exceeded" messages

---

### Test 2: Bulkhead Testing

#### 🎯 Goal
Verify that only 15 concurrent requests are allowed.

#### 📋 Step-by-Step Instructions

**Step 1: Configure Application**
```yaml
# application.yml
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
```

**Step 2: Restart Application**

**Step 3: Configure JMeter Thread Group**
```
Name: Bulkhead Test
Number of Threads (users): 30
Ramp-Up Period (seconds): 0
Loop Count: 1
```

**Step 4: Configure HTTP Request**
```
Protocol: http
Server Name or IP: localhost
Port Number: 8080
HTTP Request Method: GET
Path: /api/products
```

**Step 5: ⚠️ CRITICAL - Add Constant Timer**
```
Right-click Thread Group → Add → Timer → Constant Timer
Name: Slow Request Simulator
Thread Delay (milliseconds): 2000
```

**Why Timer is Needed?**
- Without timer: Requests complete in ~50ms, all 30 might fit
- With timer: Requests take 2000ms, only 15 can run concurrently

**Step 6: Run Test**

#### ✅ Expected Results

**View Results Tree**:
```
✅ Thread 1:  HTTP 200 - Response Time: ~2000ms (running)
✅ Thread 2:  HTTP 200 - Response Time: ~2000ms (running)
✅ Thread 3:  HTTP 200 - Response Time: ~2000ms (running)
...
✅ Thread 15: HTTP 200 - Response Time: ~2000ms (running)
❌ Thread 16: HTTP 503 - Response Time: ~10ms (rejected)
❌ Thread 17: HTTP 503 - Response Time: ~10ms (rejected)
...
❌ Thread 30: HTTP 503 - Response Time: ~10ms (rejected)
```

**Summary Report**:
```
Label         Samples  Average   Error %  Throughput
GET /products    30     ~1000ms    50%     ~7.5/sec
```

**Response Body (503)**:
```json
{
  "timestamp": "2026-06-03T06:00:00.000+00:00",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Bulkhead is full",
  "path": "/api/products"
}
```

#### 🔍 How to Verify Success

1. **Check Error Rate**: Should be ~50% (15 success, 15 failures)
2. **Check Response Times**:
   - Success (200): ~2000ms (slow)
   - Failure (503): <50ms (fast rejection)
3. **Check Concurrent Calls**: Max 15 running at same time
4. **Check Logs**: Look for "Bulkhead is full" messages

---

### Test 3: CircuitBreaker Testing

#### 🎯 Goal
Demonstrate circuit breaker state transitions: CLOSED → OPEN → HALF_OPEN → CLOSED

#### 📋 Step-by-Step Instructions

**Step 1: Configure Application**
```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

**Step 2: Restart Application**

#### Phase 1: Trigger Circuit to Open

**Step 3: Create Thread Group 1**
```
Name: Phase 1 - Trigger Failures
Number of Threads: 10
Ramp-Up Period: 0
Loop Count: 1
```

**Step 4: Configure HTTP Request**
```
Protocol: http
Server Name or IP: localhost
Port Number: 8080
HTTP Request Method: GET
Path: /api/products/999999              # Non-existent product
```

**Step 5: Run Phase 1**
1. Click "Start" button
2. Watch circuit open after 50% failures

#### ✅ Phase 1 Expected Results

**View Results Tree**:
```
❌ Request 1-5:  HTTP 404 (Not Found) - Failures counted
⚠️ Request 6-10: HTTP 503 (Circuit Open) - Circuit opened
```

**Circuit State**: CLOSED → OPEN

#### Phase 2: Wait for Half-Open

**Step 6: Wait 10 Seconds**
- Circuit automatically transitions to HALF_OPEN
- Check application logs: "CircuitBreaker 'ProductService' changed state from OPEN to HALF_OPEN"

#### Phase 3: Close Circuit

**Step 7: Create Thread Group 2**
```
Name: Phase 3 - Successful Requests
Number of Threads: 3
Ramp-Up Period: 0
Loop Count: 1
```

**Step 8: Configure HTTP Request**
```
Protocol: http
Server Name or IP: localhost
Port Number: 8080
HTTP Request Method: GET
Path: /api/products/1                   # Valid product
```

**Step 9: Run Phase 3**

#### ✅ Phase 3 Expected Results

**View Results Tree**:
```
✅ Request 1-3: HTTP 200 (Success) - Circuit testing recovery
```

**Circuit State**: HALF_OPEN → CLOSED

#### 🔍 Complete Test Verification

**Timeline**:
```
Time 0s:    Circuit CLOSED (healthy)
Time 1s:    Send 10 requests to /products/999999
Time 2s:    Circuit OPEN (50% failure rate reached)
Time 12s:   Circuit HALF_OPEN (automatic transition)
Time 13s:   Send 3 requests to /products/1
Time 14s:   Circuit CLOSED (recovery successful)
```

**Response Code Sequence**:
```
Phase 1: 404, 404, 404, 404, 404, 503, 503, 503, 503, 503
Phase 2: (waiting 10 seconds)
Phase 3: 200, 200, 200
```

---

### Test 4: Combined Pattern Testing

#### 🎯 Goal
Test all three patterns working together.

#### 📋 Step-by-Step Instructions

**Step 1: Configure Application**
```yaml
# application.yml
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 20
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 10
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 15s
```

**Step 2: Configure JMeter**
```
Thread Group:
  Number of Threads: 30
  Ramp-Up Period: 0
  Loop Count: 1

HTTP Request:
  Path: /api/products

Constant Timer:
  Thread Delay: 1000ms
```

**Step 3: Run Test**

#### ✅ Expected Results

**Request Distribution**:
```
Total Requests: 30

RateLimiter Check:
  ✅ Pass: 20 requests (within 20/s limit)
  ❌ Fail: 10 requests (HTTP 429 - Rate Limited)

Bulkhead Check (for the 20 that passed RateLimiter):
  ✅ Pass: 10 requests (within concurrent limit)
  ❌ Fail: 10 requests (HTTP 503 - Bulkhead Full)

CircuitBreaker Check (for the 10 that passed Bulkhead):
  ✅ Pass: 10 requests (Circuit CLOSED)
  ❌ Fail: 0 requests (no failures)

Final Results:
  ✅ Success: 10 requests (HTTP 200)
  ❌ Rate Limited: 10 requests (HTTP 429)
  ❌ Bulkhead Full: 10 requests (HTTP 503)
```

**Summary Report**:
```
Label         Samples  Average  Error %  Throughput
GET /products    30     ~350ms    66%     ~10/sec
```

---

### Common JMeter Tips

#### 1. Clear Results Between Tests
```
Run → Clear All (Ctrl+Shift+E)
```

#### 2. Save Test Plan
```
File → Save Test Plan As → resilience4j-test.jmx
```

#### 3. View Detailed Logs
```
Options → Log Viewer
```

#### 4. Add HTTP Header Manager
```
Right-click Thread Group → Add → Config Element → HTTP Header Manager
Add: Content-Type: application/json
```

#### 5. Add Assertions
```
Right-click HTTP Request → Add → Assertions → Response Assertion
```

#### 6. Export Results
```
Right-click Listener → Save Table Data
```

---

## 🔧 Troubleshooting Guide

### Issue 1: Not Seeing 429 Errors (RateLimiter)

#### Symptoms
- All requests return HTTP 200
- No rate limiting happening
- Error rate is 0%

#### Possible Causes & Solutions

**Cause 1: Configuration Not Applied**
```yaml
# ❌ Wrong instance name
resilience4j:
  ratelimiter:
    instances:
      wrong-name:                           # Doesn't match @RateLimiter annotation
        limit-for-period: 10

# ✅ Correct instance name
resilience4j:
  ratelimiter:
    instances:
      product-read:                         # Matches @RateLimiter("product-read")
        limit-for-period: 10
```

**Cause 2: Application Not Restarted**
```bash
# Solution: Restart the application
./gradlew bootRun
```

**Cause 3: Too Few Requests**
```
# Problem: Sending 5 requests with limit of 10
# Solution: Send more requests than the limit
Number of Threads: 20  (should be > limit-for-period)
```

**Cause 4: Requests Too Slow**
```
# Problem: Ramp-up period spreads requests over time
Ramp-Up Period: 10s  ❌ (requests spread over 10 seconds)

# Solution: Send all requests at once
Ramp-Up Period: 0s   ✅ (all requests immediately)
```

**Verification Steps**:
1. Check application logs for "Rate limit exceeded"
2. Verify annotation: `@RateLimiter(name = "product-read")`
3. Check actuator: `curl http://localhost:8080/actuator/ratelimiters`
4. Increase thread count to 2x the limit

---

### Issue 2: All Requests Failing (Bulkhead)

#### Symptoms
- All requests return HTTP 503
- Error rate is 100%
- No successful requests

#### Possible Causes & Solutions

**Cause 1: Missing Constant Timer**
```
# Problem: Requests complete too fast
# Without timer: All 30 requests complete in <1 second
# Only 1-2 concurrent at any time

# Solution: Add Constant Timer
Right-click Thread Group → Add → Timer → Constant Timer
Thread Delay: 2000ms  (make requests slow)
```

**Cause 2: Bulkhead Too Small**
```yaml
# ❌ Too restrictive
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 1             # Only 1 concurrent!

# ✅ Reasonable for demo
resilience4j:
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15            # 15 concurrent
```

**Cause 3: Wrong Parameter Relationship**
```yaml
# ❌ Bulkhead < RateLimiter (causes rejections)
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 100
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 10            # Too small!

# ✅ Bulkhead >= RateLimiter
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 100
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 100           # Matches RateLimiter
```

**Verification Steps**:
1. Add 2000ms Constant Timer
2. Check thread count < max-concurrent-calls
3. Verify some requests show ~2000ms response time
4. Check actuator: `curl http://localhost:8080/actuator/bulkheads`

---

### Issue 3: CircuitBreaker Not Opening

#### Symptoms
- Circuit stays CLOSED
- No HTTP 503 errors
- Failures don't trigger circuit

#### Possible Causes & Solutions

**Cause 1: Not Enough Failures**
```yaml
# Problem: Need 50% failure rate with minimum 5 calls
# Sending only 3 requests with 2 failures = 66% but < minimum

# Solution: Send more requests
minimum-number-of-calls: 5                  # Need at least 5 calls
# Send at least 10 requests with 50% failures
```

**Cause 2: Wrong Endpoint**
```
# ❌ Endpoint doesn't trigger failures
Path: /api/products                         # Returns 200 (success)

# ✅ Endpoint that causes failures
Path: /api/products/999999                  # Returns 404 (failure)
```

**Cause 3: Sliding Window Too Large**
```yaml
# ❌ Window too large for demo
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 100            # Need 50 failures!
        minimum-number-of-calls: 50

# ✅ Smaller window for demo
resilience4j:
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10             # Only need 5 failures
        minimum-number-of-calls: 5
```

**Cause 4: Wrong Service Name**
```java
// ❌ Annotation doesn't match config
@CircuitBreaker(name = "WrongService")
public List<Product> getProducts() { }

// Config
resilience4j:
  circuitbreaker:
    instances:
      ProductService:                       # Doesn't match!

// ✅ Matching names
@CircuitBreaker(name = "ProductService")
public List<Product> getProducts() { }
```

**Verification Steps**:
1. Send 10 requests to non-existent resource
2. Check logs for "Circuit breaker opened"
3. Verify failure rate > threshold
4. Check actuator: `curl http://localhost:8080/actuator/circuitbreakers`

---

### Issue 4: Parameters Not Taking Effect

#### Symptoms
- Changed configuration but behavior unchanged
- Old limits still applying
- New values not working

#### Possible Causes & Solutions

**Cause 1: Application Not Restarted**
```bash
# Solution: Always restart after config changes
./gradlew bootRun
```

**Cause 2: Wrong Configuration File**
```bash
# Check which profile is active
# application.yml vs application-dev.yml vs application-prod.yml

# Solution: Verify active profile
spring:
  profiles:
    active: dev                             # Using application-dev.yml
```

**Cause 3: Caching/Old State**
```bash
# Solution: Clean build and restart
./gradlew clean
./gradlew bootRun
```

**Cause 4: YAML Indentation Error**
```yaml
# ❌ Wrong indentation
resilience4j:
ratelimiter:                                # Should be indented!
  instances:
    product-read:
      limit-for-period: 10

# ✅ Correct indentation
resilience4j:
  ratelimiter:                              # Properly indented
    instances:
      product-read:
        limit-for-period: 10
```

**Verification Steps**:
1. Check application startup logs for configuration values
2. Verify YAML syntax with online validator
3. Check actuator endpoints for current configuration
4. Add logging to see which config is loaded

---

### Issue 5: Inconsistent Results

#### Symptoms
- Sometimes works, sometimes doesn't
- Different results on each run
- Unpredictable behavior

#### Possible Causes & Solutions

**Cause 1: Race Conditions**
```
# Problem: Threads starting at slightly different times
Ramp-Up Period: 1s                          # Threads spread over 1 second

# Solution: Start all threads simultaneously
Ramp-Up Period: 0s                          # All threads start at once
```

**Cause 2: State from Previous Tests**
```bash
# Problem: Circuit still OPEN from previous test
# Solution: Wait for circuit to close or restart application

# Check circuit state
curl http://localhost:8080/actuator/circuitbreakers

# Restart application
./gradlew bootRun
```

**Cause 3: Time-Based Limits**
```yaml
# Problem: RateLimiter resets every second
# If test spans multiple seconds, results vary

# Solution: Send all requests within 1 second
resilience4j:
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10
        limit-refresh-period: 1s            # Resets every second!

# JMeter: Use 0 ramp-up to send all at once
```

**Verification Steps**:
1. Clear JMeter results between runs
2. Restart application between tests
3. Use 0 ramp-up period
4. Check for consistent error rates

---

### Issue 6: Wrong HTTP Status Codes

#### Symptoms
- Getting 500 instead of 429/503
- Unexpected error responses
- Wrong error messages

#### Possible Causes & Solutions

**Cause 1: Exception Not Handled**
```java
// ❌ Exception propagates as 500
@GetMapping("/products")
public List<Product> getProducts() {
    // RequestNotPermitted exception → 500 error
}

// ✅ Global exception handler catches it
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(
        RequestNotPermitted ex) {
        return ResponseEntity.status(429)   // Return 429
            .body(new ApiErrorResponse("Rate limit exceeded"));
    }
}
```

**Cause 2: Wrong Exception Type**
```java
// Check which exception is thrown:
// - RequestNotPermitted → RateLimiter (should be 429)
// - BulkheadFullException → Bulkhead (should be 503)
// - CallNotPermittedException → CircuitBreaker (should be 503)
```

**Verification Steps**:
1. Check GlobalExceptionHandler.java
2. Verify exception mappings
3. Check application logs for exception types
4. Test each pattern individually

---

### Debugging Commands

#### Check Resilience4j Configuration
```bash
# View all actuator endpoints
curl http://localhost:8080/actuator

# Check RateLimiter status
curl http://localhost:8080/actuator/ratelimiters

# Check Bulkhead status
curl http://localhost:8080/actuator/bulkheads

# Check CircuitBreaker status
curl http://localhost:8080/actuator/circuitbreakers

# Check health
curl http://localhost:8080/actuator/health
```

#### Enable Debug Logging
```yaml
# application.yml
logging:
  level:
    io.github.resilience4j: DEBUG
    com.ibm.demo: DEBUG
```

#### Check Metrics
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep resilience4j
```

---

## 🚀 Quick Reference

### Quick Start - Learning Configuration

**Copy-paste ready configuration for demos**:

```yaml
# File: application-demo.yml
resilience4j:
  # RateLimiter: Allow 10 requests/second
  ratelimiter:
    instances:
      product-read:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 0ms
      product-write:
        limit-for-period: 5
        limit-refresh-period: 1s
        timeout-duration: 0ms
  
  # Bulkhead: Allow 15 concurrent calls (≥ RateLimiter)
  bulkhead:
    instances:
      product-read:
        max-concurrent-calls: 15
        max-wait-duration: 0ms
      product-write:
        max-concurrent-calls: 5
        max-wait-duration: 0ms
  
  # CircuitBreaker: Quick state changes
  circuitbreaker:
    instances:
      ProductService:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
```

**JMeter Quick Test**:
```
Thread Group: 20 threads, 0 ramp-up, 1 loop
HTTP Request: GET http://localhost:8080/api/products
Expected: ~10 success (200), ~10 failures (429)
```

---

### Quick Start - Production Configuration

**Copy-paste ready configuration for production**:

```yaml
# File: application-prod.yml
resilience4j:
  # RateLimiter: Based on load test results
  ratelimiter:
    configs:
      default:
        limit-refresh-period: 1s
        timeout-duration: 0ms
    instances:
      # Read operations (high throughput)
      product-read:
        limit-for-period: 1000              # 1000 req/s
      account-read:
        limit-for-period: 1000
      order-read:
        limit-for-period: 500
      
      # Write operations (lower throughput)
      product-write:
        limit-for-period: 200               # 200 req/s
      account-write:
        limit-for-period: 200
      order-write:
        limit-for-period: 100
  
  # Bulkhead: Match or exceed RateLimiter limits
  bulkhead:
    configs:
      default:
        max-wait-duration: 0ms
    instances:
      # Read operations
      product-read:
        max-concurrent-calls: 1000          # >= RateLimiter
      account-read:
        max-concurrent-calls: 1000
      order-read:
        max-concurrent-calls: 500
      
      # Write operations
      product-write:
        max-concurrent-calls: 200
      account-write:
        max-concurrent-calls: 200
      order-write:
        max-concurrent-calls: 100
  
  # CircuitBreaker: Protect against cascading failures
  circuitbreaker:
    configs:
      default:
        register-health-indicator: true
        sliding-window-type: COUNT_BASED
        sliding-window-size: 100
        minimum-number-of-calls: 50
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 10
        automatic-transition-from-open-to-half-open-enabled: true
    instances:
      ProductService:
        base-config: default
      AccountService:
        base-config: default
      OrderService:
        base-config: default
```

---

### Testing Checklist

Use this checklist to verify each pattern works correctly:

#### ✅ RateLimiter Verification
- [ ] Configure limit-for-period: 10
- [ ] Send 20 requests with 0 ramp-up
- [ ] Verify ~10 success (HTTP 200)
- [ ] Verify ~10 failures (HTTP 429)
- [ ] Check error message: "Rate limit exceeded"
- [ ] Verify fast rejection (<50ms for 429)

#### ✅ Bulkhead Verification
- [ ] Configure max-concurrent-calls: 15
- [ ] Add 2000ms Constant Timer
- [ ] Send 30 requests with 0 ramp-up
- [ ] Verify ~15 success (HTTP 200, ~2000ms)
- [ ] Verify ~15 failures (HTTP 503, <50ms)
- [ ] Check error message: "Bulkhead is full"

#### ✅ CircuitBreaker Verification
- [ ] Configure sliding-window-size: 10, minimum-calls: 5
- [ ] Phase 1: Send 10 requests to /products/999999
- [ ] Verify circuit opens (HTTP 503)
- [ ] Phase 2: Wait 10 seconds
- [ ] Verify circuit goes to HALF_OPEN
- [ ] Phase 3: Send 3 requests to /products/1
- [ ] Verify circuit closes (HTTP 200)
- [ ] Check logs for state transitions

#### ✅ Combined Patterns Verification
- [ ] Configure all three patterns
- [ ] Send 30 requests with 1000ms timer
- [ ] Verify 10 success (HTTP 200)
- [ ] Verify 10 rate limited (HTTP 429)
- [ ] Verify 10 bulkhead full (HTTP 503)
- [ ] Check execution order: RateLimiter → Bulkhead → CircuitBreaker

---

### Configuration Templates

#### Template 1: Public API (High Traffic)
```yaml
resilience4j:
  ratelimiter:
    instances:
      public-api:
        limit-for-period: 1500
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      public-api:
        max-concurrent-calls: 1500
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      PublicService:
        sliding-window-size: 100
        minimum-number-of-calls: 50
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
```

#### Template 2: Internal API (Medium Traffic)
```yaml
resilience4j:
  ratelimiter:
    instances:
      internal-api:
        limit-for-period: 500
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      internal-api:
        max-concurrent-calls: 500
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      InternalService:
        sliding-window-size: 50
        minimum-number-of-calls: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

#### Template 3: External Service Call (Low Traffic, Sensitive)
```yaml
resilience4j:
  ratelimiter:
    instances:
      external-api:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 0ms
  bulkhead:
    instances:
      external-api:
        max-concurrent-calls: 50
        max-wait-duration: 0ms
  circuitbreaker:
    instances:
      ExternalService:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 30          # More sensitive
        wait-duration-in-open-state: 120s   # Longer recovery
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
```

---

### Useful Links

- **Current Configuration**: [`src/main/resources/application.yml`](../src/main/resources/application.yml) (lines 96-194)
- **Resilience4j Documentation**: https://resilience4j.readme.io/
- **JMeter Download**: https://jmeter.apache.org/download_jmeter.cgi
- **Spring Boot Actuator**: https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html

---

### Key Takeaways

1. **🎯 Pattern Order**: RateLimiter → Bulkhead → CircuitBreaker
2. **📊 Parameter Rule**: Bulkhead ≥ RateLimiter limit
3. **⚡ Fail-Fast**: Always use 0ms timeout/wait for immediate rejection
4. **📈 Start Conservative**: Begin with lower limits, increase based on monitoring
5. **🔍 Monitor Everything**: Use actuator endpoints and metrics
6. **🧪 Test Individually**: Verify each pattern works before combining
7. **📝 Document Calculations**: Always explain why you chose specific values
8. **🔄 Environment-Specific**: Use different configs for prod/staging/dev
9. **⏱️ Response Times**: 429/503 should be <50ms (fast rejection)
10. **🛡️ Defense in Depth**: Use all three patterns together for best protection

---

**End of Guide** | Last Updated: 2026-06-03 | Maintained by: Bobby