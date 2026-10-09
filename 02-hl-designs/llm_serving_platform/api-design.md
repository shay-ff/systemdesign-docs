# LLM Serving Platform - API Design

## API Overview

The LLM serving platform provides comprehensive RESTful APIs for model inference, management, and analytics. All APIs use JSON for request/response payloads and follow OpenAPI 3.0 specifications.

**Base URL**: `https://api.llmplatform.com/v1`

**Authentication**: Bearer token (API Key) in Authorization header
```
Authorization: Bearer sk-1234567890abcdef...
```

## Authentication APIs

### POST /auth/api-keys
Create a new API key.

**Request:**
```json
{
  "name": "My Application Key",
  "permissions": ["inference", "models.read"],
  "rate_limit": {
    "requests_per_minute": 1000,
    "tokens_per_minute": 100000
  },
  "expires_at": "2024-12-31T23:59:59Z"
}
```

**Response:**
```json
{
  "id": "key_abc123",
  "name": "My Application Key",
  "api_key": "sk-1234567890abcdef...",
  "permissions": ["inference", "models.read"],
  "rate_limit": {
    "requests_per_minute": 1000,
    "tokens_per_minute": 100000
  },
  "created_at": "2024-01-15T10:30:00Z",
  "expires_at": "2024-12-31T23:59:59Z"
}
```

### GET /auth/api-keys
List API keys for the authenticated user.

### DELETE /auth/api-keys/{key_id}
Revoke an API key.

## Model Management APIs

### GET /models
List available models.

**Query Parameters:**
- `category`: Filter by model category (text-generation, chat, embedding)
- `size`: Filter by model size (small, medium, large, xl)
- `provider`: Filter by model provider (openai, anthropic, meta, google)

**Response:**
```json
{
  "models": [
    {
      "id": "gpt-4-turbo",
      "name": "GPT-4 Turbo",
      "provider": "openai",
      "category": "text-generation",
      "description": "Most capable GPT-4 model with improved instruction following",
      "context_length": 128000,
      "max_output_tokens": 4096,
      "pricing": {
        "input_tokens": 0.01,
        "output_tokens": 0.03,
        "currency": "USD",
        "per_tokens": 1000
      },
      "capabilities": ["text-generation", "chat", "function-calling"],
      "languages": ["en", "es", "fr", "de", "it", "pt", "ru", "ja", "ko", "zh"],
      "created_at": "2024-01-01T00:00:00Z",
      "updated_at": "2024-01-15T10:30:00Z"
    }
  ],
  "total": 25,
  "page": 1,
  "per_page": 20
}
```

### GET /models/{model_id}
Get detailed information about a specific model.

## Text Generation APIs

### POST /completions
Generate text completions.

**Request:**
```json
{
  "model": "gpt-4-turbo",
  "prompt": "Write a Python function to calculate fibonacci numbers:",
  "max_tokens": 150,
  "temperature": 0.7,
  "top_p": 0.9,
  "frequency_penalty": 0.0,
  "presence_penalty": 0.0,
  "stop": ["\n\n"],
  "stream": false,
  "user": "user_123"
}
```

**Response:**
```json
{
  "id": "cmpl_abc123",
  "object": "text_completion",
  "created": 1642248600,
  "model": "gpt-4-turbo",
  "choices": [
    {
      "text": "def fibonacci(n):\n    if n <= 1:\n        return n\n    else:\n        return fibonacci(n-1) + fibonacci(n-2)",
      "index": 0,
      "logprobs": null,
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 35,
    "total_tokens": 47
  },
  "metadata": {
    "request_id": "req_abc123",
    "processing_time_ms": 1250,
    "model_version": "gpt-4-turbo-2024-01-15"
  }
}
```

### POST /completions (Streaming)
For streaming responses, set `"stream": true` in the request.

**Streaming Response:**
```
data: {"id":"cmpl_abc123","object":"text_completion","created":1642248600,"model":"gpt-4-turbo","choices":[{"text":"def","index":0,"logprobs":null,"finish_reason":null}]}

data: {"id":"cmpl_abc123","object":"text_completion","created":1642248600,"model":"gpt-4-turbo","choices":[{"text":" fibonacci","index":0,"logprobs":null,"finish_reason":null}]}

data: {"id":"cmpl_abc123","object":"text_completion","created":1642248600,"model":"gpt-4-turbo","choices":[{"text":"(n):","index":0,"logprobs":null,"finish_reason":null}]}

data: [DONE]
```

