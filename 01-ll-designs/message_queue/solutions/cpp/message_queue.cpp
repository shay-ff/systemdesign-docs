/**
 * Simple In-Memory Message Queue Implementation in C++
 * 
 * This implementation provides a thread-safe message queue system with support for:
 * - Multiple topics with concurrent access
 * - Producer-consumer patterns with multiple subscribers
 * - FIFO message ordering within topics
 * - Subscription management with dynamic subscribe/unsubscribe
 * - Statistics and monitoring capabilities
 */

#include <iostream>
#include <string>
#include <vector>
#include <queue>
#include <unordered_map>
#include <unordered_set>
#include <memory>
#include <mutex>
#include <thread>
#include <chrono>
#include <atomic>
#include <functional>
#include <algorithm>
#include <future>
#include <random>
#include <sstream>

/**
 * Generates a simple UUID-like string
 */
std::string generateId() {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<> dis(0, 15);
    
    std::stringstream ss;
    for (int i = 0; i < 8; ++i) {
        ss << std::hex << dis(gen);
    }
    return ss.str();
}

/**
 * Represents a message in the queue
 */
struct Message {
    std::string id;
    std::string topic;
    std::string payload;
    std::chrono::system_clock::time_point timestamp;
    std::unordered_map<std::string, std::string> headers;
    
    Message(const std::string& topic, const std::string& payload, 
            const std::unordered_map<std::string, std::string>& headers = {})
        : id(generateId()), topic(topic), payload(payload), 
          timestamp(std::chrono::system_clock::now()), headers(headers) {}
    
    std::string toString() const {
        return "Message{id='" + id.substr(0, 8) + "', topic='" + topic + 
               "', payload='" + payload + "'}";
    }
};

/**
 * Abstract interface for handling received messages
 */
class MessageHandler {
public:
    virtual ~MessageHandler() = default;
    virtual void handleMessage(const Message& message) = 0;
};

// Forward declarations
class MessageQueue;
class Topic;

/**
 * Message consumer that can subscribe to topics
 */
class Consumer {
private:
    std::string id_;
    std::unique_ptr<MessageHandler> handler_;
    std::unordered_set<std::string> subscribedTopics_;
    std::atomic<bool> active_;
    mutable std::mutex topicsMutex_;

public:
    Consumer(const std::string& id, std::unique_ptr<MessageHandler> handler)
        : id_(id), handler_(std::move(handler)), active_(true) {}
    
    ~Consumer() {
        stop();
    }
    
    void onMessage(const Message& message) {
        if (active_.load()) {
            try {
                handler_->handleMessage(message);
            } catch (const std::exception& e) {
                std::cerr << "Error processing message " << message.id << ": " << e.what() << std::endl;
            }
        }
    }
    
    void stop() {
        active_.store(false);
    }
    
    bool isActive() const {
        return active_.load();
    }
    
    const std::string& getId() const { return id_; }
    
    std::unordered_set<std::string> getSubscribedTopics() const {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        return subscribedTopics_;
    }
    
    void addSubscription(const std::string& topic) {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        subscribedTopics_.insert(topic);
    }
    
    void removeSubscription(const std::string& topic) {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        subscribedTopics_.erase(topic);
    }
};

/**
 * Statistics structure for topics
 */
struct TopicStats {
    std::string name;
    size_t messageCount;
    size_t queueSize;
    size_t subscriberCount;
    size_t maxSize;
};

/**
 * Represents a topic with its subscribers and message queue
 */
class Topic {
private:
    std::string name_;
    size_t maxSize_;
    std::queue<Message> messages_;
    std::vector<std::shared_ptr<Consumer>> subscribers_;
    std::atomic<size_t> messageCount_;
    mutable std::mutex messagesMutex_;
    mutable std::mutex subscribersMutex_;

public:
    Topic(const std::string& name, size_t maxSize = 1000)
        : name_(name), maxSize_(maxSize), messageCount_(0) {}
    
    void addMessage(const Message& message) {
        {
            std::lock_guard<std::mutex> lock(messagesMutex_);
            if (messages_.size() >= maxSize_) {
                std::cerr << "Topic " << name_ << " is full, dropping message: " << message.id << std::endl;
                return;
            }
            messages_.push(message);
            messageCount_.fetch_add(1);
        }
        deliverMessage(message);
    }
    
