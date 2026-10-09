package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"sync"
	"time"

	"github.com/gorilla/mux"
	"github.com/gorilla/websocket"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/google/uuid"
)

// Message represents a message in the broker
type Message struct {
	ID        string                 `json:"id"`
	Topic     string                 `json:"topic"`
	Data      interface{}            `json:"data"`
	Headers   map[string]string      `json:"headers,omitempty"`
	Timestamp time.Time              `json:"timestamp"`
	RetryCount int                   `json:"retryCount"`
}

// WebSocketMessage represents a WebSocket message
type WebSocketMessage struct {
	Type      string      `json:"type"` // publish, subscribe, unsubscribe
	Topic     string      `json:"topic"`
	Data      interface{} `json:"data,omitempty"`
	MessageID string      `json:"messageId,omitempty"`
	Timestamp time.Time   `json:"timestamp"`
}

// Subscription represents a consumer subscription
type Subscription struct {
	ID       string
	Topic    string
	Channel  chan *Message
	Consumer *Consumer
}

// Consumer represents a message consumer
type Consumer struct {
	ID           string
	Subscriptions map[string]*Subscription
	WebSocket    *websocket.Conn
	mutex        sync.RWMutex
}

// Topic represents a message topic
type Topic struct {
	Name      string
	Messages  []*Message
	Consumers map[string]*Consumer
	mutex     sync.RWMutex
}

// MessageBroker is the main broker struct
type MessageBroker struct {
	topics    map[string]*Topic
	consumers map[string]*Consumer
	mutex     sync.RWMutex
	
	// Configuration
	maxMessageSize int
	maxQueueSize   int
	retentionHours int
	
	// Metrics
	messagesPublished prometheus.Counter
	messagesConsumed  prometheus.Counter
	activeConnections prometheus.Gauge
	queueSizes        *prometheus.GaugeVec
	processingTime    prometheus.Histogram
}

// WebSocket upgrader
var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all origins for demo
	},
}

// Prometheus metrics
var (
	messagesPublished = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "message_broker_messages_published_total",
		Help: "Total number of published messages",
	})
	
	messagesConsumed = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "message_broker_messages_consumed_total",
		Help: "Total number of consumed messages",
	})
	
	activeConnections = prometheus.NewGauge(prometheus.GaugeOpts{
		Name: "message_broker_active_connections",
		Help: "Number of active WebSocket connections",
	})
	
	queueSizes = prometheus.NewGaugeVec(prometheus.GaugeOpts{
		Name: "message_broker_queue_size",
		Help: "Number of messages in queue per topic",
	}, []string{"topic"})
	
	processingTime = prometheus.NewHistogram(prometheus.HistogramOpts{
		Name: "message_broker_processing_duration_seconds",
		Help: "Time spent processing messages",
	})
)

func init() {
	prometheus.MustRegister(messagesPublished)
	prometheus.MustRegister(messagesConsumed)
	prometheus.MustRegister(activeConnections)
	prometheus.MustRegister(queueSizes)
	prometheus.MustRegister(processingTime)
}

// NewMessageBroker creates a new message broker
func NewMessageBroker() *MessageBroker {
	maxMessageSize, _ := strconv.Atoi(getEnv("MAX_MESSAGE_SIZE", "1048576")) // 1MB
	maxQueueSize, _ := strconv.Atoi(getEnv("MAX_QUEUE_SIZE", "10000"))
	retentionHours, _ := strconv.Atoi(getEnv("RETENTION_HOURS", "24"))
	
	broker := &MessageBroker{
		topics:            make(map[string]*Topic),
		consumers:         make(map[string]*Consumer),
		maxMessageSize:    maxMessageSize,
		maxQueueSize:      maxQueueSize,
		retentionHours:    retentionHours,
		messagesPublished: messagesPublished,
		messagesConsumed:  messagesConsumed,
		activeConnections: activeConnections,
		queueSizes:        queueSizes,
		processingTime:    processingTime,
	}
	
	// Start cleanup routine
	go broker.cleanupRoutine()
	
	return broker
}

// getEnv gets environment variable with default value
func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

