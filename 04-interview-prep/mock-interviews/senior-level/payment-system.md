# Mock Interview: Global Payment System (Senior Level)

**Duration**: 75 minutes  
**Difficulty**: Advanced  
**Target Experience**: 5+ years  

## Interview Setup

### Interviewer Instructions
- Expect deep technical knowledge
- Challenge architectural decisions
- Focus on reliability, consistency, and compliance
- Discuss complex trade-offs and edge cases

### Candidate Preparation
- Review ACID properties and distributed transactions
- Understand financial regulations and compliance
- Know about fraud detection and security
- Practice explaining complex distributed systems

---

## Interview Script

### Opening (5 minutes)

**Interviewer**: "Today we'll design a global payment processing system like PayPal or Stripe. The system needs to handle millions of transactions per day across multiple countries, currencies, and payment methods. It must ensure strong consistency, comply with financial regulations, and provide real-time fraud detection. What questions do you have about the requirements?"

**Expected Candidate Response**: Should ask detailed questions about scale, compliance, and technical constraints.

### Requirements Clarification (12 minutes)

**Interviewer**: "Let's define the comprehensive requirements."

**Functional Requirements**:
- Process payments between users, merchants, and banks
- Support multiple payment methods (cards, bank transfers, digital wallets)
- Handle multiple currencies with real-time exchange rates
- Provide real-time transaction status and notifications
- Support refunds, chargebacks, and dispute resolution
- Merchant onboarding and KYC (Know Your Customer)
- Transaction history and reporting
- Fraud detection and prevention

**Non-Functional Requirements**:
- 10 million transactions per day globally
- 99.99% availability (52 minutes downtime per year)
- Strong consistency for financial data
- PCI DSS compliance for card data
- GDPR compliance for user data
- Sub-second transaction processing
- Support for 50+ countries and 20+ currencies
- Real-time fraud detection (< 100ms)

**Regulatory Requirements**:
- Anti-Money Laundering (AML) compliance
- Know Your Customer (KYC) verification
- Transaction reporting to financial authorities
- Data residency requirements per country
- Audit trails for all financial operations

**Interviewer Probing Questions**:
- "How do we ensure transactions are never lost or duplicated?"
- "What happens if a payment fails halfway through?"
- "How do we handle different regulatory requirements across countries?"
- "What's our approach to handling sensitive financial data?"

### Capacity Estimation (8 minutes)

**Interviewer**: "Let's estimate the system capacity and constraints."

**Expected Calculations**:
```
Transaction Volume:
- 10M transactions/day = ~115 transactions/second average
- Peak: 5x average = ~575 transactions/second
- Black Friday peak: 10x average = ~1,150 transactions/second

Data Storage:
- Transaction record: ~2KB (including metadata, audit trail)
- Daily storage: 10M × 2KB = 20GB/day
- Annual storage: 20GB × 365 = 7.3TB/year
- 7-year retention (compliance): ~50TB

Financial Flow:
- Average transaction: $50
- Daily volume: 10M × $50 = $500M/day
- Annual volume: ~$180B/year

Fraud Detection:
- 1% fraud rate = 100,000 fraudulent attempts/day
- Real-time analysis required for all transactions
- Machine learning model inference: < 100ms

Compliance Reporting:
- Daily transaction reports to regulators
- Real-time suspicious activity monitoring
- Audit trail storage and retrieval
```

**Interviewer**: "What are the key constraints this creates for our system design?"

### High-Level Architecture (25 minutes)

**Interviewer**: "Design the system architecture, focusing on reliability and consistency."

**Expected Architecture Evolution**:

#### Core Payment Flow:
```
[Client] → [API Gateway] → [Payment Service] → [Transaction Engine]
                                ↓                      ↓
                        [Fraud Detection] → [Risk Assessment]
                                ↓                      ↓
                        [Payment Processor] → [Bank/Card Network]
                                ↓                      ↓
                        [Settlement Service] → [Ledger Service]
```

#### Comprehensive Architecture:
```
                    [CDN/WAF] → [Load Balancer]
                                      ↓
                              [API Gateway] (Rate Limiting, Auth)
                                      ↓
    ┌─────────────────────────────────┼─────────────────────────────────┐
    │                                 ↓                                 │
    │  [Payment Service] ← [Fraud Detection Service] → [ML Models]      │
    │         ↓                       ↓                                 │
    │  [Transaction Engine] ← [Risk Assessment] → [Rules Engine]        │
    │         ↓                       ↓                                 │
    │  [Payment Processor] → [Routing Service] → [Bank Connectors]      │
    │         ↓                       ↓                                 │
    │  [Settlement Service] ← [Reconciliation] → [External Systems]     │
    │         ↓                       ↓                                 │
    │  [Ledger Service] ← [Audit Service] → [Compliance Reporting]      │
    └─────────────────────────────────┼─────────────────────────────────┘
                                      ↓
            [Message Queue] → [Notification Service] → [Webhooks]
                    ↓                 ↓                      ↓
            [Database Cluster] [Cache Layer] [Data Warehouse]
```

