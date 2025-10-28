/*
Simple In-Memory Message Queue Implementation in Go

This implementation provides a thread-safe message queue system with support for:
- Multiple topics with concurrent access using channels and goroutines
- Producer-consumer patterns with multiple subscribers
- FIFO message ordering within topics
- Subscription management with dynamic subscribe/unsubscribe
- Statistics and monitoring capabilities
- Graceful shutdown and cleanup
*/

package main

import (
	"context"
	"fmt"
	"log"
	"math/rand"
	"sync"
	"sync/atomic"
	"time"
)

// generateID creates a simple random ID
func generateID() string {
	const charset = "abcdefghijklmnopqrstuvwxyz0123456789"
	b := make([]byte, 8)
	for i := range b {
		b[i] = charset[rand.Intn(len(charset))]
	}
	return string(b)
}

// Message represents a message in the queue
type Message struct {
	ID        string            `json:"id"`
	Topic     string            `json:"topic"`
	Payload   string            `json:"payload"`
	Timestamp time.Time         `json:"timestamp"`
	Headers   map[string]string `json:"headers"`
}

// NewMessage creates a new message
func NewMessage(topic, payload string, headers map[string]string) *Message {
	if headers == nil {
		headers = make(map[string]string)
	}
	return &Message{
		ID:        generateID(),
		Topic:     topic,
		Payload:   payload,
		Timestamp: time.Now(),
		Headers:   headers,
	}
}

// String returns a string representation of the message
func (m *Message) String() string {
	return fmt.Sprintf("Message{id='%s', topic='%s', payload='%s'}", 
		m.ID[:8], m.Topic, m.Payload)
}

// MessageHandler defines the interface for handling messages
type MessageHandler interface {
	HandleMessage(message *Message) error
}

// MessageHandlerFunc is a function adapter for MessageHandler
type MessageHandlerFunc func(message *Message) error

// HandleMessage implements MessageHandler
func (f MessageHandlerFunc) HandleMessage(message *Message) error {
	return f(message)
}

// Consumer represents a message consumer
type Consumer struct {
	id               string
	handler          MessageHandler
	subscribedTopics map[string]bool
	active           int32 // atomic boolean
	mu               sync.RWMutex
}

// NewConsumer creates a new consumer
func NewConsumer(id string, handler MessageHandler) *Consumer {
	return &Consumer{
		id:               id,
		handler:          handler,
		subscribedTopics: make(map[string]bool),
		active:           1,
	}
}

// OnMessage processes a received message
func (c *Consumer) OnMessage(message *Message) {
	if !c.IsActive() {
		return
	}
	
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("Panic in consumer %s processing message %s: %v", 
					c.id, message.ID, r)
			}
		}()
		
		if err := c.handler.HandleMessage(message); err != nil {
			log.Printf("Error in consumer %s processing message %s: %v", 
				c.id, message.ID, err)
		}
	}()
}

// Stop stops the consumer
func (c *Consumer) Stop() {
	atomic.StoreInt32(&c.active, 0)
}

// IsActive returns whether the consumer is active
func (c *Consumer) IsActive() bool {
	return atomic.LoadInt32(&c.active) == 1
}

// ID returns the consumer ID
func (c *Consumer) ID() string {
	return c.id
}

// GetSubscribedTopics returns a copy of subscribed topics
func (c *Consumer) GetSubscribedTopics() []string {
	c.mu.RLock()
	defer c.mu.RUnlock()
	
	topics := make([]string, 0, len(c.subscribedTopics))
	for topic := range c.subscribedTopics {
		topics = append(topics, topic)
	}
	return topics
}

// addSubscription adds a topic subscription (internal use)
func (c *Consumer) addSubscription(topic string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.subscribedTopics[topic] = true
}

// removeSubscription removes a topic subscription (internal use)
func (c *Consumer) removeSubscription(topic string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	delete(c.subscribedTopics, topic)
}

// TopicStats represents statistics for a topic
type TopicStats struct {
	Name            string `json:"name"`
	MessageCount    int64  `json:"messageCount"`
	QueueSize       int    `json:"queueSize"`
	SubscriberCount int    `json:"subscriberCount"`
	MaxSize         int    `json:"maxSize"`
}

// Topic represents a message topic
type Topic struct {
	name         string
	maxSize      int
	messages     chan *Message
	subscribers  []*Consumer
	messageCount int64
	mu           sync.RWMutex
	ctx          context.Context
	cancel       context.CancelFunc
}

