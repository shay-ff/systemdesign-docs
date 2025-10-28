# Uber System API Design

## API Overview

The Uber system exposes RESTful APIs for mobile applications, web interfaces, and internal services. All APIs use JSON for request/response payloads and follow REST conventions with proper HTTP status codes.

## Authentication

All APIs require authentication using JWT tokens obtained through the authentication service.

```
Authorization: Bearer <jwt_token>
Content-Type: application/json
```

## Base URLs

- **Production**: `https://api.uber.com/v1`
- **Staging**: `https://api-staging.uber.com/v1`
- **Development**: `https://api-dev.uber.com/v1`

## User Management APIs

### Register User

Create a new user account (rider or driver).

```http
POST /users/register
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890",
  "userType": "rider", // or "driver"
  "deviceId": "device_unique_id"
}
```

**Response (201 Created):**
```json
{
  "userId": "usr_123456789",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "userType": "rider",
  "createdAt": "2024-01-15T10:30:00Z",
  "accessToken": "jwt_token_here",
  "refreshToken": "refresh_token_here"
}
```

### Authenticate User

Login with email and password.

```http
POST /auth/login
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "password": "securePassword123",
  "deviceId": "device_unique_id"
}
```

**Response (200 OK):**
```json
{
  "userId": "usr_123456789",
  "accessToken": "jwt_token_here",
  "refreshToken": "refresh_token_here",
  "expiresIn": 3600,
  "userType": "rider"
}
```

### Get User Profile

Retrieve user profile information.

```http
GET /users/{userId}
```

**Response (200 OK):**
```json
{
  "userId": "usr_123456789",
  "email": "user@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "phoneNumber": "+1234567890",
  "userType": "rider",
  "rating": 4.8,
  "totalTrips": 156,
  "memberSince": "2023-01-15T10:30:00Z",
  "preferences": {
    "defaultPaymentMethod": "card_123",
    "musicPreference": "pop",
    "temperaturePreference": "cool"
  }
}
```

## Location APIs

### Update Location

Update driver's current location (drivers only).

```http
POST /location/update
```

