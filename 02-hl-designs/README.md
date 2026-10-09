# High-Level System Designs — Real-World Architectures

> 🧭 **Navigation**: [← Low-Level Designs](../01-ll-designs/README.md) | [📍 Full Navigation](../NAVIGATION.md) | [Next: Implementations →](../03-implementations/README.md)

Master large-scale distributed system design through comprehensive breakdowns of real-world systems. Each design includes requirements analysis, architecture diagrams, API specifications, and scaling strategies.

## 🏛️ System Designs

### Start Here
- **[📄 HLD Quick Reference](hld-quick-reference.md)** — One-pagers for 12+ common systems (typeahead, web crawler, file storage, news feed, proximity, metrics, and more). Breadth here; depth in the folders below.

### Social & Communication
- **[Twitter Clone](twitter_clone/README.md)** — Social media platform with timeline, tweets, and following
- **[Chat System](chat_system/README.md)** — Real-time messaging with presence and group chats

### Content & Media  
- **[Netflix Streaming](netflix_streaming/README.md)** — Video streaming with CDN and recommendations
- **[YouTube](youtube/README.md)** — Video upload, processing, and global distribution

### Marketplace & Services
- **[Uber System](uber_system/README.md)** — Ride-sharing with real-time matching and tracking
- **[Payment Gateway](payment_gateway/README.md)** — Payments with idempotency, double-entry ledger, and reconciliation

### Infrastructure & Tools
- **[URL Shortener](url_shortener/README.md)** — Link shortening service with analytics
- **[LLM Serving Platform](llm_serving_platform/README.md)** — AI model serving with auto-scaling
### Social & Communication
- **[Twitter Clone](twitter_clone/)** — Social media platform with timeline, tweets, and following
- **[Chat System](chat_system/)** — Real-time messaging with presence and group chats *(Coming Soon)*

### Content & Media  
- **[Netflix Streaming](netflix_streaming/)** — Video streaming with CDN and recommendations
- **[YouTube](youtube/)** — Video upload, processing, and global distribution

### Marketplace & Services
- **[Uber System](uber_system/)** — Ride-sharing with real-time matching and tracking
- **[E-commerce Platform](ecommerce/)** — Online marketplace with inventory and payments *(Coming Soon)*

### Infrastructure & Tools
- **[URL Shortener](url_shortener/)** — Link shortening service with analytics
- **[LLM Serving Platform](llm_serving_platform/)** — AI model serving with auto-scaling

## 📋 Design Structure

Each system design includes:

### 📄 **Requirements Analysis**
- **`requirements.md`** — Functional & non-functional requirements with scale estimates
- Clear scope definition and success metrics

### 🏗️ **Architecture Design**  
- **`architecture.puml`** — Component diagrams showing system structure
- **`api-design.md`** — REST API specifications with request/response examples
- **`database-schema.md`** — Data models and relationships

### 📈 **Scaling Strategy**
- **`scaling-strategy.md`** — Horizontal scaling approach and bottleneck analysis
- **`tradeoffs.md`** — Design decisions, alternatives, and justifications

### 🔍 **Complete Solution**
- **`solution.md`** — End-to-end walkthrough with implementation details

## 🚀 How to Use

### For Learning
1. **Start with Requirements**: Read `requirements.md` to understand the problem scope
2. **Visualize Architecture**: Open `architecture.puml` in a PlantUML viewer
3. **Understand APIs**: Review `api-design.md` for interface contracts
4. **Analyze Data**: Study `database-schema.md` for data modeling
5. **Consider Scale**: Read `scaling-strategy.md` for growth planning
6. **Evaluate Choices**: Review `tradeoffs.md` for decision rationale

### For Interviews
1. **Practice Flow**: Follow the requirements → architecture → APIs → data → scaling pattern
2. **Memorize Numbers**: Use scale estimates from requirements documents
3. **Understand Trade-offs**: Be ready to explain design decisions
4. **Draw Diagrams**: Practice sketching architectures from memory

## 🎯 Recommended Learning Path

### Beginner Path
1. **[URL Shortener](url_shortener/README.md)** — Simple service with clear requirements
2. **[Twitter Clone](twitter_clone/README.md)** — Social platform with moderate complexity
3. **[Netflix Streaming](netflix_streaming/README.md)** — Content delivery with global scale

### Advanced Path
1. **[Uber System](uber_system/README.md)** — Real-time systems with complex matching
2. **[YouTube](youtube/README.md)** — Video processing and massive scale
3. **[LLM Serving Platform](llm_serving_platform/README.md)** — AI/ML infrastructure
1. **[URL Shortener](url_shortener/)** — Simple service with clear requirements
2. **[Twitter Clone](twitter_clone/)** — Social platform with moderate complexity
3. **[Netflix Streaming](netflix_streaming/)** — Content delivery with global scale


## 📊 Visual Learning

### Diagram Types
- **Component Diagrams** — System architecture and service boundaries
- **Sequence Diagrams** — Request flow and service interactions  
- **Data Flow Diagrams** — Information movement through the system
- **Deployment Diagrams** — Infrastructure and scaling patterns

### Rendering Diagrams
```bash
# Install PlantUML
npm install -g @plantuml/plantuml

# Render all architecture diagrams
plantuml 02-hl-designs/**/architecture.puml

# Render specific system
plantuml 02-hl-designs/twitter_clone/*.puml
```

## 💡 Design Principles

Each system design demonstrates:
- **Scalability** — Horizontal scaling patterns and bottleneck identification
- **Reliability** — Fault tolerance and disaster recovery strategies  
- **Performance** — Caching, CDNs, and optimization techniques
- **Security** — Authentication, authorization, and data protection
- **Cost Optimization** — Resource efficiency and cost-effective scaling

## 🔗 Integration with Other Sections

- **Foundations**: Apply concepts from [00-foundations/](../00-foundations/README.md) in real systems
- **Components**: Use building blocks from [01-ll-designs/](../01-ll-designs/README.md) 
- **Implementation**: See working examples in [03-implementations/](../03-implementations/README.md)
- **Interview Prep**: Practice with questions in [04-interview-prep/](../04-interview-prep/README.md)

## 🎯 Next Steps

After mastering high-level designs:

- **Build Prototypes**: Try [runnable implementations](../03-implementations/README.md) to see concepts in action
- **Interview Practice**: Use these designs in [mock interviews](../04-interview-prep/mock-interviews/README.md)
- **Structured Study**: Follow the [6-week curriculum](../05-study-plan/study_plan.md)
- **Contribute**: Add new system designs following [contribution guidelines](../CONTRIBUTING.md)

## 📚 External Resources

- **Company Blogs**: Netflix Tech, Uber Engineering, Meta Engineering
- **Papers**: MapReduce, Dynamo, BigTable, Kafka
- **Books**: Designing Data-Intensive Applications, System Design Interview

---

*🏗️ **Pro Tip**: Start by understanding the requirements deeply before jumping into architecture. Great system design begins with clear problem definition!*
