"""
Simple In-Memory Message Queue Implementation in Python

This implementation provides a basic message queue system with support for:
- Multiple topics
- Multiple producers and consumers
- Thread-safe operations
- FIFO message ordering
- Subscription management
"""

import threading
import time
import uuid
from collections import defaultdict, deque
from typing import Dict, List, Callable, Optional, Any
from dataclasses import dataclass, field
from abc import ABC, abstractmethod


@dataclass
class Message:
    """Represents a message in the queue"""
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    topic: str = ""
    payload: str = ""
    timestamp: float = field(default_factory=time.time)
    headers: Dict[str, str] = field(default_factory=dict)


class MessageHandler(ABC):
    """Abstract base class for message handlers"""
    
    @abstractmethod
    def handle_message(self, message: Message) -> None:
        """Process a received message"""
        pass


class Consumer:
    """Message consumer that can subscribe to topics"""
    
    def __init__(self, consumer_id: str, handler: MessageHandler):
        self.id = consumer_id
        self.handler = handler
        self.subscribed_topics = set()
        self._active = True
    
    def on_message(self, message: Message) -> None:
        """Called when a message is received"""
        if self._active:
            try:
                self.handler.handle_message(message)
            except Exception as e:
                print(f"Error processing message {message.id}: {e}")
    
    def stop(self):
        """Stop the consumer"""
        self._active = False
    
    def is_active(self) -> bool:
        """Check if consumer is active"""
        return self._active


class Topic:
    """Represents a topic with its subscribers and message queue"""
    
    def __init__(self, name: str, max_size: int = 1000):
        self.name = name
        self.max_size = max_size
        self.messages = deque(maxlen=max_size)
        self.subscribers: List[Consumer] = []
        self._lock = threading.RLock()
        self._message_count = 0
    
    def add_message(self, message: Message) -> None:
        """Add a message to the topic and deliver to subscribers"""
        with self._lock:
            self.messages.append(message)
            self._message_count += 1
            self._deliver_message(message)
    
    def subscribe(self, consumer: Consumer) -> None:
        """Add a subscriber to this topic"""
        with self._lock:
            if consumer not in self.subscribers:
                self.subscribers.append(consumer)
                consumer.subscribed_topics.add(self.name)
    
    def unsubscribe(self, consumer: Consumer) -> None:
        """Remove a subscriber from this topic"""
        with self._lock:
            if consumer in self.subscribers:
                self.subscribers.remove(consumer)
                consumer.subscribed_topics.discard(self.name)
    
    def _deliver_message(self, message: Message) -> None:
        """Deliver message to all active subscribers"""
        # Create a copy of subscribers to avoid modification during iteration
        current_subscribers = self.subscribers.copy()
        
        for subscriber in current_subscribers:
            if subscriber.is_active():
                try:
                    # Deliver message in a separate thread to avoid blocking
                    threading.Thread(
                        target=subscriber.on_message,
                        args=(message,),
                        daemon=True
                    ).start()
                except Exception as e:
                    print(f"Error delivering message to {subscriber.id}: {e}")
            else:
                # Remove inactive subscribers
                self.unsubscribe(subscriber)
    
    def get_stats(self) -> Dict[str, Any]:
        """Get topic statistics"""
        with self._lock:
            return {
                'name': self.name,
                'message_count': self._message_count,
                'queue_size': len(self.messages),
                'subscriber_count': len(self.subscribers),
                'max_size': self.max_size
            }


