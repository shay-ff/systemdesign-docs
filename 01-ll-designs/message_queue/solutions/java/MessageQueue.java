/**
 * Simple In-Memory Message Queue Implementation in Java
 * 
 * This implementation provides a thread-safe message queue system with support for:
 * - Multiple topics with concurrent access
 * - Producer-consumer patterns with multiple subscribers
 * - FIFO message ordering within topics
 * - Subscription management with dynamic subscribe/unsubscribe
 * - Statistics and monitoring capabilities
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.time.Instant;

/**
 * Represents a message in the queue
 */
class Message {
    private final String id;
    private final String topic;
    private final String payload;
    private final long timestamp;
    private final Map<String, String> headers;
    
    public Message(String topic, String payload, Map<String, String> headers) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.payload = payload;
        this.timestamp = Instant.now().toEpochMilli();
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
    }
    
    // Getters
    public String getId() { return id; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public long getTimestamp() { return timestamp; }
    public Map<String, String> getHeaders() { return new HashMap<>(headers); }
    
    @Override
    public String toString() {
        return String.format("Message{id='%s', topic='%s', payload='%s'}", 
                           id.substring(0, 8), topic, payload);
    }
}

/**
 * Interface for handling received messages
 */
interface MessageHandler {
    void handleMessage(Message message);
}

/**
 * Message consumer that can subscribe to topics
 */
class Consumer {
    private final String id;
    private final MessageHandler handler;
    private final Set<String> subscribedTopics;
    private volatile boolean active;
    
    public Consumer(String id, MessageHandler handler) {
        this.id = id;
        this.handler = handler;
        this.subscribedTopics = ConcurrentHashMap.newKeySet();
        this.active = true;
    }
    
    public void onMessage(Message message) {
        if (active) {
            try {
                handler.handleMessage(message);
            } catch (Exception e) {
                System.err.println("Error processing message " + message.getId() + ": " + e.getMessage());
            }
        }
    }
    
    public void stop() {
        this.active = false;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public String getId() { return id; }
    public Set<String> getSubscribedTopics() { return new HashSet<>(subscribedTopics); }
    
    void addSubscription(String topic) {
        subscribedTopics.add(topic);
    }
    
    void removeSubscription(String topic) {
        subscribedTopics.remove(topic);
    }
}

/**
 * Represents a topic with its subscribers and message queue
 */
class Topic {
    private final String name;
    private final int maxSize;
    private final BlockingQueue<Message> messages;
    private final List<Consumer> subscribers;
    private final AtomicLong messageCount;
    private final Object subscriberLock = new Object();
    
    public Topic(String name, int maxSize) {
        this.name = name;
        this.maxSize = maxSize;
        this.messages = new ArrayBlockingQueue<>(maxSize);
        this.subscribers = new ArrayList<>();
        this.messageCount = new AtomicLong(0);
    }
    
    public void addMessage(Message message) {
        try {
            if (messages.offer(message)) {
                messageCount.incrementAndGet();
                deliverMessage(message);
            } else {
                System.err.println("Topic " + name + " is full, dropping message: " + message.getId());
            }
        } catch (Exception e) {
            System.err.println("Error adding message to topic " + name + ": " + e.getMessage());
        }
    }
    
    public void subscribe(Consumer consumer) {
        synchronized (subscriberLock) {
            if (!subscribers.contains(consumer)) {
                subscribers.add(consumer);
                consumer.addSubscription(name);
            }
        }
    }
    
    public void unsubscribe(Consumer consumer) {
        synchronized (subscriberLock) {
            if (subscribers.remove(consumer)) {
                consumer.removeSubscription(name);
            }
        }
    }
    
    private void deliverMessage(Message message) {
        List<Consumer> currentSubscribers;
        synchronized (subscriberLock) {
            currentSubscribers = new ArrayList<>(subscribers);
        }
        
        // Deliver messages asynchronously to avoid blocking
        for (Consumer subscriber : currentSubscribers) {
            if (subscriber.isActive()) {
                CompletableFuture.runAsync(() -> subscriber.onMessage(message))
                    .exceptionally(throwable -> {
                        System.err.println("Error delivering message to " + subscriber.getId() + ": " + throwable.getMessage());
                        return null;
                    });
            } else {
                // Remove inactive subscribers
                unsubscribe(subscriber);
            }
        }
    }
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        synchronized (subscriberLock) {
            stats.put("name", name);
            stats.put("messageCount", messageCount.get());
            stats.put("queueSize", messages.size());
            stats.put("subscriberCount", subscribers.size());
            stats.put("maxSize", maxSize);
        }
        return stats;
    }
    
    public String getName() { return name; }
}

/**
 * Main message queue broker
 */
public class MessageQueue {
    private final ConcurrentHashMap<String, Topic> topics;
    private final List<Producer> producers;
    private final List<Consumer> consumers;
    private final Object listsLock = new Object();
    
    public MessageQueue() {
        this.topics = new ConcurrentHashMap<>();
        this.producers = new ArrayList<>();
        this.consumers = new ArrayList<>();
    }
    
    public Topic createTopic(String name, int maxSize) {
        return topics.computeIfAbsent(name, k -> new Topic(name, maxSize));
    }
    