// NewTopic creates a new topic
func NewTopic(name string, maxSize int) *Topic {
	ctx, cancel := context.WithCancel(context.Background())
	return &Topic{
		name:        name,
		maxSize:     maxSize,
		messages:    make(chan *Message, maxSize),
		subscribers: make([]*Consumer, 0),
		ctx:         ctx,
		cancel:      cancel,
	}
}

// AddMessage adds a message to the topic
func (t *Topic) AddMessage(message *Message) bool {
	select {
	case t.messages <- message:
		atomic.AddInt64(&t.messageCount, 1)
		t.deliverMessage(message)
		return true
	default:
		log.Printf("Topic %s is full, dropping message: %s", t.name, message.ID)
		return false
	}
}

// Subscribe adds a consumer to the topic
func (t *Topic) Subscribe(consumer *Consumer) {
	t.mu.Lock()
	defer t.mu.Unlock()
	
	// Check if already subscribed
	for _, sub := range t.subscribers {
		if sub.ID() == consumer.ID() {
			return
		}
	}
	
	t.subscribers = append(t.subscribers, consumer)
	consumer.addSubscription(t.name)
}

// Unsubscribe removes a consumer from the topic
func (t *Topic) Unsubscribe(consumer *Consumer) {
	t.mu.Lock()
	defer t.mu.Unlock()
	
	for i, sub := range t.subscribers {
		if sub.ID() == consumer.ID() {
			// Remove from slice
			t.subscribers = append(t.subscribers[:i], t.subscribers[i+1:]...)
			consumer.removeSubscription(t.name)
			break
		}
	}
}

// deliverMessage delivers a message to all active subscribers
func (t *Topic) deliverMessage(message *Message) {
	t.mu.RLock()
	currentSubscribers := make([]*Consumer, len(t.subscribers))
	copy(currentSubscribers, t.subscribers)
	t.mu.RUnlock()
	
	for _, subscriber := range currentSubscribers {
		if subscriber.IsActive() {
			subscriber.OnMessage(message)
		} else {
			// Remove inactive subscribers
			t.Unsubscribe(subscriber)
		}
	}
}

// GetStats returns topic statistics
func (t *Topic) GetStats() TopicStats {
	t.mu.RLock()
	defer t.mu.RUnlock()
	
	return TopicStats{
		Name:            t.name,
		MessageCount:    atomic.LoadInt64(&t.messageCount),
		QueueSize:       len(t.messages),
		SubscriberCount: len(t.subscribers),
		MaxSize:         t.maxSize,
	}
}

// Close closes the topic and cleans up resources
func (t *Topic) Close() {
	t.cancel()
	close(t.messages)
}

// MessageQueue represents the main message queue broker
type MessageQueue struct {
	topics    map[string]*Topic
	consumers []*Consumer
	mu        sync.RWMutex
}

// NewMessageQueue creates a new message queue
func NewMessageQueue() *MessageQueue {
	return &MessageQueue{
		topics:    make(map[string]*Topic),
		consumers: make([]*Consumer, 0),
	}
}

// CreateTopic creates a new topic
func (mq *MessageQueue) CreateTopic(name string, maxSize int) *Topic {
	mq.mu.Lock()
	defer mq.mu.Unlock()
	
	if topic, exists := mq.topics[name]; exists {
		return topic
	}
	
	topic := NewTopic(name, maxSize)
	mq.topics[name] = topic
	return topic
}

// DeleteTopic deletes a topic
func (mq *MessageQueue) DeleteTopic(name string) bool {
	mq.mu.Lock()
	defer mq.mu.Unlock()
	
	topic, exists := mq.topics[name]
	if !exists {
		return false
	}
	
	// Unsubscribe all consumers
	for _, consumer := range mq.consumers {
		topic.Unsubscribe(consumer)
	}
	
	topic.Close()
	delete(mq.topics, name)
	return true
}

// Publish publishes a message to a topic
func (mq *MessageQueue) Publish(topicName, payload string, headers map[string]string) string {
	// Create topic if it doesn't exist
	topic := mq.CreateTopic(topicName, 1000)
	
	message := NewMessage(topicName, payload, headers)
	topic.AddMessage(message)
	return message.ID
}

// Subscribe subscribes a consumer to a topic
func (mq *MessageQueue) Subscribe(consumer *Consumer, topicName string) {
	// Create topic if it doesn't exist
	topic := mq.CreateTopic(topicName, 1000)
	topic.Subscribe(consumer)
	
	mq.mu.Lock()
	defer mq.mu.Unlock()
	
	// Add consumer to our list if not already present
	for _, c := range mq.consumers {
		if c.ID() == consumer.ID() {
			return
		}
	}
	mq.consumers = append(mq.consumers, consumer)
}

