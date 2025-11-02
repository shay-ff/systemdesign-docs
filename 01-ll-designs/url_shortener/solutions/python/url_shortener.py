#!/usr/bin/env python3
"""
URL Shortener - Low Level Design Implementation

A comprehensive URL shortening service with analytics, expiration, and custom aliases.
Demonstrates encoding algorithms, data structure usage, and system design principles.

Author: System Design Learning Guide
"""

import time
import hashlib
import re
import threading
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, field
from urllib.parse import urlparse
import uuid


@dataclass
class URLMetadata:
    """Metadata associated with a shortened URL."""
    original_url: str
    created_at: datetime
    expires_at: Optional[datetime] = None
    click_count: int = 0
    last_accessed: Optional[datetime] = None
    creator_id: Optional[str] = None
    custom_alias: bool = False


@dataclass
class ClickEvent:
    """Represents a click event for analytics."""
    timestamp: datetime
    ip_address: Optional[str] = None
    user_agent: Optional[str] = None
    referrer: Optional[str] = None


@dataclass
class ShortenRequest:
    """Request object for URL shortening."""
    url: str
    custom_alias: Optional[str] = None
    expires_in_days: Optional[int] = None
    creator_id: Optional[str] = None


class URLValidator:
    """Validates and sanitizes URLs."""
    
    URL_PATTERN = re.compile(
        r'^https?://'  # http:// or https://
        r'(?:(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\.)+[A-Z]{2,6}\.?|'  # domain...
        r'localhost|'  # localhost...
        r'\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})'  # ...or ip
        r'(?::\d+)?'  # optional port
        r'(?:/?|[/?]\S+)$', re.IGNORECASE)
    
    @classmethod
    def is_valid_url(cls, url: str) -> bool:
        """Check if URL is valid."""
        if not url or len(url) > 2048:  # URL length limit
            return False
        
        try:
            result = urlparse(url)
            return all([result.scheme, result.netloc]) and cls.URL_PATTERN.match(url)
        except Exception:
            return False
    
    @classmethod
    def sanitize_url(cls, url: str) -> str:
        """Sanitize URL by adding protocol if missing."""
        url = url.strip()
        if not url.startswith(('http://', 'https://')):
            url = 'https://' + url
        return url


class Base62Encoder:
    """Base62 encoding for generating short codes."""
    
    ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    BASE = len(ALPHABET)
    
    @classmethod
    def encode(cls, num: int) -> str:
        """Encode integer to base62 string."""
        if num == 0:
            return cls.ALPHABET[0]
        
        result = []
        while num > 0:
            result.append(cls.ALPHABET[num % cls.BASE])
            num //= cls.BASE
        
        return ''.join(reversed(result))
    
    @classmethod
    def decode(cls, encoded: str) -> int:
        """Decode base62 string to integer."""
        num = 0
        for char in encoded:
            num = num * cls.BASE + cls.ALPHABET.index(char)
        return num
    
    @classmethod
    def generate_from_string(cls, text: str, length: int = 6) -> str:
        """Generate base62 code from string hash."""
        hash_value = int(hashlib.md5(text.encode()).hexdigest()[:8], 16)
        encoded = cls.encode(hash_value)
        return encoded[:length].ljust(length, '0')


class URLShortenerError(Exception):
    """Base exception for URL shortener operations."""
    pass


