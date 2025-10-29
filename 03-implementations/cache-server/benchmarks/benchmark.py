#!/usr/bin/env python3
"""
Cache Server Performance Benchmark
Tests cache server performance under various load conditions.
"""

import asyncio
import aiohttp
import time
import json
import statistics
from typing import List, Dict, Any
from concurrent.futures import ThreadPoolExecutor
import argparse


class CacheBenchmark:
    """Performance benchmark for cache server"""
    
    def __init__(self, base_url: str = "http://localhost:8000"):
        self.base_url = base_url
        self.session = None
    
    async def __aenter__(self):
        self.session = aiohttp.ClientSession()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()
    
    async def set_item(self, key: str, value: Any, ttl: int = 3600) -> float:
        """Set cache item and return response time"""
        start_time = time.time()
        
        async with self.session.put(
            f"{self.base_url}/cache/{key}",
            json={"value": value, "ttl": ttl}
        ) as response:
            await response.json()
            return time.time() - start_time
    
    async def get_item(self, key: str) -> tuple[float, bool]:
        """Get cache item and return (response_time, found)"""
        start_time = time.time()
        
        async with self.session.get(f"{self.base_url}/cache/{key}") as response:
            response_time = time.time() - start_time
            found = response.status == 200
            if found:
                await response.json()
            return response_time, found
    
    async def delete_item(self, key: str) -> float:
        """Delete cache item and return response time"""
        start_time = time.time()
        
        async with self.session.delete(f"{self.base_url}/cache/{key}") as response:
            await response.json()
            return time.time() - start_time
    
    async def get_stats(self) -> Dict[str, Any]:
        """Get cache statistics"""
        async with self.session.get(f"{self.base_url}/stats") as response:
            return await response.json()
    
    async def benchmark_sequential_operations(self, num_operations: int = 1000) -> Dict[str, Any]:
        """Benchmark sequential cache operations"""
        print(f"Running sequential benchmark with {num_operations} operations...")
        
        # Prepare test data
        test_data = [
            {"key": f"test:seq:{i}", "value": f"value_{i}", "ttl": 3600}
            for i in range(num_operations)
        ]
        
        # Benchmark SET operations
        set_times = []
        for item in test_data:
            response_time = await self.set_item(item["key"], item["value"], item["ttl"])
            set_times.append(response_time)
        
        # Benchmark GET operations
        get_times = []
        hits = 0
        for item in test_data:
            response_time, found = await self.get_item(item["key"])
            get_times.append(response_time)
            if found:
                hits += 1
        
        # Benchmark DELETE operations
        delete_times = []
        for item in test_data:
            response_time = await self.delete_item(item["key"])
            delete_times.append(response_time)
        
        return {
            "type": "sequential",
            "operations": num_operations,
            "set_operations": {
                "avg_time_ms": statistics.mean(set_times) * 1000,
                "min_time_ms": min(set_times) * 1000,
                "max_time_ms": max(set_times) * 1000,
                "ops_per_second": num_operations / sum(set_times)
            },
            "get_operations": {
                "avg_time_ms": statistics.mean(get_times) * 1000,
                "min_time_ms": min(get_times) * 1000,
                "max_time_ms": max(get_times) * 1000,
                "ops_per_second": num_operations / sum(get_times),
                "hit_rate": (hits / num_operations) * 100
            },
            "delete_operations": {
                "avg_time_ms": statistics.mean(delete_times) * 1000,
                "min_time_ms": min(delete_times) * 1000,
                "max_time_ms": max(delete_times) * 1000,
                "ops_per_second": num_operations / sum(delete_times)
            }
        }
    
    async def benchmark_concurrent_operations(self, num_operations: int = 1000, concurrency: int = 50) -> Dict[str, Any]:
        """Benchmark concurrent cache operations"""
        print(f"Running concurrent benchmark with {num_operations} operations, concurrency: {concurrency}...")
        
        # Prepare test data
        test_data = [
            {"key": f"test:conc:{i}", "value": {"id": i, "data": f"value_{i}"}, "ttl": 3600}
            for i in range(num_operations)
        ]
        
        # Benchmark concurrent SET operations
        start_time = time.time()
        semaphore = asyncio.Semaphore(concurrency)
        
        async def set_with_semaphore(item):
            async with semaphore:
                return await self.set_item(item["key"], item["value"], item["ttl"])
        
        set_times = await asyncio.gather(*[set_with_semaphore(item) for item in test_data])
        total_set_time = time.time() - start_time
        
        # Benchmark concurrent GET operations
        start_time = time.time()
        
        async def get_with_semaphore(item):
            async with semaphore:
                return await self.get_item(item["key"])
        
        get_results = await asyncio.gather(*[get_with_semaphore(item) for item in test_data])
        total_get_time = time.time() - start_time
        
        get_times = [result[0] for result in get_results]
        hits = sum(1 for result in get_results if result[1])
        
        # Clean up
        await asyncio.gather(*[self.delete_item(item["key"]) for item in test_data])
        
        return {
            "type": "concurrent",
            "operations": num_operations,
            "concurrency": concurrency,
            "set_operations": {
                "total_time_s": total_set_time,
                "avg_time_ms": statistics.mean(set_times) * 1000,
                "ops_per_second": num_operations / total_set_time
            },
            "get_operations": {
                "total_time_s": total_get_time,
                "avg_time_ms": statistics.mean(get_times) * 1000,
                "ops_per_second": num_operations / total_get_time,
                "hit_rate": (hits / num_operations) * 100
            }
        }
    
    async def benchmark_mixed_workload(self, duration_seconds: int = 60) -> Dict[str, Any]:
        """Benchmark mixed read/write workload for specified duration"""
        print(f"Running mixed workload benchmark for {duration_seconds} seconds...")
        
        start_time = time.time()
        end_time = start_time + duration_seconds
        
        operations = {"sets": 0, "gets": 0, "deletes": 0}
        response_times = {"sets": [], "gets": [], "deletes": []}
        hits = 0
        total_gets = 0
        
        key_counter = 0
        active_keys = set()
        
        while time.time() < end_time:
            # Mixed workload: 20% writes, 70% reads, 10% deletes
            import random
            operation = random.choices(
                ["set", "get", "delete"],
                weights=[20, 70, 10]
            )[0]
            
            if operation == "set":
                key = f"test:mixed:{key_counter}"
                value = {"id": key_counter, "timestamp": time.time()}
                response_time = await self.set_item(key, value)
                
                operations["sets"] += 1
                response_times["sets"].append(response_time)
                active_keys.add(key)
                key_counter += 1
                
            elif operation == "get" and active_keys:
                key = random.choice(list(active_keys))
                response_time, found = await self.get_item(key)
                
                operations["gets"] += 1
                response_times["gets"].append(response_time)
                total_gets += 1
                if found:
                    hits += 1
                    
            elif operation == "delete" and active_keys:
                key = random.choice(list(active_keys))
                response_time = await self.delete_item(key)
                
                operations["deletes"] += 1
                response_times["deletes"].append(response_time)
                active_keys.discard(key)
        
        actual_duration = time.time() - start_time
        total_operations = sum(operations.values())
        
        return {
            "type": "mixed_workload",
            "duration_seconds": actual_duration,
            "total_operations": total_operations,
            "ops_per_second": total_operations / actual_duration,
            "operations": operations,
            "avg_response_times_ms": {
                op: statistics.mean(times) * 1000 if times else 0
                for op, times in response_times.items()
            },
            "hit_rate": (hits / total_gets * 100) if total_gets > 0 else 0
        }


