# LLM Serving Platform - Database Schema

## Database Architecture Overview

The LLM serving platform uses a polyglot persistence approach with different databases optimized for specific use cases:

- **PostgreSQL**: User accounts, model metadata, billing data, transactional data
- **Redis**: Caching, session management, rate limiting, real-time counters
- **ClickHouse**: Usage analytics, performance metrics, time-series data
- **Elasticsearch**: Model search, log analysis, audit trails
- **S3/GCS**: Model artifacts, training data, batch processing files

## Core Database Schemas

### User Management (PostgreSQL)

#### organizations
```sql
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    plan_type VARCHAR(50) NOT NULL DEFAULT 'free', -- 'free', 'pro', 'enterprise'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    billing_email VARCHAR(255),
    tax_id VARCHAR(100),
    address JSONB,
    settings JSONB DEFAULT '{}'
);

CREATE INDEX idx_organizations_slug ON organizations(slug);
CREATE INDEX idx_organizations_plan_type ON organizations(plan_type);
```

#### users
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(50) NOT NULL DEFAULT 'member', -- 'owner', 'admin', 'member', 'viewer'
    is_active BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    preferences JSONB DEFAULT '{}'
);

CREATE INDEX idx_users_organization_id ON users(organization_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
```

#### api_keys
```sql
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(20) NOT NULL, -- 'sk-' prefix for display
    permissions TEXT[] DEFAULT '{}', -- ['inference', 'models.read', 'fine_tuning']
    rate_limits JSONB DEFAULT '{}', -- {'requests_per_minute': 1000, 'tokens_per_minute': 100000}
    is_active BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_api_keys_organization_id ON api_keys(organization_id);
CREATE INDEX idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX idx_api_keys_key_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_is_active ON api_keys(is_active);
```

### Model Management (PostgreSQL)

#### models
```sql
CREATE TABLE models (
    id VARCHAR(100) PRIMARY KEY, -- 'gpt-4-turbo', 'claude-3-opus'
    name VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL, -- 'openai', 'anthropic', 'meta', 'google'
    category VARCHAR(50) NOT NULL, -- 'text-generation', 'chat', 'embedding', 'image-generation'
    description TEXT,
    context_length INTEGER NOT NULL,
    max_output_tokens INTEGER,
    parameter_count BIGINT, -- Number of parameters (e.g., 175000000000 for 175B)
    model_size VARCHAR(20), -- 'small', 'medium', 'large', 'xl'
    capabilities TEXT[] DEFAULT '{}', -- ['text-generation', 'chat', 'function-calling']
    supported_languages TEXT[] DEFAULT '{}',
    pricing JSONB NOT NULL, -- {'input_tokens': 0.01, 'output_tokens': 0.03, 'currency': 'USD', 'per_tokens': 1000}
    is_active BOOLEAN DEFAULT TRUE,
    is_public BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_models_provider ON models(provider);
CREATE INDEX idx_models_category ON models(category);
CREATE INDEX idx_models_is_active ON models(is_active);
CREATE INDEX idx_models_is_public ON models(is_public);
```

#### model_versions
```sql
CREATE TABLE model_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id VARCHAR(100) NOT NULL REFERENCES models(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL, -- '2024-01-15', 'v1.0.0'
    is_default BOOLEAN DEFAULT FALSE,
    model_artifact_path VARCHAR(1000) NOT NULL,
    config JSONB DEFAULT '{}',
    performance_metrics JSONB DEFAULT '{}', -- {'latency_p50_ms': 1200, 'throughput_tokens_per_sec': 850}
    deployment_status VARCHAR(50) DEFAULT 'pending', -- 'pending', 'deploying', 'active', 'deprecated'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deployed_at TIMESTAMP WITH TIME ZONE,
    deprecated_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(model_id, version)
);

CREATE INDEX idx_model_versions_model_id ON model_versions(model_id);
CREATE INDEX idx_model_versions_is_default ON model_versions(is_default);
CREATE INDEX idx_model_versions_deployment_status ON model_versions(deployment_status);
```

#### custom_models
```sql
CREATE TABLE custom_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    base_model_id VARCHAR(100) NOT NULL REFERENCES models(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    training_status VARCHAR(50) DEFAULT 'pending', -- 'pending', 'training', 'completed', 'failed'
    training_job_id VARCHAR(100),
    model_artifact_path VARCHAR(1000),
    training_config JSONB DEFAULT '{}',
    performance_metrics JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_custom_models_organization_id ON custom_models(organization_id);
CREATE INDEX idx_custom_models_base_model_id ON custom_models(base_model_id);
CREATE INDEX idx_custom_models_training_status ON custom_models(training_status);
```

### Request and Response Tracking (PostgreSQL)

#### inference_requests
```sql
CREATE TABLE inference_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    api_key_id UUID REFERENCES api_keys(id),
    model_id VARCHAR(100) NOT NULL REFERENCES models(id),
    model_version VARCHAR(50),
    request_type VARCHAR(50) NOT NULL, -- 'completion', 'chat', 'embedding', 'moderation'
    prompt_tokens INTEGER NOT NULL DEFAULT 0,
    completion_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens INTEGER NOT NULL DEFAULT 0,
    request_size_bytes INTEGER,
    response_size_bytes INTEGER,
    processing_time_ms INTEGER,
    queue_time_ms INTEGER,
    total_time_ms INTEGER,
    status VARCHAR(50) NOT NULL, -- 'success', 'error', 'timeout', 'rate_limited'
    error_code VARCHAR(100),
    error_message TEXT,
    user_agent TEXT,
    ip_address INET,
    region VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Partition by month for better performance
CREATE TABLE inference_requests_y2024m01 PARTITION OF inference_requests
FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE INDEX idx_inference_requests_organization_id ON inference_requests(organization_id);
CREATE INDEX idx_inference_requests_api_key_id ON inference_requests(api_key_id);
CREATE INDEX idx_inference_requests_model_id ON inference_requests(model_id);
CREATE INDEX idx_inference_requests_created_at ON inference_requests(created_at);
CREATE INDEX idx_inference_requests_status ON inference_requests(status);
```

### Batch Processing (PostgreSQL)

#### batch_jobs
```sql
CREATE TABLE batch_jobs (
    id VARCHAR(100) PRIMARY KEY, -- 'batch_abc123'
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    endpoint VARCHAR(255) NOT NULL, -- '/v1/chat/completions'
    input_file_id VARCHAR(100) NOT NULL,
    output_file_id VARCHAR(100),
    error_file_id VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'validating', -- 'validating', 'in_progress', 'completed', 'failed', 'cancelled'
    completion_window VARCHAR(20) NOT NULL, -- '24h'
    request_counts JSONB DEFAULT '{"total": 0, "completed": 0, "failed": 0}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    in_progress_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    failed_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_batch_jobs_organization_id ON batch_jobs(organization_id);
CREATE INDEX idx_batch_jobs_user_id ON batch_jobs(user_id);
CREATE INDEX idx_batch_jobs_status ON batch_jobs(status);
CREATE INDEX idx_batch_jobs_created_at ON batch_jobs(created_at);
```

#### files
```sql
CREATE TABLE files (
    id VARCHAR(100) PRIMARY KEY, -- 'file_abc123'
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    filename VARCHAR(255) NOT NULL,
    purpose VARCHAR(50) NOT NULL, -- 'batch', 'fine-tune', 'assistants'
    bytes BIGINT NOT NULL,
    mime_type VARCHAR(100),
    storage_path VARCHAR(1000) NOT NULL,
    status VARCHAR(50) DEFAULT 'uploaded', -- 'uploaded', 'processed', 'error'
    status_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    processed_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_files_organization_id ON files(organization_id);
CREATE INDEX idx_files_user_id ON files(user_id);
CREATE INDEX idx_files_purpose ON files(purpose);
CREATE INDEX idx_files_status ON files(status);
```

### Fine-tuning (PostgreSQL)

#### fine_tuning_jobs
```sql
CREATE TABLE fine_tuning_jobs (
    id VARCHAR(100) PRIMARY KEY, -- 'ftjob_abc123'
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    model_id VARCHAR(100) NOT NULL REFERENCES models(id),
    training_file_id VARCHAR(100) NOT NULL REFERENCES files(id),
    validation_file_id VARCHAR(100) REFERENCES files(id),
    fine_tuned_model_id UUID REFERENCES custom_models(id),
    status VARCHAR(50) NOT NULL DEFAULT 'validating_files', -- 'validating_files', 'queued', 'running', 'succeeded', 'failed', 'cancelled'
    hyperparameters JSONB DEFAULT '{}',
    result_files TEXT[] DEFAULT '{}',
    trained_tokens BIGINT,
    training_loss DECIMAL(10,6),
    validation_loss DECIMAL(10,6),
    error JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    estimated_finish_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

CREATE INDEX idx_fine_tuning_jobs_organization_id ON fine_tuning_jobs(organization_id);
CREATE INDEX idx_fine_tuning_jobs_user_id ON fine_tuning_jobs(user_id);
CREATE INDEX idx_fine_tuning_jobs_model_id ON fine_tuning_jobs(model_id);
CREATE INDEX idx_fine_tuning_jobs_status ON fine_tuning_jobs(status);
```

### Billing and Usage (PostgreSQL)

#### billing_accounts
```sql
CREATE TABLE billing_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    stripe_customer_id VARCHAR(100) UNIQUE,
    payment_method_id VARCHAR(100),
    billing_email VARCHAR(255),
    billing_address JSONB,
    tax_id VARCHAR(100),
    currency VARCHAR(3) DEFAULT 'USD',
    monthly_budget DECIMAL(10,2),
    auto_recharge_enabled BOOLEAN DEFAULT FALSE,
    auto_recharge_threshold DECIMAL(10,2),
    auto_recharge_amount DECIMAL(10,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_billing_accounts_organization_id ON billing_accounts(organization_id);
CREATE INDEX idx_billing_accounts_stripe_customer_id ON billing_accounts(stripe_customer_id);
```

#### usage_records
```sql
CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    model_id VARCHAR(100) NOT NULL REFERENCES models(id),
    date DATE NOT NULL,
    requests INTEGER DEFAULT 0,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    total_tokens BIGINT DEFAULT 0,
    cost_usd DECIMAL(10,4) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(organization_id, model_id, date)
);

CREATE INDEX idx_usage_records_organization_id ON usage_records(organization_id);
CREATE INDEX idx_usage_records_model_id ON usage_records(model_id);
CREATE INDEX idx_usage_records_date ON usage_records(date);
```

#### invoices
```sql
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    billing_account_id UUID NOT NULL REFERENCES billing_accounts(id),
    invoice_number VARCHAR(100) UNIQUE NOT NULL,
    stripe_invoice_id VARCHAR(100) UNIQUE,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    tax_amount DECIMAL(10,2) DEFAULT 0,
    total_amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(50) NOT NULL, -- 'draft', 'open', 'paid', 'void', 'uncollectible'
    due_date DATE,
    paid_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    line_items JSONB DEFAULT '[]'
);

CREATE INDEX idx_invoices_organization_id ON invoices(organization_id);
CREATE INDEX idx_invoices_billing_account_id ON invoices(billing_account_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_period_start ON invoices(period_start);
```

## Cache Layer (Redis)

### Rate Limiting
```
Key Pattern: rate_limit:{api_key_id}:{window}
Value: Current request count
TTL: Window duration (60 seconds for per-minute limits)

Key Pattern: token_limit:{api_key_id}:{window}
Value: Current token count
TTL: Window duration
```

### Model Caching
```
Key Pattern: model:{model_id}:metadata
Value: JSON object with model metadata
TTL: 1 hour

Key Pattern: model:{model_id}:version:{version}
Value: JSON object with model version info
TTL: 1 hour

Key Pattern: model_servers:{model_id}
Value: List of available server endpoints
TTL: 5 minutes
```

### Session Management
```
Key Pattern: session:{session_token}
Value: JSON object with user session data
TTL: 24 hours

Key Pattern: api_key:{key_hash}
Value: JSON object with API key metadata
TTL: 1 hour
```

### Response Caching
```
Key Pattern: response:{hash_of_request}
Value: JSON object with cached response
TTL: 1 hour (configurable per model)

Key Pattern: batch_status:{batch_id}
Value: JSON object with batch job status
TTL: 5 minutes
```

## Analytics Database (ClickHouse)

### Request Analytics
```sql
CREATE TABLE request_analytics (
    timestamp DateTime,
    organization_id String,
    api_key_id String,
    model_id String,
    model_version String,
    request_type String,
    prompt_tokens UInt32,
    completion_tokens UInt32,
    total_tokens UInt32,
    processing_time_ms UInt32,
    queue_time_ms UInt32,
    total_time_ms UInt32,
    status String,
    error_code String,
    region String,
    user_agent String,
    cost_usd Float64
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, organization_id, model_id);
```

### Model Performance Metrics
```sql
CREATE TABLE model_performance (
    timestamp DateTime,
    model_id String,
    model_version String,
    region String,
    gpu_type String,
    requests_per_second Float64,
    tokens_per_second Float64,
    avg_latency_ms Float64,
    p95_latency_ms Float64,
    p99_latency_ms Float64,
    gpu_utilization Float64,
    memory_utilization Float64,
    error_rate Float64
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, model_id, region);
```

### Usage Aggregations
```sql
CREATE TABLE daily_usage_summary (
    date Date,
    organization_id String,
    model_id String,
    total_requests UInt64,
    total_tokens UInt64,
    total_cost_usd Float64,
    avg_latency_ms Float64,
    error_rate Float64
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, organization_id, model_id);
```

## Search Index (Elasticsearch)

### Model Search Index
```json
{
  "mappings": {
    "properties": {
      "model_id": {"type": "keyword"},
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"},
          "suggest": {"type": "completion"}
        }
      },
      "description": {"type": "text"},
      "provider": {"type": "keyword"},
      "category": {"type": "keyword"},
      "capabilities": {"type": "keyword"},
      "supported_languages": {"type": "keyword"},
      "parameter_count": {"type": "long"},
      "context_length": {"type": "integer"},
      "pricing": {
        "type": "object",
        "properties": {
          "input_tokens": {"type": "float"},
          "output_tokens": {"type": "float"}
        }
      },
      "is_active": {"type": "boolean"},
      "is_public": {"type": "boolean"},
      "created_at": {"type": "date"}
    }
  }
}
```

### Audit Log Index
```json
{
  "mappings": {
    "properties": {
      "timestamp": {"type": "date"},
      "organization_id": {"type": "keyword"},
      "user_id": {"type": "keyword"},
      "action": {"type": "keyword"},
      "resource_type": {"type": "keyword"},
      "resource_id": {"type": "keyword"},
      "details": {"type": "object"},
      "ip_address": {"type": "ip"},
      "user_agent": {"type": "text"},
      "success": {"type": "boolean"}
    }
  }
}
```

## Data Partitioning Strategy

### Horizontal Partitioning
- **inference_requests**: Partitioned by month for efficient querying and archival
- **usage_records**: Partitioned by date for billing calculations
- **request_analytics**: Partitioned by month in ClickHouse for analytics queries

### Geographic Partitioning
- **model_artifacts**: Replicated across regions for low-latency access
- **user_data**: Stored in user's home region for compliance
- **analytics_data**: Aggregated globally but stored regionally

### Sharding Strategy
- **Redis clusters**: Sharded by API key hash for rate limiting
- **Model serving**: Sharded by model ID for load distribution
- **Analytics**: Sharded by organization ID for tenant isolation

## Backup and Recovery

### PostgreSQL
- **Continuous Backup**: WAL archiving with point-in-time recovery
- **Cross-region Replication**: Async replication to 2 regions
- **Backup Frequency**: Full backup daily, incremental every 4 hours
- **Retention**: 30-day backup retention for compliance

### Redis
- **Persistence**: RDB snapshots every 15 minutes + AOF
- **Replication**: Master-slave with automatic failover
- **Backup**: Daily snapshots to object storage
- **Recovery**: < 5 minutes recovery time objective

### ClickHouse
- **Replication**: Multi-master replication across datacenters
- **Backup**: Daily full backups + incremental backups
- **Retention**: 1-year retention for analytics data
- **Archival**: Cold storage for historical data

### Object Storage
- **Replication**: 3-way replication across availability zones
- **Cross-region**: Critical model artifacts replicated to 3 regions
- **Versioning**: Enabled for all model artifacts
- **Lifecycle**: Automated archival to cheaper storage tiers

This database schema supports the LLM serving platform's requirements for high availability, scalability, and comprehensive analytics while maintaining data consistency and security across all components.