class MessageQueue:
    """Main message queue broker"""
    
    def __init__(self):
        self.topics: Dict[str, Topic] = {}
        self.producers: List['Producer'] = []
        self.consumers: List[Consumer] = []
        self._lock = threading.RLock()
    
    def create_topic(self, name: str, max_size: int = 1000) -> Topic:
        """Create a new topic"""
        with self._lock:
            if name not in self.topics:
                self.topics[name] = Topic(name, max_size)
            return self.topics[name]
    
    def delete_topic(self, name: str) -> bool:
        """Delete a topic"""
        with self._lock:
            if name in self.topics:
                topic = self.topics[name]
                # Unsubscribe all consumers
                for consumer in topic.subscribers.copy():
                    topic.unsubscribe(consumer)
                del self.topics[name]
                return True
            return False
    
    def publish(self, topic_name: str, payload: str, headers: Optional[Dict[str, str]] = None) -> str:
        """Publish a message to a topic"""
        with self._lock:
            # Create topic if it doesn't exist
            if topic_name not in self.topics:
                self.create_topic(topic_name)
            
            message = Message(
                topic=topic_name,
                payload=payload,
                headers=headers or {}
            )
            
            self.topics[topic_name].add_message(message)
            return message.id
    
    def subscribe(self, consumer: Consumer, topic_name: str) -> None:
        """Subscribe a consumer to a topic"""
        with self._lock:
            # Create topic if it doesn't exist
            if topic_name not in self.topics:
                self.create_topic(topic_name)
            
            self.topics[topic_name].subscribe(consumer)
            
            # Add consumer to our list if not already present
            if consumer not in self.consumers:
                self.consumers.append(consumer)
    
    def unsubscribe(self, consumer: Consumer, topic_name: str) -> None:
        """Unsubscribe a consumer from a topic"""
        with self._lock:
            if topic_name in self.topics:
                self.topics[topic_name].unsubscribe(consumer)
    
    def get_topic_stats(self, topic_name: str) -> Optional[Dict[str, Any]]:
        """Get statistics for a specific topic"""
        with self._lock:
            if topic_name in self.topics:
                return self.topics[topic_name].get_stats()
            return None
    
    def get_all_stats(self) -> Dict[str, Any]:
        """Get statistics for all topics"""
        with self._lock:
            return {
                'topics': {name: topic.get_stats() for name, topic in self.topics.items()},
                'total_topics': len(self.topics),
                'total_consumers': len(self.consumers),
                'total_producers': len(self.producers)
            }


class Producer:
    """Message producer"""
    
    def __init__(self, producer_id: str, message_queue: MessageQueue):
        self.id = producer_id
        self.message_queue = message_queue
        # Register with message queue
        message_queue.producers.append(self)
    
    def publish(self, topic: str, payload: str, headers: Optional[Dict[str, str]] = None) -> str:
        """Publish a message to a topic"""
        return self.message_queue.publish(topic, payload, headers)


# Example usage and demonstration
class PrintMessageHandler(MessageHandler):
    """Simple message handler that prints messages"""
    
    def __init__(self, consumer_id: str):
        self.consumer_id = consumer_id
    
    def handle_message(self, message: Message) -> None:
        print(f"[{self.consumer_id}] Received message {message.id[:8]} on topic '{message.topic}': {message.payload}")


def demo():
    """Demonstrate the message queue system"""
    print("=== Message Queue Demo ===\n")
    
    # Create message queue
    mq = MessageQueue()
    
    # Create consumers
    consumer1 = Consumer("consumer-1", PrintMessageHandler("consumer-1"))
    consumer2 = Consumer("consumer-2", PrintMessageHandler("consumer-2"))
    consumer3 = Consumer("consumer-3", PrintMessageHandler("consumer-3"))
    
    # Create producer
    producer = Producer("producer-1", mq)
    
    # Subscribe consumers to topics
    print("Setting up subscriptions...")
    mq.subscribe(consumer1, "orders")
    mq.subscribe(consumer2, "orders")
    mq.subscribe(consumer3, "notifications")
    
    # Publish some messages
    print("\nPublishing messages...")
    producer.publish("orders", "Order #1001 created")
    producer.publish("orders", "Order #1002 created")
    producer.publish("notifications", "System maintenance scheduled")
    producer.publish("orders", "Order #1003 created")
    
    # Wait for message processing
    time.sleep(1)
    
    # Show statistics
    print("\n=== Statistics ===")
    stats = mq.get_all_stats()
    for topic_name, topic_stats in stats['topics'].items():
        print(f"Topic '{topic_name}': {topic_stats['message_count']} messages, {topic_stats['subscriber_count']} subscribers")
    
    # Unsubscribe a consumer
    print("\nUnsubscribing consumer-1 from orders...")
    mq.unsubscribe(consumer1, "orders")
    
    # Publish more messages
    print("Publishing more messages...")
    producer.publish("orders", "Order #1004 created")
    
    time.sleep(1)
    print("\nDemo completed!")


if __name__ == "__main__":
    demo()