    void subscribe(std::shared_ptr<Consumer> consumer) {
        std::lock_guard<std::mutex> lock(subscribersMutex_);
        auto it = std::find(subscribers_.begin(), subscribers_.end(), consumer);
        if (it == subscribers_.end()) {
            subscribers_.push_back(consumer);
            consumer->addSubscription(name_);
        }
    }
    
    void unsubscribe(std::shared_ptr<Consumer> consumer) {
        std::lock_guard<std::mutex> lock(subscribersMutex_);
        auto it = std::find(subscribers_.begin(), subscribers_.end(), consumer);
        if (it != subscribers_.end()) {
            subscribers_.erase(it);
            consumer->removeSubscription(name_);
        }
    }
    
    TopicStats getStats() const {
        std::lock_guard<std::mutex> msgLock(messagesMutex_);
        std::lock_guard<std::mutex> subLock(subscribersMutex_);
        
        return TopicStats{
            name_,
            messageCount_.load(),
            messages_.size(),
            subscribers_.size(),
            maxSize_
        };
    }
    
    const std::string& getName() const { return name_; }

private:
    void deliverMessage(const Message& message) {
        std::vector<std::shared_ptr<Consumer>> currentSubscribers;
        {
            std::lock_guard<std::mutex> lock(subscribersMutex_);
            currentSubscribers = subscribers_;
        }
        
        // Deliver messages asynchronously to avoid blocking
        for (auto& subscriber : currentSubscribers) {
            if (subscriber && subscriber->isActive()) {
                // Use async to deliver message without blocking
                std::async(std::launch::async, [subscriber, message]() {
                    try {
                        subscriber->onMessage(message);
                    } catch (const std::exception& e) {
                        std::cerr << "Error delivering message to " << subscriber->getId() 
                                  << ": " << e.what() << std::endl;
                    }
                });
            } else if (subscriber) {
                // Remove inactive subscribers
                unsubscribe(subscriber);
            }
        }
    }
};

/**
 * Main message queue broker
 */
class MessageQueue {
private:
    std::unordered_map<std::string, std::shared_ptr<Topic>> topics_;
    std::vector<std::shared_ptr<Consumer>> consumers_;
    mutable std::mutex topicsMutex_;
    mutable std::mutex consumersMutex_;

public:
    std::shared_ptr<Topic> createTopic(const std::string& name, size_t maxSize = 1000) {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        auto it = topics_.find(name);
        if (it == topics_.end()) {
            auto topic = std::make_shared<Topic>(name, maxSize);
            topics_[name] = topic;
            return topic;
        }
        return it->second;
    }
    
    bool deleteTopic(const std::string& name) {
        std::lock_guard<std::mutex> topicsLock(topicsMutex_);
        auto it = topics_.find(name);
        if (it != topics_.end()) {
            auto topic = it->second;
            topics_.erase(it);
            
            // Unsubscribe all consumers
            std::lock_guard<std::mutex> consumersLock(consumersMutex_);
            for (auto& consumer : consumers_) {
                if (consumer) {
                    topic->unsubscribe(consumer);
                }
            }
            return true;
        }
        return false;
    }
    
    std::string publish(const std::string& topicName, const std::string& payload,
                       const std::unordered_map<std::string, std::string>& headers = {}) {
        auto topic = createTopic(topicName);
        Message message(topicName, payload, headers);
        std::string messageId = message.id;
        topic->addMessage(message);
        return messageId;
    }
    
    void subscribe(std::shared_ptr<Consumer> consumer, const std::string& topicName) {
        auto topic = createTopic(topicName);
        topic->subscribe(consumer);
        
        std::lock_guard<std::mutex> lock(consumersMutex_);
        auto it = std::find(consumers_.begin(), consumers_.end(), consumer);
        if (it == consumers_.end()) {
            consumers_.push_back(consumer);
        }
    }
    
    void unsubscribe(std::shared_ptr<Consumer> consumer, const std::string& topicName) {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        auto it = topics_.find(topicName);
        if (it != topics_.end()) {
            it->second->unsubscribe(consumer);
        }
    }
    
