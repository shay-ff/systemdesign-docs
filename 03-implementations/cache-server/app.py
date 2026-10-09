"""
Cache Server Implementation
A REST API cache service with Redis backend demonstrating system design caching concepts.
"""

import os
import time
import json
from typing import Optional, Dict, Any
from datetime import datetime, timedelta

import redis
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
import uvicorn


class CacheItem(BaseModel):
    """Request model for cache operations"""
    value: Any
    ttl: Optional[int] = Field(None, description="Time to live in seconds")


class CacheStats(BaseModel):
    """Cache statistics model"""
    total_keys: int
    hits: int
    misses: int
    hit_rate: float
    memory_usage: str
    uptime_seconds: int


class CacheServer:
    """Main cache server implementation"""
    
    def __init__(self):
        # Redis connection
        self.redis_client = redis.Redis(
            host=os.getenv('REDIS_HOST', 'localhost'),
            port=int(os.getenv('REDIS_PORT', 6379)),
            db=int(os.getenv('REDIS_DB', 0)),
            decode_responses=True
        )
        
        # Configuration
        self.default_ttl = int(os.getenv('DEFAULT_TTL', 3600))
        self.max_memory_policy = os.getenv('MAX_MEMORY_POLICY', 'allkeys-lru')
        
        # Statistics
        self.start_time = time.time()
        self.stats = {
            'hits': 0,
            'misses': 0
        }
        
        # Configure Redis eviction policy
        try:
            self.redis_client.config_set('maxmemory-policy', self.max_memory_policy)
        except redis.RedisError:
            print(f"Warning: Could not set maxmemory-policy to {self.max_memory_policy}")
    
    def get(self, key: str) -> Optional[Any]:
        """Retrieve value from cache"""
        try:
            value = self.redis_client.get(key)
            if value is not None:
                self.stats['hits'] += 1
                # Try to parse as JSON, fallback to string
                try:
                    return json.loads(value)
                except json.JSONDecodeError:
                    return value
            else:
                self.stats['misses'] += 1
                return None
        except redis.RedisError as e:
            raise HTTPException(status_code=500, detail=f"Redis error: {str(e)}")
    
    def set(self, key: str, value: Any, ttl: Optional[int] = None) -> bool:
        """Store value in cache"""
        try:
            # Serialize value as JSON
            serialized_value = json.dumps(value) if not isinstance(value, str) else value
            
            # Use provided TTL or default
            expiration = ttl if ttl is not None else self.default_ttl
            
            # Store in Redis
            return self.redis_client.setex(key, expiration, serialized_value)
        except redis.RedisError as e:
            raise HTTPException(status_code=500, detail=f"Redis error: {str(e)}")
    
    def delete(self, key: str) -> bool:
        """Remove key from cache"""
        try:
            return bool(self.redis_client.delete(key))
        except redis.RedisError as e:
            raise HTTPException(status_code=500, detail=f"Redis error: {str(e)}")
    
    def clear(self) -> bool:
        """Clear entire cache"""
        try:
            self.redis_client.flushdb()
            return True
        except redis.RedisError as e:
            raise HTTPException(status_code=500, detail=f"Redis error: {str(e)}")
    
    def get_stats(self) -> CacheStats:
        """Get cache statistics"""
        try:
            info = self.redis_client.info()
            total_keys = self.redis_client.dbsize()
            
            total_requests = self.stats['hits'] + self.stats['misses']
            hit_rate = (self.stats['hits'] / total_requests * 100) if total_requests > 0 else 0
            
            return CacheStats(
                total_keys=total_keys,
                hits=self.stats['hits'],
                misses=self.stats['misses'],
                hit_rate=round(hit_rate, 2),
                memory_usage=info.get('used_memory_human', 'Unknown'),
                uptime_seconds=int(time.time() - self.start_time)
            )
        except redis.RedisError as e:
            raise HTTPException(status_code=500, detail=f"Redis error: {str(e)}")


# Initialize FastAPI app and cache server
app = FastAPI(
    title="Cache Server",
    description="A high-performance REST API cache service with Redis backend",
    version="1.0.0"
)

cache_server = CacheServer()


@app.get("/health")
async def health_check():
    """Health check endpoint"""
    try:
        # Test Redis connection
        cache_server.redis_client.ping()
        return {"status": "healthy", "timestamp": datetime.utcnow().isoformat()}
    except redis.RedisError:
        raise HTTPException(status_code=503, detail="Redis connection failed")


@app.get("/cache/{key}")
async def get_cache_item(key: str):
    """Retrieve cached value by key"""
    value = cache_server.get(key)
    if value is None:
        raise HTTPException(status_code=404, detail="Key not found")
    
    return {
        "key": key,
        "value": value,
        "timestamp": datetime.utcnow().isoformat()
    }


@app.put("/cache/{key}")
async def set_cache_item(key: str, item: CacheItem):
    """Store key-value pair in cache"""
    success = cache_server.set(key, item.value, item.ttl)
    if not success:
        raise HTTPException(status_code=500, detail="Failed to store item")
    
    return {
        "key": key,
        "value": item.value,
        "ttl": item.ttl or cache_server.default_ttl,
        "timestamp": datetime.utcnow().isoformat()
    }


@app.delete("/cache/{key}")
async def delete_cache_item(key: str):
    """Remove specific key from cache"""
    success = cache_server.delete(key)
    if not success:
        raise HTTPException(status_code=404, detail="Key not found")
    
    return {
        "key": key,
        "deleted": True,
        "timestamp": datetime.utcnow().isoformat()
    }


@app.delete("/cache")
async def clear_cache():
    """Clear entire cache"""
    cache_server.clear()
    return {
        "cleared": True,
        "timestamp": datetime.utcnow().isoformat()
    }


@app.get("/stats")
async def get_cache_stats():
    """Get cache statistics and performance metrics"""
    return cache_server.get_stats()


@app.get("/")
async def root():
    """Root endpoint with API information"""
    return {
        "service": "Cache Server",
        "version": "1.0.0",
        "description": "REST API cache service with Redis backend",
        "endpoints": {
            "GET /cache/{key}": "Retrieve cached value",
            "PUT /cache/{key}": "Store key-value pair",
            "DELETE /cache/{key}": "Remove specific key",
            "DELETE /cache": "Clear entire cache",
            "GET /stats": "Cache statistics",
            "GET /health": "Health check"
        }
    }


if __name__ == "__main__":
    uvicorn.run(
        "app:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info"
    )