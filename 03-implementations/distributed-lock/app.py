#!/usr/bin/env python3
"""
Distributed Lock Service

A distributed locking service using Redis with timeout, renewal, and deadlock detection.
"""

import json
import time
import uuid
import threading
import logging
from typing import Dict, Any, Optional, Set
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta
from flask import Flask, request, jsonify
import redis
from prometheus_client import Counter, Histogram, Gauge, generate_latest, CONTENT_TYPE_LATEST

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# Prometheus metrics
LOCKS_ACQUIRED = Counter('distributed_locks_acquired_total', 'Total locks acquired')
LOCKS_RELEASED = Counter('distributed_locks_released_total', 'Total locks released')
LOCKS_ACTIVE = Gauge('distributed_locks_active', 'Currently active locks')
LOCK_WAIT_TIME = Histogram('distributed_locks_wait_time_seconds', 'Time spent waiting for locks')
RENEWAL_SUCCESS = Counter('distributed_locks_renewal_success_total', 'Successful lock renewals')
DEADLOCKS_DETECTED = Counter('distributed_locks_deadlocks_detected_total', 'Deadlocks detected')

@dataclass
class LockRequest:
    """Lock acquisition request"""
    client_id: str
    timeout: int = 30
    wait_timeout: int = 0
    auto_renew: bool = False
    metadata: Dict[str, Any] = None

@dataclass
class LockInfo:
    """Information about an active lock"""
    lock_id: str
    lock_name: str
    client_id: str
    acquired_at: datetime
    expires_at: datetime
    auto_renew: bool
    metadata: Dict[str, Any] = None
    renewal_count: int = 0