// Unsubscribe unsubscribes a consumer from a topic
func (mq *MessageQueue) Unsubscribe(consumer *Consumer, topicName string) {
	mq.mu.RLock()
	topic, exists := mq.topics[topicName]
	mq.mu.RUnlock()
	
	if exists {
		topic.Unsubscribe(consumer)
	}
}

// GetTopicStats returns statistics for a specific topic
func (mq *MessageQueue) GetTopicStats(topicName string) *TopicStats {
	mq.mu.RLock()
	topic, exists := mq.topics[topicName]
	mq.mu.RUnlock()
	
	if !exists {
		return nil
	}
	
	stats := topic.GetStats()
	return &stats
}

// GetAllStats returns statistics for all topics
func (mq *MessageQueue) GetAllStats() map[string]interface{} {
	mq.mu.RLock()
	defer mq.mu.RUnlock()
	
	topicStats := make(map[string]TopicStats)
	for name, topic := range mq.topics {
		topicStats[name] = topic.GetStats()
	}
	
	return map[string]interface{}{
		"topics":         topicStats,
		"totalTopics":    len(mq.topics),
		"totalConsumers": len(mq.consumers),
	}
}

// Close closes the message queue and all topics
func (mq *MessageQueue) Close() {
	mq.mu.Lock()
	defer mq.mu.Unlock()
	
	for _, topic := range mq.topics {
		topic.Close()
	}
	
	for _, consumer := range mq.consumers {
		consumer.Stop()
	}
}

// Producer represents a message producer
type Producer struct {
	id           string
	messageQueue *MessageQueue
}

// NewProducer creates a new producer
func NewProducer(id string, messageQueue *MessageQueue) *Producer {
	return &Producer{
		id:           id,
		messageQueue: messageQueue,
	}
}

// Publish publishes a message to a topic
func (p *Producer) Publish(topic, payload string, headers map[string]string) string {
	return p.messageQueue.Publish(topic, payload, headers)
}

// ID returns the producer ID
func (p *Producer) ID() string {
	return p.id
}

// PrintMessageHandler is a simple message handler for demonstration
type PrintMessageHandler struct {
	consumerID string
}

// NewPrintMessageHandler creates a new print message handler
func NewPrintMessageHandler(consumerID string) *PrintMessageHandler {
	return &PrintMessageHandler{consumerID: consumerID}
}

// HandleMessage implements MessageHandler
func (h *PrintMessageHandler) HandleMessage(message *Message) error {
	fmt.Printf("[%s] Received message %s on topic '%s': %s\n",
		h.consumerID, message.ID[:8], message.Topic, message.Payload)
	return nil
}

// Demo demonstrates the message queue system
func demo() {
	fmt.Println("=== Message Queue Demo ===\n")
	
	// Create message queue
	mq := NewMessageQueue()
	defer mq.Close()
	
	// Create consumers
	consumer1 := NewConsumer("consumer-1", NewPrintMessageHandler("consumer-1"))
	consumer2 := NewConsumer("consumer-2", NewPrintMessageHandler("consumer-2"))
	consumer3 := NewConsumer("consumer-3", NewPrintMessageHandler("consumer-3"))
	
	// Create producer
	producer := NewProducer("producer-1", mq)
	
	// Subscribe consumers to topics
	fmt.Println("Setting up subscriptions...")
	mq.Subscribe(consumer1, "orders")
	mq.Subscribe(consumer2, "orders")
	mq.Subscribe(consumer3, "notifications")
	
	// Publish some messages
	fmt.Println("\nPublishing messages...")
	producer.Publish("orders", "Order #1001 created", nil)
	producer.Publish("orders", "Order #1002 created", nil)
	producer.Publish("notifications", "System maintenance scheduled", nil)
	producer.Publish("orders", "Order #1003 created", nil)
	
	// Wait for message processing
	time.Sleep(1 * time.Second)
	
	// Show statistics
	fmt.Println("\n=== Statistics ===")
	stats := mq.GetAllStats()
	if topicStats, ok := stats["topics"].(map[string]TopicStats); ok {
		for topicName, topicStat := range topicStats {
			fmt.Printf("Topic '%s': %d messages, %d subscribers\n",
				topicName, topicStat.MessageCount, topicStat.SubscriberCount)
		}
	}
	
	// Unsubscribe a consumer
	fmt.Println("\nUnsubscribing consumer-1 from orders...")
	mq.Unsubscribe(consumer1, "orders")
	
	// Publish more messages
	fmt.Println("Publishing more messages...")
	producer.Publish("orders", "Order #1004 created", nil)
	
	time.Sleep(1 * time.Second)
	fmt.Println("\nDemo completed!")
}

func main() {
	rand.Seed(time.Now().UnixNano())
	demo()
}