#!/usr/bin/env python3
"""
Load testing script for Rate Limiter Service
"""

import time
import json
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any
import requests
import click
from tabulate import tabulate

class LoadTester:
    def __init__(self, base_url: str = "http://localhost:5000"):
        self.base_url = base_url
        self.session = requests.Session()
    
    def setup_config(self, key: str, algorithm: str, limit: int, window: int, burst: int = 0):
        """Setup rate limit configuration"""
        config = {
            "key": key,
            "algorithm": algorithm,
            "limit": limit,
            "window": window,
            "burst": burst
        }
        
        response = self.session.post(f"{self.base_url}/config", json=config)
        if response.status_code != 200:
            raise Exception(f"Failed to create config: {response.text}")
        
        print(f"Created config for {key}: {algorithm}, {limit}/{window}s")
    
    def check_rate_limit(self, key: str) -> Dict[str, Any]:
        """Single rate limit check"""
        start_time = time.time()
        
        try:
            response = self.session.post(
                f"{self.base_url}/check",
                json={"key": key},
                timeout=5
            )
            
            end_time = time.time()
            response_time = (end_time - start_time) * 1000  # ms
            
            if response.status_code == 200:
                data = response.json()
                return {
                    "success": True,
                    "allowed": data.get("allowed", False),
                    "response_time": response_time,
                    "status_code": response.status_code
                }
            else:
                return {
                    "success": False,
                    "allowed": False,
                    "response_time": response_time,
                    "status_code": response.status_code,
                    "error": response.text
                }
        
        except Exception as e:
            end_time = time.time()
            response_time = (end_time - start_time) * 1000
            return {
                "success": False,
                "allowed": False,
                "response_time": response_time,
                "status_code": 0,
                "error": str(e)
            }
    
    def run_load_test(self, key: str, concurrent: int, total_requests: int) -> Dict[str, Any]:
        """Run load test with specified parameters"""
        print(f"Starting load test: {total_requests} requests, {concurrent} concurrent")
        
        results = []
        start_time = time.time()
        
        with ThreadPoolExecutor(max_workers=concurrent) as executor:
            # Submit all requests
            futures = [
                executor.submit(self.check_rate_limit, key)
                for _ in range(total_requests)
            ]
            
            # Collect results
            for future in as_completed(futures):
                result = future.result()
                results.append(result)
                
                # Progress indicator
                if len(results) % 100 == 0:
                    print(f"Completed {len(results)}/{total_requests} requests")
        
        end_time = time.time()
        total_time = end_time - start_time
        
        return self.analyze_results(results, total_time)
    
    def analyze_results(self, results: List[Dict[str, Any]], total_time: float) -> Dict[str, Any]:
        """Analyze load test results"""
        total_requests = len(results)
        successful_requests = sum(1 for r in results if r["success"])
        allowed_requests = sum(1 for r in results if r["allowed"])
        denied_requests = sum(1 for r in results if r["success"] and not r["allowed"])
        failed_requests = total_requests - successful_requests
        
        response_times = [r["response_time"] for r in results if r["success"]]
        
        if response_times:
            avg_response_time = statistics.mean(response_times)
            median_response_time = statistics.median(response_times)
            p95_response_time = sorted(response_times)[int(0.95 * len(response_times))]
            p99_response_time = sorted(response_times)[int(0.99 * len(response_times))]
            min_response_time = min(response_times)
            max_response_time = max(response_times)
        else:
            avg_response_time = median_response_time = p95_response_time = p99_response_time = 0
            min_response_time = max_response_time = 0
        
        throughput = total_requests / total_time if total_time > 0 else 0
        
        return {
            "total_requests": total_requests,
            "successful_requests": successful_requests,
            "allowed_requests": allowed_requests,
            "denied_requests": denied_requests,
            "failed_requests": failed_requests,
            "total_time": total_time,
            "throughput": throughput,
            "response_times": {
                "avg": avg_response_time,
                "median": median_response_time,
                "p95": p95_response_time,
                "p99": p99_response_time,
                "min": min_response_time,
                "max": max_response_time
            }
        }
    
    def print_results(self, results: Dict[str, Any]):
        """Print formatted results"""
        print("\n" + "="*60)
        print("LOAD TEST RESULTS")
        print("="*60)
        
        # Summary table
        summary_data = [
            ["Total Requests", results["total_requests"]],
            ["Successful Requests", results["successful_requests"]],
            ["Allowed Requests", results["allowed_requests"]],
            ["Denied Requests", results["denied_requests"]],
            ["Failed Requests", results["failed_requests"]],
            ["Total Time", f"{results['total_time']:.2f}s"],
            ["Throughput", f"{results['throughput']:.2f} req/s"]
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
        
        # Success rates
        success_rate = (results["successful_requests"] / results["total_requests"]) * 100
        allow_rate = (results["allowed_requests"] / results["successful_requests"]) * 100 if results["successful_requests"] > 0 else 0
        
        print(f"\nSuccess Rate: {success_rate:.2f}%")
        print(f"Allow Rate: {allow_rate:.2f}%")

@click.command()
@click.option('--url', default='http://localhost:5000', help='Base URL of the rate limiter service')
@click.option('--key', default='load-test', help='Rate limit key to test')
@click.option('--algorithm', default='token_bucket', type=click.Choice(['token_bucket', 'sliding_window', 'fixed_window']), help='Rate limiting algorithm')
@click.option('--limit', default=100, help='Rate limit (requests per window)')
@click.option('--window', default=60, help='Time window in seconds')
@click.option('--burst', default=20, help='Burst capacity (for token bucket)')
@click.option('--concurrent', default=10, help='Number of concurrent requests')
@click.option('--requests', default=1000, help='Total number of requests')
@click.option('--setup/--no-setup', default=True, help='Setup configuration before testing')
def main(url, key, algorithm, limit, window, burst, concurrent, requests, setup):
    """Load test the rate limiter service"""
    
    tester = LoadTester(url)
    
    try:
        # Health check
        response = tester.session.get(f"{url}/health")
        if response.status_code != 200:
            print(f"Service health check failed: {response.status_code}")
            return
        
        print(f"Service is healthy: {response.json()}")
        
        # Setup configuration if requested
        if setup:
            tester.setup_config(key, algorithm, limit, window, burst)
        
        # Run load test
        results = tester.run_load_test(key, concurrent, requests)
        
        # Print results
        tester.print_results(results)
        
    except KeyboardInterrupt:
        print("\nLoad test interrupted by user")
    except Exception as e:
        print(f"Load test failed: {e}")

if __name__ == "__main__":
    main()