// GetOrCreateTopic gets or creates a topic
func (mb *MessageBroker) GetOrCreateTopic(name string) *Topic {
	mb.mutex.Lock()
	defer mb.mutex.Unlock()
	
	if topic, exists := mb.topics[name]; exists {
		return topic
	}
	
	topic := &Topic{
		Name:      name,
		Messages:  make([]*Message, 0),
		Consumers: make(map[string]*Consumer),
	}
	
	mb.topics[name] = topic
	return topic
}

// PublishMessage publishes a message to a topic
func (mb *MessageBroker) PublishMessage(topicName string, data interface{}, headers map[string]string) (*Message, error) {
	timer := prometheus.NewTimer(mb.processingTime)
	defer timer.ObserveDuration()
	
	topic := mb.GetOrCreateTopic(topicName)
	
	message := &Message{
		ID:        uuid.New().String(),
		Topic:     topicName,
		Data:      data,
		Headers:   headers,
		Timestamp: time.Now(),
		RetryCount: 0,
	}
	
	topic.mutex.Lock()
	
	// Check queue size limit
	if len(topic.Messages) >= mb.maxQueueSize {
		topic.mutex.Unlock()
		return nil, fmt.Errorf("topic queue is full")
	}
	
	// Add message to topic
	topic.Messages = append(topic.Messages, message)
	
	// Update metrics
	mb.messagesPublished.Inc()
	mb.queueSizes.WithLabelValues(topicName).Set(float64(len(topic.Messages)))
	
	// Notify consumers
	for _, consumer := range topic.Consumers {
		select {
		case consumer.Subscriptions[topicName].Channel <- message:
		default:
			// Consumer channel is full, skip
		}
	}
	
	topic.mutex.Unlock()
	
	log.Printf("Published message %s to topic %s", message.ID, topicName)
	return message, nil
}

// ConsumeMessage consumes a message from a topic
func (mb *MessageBroker) ConsumeMessage(topicName string) (*Message, error) {
	timer := prometheus.NewTimer(mb.processingTime)
	defer timer.ObserveDuration()
	
	topic := mb.GetOrCreateTopic(topicName)
	
	topic.mutex.Lock()
	defer topic.mutex.Unlock()
	
	if len(topic.Messages) == 0 {
		return nil, fmt.Errorf("no messages available")
	}
	
	// Get first message (FIFO)
	message := topic.Messages[0]
	topic.Messages = topic.Messages[1:]
	
	// Update metrics
	mb.messagesConsumed.Inc()
	mb.queueSizes.WithLabelValues(topicName).Set(float64(len(topic.Messages)))
	
	log.Printf("Consumed message %s from topic %s", message.ID, topicName)
	return message, nil
}

// Subscribe creates a subscription for a consumer
func (mb *MessageBroker) Subscribe(consumerID, topicName string) *Subscription {
	topic := mb.GetOrCreateTopic(topicName)
	
	mb.mutex.Lock()
	consumer, exists := mb.consumers[consumerID]
	if !exists {
		consumer = &Consumer{
			ID:            consumerID,
			Subscriptions: make(map[string]*Subscription),
		}
		mb.consumers[consumerID] = consumer
	}
	mb.mutex.Unlock()
	
	subscription := &Subscription{
		ID:       uuid.New().String(),
		Topic:    topicName,
		Channel:  make(chan *Message, 100),
		Consumer: consumer,
	}
	
	consumer.mutex.Lock()
	consumer.Subscriptions[topicName] = subscription
	consumer.mutex.Unlock()
	
	topic.mutex.Lock()
	topic.Consumers[consumerID] = consumer
	topic.mutex.Unlock()
	
	log.Printf("Consumer %s subscribed to topic %s", consumerID, topicName)
	return subscription
}

// Unsubscribe removes a subscription
func (mb *MessageBroker) Unsubscribe(consumerID, topicName string) {
	mb.mutex.RLock()
	consumer, exists := mb.consumers[consumerID]
	mb.mutex.RUnlock()
	
	if !exists {
		return
	}
	
	consumer.mutex.Lock()
	if subscription, exists := consumer.Subscriptions[topicName]; exists {
		close(subscription.Channel)
		delete(consumer.Subscriptions, topicName)
	}
	consumer.mutex.Unlock()
	
	// Remove from topic
	if topic, exists := mb.topics[topicName]; exists {
		topic.mutex.Lock()
		delete(topic.Consumers, consumerID)
		topic.mutex.Unlock()
	}
	
	log.Printf("Consumer %s unsubscribed from topic %s", consumerID, topicName)
}