    std::optional<TopicStats> getTopicStats(const std::string& topicName) const {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        auto it = topics_.find(topicName);
        if (it != topics_.end()) {
            return it->second->getStats();
        }
        return std::nullopt;
    }
    
    std::unordered_map<std::string, TopicStats> getAllTopicStats() const {
        std::lock_guard<std::mutex> topicsLock(topicsMutex_);
        std::lock_guard<std::mutex> consumersLock(consumersMutex_);
        
        std::unordered_map<std::string, TopicStats> stats;
        for (const auto& pair : topics_) {
            stats[pair.first] = pair.second->getStats();
        }
        return stats;
    }
    
    size_t getTopicCount() const {
        std::lock_guard<std::mutex> lock(topicsMutex_);
        return topics_.size();
    }
    
    size_t getConsumerCount() const {
        std::lock_guard<std::mutex> lock(consumersMutex_);
        return consumers_.size();
    }
};

/**
 * Message producer
 */
class Producer {
private:
    std::string id_;
    std::shared_ptr<MessageQueue> messageQueue_;

public:
    Producer(const std::string& id, std::shared_ptr<MessageQueue> messageQueue)
        : id_(id), messageQueue_(messageQueue) {}
    
    std::string publish(const std::string& topic, const std::string& payload,
                       const std::unordered_map<std::string, std::string>& headers = {}) {
        return messageQueue_->publish(topic, payload, headers);
    }
    
    const std::string& getId() const { return id_; }
};

/**
 * Simple message handler implementation for demonstration
 */
class PrintMessageHandler : public MessageHandler {
private:
    std::string consumerId_;

public:
    PrintMessageHandler(const std::string& consumerId) : consumerId_(consumerId) {}
    
    void handleMessage(const Message& message) override {
        std::cout << "[" << consumerId_ << "] Received message " 
                  << message.id.substr(0, 8) << " on topic '" << message.topic 
                  << "': " << message.payload << std::endl;
    }
};

/**
 * Demonstration function
 */
void demo() {
    std::cout << "=== Message Queue Demo ===" << std::endl << std::endl;
    
    // Create message queue
    auto mq = std::make_shared<MessageQueue>();
    
    // Create consumers
    auto consumer1 = std::make_shared<Consumer>("consumer-1", 
        std::make_unique<PrintMessageHandler>("consumer-1"));
    auto consumer2 = std::make_shared<Consumer>("consumer-2", 
        std::make_unique<PrintMessageHandler>("consumer-2"));
    auto consumer3 = std::make_shared<Consumer>("consumer-3", 
        std::make_unique<PrintMessageHandler>("consumer-3"));
    
    // Create producer
    Producer producer("producer-1", mq);
    
    // Subscribe consumers to topics
    std::cout << "Setting up subscriptions..." << std::endl;
    mq->subscribe(consumer1, "orders");
    mq->subscribe(consumer2, "orders");
    mq->subscribe(consumer3, "notifications");
    
    // Publish some messages
    std::cout << std::endl << "Publishing messages..." << std::endl;
    producer.publish("orders", "Order #1001 created");
    producer.publish("orders", "Order #1002 created");
    producer.publish("notifications", "System maintenance scheduled");
    producer.publish("orders", "Order #1003 created");
    
    // Wait for message processing
    std::this_thread::sleep_for(std::chrono::milliseconds(1000));
    
    // Show statistics
    std::cout << std::endl << "=== Statistics ===" << std::endl;
    auto stats = mq->getAllTopicStats();
    for (const auto& pair : stats) {
        const auto& topicStats = pair.second;
        std::cout << "Topic '" << topicStats.name << "': " 
                  << topicStats.messageCount << " messages, " 
                  << topicStats.subscriberCount << " subscribers" << std::endl;
    }
    
    // Unsubscribe a consumer
    std::cout << std::endl << "Unsubscribing consumer-1 from orders..." << std::endl;
    mq->unsubscribe(consumer1, "orders");
    
    // Publish more messages
    std::cout << "Publishing more messages..." << std::endl;
    producer.publish("orders", "Order #1004 created");
    
    std::this_thread::sleep_for(std::chrono::milliseconds(1000));
    std::cout << std::endl << "Demo completed!" << std::endl;
}

int main() {
    demo();
    return 0;
}