**Request Body:**
```json
{
  "driverId": "drv_123456789",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "heading": 45.5,
  "speed": 25.0,
  "accuracy": 5.0,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Response (200 OK):**
```json
{
  "status": "success",
  "locationId": "loc_987654321",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Get Nearby Drivers

Get available drivers near a location (internal service call).

```http
GET /location/drivers/nearby?lat=37.7749&lng=-122.4194&radius=5000
```

**Response (200 OK):**
```json
{
  "drivers": [
    {
      "driverId": "drv_123456789",
      "latitude": 37.7751,
      "longitude": -122.4190,
      "distance": 150,
      "eta": 3,
      "rating": 4.9,
      "vehicleType": "economy",
      "vehicleInfo": {
        "make": "Toyota",
        "model": "Camry",
        "year": 2020,
        "color": "Silver",
        "licensePlate": "ABC123"
      }
    }
  ],
  "totalCount": 15,
  "searchRadius": 5000
}
```

### Geocode Address

Convert address to coordinates.

```http
GET /location/geocode?address=123%20Main%20St,%20San%20Francisco,%20CA
```

**Response (200 OK):**
```json
{
  "address": "123 Main St, San Francisco, CA 94105",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "confidence": 0.95,
  "components": {
    "streetNumber": "123",
    "streetName": "Main St",
    "city": "San Francisco",
    "state": "CA",
    "zipCode": "94105",
    "country": "USA"
  }
}
```

## Trip Management APIs

### Request Ride

Create a new ride request.

```http
POST /trips/request
```

**Request Body:**
```json
{
  "riderId": "usr_123456789",
  "pickupLocation": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "address": "123 Main St, San Francisco, CA"
  },
  "destination": {
    "latitude": 37.7849,
    "longitude": -122.4094,
    "address": "456 Oak St, San Francisco, CA"
  },
  "vehicleType": "economy",
  "paymentMethodId": "card_123",
  "scheduledTime": null, // null for immediate ride
  "passengers": 1,
  "notes": "Please call when you arrive"
}
```

**Response (201 Created):**
```json
{
  "tripId": "trip_987654321",
  "status": "searching",
  "estimatedFare": {
    "baseFare": 2.50,
    "distanceFare": 8.75,
    "timeFare": 3.25,
    "surgeFare": 0.00,
    "totalFare": 14.50,
    "currency": "USD"
  },
  "estimatedDuration": 18,
  "estimatedDistance": 3.2,
  "searchTimeout": 300,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Get Trip Status

Get current status of a trip.

```http
GET /trips/{tripId}
```

**Response (200 OK):**
```json
{
  "tripId": "trip_987654321",
  "status": "in_progress",
  "rider": {
    "userId": "usr_123456789",
    "firstName": "John",
    "rating": 4.8
  },
  "driver": {
    "driverId": "drv_123456789",
    "firstName": "Jane",
    "rating": 4.9,
    "phoneNumber": "+1987654321",
    "vehicle": {
      "make": "Toyota",
      "model": "Camry",
      "color": "Silver",
      "licensePlate": "ABC123"
    }
  },
  "pickupLocation": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "address": "123 Main St, San Francisco, CA"
  },
  "destination": {
    "latitude": 37.7849,
    "longitude": -122.4094,
    "address": "456 Oak St, San Francisco, CA"
  },
  "currentLocation": {
    "latitude": 37.7799,
    "longitude": -122.4144
  },
  "estimatedArrival": "2024-01-15T10:48:00Z",
  "fare": {
    "baseFare": 2.50,
    "distanceFare": 8.75,
    "timeFare": 3.25,
    "surgeFare": 0.00,
    "totalFare": 14.50,
    "currency": "USD"
  },
  "startedAt": "2024-01-15T10:35:00Z",
  "route": {
    "distance": 3.2,
    "duration": 18,
    "polyline": "encoded_polyline_string"
  }
}
```

### Cancel Trip

Cancel an active trip.

```http
POST /trips/{tripId}/cancel
```

**Request Body:**
```json
{
  "reason": "rider_cancelled",
  "comment": "Plans changed"
}
```

**Response (200 OK):**
```json
{
  "tripId": "trip_987654321",
  "status": "cancelled",
  "cancellationFee": 5.00,
  "refundAmount": 0.00,
  "cancelledAt": "2024-01-15T10:32:00Z",
  "cancelledBy": "rider"
}
```

### Complete Trip

Mark trip as completed (driver only).

```http
POST /trips/{tripId}/complete
```

**Request Body:**
```json
{
  "driverId": "drv_123456789",
  "endLocation": {
    "latitude": 37.7849,
    "longitude": -122.4094
  },
  "actualDistance": 3.4,
  "actualDuration": 22,
  "waitTime": 2
}
```

**Response (200 OK):**
```json
{
  "tripId": "trip_987654321",
  "status": "completed",
  "finalFare": {
    "baseFare": 2.50,
    "distanceFare": 9.35,
    "timeFare": 3.67,
    "waitTimeFare": 1.00,
    "surgeFare": 0.00,
    "totalFare": 16.52,
    "currency": "USD"
  },
  "completedAt": "2024-01-15T10:52:00Z",
  "paymentStatus": "processing"
}
```

## Driver APIs

### Get Available Trips

Get nearby trip requests for drivers.

```http
GET /drivers/{driverId}/available-trips
```

**Response (200 OK):**
```json
{
  "trips": [
    {
      "tripId": "trip_987654321",
      "pickupLocation": {
        "latitude": 37.7749,
        "longitude": -122.4194,
        "address": "123 Main St, San Francisco, CA"
      },
      "destination": {
        "latitude": 37.7849,
        "longitude": -122.4094,
        "address": "456 Oak St, San Francisco, CA"
      },
      "estimatedFare": 14.50,
      "estimatedDuration": 18,
      "estimatedDistance": 3.2,
      "pickupEta": 5,
      "riderRating": 4.8,
      "surgeMultiplier": 1.0
    }
  ]
}
```

### Accept Trip

Accept a trip request.

```http
POST /drivers/{driverId}/accept-trip
```

**Request Body:**
```json
{
  "tripId": "trip_987654321"
}
```

**Response (200 OK):**
```json
{
  "tripId": "trip_987654321",
  "status": "accepted",
  "rider": {
    "firstName": "John",
    "rating": 4.8,
    "phoneNumber": "+1234567890"
  },
  "pickupLocation": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "address": "123 Main St, San Francisco, CA"
  },
  "acceptedAt": "2024-01-15T10:31:00Z"
}
```

### Update Driver Status

Update driver availability status.

```http
POST /drivers/{driverId}/status
```

**Request Body:**
```json
{
  "status": "online", // online, offline, busy
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194
  }
}
```

**Response (200 OK):**
```json
{
  "driverId": "drv_123456789",
  "status": "online",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

## Payment APIs

### Add Payment Method

Add a new payment method for a user.

```http
POST /payments/methods
```

**Request Body:**
```json
{
  "userId": "usr_123456789",
  "type": "credit_card",
  "cardNumber": "4111111111111111",
  "expiryMonth": 12,
  "expiryYear": 2025,
  "cvv": "123",
  "billingAddress": {
    "street": "123 Main St",
    "city": "San Francisco",
    "state": "CA",
    "zipCode": "94105",
    "country": "USA"
  }
}
```

**Response (201 Created):**
```json
{
  "paymentMethodId": "card_123",
  "type": "credit_card",
  "last4": "1111",
  "brand": "visa",
  "expiryMonth": 12,
  "expiryYear": 2025,
  "isDefault": false,
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Process Payment

Process payment for a completed trip.

```http
POST /payments/process
```

**Request Body:**
```json
{
  "tripId": "trip_987654321",
  "paymentMethodId": "card_123",
  "amount": 16.52,
  "currency": "USD",
  "tip": 3.00
}
```

**Response (200 OK):**
```json
{
  "paymentId": "pay_987654321",
  "status": "succeeded",
  "amount": 16.52,
  "tip": 3.00,
  "totalCharged": 19.52,
  "currency": "USD",
  "processedAt": "2024-01-15T10:53:00Z",
  "receipt": {
    "receiptId": "rcpt_123456789",
    "receiptUrl": "https://receipts.uber.com/rcpt_123456789"
  }
}
```

## Rating and Feedback APIs

### Submit Rating

Submit rating and feedback after trip completion.

```http
POST /trips/{tripId}/rating
```

**Request Body:**
```json
{
  "ratedBy": "usr_123456789", // rider or driver ID
  "rating": 5,
  "feedback": "Great driver, very professional!",
  "tags": ["professional", "clean_car", "safe_driving"]
}
```

**Response (201 Created):**
```json
{
  "ratingId": "rating_123456789",
  "tripId": "trip_987654321",
  "rating": 5,
  "submittedAt": "2024-01-15T11:00:00Z"
}
```

## Notification APIs

### Send Push Notification

Send push notification to user (internal service).

```http
POST /notifications/push
```

**Request Body:**
```json
{
  "userId": "usr_123456789",
  "title": "Driver Arriving",
  "message": "Your driver Jane will arrive in 2 minutes",
  "type": "trip_update",
  "data": {
    "tripId": "trip_987654321",
    "eta": 2
  }
}
```

**Response (200 OK):**
```json
{
  "notificationId": "notif_123456789",
  "status": "sent",
  "sentAt": "2024-01-15T10:33:00Z"
}
```

## Error Responses

All APIs return consistent error responses:

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "The request is invalid",
    "details": "Missing required field: pickupLocation",
    "timestamp": "2024-01-15T10:30:00Z",
    "requestId": "req_123456789"
  }
}
```

### Common Error Codes

- `INVALID_REQUEST` (400) - Malformed request
- `UNAUTHORIZED` (401) - Invalid or missing authentication
- `FORBIDDEN` (403) - Insufficient permissions
- `NOT_FOUND` (404) - Resource not found
- `CONFLICT` (409) - Resource conflict
- `RATE_LIMITED` (429) - Too many requests
- `INTERNAL_ERROR` (500) - Server error
- `SERVICE_UNAVAILABLE` (503) - Service temporarily unavailable

## Rate Limiting

APIs are rate limited per user:
- **Authentication APIs**: 10 requests per minute
- **Location Updates**: 60 requests per minute
- **Trip APIs**: 30 requests per minute
- **General APIs**: 100 requests per minute

Rate limit headers are included in responses:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1642248600
```