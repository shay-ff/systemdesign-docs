#!/usr/bin/env python3
"""
Load testing script for Distributed Lock Service
"""

import time
import json
import statistics
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any
import requests
import click
from tabulate import tabulate

class LoadTester:
    def __init__(self, base_url: str = "http://localhost:5000"):
        self.base_url = base_url
        self.session = requests.Session()
        self.client_id_counter = 0
        self.lock = threading.Lock()
    
    def get_client_id(self) -> str:
        """Get unique client ID"""
        with self.lock:
            self.client_id_counter += 1
            return f"load-test-client-{self.client_id_counter}"
    
    def acquire_and_release_lock(self, lock_name: str, hold_time: float = 0.1) -> Dict[str, Any]:
        """Acquire a lock, hold it, then release it"""
        client_id = self.get_client_id()
        start_time = time.time()
        
        try:
            # Acquire lock
            acquire_data = {
                "clientId": client_id,
                "timeout": 30,
                "waitTimeout": 5
            }
            
            acquire_response = self.session.post(
                f"{self.base_url}/locks/{lock_name}/acquire",
                json=acquire_data,
                timeout=10
            )
            
            acquire_time = time.time()
            
            if acquire_response.status_code != 200:
                return {
                    "success": False,
                    "operation": "acquire",
                    "response_time": (acquire_time - start_time) * 1000,
                    "error": acquire_response.text,
                    "status_code": acquire_response.status_code
                }
            
            acquire_result = acquire_response.json()
            if not acquire_result.get("acquired"):
                return {
                    "success": False,
                    "operation": "acquire",
                    "response_time": (acquire_time - start_time) * 1000,
                    "error": acquire_result.get("error", "Lock not acquired"),
                    "status_code": acquire_response.status_code
                }
            
            lock_id = acquire_result["lockId"]
            
            # Hold the lock
            time.sleep(hold_time)
            
            # Release lock
            release_data = {
                "clientId": client_id,
                "lockId": lock_id
            }
            
            release_response = self.session.post(
                f"{self.base_url}/locks/{lock_name}/release",
                json=release_data,
                timeout=10
            )
            
            end_time = time.time()
            total_time = (end_time - start_time) * 1000
            
            if release_response.status_code != 200:
                return {
                    "success": False,
                    "operation": "release",
                    "response_time": total_time,
                    "error": release_response.text,
                    "status_code": release_response.status_code
                }
            
            release_result = release_response.json()
            if not release_result.get("success"):
                return {
                    "success": False,
                    "operation": "release",
                    "response_time": total_time,
                    "error": release_result.get("error", "Release failed"),
                    "status_code": release_response.status_code
                }
            
            return {
                "success": True,
                "response_time": total_time,
                "acquire_time": (acquire_time - start_time) * 1000,
                "wait_time": acquire_result.get("waitTime", 0) * 1000,
                "lock_id": lock_id
            }
            
        except Exception as e:
            end_time = time.time()
            return {
                "success": False,
                "response_time": (end_time - start_time) * 1000,
                "error": str(e),
                "operation": "exception"
            }
    
    def run_contention_test(self, lock_name: str, concurrent: int, operations: int, 
                           hold_time: float = 0.1) -> Dict[str, Any]:
        """Run contention test where multiple clients compete for the same lock"""
        print(f"Running contention test: {operations} operations, {concurrent} concurrent clients")
        print(f"Lock: {lock_name}, Hold time: {hold_time}s")
        
        results = []
        start_time = time.time()
        
        with ThreadPoolExecutor(max_workers=concurrent) as executor:
            # Submit all operations
            futures = [
                executor.submit(self.acquire_and_release_lock, lock_name, hold_time)
                for _ in range(operations)
            ]
            
            # Collect results
            for i, future in enumerate(as_completed(futures)):
                result = future.result()
                results.append(result)
                
                # Progress indicator
                if (i + 1) % 10 == 0:
                    print(f"Completed {i + 1}/{operations} operations")
        
        end_time = time.time()
        total_time = end_time - start_time
        
        return self.analyze_results(results, total_time, "Contention Test")
    
    def run_parallel_locks_test(self, concurrent: int, operations: int, 
                               hold_time: float = 0.1) -> Dict[str, Any]:
        """Run test with different locks (no contention)"""
        print(f"Running parallel locks test: {operations} operations, {concurrent} concurrent clients")
        print(f"Hold time: {hold_time}s")
        
        results = []
        start_time = time.time()
        
        with ThreadPoolExecutor(max_workers=concurrent) as executor:
            # Submit operations with unique lock names
            futures = [
                executor.submit(self.acquire_and_release_lock, f"lock-{i}", hold_time)
                for i in range(operations)
            ]
            
            # Collect results
            for i, future in enumerate(as_completed(futures)):
                result = future.result()
                results.append(result)
                
                # Progress indicator
                if (i + 1) % 10 == 0:
                    print(f"Completed {i + 1}/{operations} operations")
        
        end_time = time.time()
        total_time = end_time - start_time
        
        return self.analyze_results(results, total_time, "Parallel Locks Test")
    
    def run_renewal_test(self, lock_name: str, renewal_count: int = 5) -> Dict[str, Any]:
        """Test lock renewal functionality"""
        print(f"Running renewal test: {renewal_count} renewals")
        
        client_id = self.get_client_id()
        start_time = time.time()
        
        try:
            # Acquire lock
            acquire_data = {
                "clientId": client_id,
                "timeout": 10,
                "autoRenew": False
            }
            
            response = self.session.post(
                f"{self.base_url}/locks/{lock_name}/acquire",
                json=acquire_data,
                timeout=10
            )
            
            if response.status_code != 200:
                return {"success": False, "error": "Failed to acquire lock"}
            
            result = response.json()
            if not result.get("acquired"):
                return {"success": False, "error": "Lock not acquired"}
            
            lock_id = result["lockId"]
            renewal_times = []
            
            # Perform renewals
            for i in range(renewal_count):
                renewal_start = time.time()
                
                renewal_data = {
                    "clientId": client_id,
                    "lockId": lock_id,
                    "extendBy": 30
                }
                
                renewal_response = self.session.post(
                    f"{self.base_url}/locks/{lock_name}/renew",
                    json=renewal_data,
                    timeout=10
                )
                
                renewal_end = time.time()
                renewal_time = (renewal_end - renewal_start) * 1000
                renewal_times.append(renewal_time)
                
                if renewal_response.status_code != 200:
                    return {
                        "success": False,
                        "error": f"Renewal {i+1} failed: {renewal_response.text}"
                    }
                
                renewal_result = renewal_response.json()
                if not renewal_result.get("success"):
                    return {
                        "success": False,
                        "error": f"Renewal {i+1} failed: {renewal_result.get('error')}"
                    }
                
                time.sleep(1)  # Wait between renewals
            
            # Release lock
            release_data = {
                "clientId": client_id,
                "lockId": lock_id
            }
            
            self.session.post(
                f"{self.base_url}/locks/{lock_name}/release",
                json=release_data,
                timeout=10
            )
            
            end_time = time.time()
            total_time = (end_time - start_time) * 1000
            
            return {
                "success": True,
                "total_time": total_time,
                "renewal_count": renewal_count,
                "avg_renewal_time": statistics.mean(renewal_times),
                "min_renewal_time": min(renewal_times),
                "max_renewal_time": max(renewal_times),
                "renewal_times": renewal_times
            }
            
        except Exception as e:
            return {"success": False, "error": str(e)}
    
    def analyze_results(self, results: List[Dict[str, Any]], total_time: float, 
                       test_name: str) -> Dict[str, Any]:
        """Analyze test results"""
        total_operations = len(results)
        successful_ops = sum(1 for r in results if r.get("success", False))
        failed_ops = total_operations - successful_ops
        
        if successful_ops > 0:
            response_times = [r["response_time"] for r in results if r.get("success", False)]
            acquire_times = [r.get("acquire_time", 0) for r in results if r.get("success", False) and "acquire_time" in r]
            wait_times = [r.get("wait_time", 0) for r in results if r.get("success", False) and "wait_time" in r]
            
            avg_response_time = statistics.mean(response_times)
            median_response_time = statistics.median(response_times)
            p95_response_time = sorted(response_times)[int(0.95 * len(response_times))] if response_times else 0
            p99_response_time = sorted(response_times)[int(0.99 * len(response_times))] if response_times else 0
            min_response_time = min(response_times) if response_times else 0
            max_response_time = max(response_times) if response_times else 0
            
            avg_acquire_time = statistics.mean(acquire_times) if acquire_times else 0
            avg_wait_time = statistics.mean(wait_times) if wait_times else 0
        else:
            avg_response_time = median_response_time = p95_response_time = p99_response_time = 0
            min_response_time = max_response_time = avg_acquire_time = avg_wait_time = 0
        
        throughput = total_operations / total_time if total_time > 0 else 0
        
        return {
            "test_name": test_name,
            "total_operations": total_operations,
            "successful_operations": successful_ops,
            "failed_operations": failed_ops,
            "success_rate": (successful_ops / total_operations) * 100 if total_operations > 0 else 0,
            "total_time": total_time,
            "throughput": throughput,
            "response_times": {
                "avg": avg_response_time,
                "median": median_response_time,
                "p95": p95_response_time,
                "p99": p99_response_time,
                "min": min_response_time,
                "max": max_response_time
            },
            "acquire_time_avg": avg_acquire_time,
            "wait_time_avg": avg_wait_time
        }
    
    def print_results(self, results: Dict[str, Any]):
        """Print formatted results"""
        print("\n" + "="*60)
        print(f"{results['test_name'].upper()} RESULTS")
        print("="*60)
        
        # Summary table
        summary_data = [
            ["Total Operations", results["total_operations"]],
            ["Successful Operations", results["successful_operations"]],
            ["Failed Operations", results["failed_operations"]],
            ["Success Rate", f"{results['success_rate']:.2f}%"],
            ["Total Time", f"{results['total_time']:.2f}s"],
            ["Throughput", f"{results['throughput']:.2f} ops/s"],
            ["Avg Acquire Time", f"{results['acquire_time_avg']:.2f}ms"],
            ["Avg Wait Time", f"{results['wait_time_avg']:.2f}ms"]
        ]
        
        print("\nSummary:")
        print(tabulate(summary_data, headers=["Metric", "Value"], tablefmt="grid"))
        
        # Response time table
        rt = results["response_times"]
        response_time_data = [
            ["Average", f"{rt['avg']:.2f}ms"],
            ["Median", f"{rt['median']:.2f}ms"],
            ["95th Percentile", f"{rt['p95']:.2f}ms"],
            ["99th Percentile", f"{rt['p99']:.2f}ms"],
            ["Min", f"{rt['min']:.2f}ms"],
            ["Max", f"{rt['max']:.2f}ms"]
        ]
        
        print("\nResponse Times:")
        print(tabulate(response_time_data, headers=["Metric", "Value"], tablefmt="grid"))