## Chat APIs

### POST /chat/completions
Create a chat completion.

**Request:**
```json
{
  "model": "gpt-4-turbo",
  "messages": [
    {
      "role": "system",
      "content": "You are a helpful assistant that explains programming concepts clearly."
    },
    {
      "role": "user",
      "content": "Explain what recursion is in programming"
    }
  ],
  "max_tokens": 500,
  "temperature": 0.7,
  "top_p": 0.9,
  "stream": false,
  "functions": [
    {
      "name": "get_code_example",
      "description": "Get a code example for a programming concept",
      "parameters": {
        "type": "object",
        "properties": {
          "language": {"type": "string"},
          "concept": {"type": "string"}
        },
        "required": ["language", "concept"]
      }
    }
  ],
  "function_call": "auto"
}
```

**Response:**
```json
{
  "id": "chatcmpl_abc123",
  "object": "chat.completion",
  "created": 1642248600,
  "model": "gpt-4-turbo",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Recursion is a programming technique where a function calls itself to solve a problem. It's like a loop, but instead of iterating, the function breaks down the problem into smaller, similar subproblems.",
        "function_call": null
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 45,
    "completion_tokens": 87,
    "total_tokens": 132
  }
}
```

## Batch Processing APIs

### POST /batches
Create a batch processing job.

**Request:**
```json
{
  "input_file_id": "file_abc123",
  "endpoint": "/v1/chat/completions",
  "completion_window": "24h",
  "metadata": {
    "description": "Batch processing for customer support responses",
    "priority": "normal"
  }
}
```

**Response:**
```json
{
  "id": "batch_abc123",
  "object": "batch",
  "endpoint": "/v1/chat/completions",
  "input_file_id": "file_abc123",
  "completion_window": "24h",
  "status": "validating",
  "output_file_id": null,
  "error_file_id": null,
  "created_at": 1642248600,
  "in_progress_at": null,
  "expires_at": 1642335000,
  "completed_at": null,
  "failed_at": null,
  "expired_at": null,
  "request_counts": {
    "total": 0,
    "completed": 0,
    "failed": 0
  },
  "metadata": {
    "description": "Batch processing for customer support responses",
    "priority": "normal"
  }
}
```

### GET /batches/{batch_id}
Retrieve batch job status.

### POST /batches/{batch_id}/cancel
Cancel a batch job.

## File Management APIs

### POST /files
Upload a file for batch processing.

**Request (Multipart Form Data):**
```
Content-Type: multipart/form-data

file: [binary file data]
purpose: "batch"
```

**Response:**
```json
{
  "id": "file_abc123",
  "object": "file",
  "bytes": 175000,
  "created_at": 1642248600,
  "filename": "batch_input.jsonl",
  "purpose": "batch",
  "status": "uploaded",
  "status_details": null
}
```

### GET /files
List uploaded files.

### GET /files/{file_id}
Retrieve file information.

### DELETE /files/{file_id}
Delete a file.

### GET /files/{file_id}/content
Download file content.

## Usage and Analytics APIs

### GET /usage
Get usage statistics.

**Query Parameters:**
- `start_date`: Start date (YYYY-MM-DD)
- `end_date`: End date (YYYY-MM-DD)
- `granularity`: Data granularity (day, hour)
- `model`: Filter by specific model

**Response:**
```json
{
  "data": [
    {
      "date": "2024-01-15",
      "model": "gpt-4-turbo",
      "requests": 15420,
      "tokens": {
        "prompt_tokens": 1250000,
        "completion_tokens": 890000,
        "total_tokens": 2140000
      },
      "cost": {
        "amount": 89.50,
        "currency": "USD"
      },
      "latency": {
        "p50_ms": 1200,
        "p95_ms": 2800,
        "p99_ms": 4500
      }
    }
  ],
  "total": {
    "requests": 15420,
    "tokens": 2140000,
    "cost": 89.50
  }
}
```

### GET /usage/billing
Get billing information.

**Response:**
```json
{
  "current_period": {
    "start_date": "2024-01-01",
    "end_date": "2024-01-31",
    "usage": {
      "requests": 125000,
      "tokens": 15750000,
      "cost": 675.25
    },
    "limits": {
      "monthly_budget": 1000.00,
      "requests_per_month": 1000000,
      "tokens_per_month": 50000000
    }
  },
  "payment_method": {
    "type": "credit_card",
    "last_four": "4242",
    "expires": "12/25"
  },
  "next_billing_date": "2024-02-01"
}
```