**Key Components Deep Dive**:

#### 1. Payment Service
**Interviewer**: "How would you design the payment processing flow?"

**Expected Response**:
```python
class PaymentProcessor:
    def process_payment(self, payment_request):
        # 1. Validate request
        validation_result = self.validate_payment(payment_request)
        if not validation_result.valid:
            return PaymentResult.failed(validation_result.errors)
        
        # 2. Fraud detection (real-time)
        fraud_score = self.fraud_detector.analyze(payment_request)
        if fraud_score > FRAUD_THRESHOLD:
            return PaymentResult.blocked("High fraud risk")
        
        # 3. Reserve funds (pre-authorization)
        reservation = self.reserve_funds(payment_request)
        if not reservation.success:
            return PaymentResult.failed("Insufficient funds")
        
        # 4. Process with payment provider
        try:
            provider_result = self.payment_provider.charge(payment_request)
            if provider_result.success:
                # 5. Update ledger (ACID transaction)
                self.ledger.record_transaction(payment_request, provider_result)
                # 6. Trigger settlement
                self.settlement_service.schedule_settlement(provider_result)
                return PaymentResult.success(provider_result.transaction_id)
            else:
                # Release reservation
                self.release_funds(reservation)
                return PaymentResult.failed(provider_result.error)
        except Exception as e:
            # Compensating transaction
            self.release_funds(reservation)
            self.audit_service.log_error(payment_request, e)
            raise
```

#### 2. Transaction Engine & ACID Compliance
**Interviewer**: "How do you ensure ACID properties in a distributed system?"

**Expected Discussion**:
- **Atomicity**: Use distributed transactions (2PC) or Saga pattern
- **Consistency**: Database constraints and application-level validation
- **Isolation**: Appropriate isolation levels, optimistic/pessimistic locking
- **Durability**: Replicated storage, write-ahead logging

**Saga Pattern Implementation**:
```python
class PaymentSaga:
    def execute_payment(self, payment_request):
        saga_id = generate_saga_id()
        
        try:
            # Step 1: Reserve funds
            reservation_id = self.reserve_funds(payment_request)
            self.saga_log.record_step(saga_id, "reserve_funds", reservation_id)
            
            # Step 2: Charge payment method
            charge_id = self.charge_payment_method(payment_request)
            self.saga_log.record_step(saga_id, "charge_payment", charge_id)
            
            # Step 3: Update ledger
            ledger_entry = self.update_ledger(payment_request, charge_id)
            self.saga_log.record_step(saga_id, "update_ledger", ledger_entry)
            
            # Step 4: Release reservation
            self.release_reservation(reservation_id)
            self.saga_log.complete_saga(saga_id)
            
            return PaymentResult.success(charge_id)
            
        except Exception as e:
            # Compensating actions
            self.compensate_saga(saga_id)
            return PaymentResult.failed(str(e))
    
    def compensate_saga(self, saga_id):
        steps = self.saga_log.get_completed_steps(saga_id)
        for step in reversed(steps):
            if step.action == "update_ledger":
                self.reverse_ledger_entry(step.data)
            elif step.action == "charge_payment":
                self.refund_charge(step.data)
            elif step.action == "reserve_funds":
                self.release_reservation(step.data)
```

#### 3. Fraud Detection System
**Interviewer**: "How would you implement real-time fraud detection?"

**Expected Architecture**:
```
[Transaction] → [Feature Extraction] → [ML Model] → [Risk Score]
                        ↓                   ↓             ↓
                [Historical Data] → [Model Training] → [Rules Engine]
                        ↓                   ↓             ↓
                [Feedback Loop] ← [Manual Review] ← [Decision]
```