async def run_benchmarks():
    """Run all benchmark tests"""
    parser = argparse.ArgumentParser(description="Cache Server Benchmark")
    parser.add_argument("--url", default="http://localhost:8000", help="Cache server URL")
    parser.add_argument("--sequential", type=int, default=1000, help="Number of sequential operations")
    parser.add_argument("--concurrent", type=int, default=1000, help="Number of concurrent operations")
    parser.add_argument("--concurrency", type=int, default=50, help="Concurrency level")
    parser.add_argument("--duration", type=int, default=60, help="Mixed workload duration in seconds")
    
    args = parser.parse_args()
    
    print("Cache Server Performance Benchmark")
    print("=" * 50)
    
    async with CacheBenchmark(args.url) as benchmark:
        # Check server health
        try:
            stats = await benchmark.get_stats()
            print(f"Server is healthy. Current keys: {stats['total_keys']}")
            print()
        except Exception as e:
            print(f"Error connecting to server: {e}")
            return
        
        results = []
        
        # Sequential benchmark
        try:
            result = await benchmark.benchmark_sequential_operations(args.sequential)
            results.append(result)
            print_benchmark_result(result)
        except Exception as e:
            print(f"Sequential benchmark failed: {e}")
        
        # Concurrent benchmark
        try:
            result = await benchmark.benchmark_concurrent_operations(args.concurrent, args.concurrency)
            results.append(result)
            print_benchmark_result(result)
        except Exception as e:
            print(f"Concurrent benchmark failed: {e}")
        
        # Mixed workload benchmark
        try:
            result = await benchmark.benchmark_mixed_workload(args.duration)
            results.append(result)
            print_benchmark_result(result)
        except Exception as e:
            print(f"Mixed workload benchmark failed: {e}")
        
        # Save results
        with open("benchmark_results.json", "w") as f:
            json.dump(results, f, indent=2)
        
        print("\nBenchmark results saved to benchmark_results.json")


def print_benchmark_result(result: Dict[str, Any]):
    """Print formatted benchmark result"""
    print(f"\n{result['type'].upper()} BENCHMARK RESULTS")
    print("-" * 40)
    
    if result["type"] == "sequential":
        print(f"Operations: {result['operations']}")
        print(f"SET - Avg: {result['set_operations']['avg_time_ms']:.2f}ms, "
              f"OPS: {result['set_operations']['ops_per_second']:.0f}/s")
        print(f"GET - Avg: {result['get_operations']['avg_time_ms']:.2f}ms, "
              f"OPS: {result['get_operations']['ops_per_second']:.0f}/s, "
              f"Hit Rate: {result['get_operations']['hit_rate']:.1f}%")
        print(f"DEL - Avg: {result['delete_operations']['avg_time_ms']:.2f}ms, "
              f"OPS: {result['delete_operations']['ops_per_second']:.0f}/s")
    
    elif result["type"] == "concurrent":
        print(f"Operations: {result['operations']}, Concurrency: {result['concurrency']}")
        print(f"SET - Total: {result['set_operations']['total_time_s']:.2f}s, "
              f"OPS: {result['set_operations']['ops_per_second']:.0f}/s")
        print(f"GET - Total: {result['get_operations']['total_time_s']:.2f}s, "
              f"OPS: {result['get_operations']['ops_per_second']:.0f}/s, "
              f"Hit Rate: {result['get_operations']['hit_rate']:.1f}%")
    
    elif result["type"] == "mixed_workload":
        print(f"Duration: {result['duration_seconds']:.1f}s")
        print(f"Total Operations: {result['total_operations']}")
        print(f"Overall OPS: {result['ops_per_second']:.0f}/s")
        print(f"Operations: SET={result['operations']['sets']}, "
              f"GET={result['operations']['gets']}, "
              f"DEL={result['operations']['deletes']}")
        print(f"Hit Rate: {result['hit_rate']:.1f}%")


if __name__ == "__main__":
    asyncio.run(run_benchmarks())