class URLShortener:
    """Main URL shortener service."""
    
    def __init__(self, base_url: str = "https://short.ly/", min_code_length: int = 6):
        self.base_url = base_url.rstrip('/') + '/'
        self.min_code_length = min_code_length
        
        # Thread-safe storage
        self._lock = threading.RLock()
        self._url_to_code: Dict[str, str] = {}  # original_url -> short_code
        self._code_to_metadata: Dict[str, URLMetadata] = {}  # short_code -> metadata
        self._analytics: Dict[str, List[ClickEvent]] = {}  # short_code -> click_events
        
        # Counter for sequential ID generation
        self._counter = 1
        
        # Configuration
        self.default_expiration_days = 365
        self.max_analytics_events = 1000  # Limit analytics storage
    
    def shorten_url(self, request: ShortenRequest) -> Tuple[str, str]:
        """
        Shorten a URL and return (short_code, full_short_url).
        
        Args:
            request: ShortenRequest object with URL and options
            
        Returns:
            Tuple of (short_code, full_short_url)
            
        Raises:
            URLShortenerError: If URL is invalid or operation fails
        """
        # Validate URL
        sanitized_url = URLValidator.sanitize_url(request.url)
        if not URLValidator.is_valid_url(sanitized_url):
            raise URLShortenerError(f"Invalid URL: {request.url}")
        
        with self._lock:
            # Check if URL already exists (optional deduplication)
            existing_code = self._url_to_code.get(sanitized_url)
            if existing_code and existing_code in self._code_to_metadata:
                metadata = self._code_to_metadata[existing_code]
                # Check if not expired
                if not metadata.expires_at or metadata.expires_at > datetime.now():
                    return existing_code, self.base_url + existing_code
            
            # Generate short code
            if request.custom_alias:
                short_code = self._create_custom_alias(request.custom_alias)
            else:
                short_code = self._generate_short_code(sanitized_url)
            
            # Calculate expiration
            expires_at = None
            if request.expires_in_days:
                expires_at = datetime.now() + timedelta(days=request.expires_in_days)
            elif self.default_expiration_days:
                expires_at = datetime.now() + timedelta(days=self.default_expiration_days)
            
            # Create metadata
            metadata = URLMetadata(
                original_url=sanitized_url,
                created_at=datetime.now(),
                expires_at=expires_at,
                creator_id=request.creator_id,
                custom_alias=bool(request.custom_alias)
            )
            
            # Store mappings
            self._url_to_code[sanitized_url] = short_code
            self._code_to_metadata[short_code] = metadata
            self._analytics[short_code] = []
            
            return short_code, self.base_url + short_code
    
    def expand_url(self, short_code: str, track_click: bool = True, 
                   click_info: Optional[Dict] = None) -> Optional[str]:
        """
        Expand a short code to original URL.
        
        Args:
            short_code: The short code to expand
            track_click: Whether to track this as a click event
            click_info: Optional click information for analytics
            
        Returns:
            Original URL if found and not expired, None otherwise
        """
        with self._lock:
            metadata = self._code_to_metadata.get(short_code)
            if not metadata:
                return None
            
            # Check expiration
            if metadata.expires_at and metadata.expires_at <= datetime.now():
                # Clean up expired URL
                self._cleanup_expired_url(short_code, metadata)
                return None
            
            # Track click if requested
            if track_click:
                self._track_click(short_code, click_info or {})
            
            # Update access time and count
            metadata.last_accessed = datetime.now()
            metadata.click_count += 1
            
            return metadata.original_url
    
    def get_analytics(self, short_code: str) -> Optional[Dict]:
        """Get analytics data for a short code."""
        with self._lock:
            metadata = self._code_to_metadata.get(short_code)
            if not metadata:
                return None
            
            click_events = self._analytics.get(short_code, [])
            
            return {
                'short_code': short_code,
                'original_url': metadata.original_url,
                'created_at': metadata.created_at.isoformat(),
                'expires_at': metadata.expires_at.isoformat() if metadata.expires_at else None,
                'total_clicks': metadata.click_count,
                'last_accessed': metadata.last_accessed.isoformat() if metadata.last_accessed else None,
                'recent_clicks': len([e for e in click_events if e.timestamp > datetime.now() - timedelta(days=7)]),
                'is_custom_alias': metadata.custom_alias,
                'creator_id': metadata.creator_id
            }
    
    def delete_url(self, short_code: str) -> bool:
        """Delete a shortened URL."""
        with self._lock:
            metadata = self._code_to_metadata.get(short_code)
            if not metadata:
                return False
            
            # Remove all mappings
            self._url_to_code.pop(metadata.original_url, None)
            self._code_to_metadata.pop(short_code, None)
            self._analytics.pop(short_code, None)
            
            return True
    
    def list_urls(self, creator_id: Optional[str] = None, limit: int = 100) -> List[Dict]:
        """List URLs, optionally filtered by creator."""
        with self._lock:
            results = []
            count = 0
            
            for short_code, metadata in self._code_to_metadata.items():
                if count >= limit:
                    break
                
                if creator_id and metadata.creator_id != creator_id:
                    continue
                
                # Skip expired URLs
                if metadata.expires_at and metadata.expires_at <= datetime.now():
                    continue
                
                results.append({
                    'short_code': short_code,
                    'original_url': metadata.original_url,
                    'full_short_url': self.base_url + short_code,
                    'created_at': metadata.created_at.isoformat(),
                    'expires_at': metadata.expires_at.isoformat() if metadata.expires_at else None,
                    'click_count': metadata.click_count,
                    'is_custom_alias': metadata.custom_alias
                })
                count += 1
            
            return results
    
    def cleanup_expired_urls(self) -> int:
        """Clean up expired URLs and return count of cleaned URLs."""
        with self._lock:
            expired_codes = []
            now = datetime.now()
            
            for short_code, metadata in self._code_to_metadata.items():
                if metadata.expires_at and metadata.expires_at <= now:
                    expired_codes.append((short_code, metadata))
            
            for short_code, metadata in expired_codes:
                self._cleanup_expired_url(short_code, metadata)
            
            return len(expired_codes)
    
    def _generate_short_code(self, url: str) -> str:
        """Generate a unique short code for the URL."""
        # Try hash-based approach first
        hash_code = Base62Encoder.generate_from_string(url, self.min_code_length)
        if hash_code not in self._code_to_metadata:
            return hash_code
        
        # Fall back to counter-based approach
        max_attempts = 10
        for _ in range(max_attempts):
            counter_code = Base62Encoder.encode(self._counter)
            self._counter += 1
            
            # Ensure minimum length
            if len(counter_code) < self.min_code_length:
                counter_code = counter_code.zfill(self.min_code_length)
            
            if counter_code not in self._code_to_metadata:
                return counter_code
        
        # Last resort: use UUID
        return str(uuid.uuid4())[:self.min_code_length]
    
    def _create_custom_alias(self, alias: str) -> str:
        """Create and validate a custom alias."""
        # Validate alias
        if not alias or len(alias) < 3 or len(alias) > 50:
            raise URLShortenerError("Custom alias must be 3-50 characters long")
        
        if not re.match(r'^[a-zA-Z0-9_-]+$', alias):
            raise URLShortenerError("Custom alias can only contain letters, numbers, hyphens, and underscores")
        
        if alias in self._code_to_metadata:
            raise URLShortenerError(f"Custom alias '{alias}' is already taken")
        
        return alias
    
    def _track_click(self, short_code: str, click_info: Dict) -> None:
        """Track a click event for analytics."""
        click_event = ClickEvent(
            timestamp=datetime.now(),
            ip_address=click_info.get('ip_address'),
            user_agent=click_info.get('user_agent'),
            referrer=click_info.get('referrer')
        )
        
        events = self._analytics[short_code]
        events.append(click_event)
        
        # Limit analytics storage
        if len(events) > self.max_analytics_events:
            events.pop(0)  # Remove oldest event
    
    def _cleanup_expired_url(self, short_code: str, metadata: URLMetadata) -> None:
        """Clean up an expired URL."""
        self._url_to_code.pop(metadata.original_url, None)
        self._code_to_metadata.pop(short_code, None)
        self._analytics.pop(short_code, None)
    
    def get_stats(self) -> Dict:
        """Get overall service statistics."""
        with self._lock:
            total_urls = len(self._code_to_metadata)
            total_clicks = sum(metadata.click_count for metadata in self._code_to_metadata.values())
            custom_aliases = sum(1 for metadata in self._code_to_metadata.values() if metadata.custom_alias)
            
            return {
                'total_urls': total_urls,
                'total_clicks': total_clicks,
                'custom_aliases': custom_aliases,
                'average_clicks_per_url': total_clicks / total_urls if total_urls > 0 else 0
            }