class DistributedLockService:
    """Distributed lock service implementation"""
    
    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client
        self.active_locks: Dict[str, LockInfo] = {}
        self.waiting_clients: Dict[str, Set[str]] = {}  # lock_name -> set of client_ids
        self.client_locks: Dict[str, Set[str]] = {}     # client_id -> set of lock_names
        self.lock = threading.RLock()
        
        # Configuration
        self.default_timeout = 30
        self.max_timeout = 300
        self.renewal_interval = 5
        self.deadlock_detection = True
        
        # Start background workers
        self.start_renewal_worker()
        if self.deadlock_detection:
            self.start_deadlock_detector()
    
    def start_renewal_worker(self):
        """Start background worker for lock renewal"""
        def renewal_worker():
            while True:
                try:
                    self.process_renewals()
                    time.sleep(self.renewal_interval)
                except Exception as e:
                    logger.error(f"Renewal worker error: {e}")
        
        thread = threading.Thread(target=renewal_worker, daemon=True)
        thread.start()
        logger.info("Lock renewal worker started")
    
    def start_deadlock_detector(self):
        """Start background worker for deadlock detection"""
        def deadlock_detector():
            while True:
                try:
                    self.detect_deadlocks()
                    time.sleep(10)  # Check every 10 seconds
                except Exception as e:
                    logger.error(f"Deadlock detector error: {e}")
        
        thread = threading.Thread(target=deadlock_detector, daemon=True)
        thread.start()
        logger.info("Deadlock detector started")
    
    def acquire_lock(self, lock_name: str, request: LockRequest) -> Dict[str, Any]:
        """Acquire a distributed lock"""
        start_time = time.time()
        
        # Validate timeout
        timeout = min(request.timeout, self.max_timeout)
        if timeout <= 0:
            timeout = self.default_timeout
        
        lock_id = str(uuid.uuid4())
        
        # Try to acquire immediately
        if self._try_acquire(lock_name, lock_id, request, timeout):
            wait_time = time.time() - start_time
            LOCK_WAIT_TIME.observe(wait_time)
            LOCKS_ACQUIRED.inc()
            LOCKS_ACTIVE.inc()
            
            return {
                'lockId': lock_id,
                'lockName': lock_name,
                'clientId': request.client_id,
                'acquired': True,
                'expiresAt': (datetime.now() + timedelta(seconds=timeout)).isoformat(),
                'renewalToken': lock_id if request.auto_renew else None,
                'waitTime': wait_time
            }
        
        # If wait_timeout is 0, fail immediately
        if request.wait_timeout <= 0:
            return {
                'lockId': None,
                'lockName': lock_name,
                'clientId': request.client_id,
                'acquired': False,
                'error': 'Lock not available',
                'waitTime': time.time() - start_time
            }
        
        # Wait for lock to become available
        return self._wait_for_lock(lock_name, lock_id, request, timeout, start_time)
    
    def _try_acquire(self, lock_name: str, lock_id: str, request: LockRequest, timeout: int) -> bool:
        """Try to acquire lock immediately"""
        redis_key = f"lock:{lock_name}"
        
        # Use Redis SET with NX (not exists) and EX (expiration)
        result = self.redis.set(redis_key, lock_id, nx=True, ex=timeout)
        
        if result:
            # Lock acquired successfully
            with self.lock:
                lock_info = LockInfo(
                    lock_id=lock_id,
                    lock_name=lock_name,
                    client_id=request.client_id,
                    acquired_at=datetime.now(),
                    expires_at=datetime.now() + timedelta(seconds=timeout),
                    auto_renew=request.auto_renew,
                    metadata=request.metadata or {}
                )
                
                self.active_locks[lock_id] = lock_info
                
                # Track client locks
                if request.client_id not in self.client_locks:
                    self.client_locks[request.client_id] = set()
                self.client_locks[request.client_id].add(lock_name)
            
            logger.info(f"Lock acquired: {lock_name} by {request.client_id} (ID: {lock_id})")
            return True
        
        return False
    
    def _wait_for_lock(self, lock_name: str, lock_id: str, request: LockRequest, 
                      timeout: int, start_time: float) -> Dict[str, Any]:
        """Wait for lock to become available"""
        end_time = start_time + request.wait_timeout
        
        # Add to waiting clients
        with self.lock:
            if lock_name not in self.waiting_clients:
                self.waiting_clients[lock_name] = set()
            self.waiting_clients[lock_name].add(request.client_id)
        
        try:
            while time.time() < end_time:
                if self._try_acquire(lock_name, lock_id, request, timeout):
                    wait_time = time.time() - start_time
                    LOCK_WAIT_TIME.observe(wait_time)
                    LOCKS_ACQUIRED.inc()
                    LOCKS_ACTIVE.inc()
                    
                    return {
                        'lockId': lock_id,
                        'lockName': lock_name,
                        'clientId': request.client_id,
                        'acquired': True,
                        'expiresAt': (datetime.now() + timedelta(seconds=timeout)).isoformat(),
                        'renewalToken': lock_id if request.auto_renew else None,
                        'waitTime': wait_time
                    }
                
                time.sleep(0.1)  # Poll every 100ms
            
            # Timeout waiting for lock
            return {
                'lockId': None,
                'lockName': lock_name,
                'clientId': request.client_id,
                'acquired': False,
                'error': 'Wait timeout exceeded',
                'waitTime': time.time() - start_time
            }
        
        finally:
            # Remove from waiting clients
            with self.lock:
                if lock_name in self.waiting_clients:
                    self.waiting_clients[lock_name].discard(request.client_id)
                    if not self.waiting_clients[lock_name]:
                        del self.waiting_clients[lock_name]
    
    def release_lock(self, lock_name: str, client_id: str, lock_id: str) -> Dict[str, Any]:
        """Release a distributed lock"""
        redis_key = f"lock:{lock_name}"
        
        # Verify lock ownership using Lua script for atomicity
        lua_script = """
        if redis.call("GET", KEYS[1]) == ARGV[1] then
            return redis.call("DEL", KEYS[1])
        else
            return 0
        end
        """
        
        result = self.redis.eval(lua_script, 1, redis_key, lock_id)
        
        if result == 1:
            # Lock released successfully
            with self.lock:
                if lock_id in self.active_locks:
                    lock_info = self.active_locks[lock_id]
                    del self.active_locks[lock_id]
                    
                    # Remove from client locks
                    if client_id in self.client_locks:
                        self.client_locks[client_id].discard(lock_name)
                        if not self.client_locks[client_id]:
                            del self.client_locks[client_id]
            
            LOCKS_RELEASED.inc()
            LOCKS_ACTIVE.dec()
            
            logger.info(f"Lock released: {lock_name} by {client_id} (ID: {lock_id})")
            return {
                'success': True,
                'message': 'Lock released successfully'
            }
        else:
            return {
                'success': False,
                'error': 'Lock not owned by client or already expired'
            }
    
    def renew_lock(self, lock_name: str, client_id: str, lock_id: str, 
                   extend_by: int = None) -> Dict[str, Any]:
        """Renew a lock's expiration time"""
        if extend_by is None:
            extend_by = self.default_timeout
        
        redis_key = f"lock:{lock_name}"
        
        # Verify ownership and extend expiration using Lua script
        lua_script = """
        if redis.call("GET", KEYS[1]) == ARGV[1] then
            return redis.call("EXPIRE", KEYS[1], ARGV[2])
        else
            return 0
        end
        """
        
        result = self.redis.eval(lua_script, 1, redis_key, lock_id, extend_by)
        
        if result == 1:
            # Update local tracking
            with self.lock:
                if lock_id in self.active_locks:
                    lock_info = self.active_locks[lock_id]
                    lock_info.expires_at = datetime.now() + timedelta(seconds=extend_by)
                    lock_info.renewal_count += 1
            
            RENEWAL_SUCCESS.inc()
            
            logger.info(f"Lock renewed: {lock_name} by {client_id} (ID: {lock_id})")
            return {
                'success': True,
                'expiresAt': (datetime.now() + timedelta(seconds=extend_by)).isoformat(),
                'renewalCount': lock_info.renewal_count if lock_id in self.active_locks else 0
            }
        else:
            return {
                'success': False,
                'error': 'Lock not owned by client or already expired'
            }
    
    def get_lock_status(self, lock_name: str) -> Dict[str, Any]:
        """Get status of a specific lock"""
        redis_key = f"lock:{lock_name}"
        
        # Check if lock exists in Redis
        lock_id = self.redis.get(redis_key)
        ttl = self.redis.ttl(redis_key)
        
        if lock_id is None:
            return {
                'lockName': lock_name,
                'exists': False,
                'available': True
            }
        
        # Find lock info
        with self.lock:
            lock_info = self.active_locks.get(lock_id.decode() if isinstance(lock_id, bytes) else lock_id)
        
        if lock_info:
            return {
                'lockName': lock_name,
                'exists': True,
                'available': False,
                'lockId': lock_info.lock_id,
                'clientId': lock_info.client_id,
                'acquiredAt': lock_info.acquired_at.isoformat(),
                'expiresAt': lock_info.expires_at.isoformat(),
                'ttl': ttl,
                'autoRenew': lock_info.auto_renew,
                'renewalCount': lock_info.renewal_count,
                'metadata': lock_info.metadata
            }
        else:
            return {
                'lockName': lock_name,
                'exists': True,
                'available': False,
                'ttl': ttl,
                'lockId': lock_id.decode() if isinstance(lock_id, bytes) else lock_id
            }
    
    def list_locks(self) -> Dict[str, Any]:
        """List all active locks"""
        with self.lock:
            locks = []
            for lock_info in self.active_locks.values():
                locks.append({
                    'lockName': lock_info.lock_name,
                    'lockId': lock_info.lock_id,
                    'clientId': lock_info.client_id,
                    'acquiredAt': lock_info.acquired_at.isoformat(),
                    'expiresAt': lock_info.expires_at.isoformat(),
                    'autoRenew': lock_info.auto_renew,
                    'renewalCount': lock_info.renewal_count
                })
            
            return {
                'locks': locks,
                'count': len(locks),
                'waitingClients': {
                    lock_name: list(clients) 
                    for lock_name, clients in self.waiting_clients.items()
                }
            }
    
    def get_stats(self) -> Dict[str, Any]:
        """Get lock service statistics"""
        with self.lock:
            return {
                'activeLocks': len(self.active_locks),
                'waitingClients': sum(len(clients) for clients in self.waiting_clients.values()),
                'totalClients': len(self.client_locks),
                'averageRenewals': sum(lock.renewal_count for lock in self.active_locks.values()) / max(1, len(self.active_locks))
            }
    
    def force_release(self, lock_name: str) -> Dict[str, Any]:
        """Force release a lock (admin operation)"""
        redis_key = f"lock:{lock_name}"
        
        # Get current lock info
        lock_id = self.redis.get(redis_key)
        if lock_id is None:
            return {
                'success': False,
                'error': 'Lock does not exist'
            }
        
        # Delete from Redis
        self.redis.delete(redis_key)
        
        # Clean up local tracking
        with self.lock:
            lock_id_str = lock_id.decode() if isinstance(lock_id, bytes) else lock_id
            if lock_id_str in self.active_locks:
                lock_info = self.active_locks[lock_id_str]
                del self.active_locks[lock_id_str]
                
                # Remove from client locks
                if lock_info.client_id in self.client_locks:
                    self.client_locks[lock_info.client_id].discard(lock_name)
                    if not self.client_locks[lock_info.client_id]:
                        del self.client_locks[lock_info.client_id]
        
        LOCKS_RELEASED.inc()
        LOCKS_ACTIVE.dec()
        
        logger.warning(f"Lock force released: {lock_name} (ID: {lock_id_str})")
        return {
            'success': True,
            'message': 'Lock force released'
        }
    
    def process_renewals(self):
        """Process automatic lock renewals"""
        with self.lock:
            locks_to_renew = [
                lock_info for lock_info in self.active_locks.values()
                if lock_info.auto_renew and 
                   lock_info.expires_at - datetime.now() < timedelta(seconds=self.renewal_interval * 2)
            ]
        
        for lock_info in locks_to_renew:
            try:
                result = self.renew_lock(
                    lock_info.lock_name,
                    lock_info.client_id,
                    lock_info.lock_id,
                    self.default_timeout
                )
                if not result['success']:
                    logger.warning(f"Auto-renewal failed for lock {lock_info.lock_name}: {result.get('error')}")
            except Exception as e:
                logger.error(f"Error during auto-renewal of lock {lock_info.lock_name}: {e}")
    
    def detect_deadlocks(self):
        """Detect potential deadlocks"""
        if not self.deadlock_detection:
            return
        
        with self.lock:
            # Build dependency graph: client -> locks they're waiting for
            waiting_graph = {}
            holding_graph = {}
            
            # Map clients to locks they're waiting for
            for lock_name, waiting_clients in self.waiting_clients.items():
                for client_id in waiting_clients:
                    if client_id not in waiting_graph:
                        waiting_graph[client_id] = set()
                    waiting_graph[client_id].add(lock_name)
            
            # Map locks to clients that hold them
            for lock_info in self.active_locks.values():
                holding_graph[lock_info.lock_name] = lock_info.client_id
            
            # Detect cycles
            for waiting_client in waiting_graph:
                if self._has_deadlock_cycle(waiting_client, waiting_graph, holding_graph, set()):
                    DEADLOCKS_DETECTED.inc()
                    logger.warning(f"Potential deadlock detected involving client {waiting_client}")
                    # In a real implementation, you might break the deadlock by failing some requests
    
    def _has_deadlock_cycle(self, client_id: str, waiting_graph: Dict, 
                           holding_graph: Dict, visited: Set[str]) -> bool:
        """Check if there's a deadlock cycle starting from client_id"""
        if client_id in visited:
            return True
        
        visited.add(client_id)
        
        # Check what locks this client is waiting for
        if client_id in waiting_graph:
            for lock_name in waiting_graph[client_id]:
                # Who holds this lock?
                if lock_name in holding_graph:
                    holding_client = holding_graph[lock_name]
                    if self._has_deadlock_cycle(holding_client, waiting_graph, holding_graph, visited.copy()):
                        return True
        
        return False

