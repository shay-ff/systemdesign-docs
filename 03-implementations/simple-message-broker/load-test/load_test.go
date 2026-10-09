package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"
)

type LoadTestConfig struct {
	BaseURL     string
	Concurrent  int
	Messages    int
	Topic       string
	MessageSize int
}

type TestResult struct {
	TotalRequests    int
	SuccessfulReqs   int
	FailedReqs       int
	TotalTime        time.Duration
	AvgResponseTime  time.Duration
	MinResponseTime  time.Duration
	MaxResponseTime  time.Duration
	RequestsPerSec   float64
}

type RequestResult struct {
	Success      bool
	ResponseTime time.Duration
	Error        error
}

func main() {
	var (
		baseURL    = flag.String("url", "http://localhost:8080", "Base URL of message broker")
		concurrent = flag.Int("concurrent", 10, "Number of concurrent goroutines")
		messages   = flag.Int("messages", 1000, "Total number of messages to send")
		topic      = flag.String("topic", "load-test", "Topic name for testing")
		msgSize    = flag.Int("size", 100, "Message size in bytes")
	)
	flag.Parse()

	config := LoadTestConfig{
		BaseURL:     *baseURL,
		Concurrent:  *concurrent,
		Messages:    *messages,
		Topic:       *topic,
		MessageSize: *msgSize,
	}

	fmt.Printf("Starting load test with config:\n")
	fmt.Printf("  URL: %s\n", config.BaseURL)
	fmt.Printf("  Concurrent: %d\n", config.Concurrent)
	fmt.Printf("  Messages: %d\n", config.Messages)
	fmt.Printf("  Topic: %s\n", config.Topic)
	fmt.Printf("  Message Size: %d bytes\n", config.MessageSize)
	fmt.Println()

	// Health check
	if !healthCheck(config.BaseURL) {
		log.Fatal("Health check failed")
	}

	// Run publish test
	fmt.Println("Running publish test...")
	publishResult := runPublishTest(config)
	printResults("PUBLISH TEST", publishResult)

	// Wait a bit
	time.Sleep(2 * time.Second)

	// Run consume test
	fmt.Println("Running consume test...")
	consumeResult := runConsumeTest(config)
	printResults("CONSUME TEST", consumeResult)
}

func healthCheck(baseURL string) bool {
	resp, err := http.Get(baseURL + "/health")
	if err != nil {
		log.Printf("Health check failed: %v", err)
		return false
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Printf("Health check failed with status: %d", resp.StatusCode)
		return false
	}

	fmt.Println("✓ Health check passed")
	return true
}

func runPublishTest(config LoadTestConfig) TestResult {
	var wg sync.WaitGroup
	results := make(chan RequestResult, config.Messages)

	// Generate test message
	testData := generateTestMessage(config.MessageSize)

	startTime := time.Now()

	// Create worker pool
	semaphore := make(chan struct{}, config.Concurrent)

	for i := 0; i < config.Messages; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			semaphore <- struct{}{} // Acquire
			defer func() { <-semaphore }() // Release

			result := publishMessage(config.BaseURL, config.Topic, testData)
			results <- result
		}()
	}

	wg.Wait()
	close(results)

	endTime := time.Now()
	totalTime := endTime.Sub(startTime)

	return analyzeResults(results, totalTime)
}

func runConsumeTest(config LoadTestConfig) TestResult {
	var wg sync.WaitGroup
	results := make(chan RequestResult, config.Messages)

	startTime := time.Now()

	// Create worker pool
	semaphore := make(chan struct{}, config.Concurrent)

	for i := 0; i < config.Messages; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			semaphore <- struct{}{} // Acquire
			defer func() { <-semaphore }() // Release

			result := consumeMessage(config.BaseURL, config.Topic)
			results <- result
		}()
	}

	wg.Wait()
	close(results)

	endTime := time.Now()
	totalTime := endTime.Sub(startTime)

	return analyzeResults(results, totalTime)
}

