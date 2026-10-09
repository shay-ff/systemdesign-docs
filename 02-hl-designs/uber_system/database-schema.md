# Uber System Database Schema

## Database Architecture Overview

The Uber system uses a microservices architecture with dedicated databases for each service domain. This approach provides better scalability, fault isolation, and allows teams to choose the most appropriate database technology for their specific use case.

### Database Technologies Used

- **PostgreSQL**: Primary relational database for user data, trips, and payments
- **MongoDB**: Document database for flexible schemas like driver documents and analytics
- **Redis**: In-memory cache for sessions, real-time data, and frequently accessed information
- **Elasticsearch**: Search engine for location-based queries and analytics
- **InfluxDB**: Time-series database for location tracking and metrics

## User Service Database (PostgreSQL)

### Users Table

Stores basic user information for both riders and drivers.

```sql
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    user_type VARCHAR(10) NOT NULL CHECK (user_type IN ('rider', 'driver')),
    status VARCHAR(20) DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'deleted')),
    email_verified BOOLEAN DEFAULT FALSE,
    phone_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP WITH TIME ZONE,
    
    -- Indexes
    INDEX idx_users_email (email),
    INDEX idx_users_phone (phone_number),
    INDEX idx_users_type (user_type),
    INDEX idx_users_status (status)
);
```

### User Profiles Table

Extended profile information for users.

```sql
CREATE TABLE user_profiles (
    profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    date_of_birth DATE,
    profile_picture_url VARCHAR(500),
    address JSONB,
    preferences JSONB,
    emergency_contact JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    UNIQUE INDEX idx_user_profiles_user_id (user_id)
);
```

### Driver Profiles Table

Additional information specific to drivers.

```sql
CREATE TABLE driver_profiles (
    driver_profile_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    license_number VARCHAR(50) UNIQUE NOT NULL,
    license_expiry_date DATE NOT NULL,
    background_check_status VARCHAR(20) DEFAULT 'pending',
    background_check_date TIMESTAMP WITH TIME ZONE,
    onboarding_status VARCHAR(20) DEFAULT 'pending',
    approval_date TIMESTAMP WITH TIME ZONE,
    rating DECIMAL(3,2) DEFAULT 5.00,
    total_trips INTEGER DEFAULT 0,
    total_earnings DECIMAL(10,2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'offline' CHECK (status IN ('online', 'offline', 'busy')),
    current_location POINT,
    last_location_update TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    UNIQUE INDEX idx_driver_profiles_user_id (user_id),
    INDEX idx_driver_profiles_license (license_number),
    INDEX idx_driver_profiles_status (status),
    INDEX idx_driver_profiles_location USING GIST (current_location),
    INDEX idx_driver_profiles_rating (rating)
);
```

### Vehicles Table

Information about driver vehicles.

```sql
CREATE TABLE vehicles (
    vehicle_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL REFERENCES driver_profiles(driver_profile_id) ON DELETE CASCADE,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    year INTEGER NOT NULL,
    color VARCHAR(30) NOT NULL,
    license_plate VARCHAR(20) UNIQUE NOT NULL,
    vehicle_type VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('economy', 'premium', 'luxury', 'suv')),
    capacity INTEGER DEFAULT 4,
    insurance_policy_number VARCHAR(100),
    insurance_expiry_date DATE,
    registration_expiry_date DATE,
    inspection_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_vehicles_driver_id (driver_id),
    INDEX idx_vehicles_license_plate (license_plate),
    INDEX idx_vehicles_type (vehicle_type),
    INDEX idx_vehicles_active (is_active)
);
```

## Trip Service Database (PostgreSQL)

### Trips Table

Core trip information and state management.

```sql
CREATE TABLE trips (
    trip_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id UUID NOT NULL,
    driver_id UUID,
    vehicle_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'requested' CHECK (
        status IN ('requested', 'searching', 'matched', 'accepted', 'driver_arriving', 
                  'driver_arrived', 'in_progress', 'completed', 'cancelled')
    ),
    
    -- Location information
    pickup_latitude DECIMAL(10, 8) NOT NULL,
    pickup_longitude DECIMAL(11, 8) NOT NULL,
    pickup_address TEXT NOT NULL,
    destination_latitude DECIMAL(10, 8) NOT NULL,
    destination_longitude DECIMAL(11, 8) NOT NULL,
    destination_address TEXT NOT NULL,
    
    -- Trip details
    vehicle_type VARCHAR(20) NOT NULL,
    passenger_count INTEGER DEFAULT 1,
    scheduled_time TIMESTAMP WITH TIME ZONE,
    special_instructions TEXT,
    
    -- Timing information
    requested_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    matched_at TIMESTAMP WITH TIME ZONE,
    accepted_at TIMESTAMP WITH TIME ZONE,
    pickup_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    
    -- Trip metrics
    estimated_distance_km DECIMAL(8, 2),
    estimated_duration_minutes INTEGER,
    actual_distance_km DECIMAL(8, 2),
    actual_duration_minutes INTEGER,
    wait_time_minutes INTEGER DEFAULT 0,
    
    -- Cancellation information
    cancelled_by VARCHAR(10) CHECK (cancelled_by IN ('rider', 'driver', 'system')),
    cancellation_reason VARCHAR(100),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_trips_rider_id (rider_id),
    INDEX idx_trips_driver_id (driver_id),
    INDEX idx_trips_status (status),
    INDEX idx_trips_requested_at (requested_at),
    INDEX idx_trips_pickup_location (pickup_latitude, pickup_longitude),
    INDEX idx_trips_destination_location (destination_latitude, destination_longitude),
    INDEX idx_trips_vehicle_type (vehicle_type)
);
```