# Initialize Redis connection
redis_client = redis.Redis(
    host='localhost',
    port=6379,
    db=0,
    decode_responses=True
)

lock_service = DistributedLockService(redis_client)

# Flask routes

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    try:
        redis_client.ping()
        return jsonify({
            'status': 'healthy',
            'redis': 'connected',
            'timestamp': datetime.now().isoformat()
        })
    except Exception as e:
        return jsonify({
            'status': 'unhealthy',
            'error': str(e),
            'timestamp': datetime.now().isoformat()
        }), 503

@app.route('/locks/<lock_name>/acquire', methods=['POST'])
def acquire_lock(lock_name: str):
    """Acquire a lock"""
    try:
        data = request.get_json() or {}
        
        # Validate required fields
        if 'clientId' not in data:
            return jsonify({'error': 'Missing required field: clientId'}), 400
        
        lock_request = LockRequest(
            client_id=data['clientId'],
            timeout=data.get('timeout', 30),
            wait_timeout=data.get('waitTimeout', 0),
            auto_renew=data.get('autoRenew', False),
            metadata=data.get('metadata', {})
        )
        
        result = lock_service.acquire_lock(lock_name, lock_request)
        
        if result.get('acquired'):
            return jsonify(result)
        else:
            return jsonify(result), 409  # Conflict
        
    except Exception as e:
        logger.error(f"Error acquiring lock {lock_name}: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/locks/<lock_name>/release', methods=['POST'])
def release_lock(lock_name: str):
    """Release a lock"""
    try:
        data = request.get_json() or {}
        
        # Validate required fields
        required_fields = ['clientId', 'lockId']
        for field in required_fields:
            if field not in data:
                return jsonify({'error': f'Missing required field: {field}'}), 400
        
        result = lock_service.release_lock(lock_name, data['clientId'], data['lockId'])
        
        if result['success']:
            return jsonify(result)
        else:
            return jsonify(result), 400
        
    except Exception as e:
        logger.error(f"Error releasing lock {lock_name}: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/locks/<lock_name>/renew', methods=['POST'])
def renew_lock(lock_name: str):
    """Renew a lock"""
    try:
        data = request.get_json() or {}
        
        # Validate required fields
        required_fields = ['clientId', 'lockId']
        for field in required_fields:
            if field not in data:
                return jsonify({'error': f'Missing required field: {field}'}), 400
        
        result = lock_service.renew_lock(
            lock_name,
            data['clientId'],
            data['lockId'],
            data.get('extendBy')
        )
        
        if result['success']:
            return jsonify(result)
        else:
            return jsonify(result), 400
        
    except Exception as e:
        logger.error(f"Error renewing lock {lock_name}: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/locks/<lock_name>/status', methods=['GET'])
def get_lock_status(lock_name: str):
    """Get lock status"""
    try:
        result = lock_service.get_lock_status(lock_name)
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Error getting lock status {lock_name}: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/locks', methods=['GET'])
def list_locks():
    """List all active locks"""
    try:
        result = lock_service.list_locks()
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Error listing locks: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/locks/stats', methods=['GET'])
def get_stats():
    """Get lock statistics"""
    try:
        result = lock_service.get_stats()
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Error getting stats: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/locks/<lock_name>', methods=['DELETE'])
def force_release_lock(lock_name: str):
    """Force release a lock (admin operation)"""
    try:
        result = lock_service.force_release(lock_name)
        
        if result['success']:
            return jsonify(result)
        else:
            return jsonify(result), 404
        
    except Exception as e:
        logger.error(f"Error force releasing lock {lock_name}: {e}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/metrics', methods=['GET'])
def metrics():
    """Prometheus metrics endpoint"""
    return generate_latest(), 200, {'Content-Type': CONTENT_TYPE_LATEST}

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)