def demo():
    """Demonstrate the URL shortener functionality."""
    print("=== URL Shortener Demo ===\n")
    
    shortener = URLShortener("https://short.ly/")
    
    # Test URLs
    test_urls = [
        "https://example.com/very/long/path/to/some/resource?param1=value1&param2=value2",
        "https://github.com/user/repository/blob/main/README.md",
        "https://stackoverflow.com/questions/12345/how-to-implement-url-shortener"
    ]
    
    shortened_urls = []
    
    print("Shortening URLs:")
    for i, url in enumerate(test_urls):
        try:
            request = ShortenRequest(url=url, expires_in_days=30)
            short_code, full_url = shortener.shorten_url(request)
            shortened_urls.append(short_code)
            print(f"✓ {url[:50]}... -> {short_code}")
        except URLShortenerError as e:
            print(f"✗ Failed to shorten {url}: {e}")
    
    print()
    
    # Test custom alias
    print("Creating custom alias:")
    try:
        request = ShortenRequest(
            url="https://custom.example.com/special-page",
            custom_alias="my-special-link"
        )
        custom_code, custom_url = shortener.shorten_url(request)
        shortened_urls.append(custom_code)
        print(f"✓ Custom alias 'my-special-link' created -> {custom_url}")
    except URLShortenerError as e:
        print(f"✗ Failed to create custom alias: {e}")
    
    print()
    
    # Test URL expansion and click tracking
    print("Expanding URLs and tracking clicks:")
    for short_code in shortened_urls:
        original_url = shortener.expand_url(short_code, track_click=True, 
                                          click_info={'ip_address': '192.168.1.1'})
        if original_url:
            print(f"✓ {short_code} -> {original_url[:50]}...")
        else:
            print(f"✗ Failed to expand {short_code}")
    
    print()
    
    # Simulate some more clicks
    print("Simulating additional clicks...")
    for _ in range(3):
        for short_code in shortened_urls[:2]:  # Click first 2 URLs
            shortener.expand_url(short_code, track_click=True)
    
    # Show analytics
    print("Analytics:")
    for short_code in shortened_urls:
        analytics = shortener.get_analytics(short_code)
        if analytics:
            print(f"  {short_code}: {analytics['total_clicks']} clicks, "
                  f"created {analytics['created_at'][:19]}")
    
    print()
    
    # Show overall stats
    stats = shortener.get_stats()
    print("Overall Statistics:")
    print(f"  Total URLs: {stats['total_urls']}")
    print(f"  Total clicks: {stats['total_clicks']}")
    print(f"  Custom aliases: {stats['custom_aliases']}")
    print(f"  Average clicks per URL: {stats['average_clicks_per_url']:.1f}")
    
    print()
    
    # Test error handling
    print("Error Handling Tests:")
    
    # Invalid URL
    try:
        request = ShortenRequest(url="not-a-valid-url")
        shortener.shorten_url(request)
        print("✗ Invalid URL was accepted (unexpected)")
    except URLShortenerError:
        print("✓ Invalid URL correctly rejected")
    
    # Duplicate custom alias
    try:
        request = ShortenRequest(url="https://example.com", custom_alias="my-special-link")
        shortener.shorten_url(request)
        print("✗ Duplicate custom alias was accepted (unexpected)")
    except URLShortenerError:
        print("✓ Duplicate custom alias correctly rejected")
    
    # Non-existent short code
    result = shortener.expand_url("nonexistent")
    if result is None:
        print("✓ Non-existent short code correctly returned None")
    else:
        print("✗ Non-existent short code unexpectedly returned a URL")
    
    print("\n=== Demo Complete ===")


if __name__ == "__main__":
    demo()