**Real-time Processing**:
```python
class FraudDetector:
    def __init__(self):
        self.ml_model = load_fraud_model()
        self.rules_engine = RulesEngine()
        self.feature_store = FeatureStore()
    
    def analyze_transaction(self, transaction):
        # Extract features in real-time
        features = self.extract_features(transaction)
        
        # Get historical features
        user_features = self.feature_store.get_user_features(transaction.user_id)
        merchant_features = self.feature_store.get_merchant_features(transaction.merchant_id)
        
        # Combine features
        all_features = {**features, **user_features, **merchant_features}
        
        # ML model prediction
        ml_score = self.ml_model.predict_proba(all_features)
        
        # Rules-based checks
        rules_score = self.rules_engine.evaluate(transaction, all_features)
        
        # Combine scores
        final_score = self.combine_scores(ml_score, rules_score)
        
        # Update feature store asynchronously
        self.feature_store.update_async(transaction, all_features)
        
        return FraudScore(
            score=final_score,
            ml_score=ml_score,
            rules_score=rules_score,
            features=all_features
        )
```

### Database Design and Consistency (10 minutes)

**Interviewer**: "How would you design the database schema for strong consistency?"

**Expected Schema Design**:
```sql
-- Accounts table
CREATE TABLE accounts (
    account_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    available_balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 1, -- Optimistic locking
    CONSTRAINT positive_balance CHECK (balance >= 0),
    CONSTRAINT available_le_balance CHECK (available_balance <= balance)
);

-- Transactions table (immutable)
CREATE TABLE transactions (
    transaction_id UUID PRIMARY KEY,
    from_account_id UUID,
    to_account_id UUID,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    external_reference VARCHAR(100),
    metadata JSONB,
    CONSTRAINT positive_amount CHECK (amount > 0)
);

-- Ledger entries (double-entry bookkeeping)
CREATE TABLE ledger_entries (
    entry_id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(transaction_id),
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    amount DECIMAL(15,2) NOT NULL, -- Positive for credit, negative for debit
    entry_type VARCHAR(10) NOT NULL, -- 'DEBIT' or 'CREDIT'
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT debit_negative CHECK (
        (entry_type = 'DEBIT' AND amount < 0) OR 
        (entry_type = 'CREDIT' AND amount > 0)
    )
);

-- Ensure double-entry bookkeeping
CREATE OR REPLACE FUNCTION validate_transaction_balance()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT SUM(amount) FROM ledger_entries WHERE transaction_id = NEW.transaction_id) != 0 THEN
        RAISE EXCEPTION 'Transaction does not balance: %', NEW.transaction_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ensure_balanced_transaction
    AFTER INSERT OR UPDATE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION validate_transaction_balance();
```

**Consistency Strategies**:
- **Optimistic Locking**: Version numbers for account updates
- **Database Constraints**: Ensure data integrity at DB level
- **Idempotency**: Unique transaction IDs prevent duplicates
- **Audit Trail**: Immutable transaction records

### Security and Compliance (8 minutes)

**Interviewer**: "How would you handle security and regulatory compliance?"

**Expected Security Measures**:

#### Data Protection:
```python
class SecurityService:
    def encrypt_sensitive_data(self, data, data_type):
        if data_type == "PAN":  # Primary Account Number
            # Use format-preserving encryption for card numbers
            return self.fpe_encrypt(data, self.get_key("PAN"))
        elif data_type == "PII":
            # Use AES for personal information
            return self.aes_encrypt(data, self.get_key("PII"))
        
    def tokenize_card_number(self, pan):
        # Replace PAN with non-sensitive token
        token = self.generate_token()
        self.token_vault.store(token, self.encrypt_sensitive_data(pan, "PAN"))
        return token
    
    def audit_access(self, user_id, resource, action):
        audit_entry = {
            "timestamp": datetime.utcnow(),
            "user_id": user_id,
            "resource": resource,
            "action": action,
            "ip_address": self.get_client_ip(),
            "user_agent": self.get_user_agent()
        }
        self.audit_log.write(audit_entry)
```

#### Compliance Framework:
- **PCI DSS**: Tokenization, encryption, network segmentation
- **GDPR**: Data minimization, right to erasure, consent management
- **AML/KYC**: Identity verification, transaction monitoring
- **SOX**: Financial reporting controls, audit trails

### Scaling and Global Distribution (5 minutes)

**Interviewer**: "How would you scale this system globally?"

**Expected Scaling Strategy**:

#### Geographic Distribution:
```
Region: US-East
├── Payment Processing
├── Fraud Detection
├── Primary Database
└── Compliance Reporting

Region: EU-West
├── Payment Processing (GDPR compliant)
├── Fraud Detection
├── Database Replica
└── Local Compliance

Region: Asia-Pacific
├── Payment Processing
├── Fraud Detection
├── Database Replica
└── Local Compliance
```

