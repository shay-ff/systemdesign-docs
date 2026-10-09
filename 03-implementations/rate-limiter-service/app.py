#!/usr/bin/env python3
"""
Rate Limiter Service

A standalone rate limiting service with multiple algorithms and Redis backend.
"""

import json
import time
import logging
from typing import Dict, Any, Optional
from dataclasses import dataclass, asdict
from flask import Flask, request, jsonify
import redis
from prometheus_client import Counter, Histogram, generate_latest, CONTENT_TYPE_LATEST

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Prometheus metrics
REQUEST_COUNT = Counter('rate_limiter_requests_total', 'Total requests', ['method', 'endpoint'])
ALLOWED_COUNT = Counter('rate_limiter_allowed_total', 'Requests allowed', ['algorithm'])
DENIED_COUNT = Counter('rate_limiter_denied_total', 'Requests denied', ['algorithm'])
RESPONSE_TIME = Histogram('rate_limiter_response_time_seconds', 'Response time')

@dataclass
class RateLimitConfig:
    """Rate limit configuration"""
    key: str
    algorithm: str  # token_bucket, sliding_window, fixed_window
    limit: int      # requests per window
    window: int     # time window in seconds
    burst: int = 0  # burst capacity (for token bucket)

class RateLimiter:
    """Rate limiter with multiple algorithms"""
    
    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client
        
    def check_rate_limit(self, config: RateLimitConfig) -> Dict[str, Any]:
        """Check if request is within rate limit"""
        if config.algorithm == "token_bucket":
            return self._token_bucket(config)
        elif config.algorithm == "sliding_window":
            return self._sliding_window(config)
        elif config.algorithm == "fixed_window":
            return self._fixed_window(config)
        else:
            raise ValueError(f"Unknown algorithm: {config.algorithm}")
    
    def _token_bucket(self, config: RateLimitConfig) -> Dict[str, Any]:
        """Token bucket algorithm implementation"""
        key = f"tb:{config.key}"
        now = time.time()
        
        # Get current state
        pipe = self.redis.pipeline()
        pipe.hmget(key, 'tokens', 'last_refill')
        pipe.expire(key, config.window * 2)  # Cleanup old keys
        result = pipe.execute()
        
        tokens, last_refill = result[0]
        
        # Initialize if first request
        if tokens is None:
            tokens = config.limit + config.burst
            last_refill = now
        else:
            tokens = float(tokens)
            last_refill = float(last_refill)
        
        # Refill tokens
        time_passed = now - last_refill
        tokens_to_add = time_passed * (config.limit / config.window)
        tokens = min(config.limit + config.burst, tokens + tokens_to_add)
        
        # Check if request allowed
        allowed = tokens >= 1
        if allowed:
            tokens -= 1
            ALLOWED_COUNT.labels(algorithm='token_bucket').inc()
        else:
            DENIED_COUNT.labels(algorithm='token_bucket').inc()
        
        # Update state
        self.redis.hmset(key, {
            'tokens': tokens,
            'last_refill': now
        })
        
        return {
            'allowed': allowed,
            'remaining': int(tokens),
            'reset_time': int(now + (1 - tokens % 1) * (config.window / config.limit)),
            'algorithm': 'token_bucket'
        }
    
    def _sliding_window(self, config: RateLimitConfig) -> Dict[str, Any]:
        """Sliding window algorithm using sorted sets"""
        key = f"sw:{config.key}"
        now = time.time()
        window_start = now - config.window
        
        pipe = self.redis.pipeline()
        # Remove old entries
        pipe.zremrangebyscore(key, 0, window_start)
        # Count current requests
        pipe.zcard(key)
        # Add current request
        pipe.zadd(key, {str(now): now})
        # Set expiry
        pipe.expire(key, config.window)
        
        results = pipe.execute()
        current_count = results[1]
        
        allowed = current_count < config.limit
        if allowed:
            ALLOWED_COUNT.labels(algorithm='sliding_window').inc()
        else:
            DENIED_COUNT.labels(algorithm='sliding_window').inc()
            # Remove the request we just added since it's not allowed
            self.redis.zrem(key, str(now))
        
        return {
            'allowed': allowed,
            'remaining': max(0, config.limit - current_count - (1 if allowed else 0)),
            'reset_time': int(now + config.window),
            'algorithm': 'sliding_window'
        }
    
    def _fixed_window(self, config: RateLimitConfig) -> Dict[str, Any]:
        """Fixed window algorithm"""
        now = time.time()
        window_start = int(now // config.window) * config.window
        key = f"fw:{config.key}:{window_start}"
        
        # Get current count and increment
        current_count = self.redis.incr(key)
        if current_count == 1:
            self.redis.expire(key, config.window)
        
        allowed = current_count <= config.limit
        if allowed:
            ALLOWED_COUNT.labels(algorithm='fixed_window').inc()
        else:
            DENIED_COUNT.labels(algorithm='fixed_window').inc()
        
        return {
            'allowed': allowed,
            'remaining': max(0, config.limit - current_count),
            'reset_time': int(window_start + config.window),
            'algorithm': 'fixed_window'
        }

# Initialize Redis connection
redis_client = redis.Redis(
    host='localhost',
    port=6379,
    db=0,
    decode_responses=True
)

rate_limiter = RateLimiter(redis_client)

@app.before_request
def before_request():
    """Record request metrics"""
    REQUEST_COUNT.labels(method=request.method, endpoint=request.endpoint).inc()

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    try:
        redis_client.ping()
        return jsonify({'status': 'healthy', 'redis': 'connected'})
    except Exception as e:
        return jsonify({'status': 'unhealthy', 'error': str(e)}), 503

@app.route('/check', methods=['POST'])
@RESPONSE_TIME.time()
def check_rate_limit():
    """Check if request is within rate limit"""
    try:
        data = request.get_json()
        if not data or 'key' not in data:
            return jsonify({'error': 'Missing required field: key'}), 400
        
        # Get configuration
        config_key = f"config:{data['key']}"
        config_data = redis_client.hgetall(config_key)
        
        if not config_data:
            return jsonify({'error': 'No configuration found for key'}), 404
        
        config = RateLimitConfig(
            key=data['key'],
            algorithm=config_data['algorithm'],
            limit=int(config_data['limit']),
            window=int(config_data['window']),
            burst=int(config_data.get('burst', 0))
        )
        
        result = rate_limiter.check_rate_limit(config)
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Error checking rate limit: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/status/<key>', methods=['GET'])
def get_status(key: str):
    """Get current rate limit status for a key"""
    try:
        config_key = f"config:{key}"
        config_data = redis_client.hgetall(config_key)
        
        if not config_data:
            return jsonify({'error': 'No configuration found for key'}), 404
        
        # Get current status without consuming quota
        algorithm = config_data['algorithm']
        
        if algorithm == "token_bucket":
            tb_key = f"tb:{key}"
            tokens, last_refill = redis_client.hmget(tb_key, 'tokens', 'last_refill')
            if tokens:
                return jsonify({
                    'key': key,
                    'algorithm': algorithm,
                    'tokens_remaining': float(tokens),
                    'last_refill': float(last_refill) if last_refill else None
                })
        elif algorithm == "sliding_window":
            sw_key = f"sw:{key}"
            now = time.time()
            window = int(config_data['window'])
            count = redis_client.zcount(sw_key, now - window, now)
            return jsonify({
                'key': key,
                'algorithm': algorithm,
                'requests_in_window': count,
                'limit': int(config_data['limit'])
            })
        elif algorithm == "fixed_window":
            now = time.time()
            window = int(config_data['window'])
            window_start = int(now // window) * window
            fw_key = f"fw:{key}:{window_start}"
            count = redis_client.get(fw_key) or 0
            return jsonify({
                'key': key,
                'algorithm': algorithm,
                'requests_in_window': int(count),
                'limit': int(config_data['limit']),
                'window_reset': window_start + window
            })
        
        return jsonify({'key': key, 'status': 'no_data'})
        
    except Exception as e:
        logger.error(f"Error getting status: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/config', methods=['POST'])
def create_config():
    """Create or update rate limit configuration"""
    try:
        data = request.get_json()
        
        # Validate required fields
        required_fields = ['key', 'algorithm', 'limit', 'window']
        for field in required_fields:
            if field not in data:
                return jsonify({'error': f'Missing required field: {field}'}), 400
        
        # Validate algorithm
        valid_algorithms = ['token_bucket', 'sliding_window', 'fixed_window']
        if data['algorithm'] not in valid_algorithms:
            return jsonify({'error': f'Invalid algorithm. Must be one of: {valid_algorithms}'}), 400
        
        config = RateLimitConfig(
            key=data['key'],
            algorithm=data['algorithm'],
            limit=int(data['limit']),
            window=int(data['window']),
            burst=int(data.get('burst', 0))
        )
        
        # Store configuration
        config_key = f"config:{config.key}"
        redis_client.hmset(config_key, asdict(config))
        
        return jsonify({'message': 'Configuration created', 'config': asdict(config)})
        
    except Exception as e:
        logger.error(f"Error creating config: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/config/<key>', methods=['GET'])
def get_config(key: str):
    """Get configuration for a key"""
    try:
        config_key = f"config:{key}"
        config_data = redis_client.hgetall(config_key)
        
        if not config_data:
            return jsonify({'error': 'Configuration not found'}), 404
        
        return jsonify(config_data)
        
    except Exception as e:
        logger.error(f"Error getting config: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/config/<key>', methods=['DELETE'])
def delete_config(key: str):
    """Delete configuration for a key"""
    try:
        config_key = f"config:{key}"
        deleted = redis_client.delete(config_key)
        
        if deleted:
            # Clean up rate limit data
            redis_client.delete(f"tb:{key}", f"sw:{key}")
            # Clean up fixed window keys (harder to clean all, but they expire)
            return jsonify({'message': 'Configuration deleted'})
        else:
            return jsonify({'error': 'Configuration not found'}), 404
        
    except Exception as e:
        logger.error(f"Error deleting config: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/metrics', methods=['GET'])
def metrics():
    """Prometheus metrics endpoint"""
    return generate_latest(), 200, {'Content-Type': CONTENT_TYPE_LATEST}

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)