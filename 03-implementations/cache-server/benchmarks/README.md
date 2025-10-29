# Cache Server Performance Benchmarks

This directory contains performance benchmarking tools and results for the cache server implementation.

## Benchmark Tool

The `benchmark.py` script provides comprehensive performance testing with three different test scenarios:

### 1. Sequential Operations

Tests cache operations performed one after another to measure baseline performance.

### 2. Concurrent Operations

Tests cache operations performed simultaneously with configurable concurrency levels.

### 3. Mixed Workload

Simulates realistic usage patterns with a mix of read/write/delete operations over time.

## Running Benchmarks

### Prerequisites

```bash
pip install aiohttp
```

### Basic Usage

```bash
# Run all benchmarks with default settings
python benchmark.py

# Custom server URL
python benchmark.py --url http://your-server:8000

# Custom operation counts
python benchmark.py --sequential 5000 --concurrent 2000

# Custom concurrency level
python benchmark.py --concurrency 100

# Custom mixed workload duration
python benchmark.py --duration 120
```

### Full Options

```bash
python benchmark.py \
  --url http://localhost:8000 \
  --sequential 1000 \
  --concurrent 1000 \
  --concurrency 50 \
  --duration 60
```

## Benchmark Results

Results are automatically saved to `benchmark_results.json` and include:

- **Response Times**: Average, min, max response times for each operation
- **Throughput**: Operations per second for different scenarios
- **Hit Rates**: Cache hit percentages for read operations
- **Concurrency Performance**: How the system performs under concurrent load

### Sample Results

```json
{
  "type": "sequential",
  "operations": 1000,
  "set_operations": {
    "avg_time_ms": 2.5,
    "ops_per_second": 400
  },
  "get_operations": {
    "avg_time_ms": 1.8,
    "ops_per_second": 555,
    "hit_rate": 100.0
  }
}
```

## Performance Expectations

### Typical Performance Ranges

**Sequential Operations:**

- SET: 300-500 ops/sec
- GET: 500-800 ops/sec
- DELETE: 400-600 ops/sec

**Concurrent Operations (50 concurrent):**

- SET: 1000-2000 ops/sec
- GET: 2000-4000 ops/sec

**Mixed Workload:**

- Overall: 800-1500 ops/sec
- Hit Rate: 85-95%

### Factors Affecting Performance

1. **Network Latency**: Local vs remote Redis
2. **Redis Configuration**: Memory limits, eviction policies
3. **System Resources**: CPU, memory, network bandwidth
4. **Payload Size**: Larger values reduce throughput
5. **Concurrency Level**: Higher concurrency can improve or degrade performance

## Interpreting Results

### Good Performance Indicators

- Low and consistent response times
- High throughput (ops/sec)
- High cache hit rates (>90%)
- Linear scaling with concurrency (up to a point)

### Performance Issues

- High response time variance
- Decreasing hit rates over time
- Poor scaling with increased concurrency
- Memory pressure indicators

## Optimization Tips

### Server-Side Optimizations

1. **Redis Configuration**:

   ```
   maxmemory-policy allkeys-lru
   maxmemory 1gb
   tcp-keepalive 60
   ```

2. **Connection Pooling**: Use Redis connection pools
3. **Serialization**: Optimize JSON serialization for large objects
4. **Monitoring**: Track Redis memory usage and evictions

### Client-Side Optimizations

1. **Connection Reuse**: Use persistent HTTP connections
2. **Batching**: Group multiple operations when possible
3. **Async Operations**: Use async/await for concurrent requests
4. **Caching Strategy**: Implement appropriate TTL values

## Monitoring in Production

### Key Metrics to Track

- Response time percentiles (P50, P95, P99)
- Throughput (requests/second)
- Error rates
- Cache hit/miss ratios
- Redis memory usage
- Connection pool utilization

### Alerting Thresholds

- Response time P95 > 50ms
- Error rate > 1%
- Hit rate < 80%
- Redis memory usage > 80%

## Load Testing

For production load testing, consider:

1. **Gradual Ramp-up**: Start with low load and gradually increase
2. **Realistic Data**: Use production-like data sizes and patterns
3. **Duration**: Run tests for extended periods (30+ minutes)
4. **Monitoring**: Watch system resources during tests
5. **Failure Scenarios**: Test Redis failover and recovery

## Troubleshooting Performance Issues

### Common Issues and Solutions

**High Response Times:**

- Check Redis latency: `redis-cli --latency`
- Monitor system resources (CPU, memory, network)
- Review Redis slow log: `SLOWLOG GET 10`

**Low Throughput:**

- Increase connection pool size
- Optimize serialization (use msgpack instead of JSON)
- Consider Redis pipelining for bulk operations

**Memory Issues:**

- Monitor Redis memory usage: `INFO memory`
- Adjust eviction policies
- Implement data compression for large values

**Connection Errors:**

- Check Redis max connections: `CONFIG GET maxclients`
- Monitor connection pool metrics
- Implement proper connection retry logic