#### Data Consistency Across Regions:
- **Master-Slave Replication**: For read scaling
- **Event Sourcing**: Replicate events across regions
- **CQRS**: Separate read/write models
- **Eventual Consistency**: For non-critical data

### Failure Scenarios and Recovery (2 minutes)

**Interviewer**: "How would you handle various failure scenarios?"

**Expected Failure Handling**:
- **Database Failure**: Automatic failover to replica
- **Payment Provider Outage**: Route to backup providers
- **Network Partition**: Graceful degradation, queue transactions
- **Data Center Failure**: Cross-region failover
- **Fraud Detection Failure**: Fallback to rules-based system

---

## Evaluation Criteria

### Technical Expertise (60 points)

**Excellent (54-60 points)**:
- Deep understanding of distributed transactions
- Comprehensive security and compliance knowledge
- Sophisticated fraud detection design
- Advanced database design with consistency guarantees
- Complex failure handling strategies

**Good (42-53 points)**:
- Good understanding of financial systems
- Basic security and compliance awareness
- Reasonable fraud detection approach
- Solid database design
- Some failure handling consideration

**Needs Improvement (30-41 points)**:
- Limited distributed systems knowledge
- Poor security understanding
- Weak fraud detection design
- Basic database design
- No failure handling

**Poor (0-29 points)**:
- No understanding of financial system requirements
- No security consideration
- Inappropriate architecture
- Major technical gaps

### System Design and Architecture (25 points)

**Excellent (23-25 points)**:
- Sophisticated multi-region architecture
- Appropriate service decomposition
- Handles complex requirements elegantly
- Considers multiple architectural patterns

**Good (18-22 points)**:
- Reasonable architecture design
- Most services identified correctly
- Handles most requirements
- Some architectural pattern usage

**Needs Improvement (13-17 points)**:
- Basic architecture
- Missing key services
- Doesn't handle complex requirements
- Limited architectural thinking

**Poor (0-12 points)**:
- Inappropriate architecture
- Major design flaws
- Cannot handle requirements
- No architectural understanding

### Communication and Leadership (15 points)

**Excellent (14-15 points)**:
- Clear explanation of complex concepts
- Demonstrates technical leadership thinking
- Handles challenging questions confidently
- Shows business understanding

**Good (11-13 points)**:
- Generally clear communication
- Some leadership perspective
- Handles most questions well
- Basic business awareness

**Needs Improvement (8-10 points)**:
- Unclear explanations
- Limited leadership thinking
- Struggles with questions
- No business context

**Poor (0-7 points)**:
- Very poor communication
- No leadership perspective
- Cannot handle questions
- No business understanding

---

## Follow-up Questions

### Advanced Technical:
- "How would you implement cross-border payments with currency conversion?"
- "How would you handle regulatory reporting across different jurisdictions?"
- "What's your strategy for zero-downtime database migrations?"
- "How would you implement real-time risk scoring with sub-100ms latency?"

### Architecture and Scale:
- "How would you migrate from monolith to microservices for a live payment system?"
- "What's your approach to handling 10x traffic during Black Friday?"
- "How would you implement global transaction consistency?"
- "What's your disaster recovery strategy for financial data?"

### Business and Compliance:
- "How would you handle new regulatory requirements in different countries?"
- "What's your approach to managing technical debt in a financial system?"
- "How would you balance innovation with regulatory compliance?"
- "What metrics would you use to measure system health and business impact?"

---

## Interviewer Debrief Notes

### Senior-Level Expectations:
- [ ] Demonstrates deep technical expertise in distributed systems
- [ ] Shows understanding of financial regulations and compliance
- [ ] Designs sophisticated fraud detection and security measures
- [ ] Handles complex failure scenarios and recovery strategies
- [ ] Exhibits technical leadership and business acumen
- [ ] Communicates complex concepts clearly to technical and business stakeholders

### Red Flags for Senior Role:
- [ ] Lacks understanding of financial system requirements
- [ ] Poor grasp of distributed systems and consistency models
- [ ] No consideration of security and compliance
- [ ] Cannot handle complex technical questions
- [ ] Poor communication of technical concepts
- [ ] No business or regulatory awareness

### Overall Assessment:
- **Excellent**: 90+ points, ready for senior/staff engineer roles in fintech
- **Good**: 75-89 points, solid senior engineer with growth potential
- **Adequate**: 60-74 points, needs improvement for senior fintech roles
- **Insufficient**: <60 points, not ready for senior-level system design roles