### Trip Routes Table

Detailed route information for trips.

```sql
CREATE TABLE trip_routes (
    route_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(trip_id) ON DELETE CASCADE,
    route_polyline TEXT, -- Encoded polyline string
    waypoints JSONB, -- Array of waypoint coordinates
    traffic_conditions JSONB,
    estimated_duration_with_traffic INTEGER,
    alternative_routes JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    UNIQUE INDEX idx_trip_routes_trip_id (trip_id)
);
```

## Payment Service Database (PostgreSQL)

### Payment Methods Table

User payment methods and billing information.

```sql
CREATE TABLE payment_methods (
    payment_method_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('credit_card', 'debit_card', 'paypal', 'apple_pay', 'google_pay', 'cash')),
    is_default BOOLEAN DEFAULT FALSE,
    
    -- Card information (encrypted)
    card_last_four VARCHAR(4),
    card_brand VARCHAR(20),
    card_expiry_month INTEGER,
    card_expiry_year INTEGER,
    
    -- External payment provider information
    external_payment_id VARCHAR(100), -- Stripe customer ID, PayPal account, etc.
    
    -- Billing address
    billing_address JSONB,
    
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_payment_methods_user_id (user_id),
    INDEX idx_payment_methods_type (type),
    INDEX idx_payment_methods_default (user_id, is_default) WHERE is_default = TRUE
);
```

### Payments Table

Payment transactions for trips.

```sql
CREATE TABLE payments (
    payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(trip_id),
    rider_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    payment_method_id UUID REFERENCES payment_methods(payment_method_id),
    
    -- Payment amounts (in cents to avoid floating point issues)
    base_fare_cents INTEGER NOT NULL,
    distance_fare_cents INTEGER NOT NULL,
    time_fare_cents INTEGER NOT NULL,
    surge_fare_cents INTEGER DEFAULT 0,
    wait_time_fare_cents INTEGER DEFAULT 0,
    toll_fare_cents INTEGER DEFAULT 0,
    tip_cents INTEGER DEFAULT 0,
    total_fare_cents INTEGER NOT NULL,
    
    -- Fees and taxes
    service_fee_cents INTEGER DEFAULT 0,
    tax_cents INTEGER DEFAULT 0,
    
    -- Payment processing
    currency VARCHAR(3) DEFAULT 'USD',
    payment_status VARCHAR(20) DEFAULT 'pending' CHECK (
        payment_status IN ('pending', 'processing', 'succeeded', 'failed', 'refunded', 'disputed')
    ),
    external_transaction_id VARCHAR(100), -- Stripe charge ID, etc.
    failure_reason TEXT,
    
    -- Timing
    processed_at TIMESTAMP WITH TIME ZONE,
    refunded_at TIMESTAMP WITH TIME ZONE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_payments_trip_id (trip_id),
    INDEX idx_payments_rider_id (rider_id),
    INDEX idx_payments_driver_id (driver_id),
    INDEX idx_payments_status (payment_status),
    INDEX idx_payments_processed_at (processed_at)
);
```

### Driver Earnings Table

Track driver earnings and payouts.

```sql
CREATE TABLE driver_earnings (
    earning_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL,
    trip_id UUID REFERENCES trips(trip_id),
    
    -- Earnings breakdown (in cents)
    gross_fare_cents INTEGER NOT NULL,
    platform_fee_cents INTEGER NOT NULL,
    net_earnings_cents INTEGER NOT NULL,
    tip_cents INTEGER DEFAULT 0,
    bonus_cents INTEGER DEFAULT 0,
    
    -- Payout information
    payout_status VARCHAR(20) DEFAULT 'pending' CHECK (
        payout_status IN ('pending', 'processing', 'paid', 'failed')
    ),
    payout_date DATE,
    payout_method VARCHAR(20),
    external_payout_id VARCHAR(100),
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_driver_earnings_driver_id (driver_id),
    INDEX idx_driver_earnings_trip_id (trip_id),
    INDEX idx_driver_earnings_payout_status (payout_status),
    INDEX idx_driver_earnings_payout_date (payout_date)
);
```

## Location Service Database (InfluxDB)

### Driver Locations (Time Series)

Real-time and historical location data for drivers.