func generateTestMessage(size int) map[string]interface{} {
	// Create a message with approximately the specified size
	data := make([]byte, size-50) // Account for JSON overhead
	for i := range data {
		data[i] = byte('A' + (i % 26))
	}

	return map[string]interface{}{
		"id":        fmt.Sprintf("msg-%d", time.Now().UnixNano()),
		"data":      string(data),
		"timestamp": time.Now().Format(time.RFC3339),
	}
}

func publishMessage(baseURL, topic string, data interface{}) RequestResult {
	jsonData, err := json.Marshal(data)
	if err != nil {
		return RequestResult{Success: false, Error: err}
	}

	startTime := time.Now()

	resp, err := http.Post(
		fmt.Sprintf("%s/publish/%s", baseURL, topic),
		"application/json",
		bytes.NewBuffer(jsonData),
	)

	responseTime := time.Since(startTime)

	if err != nil {
		return RequestResult{Success: false, ResponseTime: responseTime, Error: err}
	}
	defer resp.Body.Close()

	success := resp.StatusCode == http.StatusOK
	return RequestResult{Success: success, ResponseTime: responseTime}
}

func consumeMessage(baseURL, topic string) RequestResult {
	startTime := time.Now()

	resp, err := http.Get(fmt.Sprintf("%s/consume/%s", baseURL, topic))
	responseTime := time.Since(startTime)

	if err != nil {
		return RequestResult{Success: false, ResponseTime: responseTime, Error: err}
	}
	defer resp.Body.Close()

	// Accept both 200 (message found) and 404 (no message) as success
	success := resp.StatusCode == http.StatusOK || resp.StatusCode == http.StatusNotFound
	return RequestResult{Success: success, ResponseTime: responseTime}
}

func analyzeResults(results chan RequestResult, totalTime time.Duration) TestResult {
	var (
		totalRequests   int
		successfulReqs  int
		failedReqs      int
		responseTimes   []time.Duration
		totalRespTime   time.Duration
	)

	for result := range results {
		totalRequests++
		if result.Success {
			successfulReqs++
		} else {
			failedReqs++
		}
		responseTimes = append(responseTimes, result.ResponseTime)
		totalRespTime += result.ResponseTime
	}

	var avgResponseTime, minResponseTime, maxResponseTime time.Duration
	if len(responseTimes) > 0 {
		avgResponseTime = totalRespTime / time.Duration(len(responseTimes))
		minResponseTime = responseTimes[0]
		maxResponseTime = responseTimes[0]

		for _, rt := range responseTimes {
			if rt < minResponseTime {
				minResponseTime = rt
			}
			if rt > maxResponseTime {
				maxResponseTime = rt
			}
		}
	}

	requestsPerSec := float64(totalRequests) / totalTime.Seconds()

	return TestResult{
		TotalRequests:   totalRequests,
		SuccessfulReqs:  successfulReqs,
		FailedReqs:      failedReqs,
		TotalTime:       totalTime,
		AvgResponseTime: avgResponseTime,
		MinResponseTime: minResponseTime,
		MaxResponseTime: maxResponseTime,
		RequestsPerSec:  requestsPerSec,
	}
}

func printResults(testName string, result TestResult) {
	fmt.Printf("\n%s RESULTS:\n", testName)
	fmt.Printf("=====================================\n")
	fmt.Printf("Total Requests:     %d\n", result.TotalRequests)
	fmt.Printf("Successful:         %d\n", result.SuccessfulReqs)
	fmt.Printf("Failed:             %d\n", result.FailedReqs)
	fmt.Printf("Success Rate:       %.2f%%\n", float64(result.SuccessfulReqs)/float64(result.TotalRequests)*100)
	fmt.Printf("Total Time:         %v\n", result.TotalTime)
	fmt.Printf("Requests/sec:       %.2f\n", result.RequestsPerSec)
	fmt.Printf("Avg Response Time:  %v\n", result.AvgResponseTime)
	fmt.Printf("Min Response Time:  %v\n", result.MinResponseTime)
	fmt.Printf("Max Response Time:  %v\n", result.MaxResponseTime)
	fmt.Println()
}