// GetTopicStats returns statistics for a topic
func (mb *MessageBroker) GetTopicStats(topicName string) map[string]interface{} {
	mb.mutex.RLock()
	topic, exists := mb.topics[topicName]
	mb.mutex.RUnlock()
	
	if !exists {
		return map[string]interface{}{
			"exists": false,
		}
	}
	
	topic.mutex.RLock()
	defer topic.mutex.RUnlock()
	
	return map[string]interface{}{
		"exists":        true,
		"messageCount":  len(topic.Messages),
		"consumerCount": len(topic.Consumers),
	}
}

// cleanupRoutine periodically cleans up old messages
func (mb *MessageBroker) cleanupRoutine() {
	ticker := time.NewTicker(time.Hour)
	defer ticker.Stop()
	
	for range ticker.C {
		mb.cleanupOldMessages()
	}
}

// cleanupOldMessages removes messages older than retention period
func (mb *MessageBroker) cleanupOldMessages() {
	cutoff := time.Now().Add(-time.Duration(mb.retentionHours) * time.Hour)
	
	mb.mutex.RLock()
	topics := make([]*Topic, 0, len(mb.topics))
	for _, topic := range mb.topics {
		topics = append(topics, topic)
	}
	mb.mutex.RUnlock()
	
	for _, topic := range topics {
		topic.mutex.Lock()
		
		// Find first message to keep
		keepIndex := 0
		for i, message := range topic.Messages {
			if message.Timestamp.After(cutoff) {
				keepIndex = i
				break
			}
		}
		
		// Remove old messages
		if keepIndex > 0 {
			topic.Messages = topic.Messages[keepIndex:]
			mb.queueSizes.WithLabelValues(topic.Name).Set(float64(len(topic.Messages)))
			log.Printf("Cleaned up %d old messages from topic %s", keepIndex, topic.Name)
		}
		
		topic.mutex.Unlock()
	}
}

// HTTP Handlers

func (mb *MessageBroker) publishHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	topic := vars["topic"]
	
	var data interface{}
	if err := json.NewDecoder(r.Body).Decode(&data); err != nil {
		http.Error(w, "Invalid JSON", http.StatusBadRequest)
		return
	}
	
	headers := make(map[string]string)
	for key, values := range r.Header {
		if len(values) > 0 {
			headers[key] = values[0]
		}
	}
	
	message, err := mb.PublishMessage(topic, data, headers)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"messageId": message.ID,
		"topic":     message.Topic,
		"timestamp": message.Timestamp,
	})
}

func (mb *MessageBroker) publishBatchHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	topic := vars["topic"]
	
	var dataArray []interface{}
	if err := json.NewDecoder(r.Body).Decode(&dataArray); err != nil {
		http.Error(w, "Invalid JSON array", http.StatusBadRequest)
		return
	}
	
	headers := make(map[string]string)
	for key, values := range r.Header {
		if len(values) > 0 {
			headers[key] = values[0]
		}
	}
	
	var messages []map[string]interface{}
	for _, data := range dataArray {
		message, err := mb.PublishMessage(topic, data, headers)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		
		messages = append(messages, map[string]interface{}{
			"messageId": message.ID,
			"topic":     message.Topic,
			"timestamp": message.Timestamp,
		})
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"messages": messages,
		"count":    len(messages),
	})
}

func (mb *MessageBroker) consumeHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	topic := vars["topic"]
	
	message, err := mb.ConsumeMessage(topic)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(message)
}

func (mb *MessageBroker) consumeBatchHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	topic := vars["topic"]
	
	limitStr := r.URL.Query().Get("limit")
	limit := 10 // default
	if limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil && l > 0 {
			limit = l
		}
	}
	
	var messages []*Message
	for i := 0; i < limit; i++ {
		message, err := mb.ConsumeMessage(topic)
		if err != nil {
			break // No more messages
		}
		messages = append(messages, message)
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"messages": messages,
		"count":    len(messages),
	})
}