```sql
-- InfluxDB measurement for driver locations
CREATE MEASUREMENT driver_locations (
    time TIMESTAMP,
    driver_id TAG,
    latitude FIELD,
    longitude FIELD,
    heading FIELD,
    speed FIELD,
    accuracy FIELD,
    status TAG, -- online, offline, busy
    trip_id TAG -- current trip if any
);

-- Retention policy for location data
CREATE RETENTION POLICY "location_retention" ON "uber_db" 
    DURATION 30d REPLICATION 1 DEFAULT;
```

### Trip Tracking (Time Series)

Real-time location updates during trips.

```sql
-- InfluxDB measurement for trip tracking
CREATE MEASUREMENT trip_tracking (
    time TIMESTAMP,
    trip_id TAG,
    driver_id TAG,
    rider_id TAG,
    latitude FIELD,
    longitude FIELD,
    speed FIELD,
    heading FIELD,
    distance_from_pickup FIELD,
    distance_from_destination FIELD,
    trip_status TAG
);
```

## Rating Service Database (PostgreSQL)

### Ratings Table

User ratings and feedback system.

```sql
CREATE TABLE ratings (
    rating_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID NOT NULL REFERENCES trips(trip_id),
    rated_by_user_id UUID NOT NULL, -- Who gave the rating
    rated_user_id UUID NOT NULL, -- Who received the rating
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    feedback TEXT,
    tags JSONB, -- Array of predefined tags
    is_anonymous BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_ratings_trip_id (trip_id),
    INDEX idx_ratings_rated_by (rated_by_user_id),
    INDEX idx_ratings_rated_user (rated_user_id),
    INDEX idx_ratings_rating (rating),
    INDEX idx_ratings_created_at (created_at)
);
```

## Cache Layer (Redis)

### Session Storage

```redis
# User sessions
SET session:{session_id} "{user_id: 'usr_123', expires_at: '2024-01-15T12:00:00Z'}" EX 3600

# Driver status cache
SET driver:status:{driver_id} "{status: 'online', location: {lat: 37.7749, lng: -122.4194}, updated_at: '2024-01-15T10:30:00Z'}" EX 300

# Active trip cache
SET trip:active:{trip_id} "{status: 'in_progress', driver_id: 'drv_123', rider_id: 'usr_456'}" EX 7200
```

### Location Cache

```redis
# Nearby drivers (geospatial index)
GEOADD drivers:online -122.4194 37.7749 "drv_123"
GEOADD drivers:online -122.4094 37.7849 "drv_456"

# Query nearby drivers
GEORADIUS drivers:online -122.4194 37.7749 5 km WITHDIST WITHCOORD
```

## Search Engine (Elasticsearch)

### Driver Search Index

```json
{
  "mappings": {
    "properties": {
      "driver_id": {"type": "keyword"},
      "location": {"type": "geo_point"},
      "status": {"type": "keyword"},
      "vehicle_type": {"type": "keyword"},
      "rating": {"type": "float"},
      "total_trips": {"type": "integer"},
      "last_updated": {"type": "date"}
    }
  }
}
```

### Location Search Index

```json
{
  "mappings": {
    "properties": {
      "address": {"type": "text", "analyzer": "standard"},
      "location": {"type": "geo_point"},
      "city": {"type": "keyword"},
      "state": {"type": "keyword"},
      "country": {"type": "keyword"},
      "place_type": {"type": "keyword"}
    }
  }
}
```

## Database Sharding Strategy

### Horizontal Sharding

**User Data Sharding:**
- Shard by user_id hash
- 16 shards initially, can expand to 64
- Each shard handles ~6.25M users

**Trip Data Sharding:**
- Shard by geographic region (city/metro area)
- Allows for better locality and compliance with local regulations
- Cross-shard queries handled by application layer

**Payment Data Sharding:**
- Shard by user_id to co-locate with user data
- Ensures ACID properties for user-specific transactions

### Replication Strategy

- **Master-Slave Replication**: Each shard has 1 master + 2 read replicas
- **Cross-Region Replication**: Async replication to disaster recovery regions
- **Read Scaling**: Route read queries to replicas based on consistency requirements

## Data Consistency Patterns

### Strong Consistency
- User authentication and authorization
- Payment processing and financial transactions
- Trip state transitions (requested → matched → completed)

### Eventual Consistency
- Driver location updates
- Analytics and reporting data
- User profile updates (non-critical fields)

### Conflict Resolution
- Last-write-wins for location updates
- Application-level conflict resolution for concurrent trip requests
- Compensating transactions for payment failures

## Backup and Recovery

### Backup Strategy
- **Full Backups**: Daily full database backups
- **Incremental Backups**: Hourly incremental backups
- **Point-in-Time Recovery**: Transaction log backups every 15 minutes
- **Cross-Region Backup**: Replicate backups to geographically distant regions

### Recovery Objectives
- **RTO (Recovery Time Objective)**: 4 hours for full system recovery
- **RPO (Recovery Point Objective)**: 15 minutes maximum data loss
- **Partial Recovery**: Critical services (trip matching, payments) within 30 minutes