    public Topic createTopic(String name) {
        return createTopic(name, 1000);
    }
    
    public boolean deleteTopic(String name) {
        Topic topic = topics.remove(name);
        if (topic != null) {
            // Unsubscribe all consumers
            synchronized (listsLock) {
                for (Consumer consumer : consumers) {
                    topic.unsubscribe(consumer);
                }
            }
            return true;
        }
        return false;
    }
    
    public String publish(String topicName, String payload, Map<String, String> headers) {
        Topic topic = topics.computeIfAbsent(topicName, k -> new Topic(topicName, 1000));
        Message message = new Message(topicName, payload, headers);
        topic.addMessage(message);
        return message.getId();
    }
    
    public String publish(String topicName, String payload) {
        return publish(topicName, payload, null);
    }
    
    public void subscribe(Consumer consumer, String topicName) {
        Topic topic = topics.computeIfAbsent(topicName, k -> new Topic(topicName, 1000));
        topic.subscribe(consumer);
        
        synchronized (listsLock) {
            if (!consumers.contains(consumer)) {
                consumers.add(consumer);
            }
        }
    }
    
    public void unsubscribe(Consumer consumer, String topicName) {
        Topic topic = topics.get(topicName);
        if (topic != null) {
            topic.unsubscribe(consumer);
        }
    }
    
    public Map<String, Object> getTopicStats(String topicName) {
        Topic topic = topics.get(topicName);
        return topic != null ? topic.getStats() : null;
    }
    
    public Map<String, Object> getAllStats() {
        Map<String, Object> stats = new HashMap<>();
        Map<String, Object> topicStats = new HashMap<>();
        
        for (Topic topic : topics.values()) {
            topicStats.put(topic.getName(), topic.getStats());
        }
        
        synchronized (listsLock) {
            stats.put("topics", topicStats);
            stats.put("totalTopics", topics.size());
            stats.put("totalConsumers", consumers.size());
            stats.put("totalProducers", producers.size());
        }
        
        return stats;
    }
    
    void addProducer(Producer producer) {
        synchronized (listsLock) {
            producers.add(producer);
        }
    }
}

/**
 * Message producer
 */
class Producer {
    private final String id;
    private final MessageQueue messageQueue;
    
    public Producer(String id, MessageQueue messageQueue) {
        this.id = id;
        this.messageQueue = messageQueue;
        messageQueue.addProducer(this);
    }
    
    public String publish(String topic, String payload, Map<String, String> headers) {
        return messageQueue.publish(topic, payload, headers);
    }
    
    public String publish(String topic, String payload) {
        return messageQueue.publish(topic, payload);
    }
    
    public String getId() { return id; }
}

/**
 * Simple message handler implementation for demonstration
 */
class PrintMessageHandler implements MessageHandler {
    private final String consumerId;
    
    public PrintMessageHandler(String consumerId) {
        this.consumerId = consumerId;
    }
    
    @Override
    public void handleMessage(Message message) {
        System.out.printf("[%s] Received message %s on topic '%s': %s%n",
                         consumerId, message.getId().substring(0, 8), 
                         message.getTopic(), message.getPayload());
    }
}

/**
 * Demonstration class
 */
class MessageQueueDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Message Queue Demo ===\n");
        
        // Create message queue
        MessageQueue mq = new MessageQueue();
        
        // Create consumers
        Consumer consumer1 = new Consumer("consumer-1", new PrintMessageHandler("consumer-1"));
        Consumer consumer2 = new Consumer("consumer-2", new PrintMessageHandler("consumer-2"));
        Consumer consumer3 = new Consumer("consumer-3", new PrintMessageHandler("consumer-3"));
        
        // Create producer
        Producer producer = new Producer("producer-1", mq);
        
        // Subscribe consumers to topics
        System.out.println("Setting up subscriptions...");
        mq.subscribe(consumer1, "orders");
        mq.subscribe(consumer2, "orders");
        mq.subscribe(consumer3, "notifications");
        
        // Publish some messages
        System.out.println("\nPublishing messages...");
        producer.publish("orders", "Order #1001 created");
        producer.publish("orders", "Order #1002 created");
        producer.publish("notifications", "System maintenance scheduled");
        producer.publish("orders", "Order #1003 created");
        
        // Wait for message processing
        Thread.sleep(1000);
        
        // Show statistics
        System.out.println("\n=== Statistics ===");
        Map<String, Object> stats = mq.getAllStats();
        @SuppressWarnings("unchecked")
        Map<String, Object> topics = (Map<String, Object>) stats.get("topics");
        
        for (Map.Entry<String, Object> entry : topics.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> topicStats = (Map<String, Object>) entry.getValue();
            System.out.printf("Topic '%s': %d messages, %d subscribers%n",
                            entry.getKey(), 
                            topicStats.get("messageCount"),
                            topicStats.get("subscriberCount"));
        }
        
        // Unsubscribe a consumer
        System.out.println("\nUnsubscribing consumer-1 from orders...");
        mq.unsubscribe(consumer1, "orders");
        
        // Publish more messages
        System.out.println("Publishing more messages...");
        producer.publish("orders", "Order #1004 created");
        
        Thread.sleep(1000);
        System.out.println("\nDemo completed!");
    }
}