func (mb *MessageBroker) topicsHandler(w http.ResponseWriter, r *http.Request) {
	mb.mutex.RLock()
	defer mb.mutex.RUnlock()
	
	topics := make([]map[string]interface{}, 0, len(mb.topics))
	for name, topic := range mb.topics {
		topic.mutex.RLock()
		topics = append(topics, map[string]interface{}{
			"name":          name,
			"messageCount":  len(topic.Messages),
			"consumerCount": len(topic.Consumers),
		})
		topic.mutex.RUnlock()
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"topics": topics,
		"count":  len(topics),
	})
}

func (mb *MessageBroker) topicStatsHandler(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	topic := vars["topic"]
	
	stats := mb.GetTopicStats(topic)
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (mb *MessageBroker) healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status":    "healthy",
		"timestamp": time.Now(),
		"version":   "1.0.0",
	})
}

// WebSocket handler
func (mb *MessageBroker) websocketHandler(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket upgrade failed: %v", err)
		return
	}
	defer conn.Close()
	
	consumerID := uuid.New().String()
	mb.activeConnections.Inc()
	defer mb.activeConnections.Dec()
	
	log.Printf("WebSocket connection established: %s", consumerID)
	
	// Handle messages
	for {
		var wsMsg WebSocketMessage
		err := conn.ReadJSON(&wsMsg)
		if err != nil {
			log.Printf("WebSocket read error: %v", err)
			break
		}
		
		switch wsMsg.Type {
		case "publish":
			message, err := mb.PublishMessage(wsMsg.Topic, wsMsg.Data, nil)
			if err != nil {
				conn.WriteJSON(map[string]interface{}{
					"type":  "error",
					"error": err.Error(),
				})
			} else {
				conn.WriteJSON(map[string]interface{}{
					"type":      "published",
					"messageId": message.ID,
					"topic":     message.Topic,
				})
			}
			
		case "subscribe":
			subscription := mb.Subscribe(consumerID, wsMsg.Topic)
			
			// Start goroutine to forward messages
			go func() {
				for message := range subscription.Channel {
					err := conn.WriteJSON(map[string]interface{}{
						"type":    "message",
						"topic":   message.Topic,
						"data":    message.Data,
						"headers": message.Headers,
						"messageId": message.ID,
						"timestamp": message.Timestamp,
					})
					if err != nil {
						log.Printf("WebSocket write error: %v", err)
						return
					}
				}
			}()
			
			conn.WriteJSON(map[string]interface{}{
				"type":  "subscribed",
				"topic": wsMsg.Topic,
			})
			
		case "unsubscribe":
			mb.Unsubscribe(consumerID, wsMsg.Topic)
			conn.WriteJSON(map[string]interface{}{
				"type":  "unsubscribed",
				"topic": wsMsg.Topic,
			})
		}
	}
	
	// Cleanup subscriptions
	mb.mutex.RLock()
	if consumer, exists := mb.consumers[consumerID]; exists {
		consumer.mutex.RLock()
		for topic := range consumer.Subscriptions {
			mb.Unsubscribe(consumerID, topic)
		}
		consumer.mutex.RUnlock()
	}
	mb.mutex.RUnlock()
	
	log.Printf("WebSocket connection closed: %s", consumerID)
}

func main() {
	broker := NewMessageBroker()
	
	r := mux.NewRouter()
	
	// HTTP API routes
	r.HandleFunc("/publish/{topic}", broker.publishHandler).Methods("POST")
	r.HandleFunc("/publish/batch/{topic}", broker.publishBatchHandler).Methods("POST")
	r.HandleFunc("/consume/{topic}", broker.consumeHandler).Methods("GET")
	r.HandleFunc("/consume/{topic}/batch", broker.consumeBatchHandler).Methods("GET")
	r.HandleFunc("/topics", broker.topicsHandler).Methods("GET")
	r.HandleFunc("/topics/{topic}/stats", broker.topicStatsHandler).Methods("GET")
	r.HandleFunc("/health", broker.healthHandler).Methods("GET")
	r.Handle("/metrics", promhttp.Handler()).Methods("GET")
	
	// WebSocket route
	r.HandleFunc("/ws", broker.websocketHandler)
	
	port := getEnv("PORT", "8080")
	log.Printf("Starting message broker on port %s", port)
	log.Fatal(http.ListenAndServe(":"+port, r))
}