## Model Fine-tuning APIs

### POST /fine-tuning/jobs
Create a fine-tuning job.

**Request:**
```json
{
  "training_file": "file_abc123",
  "validation_file": "file_def456",
  "model": "gpt-3.5-turbo",
  "hyperparameters": {
    "n_epochs": 3,
    "batch_size": 4,
    "learning_rate_multiplier": 0.1
  },
  "suffix": "my-custom-model"
}
```

**Response:**
```json
{
  "id": "ftjob_abc123",
  "object": "fine_tuning.job",
  "model": "gpt-3.5-turbo",
  "created_at": 1642248600,
  "finished_at": null,
  "fine_tuned_model": null,
  "organization_id": "org_abc123",
  "result_files": [],
  "status": "validating_files",
  "validation_file": "file_def456",
  "training_file": "file_abc123",
  "hyperparameters": {
    "n_epochs": 3,
    "batch_size": 4,
    "learning_rate_multiplier": 0.1
  },
  "trained_tokens": null,
  "error": null
}
```

### GET /fine-tuning/jobs
List fine-tuning jobs.

### GET /fine-tuning/jobs/{job_id}
Get fine-tuning job details.

### POST /fine-tuning/jobs/{job_id}/cancel
Cancel a fine-tuning job.

## Content Safety APIs

### POST /moderations
Classify text for safety violations.

**Request:**
```json
{
  "input": "I want to hurt someone",
  "model": "text-moderation-latest"
}
```

**Response:**
```json
{
  "id": "modr_abc123",
  "model": "text-moderation-latest",
  "results": [
    {
      "flagged": true,
      "categories": {
        "hate": false,
        "hate/threatening": false,
        "harassment": false,
        "harassment/threatening": false,
        "self-harm": false,
        "self-harm/intent": false,
        "self-harm/instructions": false,
        "sexual": false,
        "sexual/minors": false,
        "violence": true,
        "violence/graphic": false
      },
      "category_scores": {
        "hate": 0.1,
        "hate/threatening": 0.05,
        "harassment": 0.2,
        "harassment/threatening": 0.1,
        "self-harm": 0.05,
        "self-harm/intent": 0.1,
        "self-harm/instructions": 0.05,
        "sexual": 0.1,
        "sexual/minors": 0.05,
        "violence": 0.9,
        "violence/graphic": 0.2
      }
    }
  ]
}
```

## Error Responses

All APIs return consistent error responses:

```json
{
  "error": {
    "message": "Invalid request: missing required parameter 'model'",
    "type": "invalid_request_error",
    "param": "model",
    "code": "missing_parameter"
  },
  "request_id": "req_abc123"
}
```

### Common Error Codes

- `invalid_request_error` (400): Request validation failed
- `authentication_error` (401): Invalid API key
- `permission_error` (403): Insufficient permissions
- `not_found_error` (404): Resource not found
- `rate_limit_error` (429): Rate limit exceeded
- `quota_exceeded_error` (429): Usage quota exceeded
- `server_error` (500): Internal server error
- `service_unavailable_error` (503): Service temporarily unavailable

## Rate Limiting

APIs are rate limited with different tiers:

**Free Tier:**
- 20 requests per minute
- 40,000 tokens per minute

**Pro Tier:**
- 3,500 requests per minute
- 90,000 tokens per minute

**Enterprise Tier:**
- Custom limits based on agreement

Rate limit headers:
```
X-RateLimit-Limit-Requests: 3500
X-RateLimit-Limit-Tokens: 90000
X-RateLimit-Remaining-Requests: 3499
X-RateLimit-Remaining-Tokens: 89950
X-RateLimit-Reset-Requests: 1642248660
X-RateLimit-Reset-Tokens: 1642248660
```

## Webhooks

### POST /webhooks
Create a webhook endpoint.

**Request:**
```json
{
  "url": "https://myapp.com/webhooks/llm",
  "events": ["batch.completed", "fine_tuning.job.completed"],
  "secret": "whsec_abc123"
}
```

### Webhook Events

- `batch.completed`: Batch processing job completed
- `batch.failed`: Batch processing job failed
- `fine_tuning.job.completed`: Fine-tuning job completed
- `fine_tuning.job.failed`: Fine-tuning job failed
- `usage.limit.reached`: Usage limit reached
- `payment.failed`: Payment processing failed