@click.command()
@click.option('--url', default='http://localhost:5000', help='Base URL of the distributed lock service')
@click.option('--test-type', default='contention', 
              type=click.Choice(['contention', 'parallel', 'renewal', 'all']), 
              help='Type of test to run')
@click.option('--concurrent', default=10, help='Number of concurrent clients')
@click.option('--operations', default=100, help='Total number of operations')
@click.option('--hold-time', default=0.1, help='Time to hold lock in seconds')
@click.option('--lock-name', default='test-lock', help='Lock name for contention test')
def main(url, test_type, concurrent, operations, hold_time, lock_name):
    """Load test the distributed lock service"""
    
    tester = LoadTester(url)
    
    try:
        # Health check
        response = tester.session.get(f"{url}/health")
        if response.status_code != 200:
            print(f"Service health check failed: {response.status_code}")
            return
        
        print(f"Service is healthy: {response.json()}")
        
        if test_type == 'contention' or test_type == 'all':
            results = tester.run_contention_test(lock_name, concurrent, operations, hold_time)
            tester.print_results(results)
        
        if test_type == 'parallel' or test_type == 'all':
            results = tester.run_parallel_locks_test(concurrent, operations, hold_time)
            tester.print_results(results)
        
        if test_type == 'renewal' or test_type == 'all':
            results = tester.run_renewal_test(f"{lock_name}-renewal", 10)
            if results.get('success'):
                print(f"\nRenewal Test Results:")
                print(f"Total Time: {results['total_time']:.2f}ms")
                print(f"Renewals: {results['renewal_count']}")
                print(f"Avg Renewal Time: {results['avg_renewal_time']:.2f}ms")
                print(f"Min Renewal Time: {results['min_renewal_time']:.2f}ms")
                print(f"Max Renewal Time: {results['max_renewal_time']:.2f}ms")
            else:
                print(f"Renewal test failed: {results.get('error')}")
        
    except KeyboardInterrupt:
        print("\nLoad test interrupted by user")
    except Exception as e:
        print(f"Load test failed: {e}")

if __name__ == "__main__":
    main()