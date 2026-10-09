# Technical Evaluation Rubric for System Design Interviews

This rubric provides standardized criteria for evaluating technical performance in system design interviews across different experience levels.

## Overall Scoring Framework

**Total Points**: 100  
**Passing Threshold**: 
- Junior Level: 60+ points
- Mid Level: 70+ points  
- Senior Level: 80+ points

---

## Core Technical Areas (70 points total)

### 1. System Architecture Design (25 points)

#### Excellent (23-25 points)
- **Component Identification**: Correctly identifies all major system components and their responsibilities
- **Service Boundaries**: Appropriate decomposition with clear service boundaries and interfaces
- **Data Flow**: Clear understanding of how data flows through the system
- **Scalability**: Architecture naturally supports horizontal and vertical scaling
- **Flexibility**: Design accommodates future requirements and changes
- **Industry Patterns**: Correctly applies established architectural patterns (microservices, event-driven, etc.)

#### Good (18-22 points)
- **Component Identification**: Identifies most major components with minor gaps
- **Service Boundaries**: Generally appropriate decomposition with some unclear boundaries
- **Data Flow**: Understands most data flow paths with some confusion
- **Scalability**: Architecture supports basic scaling with some limitations
- **Flexibility**: Design handles some future changes but may require significant modifications
- **Industry Patterns**: Uses some architectural patterns appropriately

#### Needs Improvement (13-17 points)
- **Component Identification**: Identifies basic components but misses several important ones
- **Service Boundaries**: Poor service decomposition with unclear responsibilities
- **Data Flow**: Limited understanding of data flow through system
- **Scalability**: Architecture has significant scaling limitations
- **Flexibility**: Design is rigid and difficult to modify
- **Industry Patterns**: Limited or inappropriate use of architectural patterns

#### Poor (0-12 points)
- **Component Identification**: Fails to identify key system components
- **Service Boundaries**: No clear service decomposition
- **Data Flow**: No understanding of data flow
- **Scalability**: Architecture cannot scale
- **Flexibility**: Design is completely inflexible
- **Industry Patterns**: No use of established patterns

### 2. Database and Storage Design (20 points)

#### Excellent (18-20 points)
- **Schema Design**: Well-normalized schema with appropriate relationships and constraints
- **Technology Choice**: Optimal database technology selection (SQL vs NoSQL) based on requirements
- **Indexing Strategy**: Comprehensive indexing strategy for query optimization
- **Consistency Model**: Appropriate consistency model (ACID vs BASE) for use case
- **Partitioning/Sharding**: Effective data partitioning strategy for scale
- **Backup/Recovery**: Considers data durability and disaster recovery

#### Good (14-17 points)
- **Schema Design**: Reasonable schema with minor normalization issues
- **Technology Choice**: Appropriate database selection with basic justification
- **Indexing Strategy**: Basic indexing considerations
- **Consistency Model**: Understands consistency trade-offs
- **Partitioning/Sharding**: Basic partitioning strategy
- **Backup/Recovery**: Some consideration of data durability

#### Needs Improvement (10-13 points)
- **Schema Design**: Poor schema design with significant issues
- **Technology Choice**: Inappropriate database selection or poor justification
- **Indexing Strategy**: Limited indexing considerations
- **Consistency Model**: Confused about consistency requirements
- **Partitioning/Sharding**: No partitioning strategy
- **Backup/Recovery**: No consideration of data durability

#### Poor (0-9 points)
- **Schema Design**: Fundamentally flawed schema
- **Technology Choice**: Completely inappropriate database selection
- **Indexing Strategy**: No indexing considerations
- **Consistency Model**: No understanding of consistency
- **Partitioning/Sharding**: No understanding of data distribution
- **Backup/Recovery**: No consideration of data protection

### 3. Scalability and Performance (15 points)

#### Excellent (14-15 points)
- **Horizontal Scaling**: Comprehensive strategy for scaling out services and databases
- **Caching Strategy**: Multi-level caching with appropriate cache policies and invalidation
- **Load Balancing**: Sophisticated load balancing with appropriate algorithms
- **Performance Optimization**: Identifies and addresses performance bottlenecks
- **Capacity Planning**: Accurate capacity estimation and growth planning
- **Monitoring**: Comprehensive performance monitoring and alerting strategy

#### Good (11-13 points)
- **Horizontal Scaling**: Basic horizontal scaling strategy
- **Caching Strategy**: Appropriate caching with basic policies
- **Load Balancing**: Basic load balancing implementation
- **Performance Optimization**: Identifies some performance considerations
- **Capacity Planning**: Reasonable capacity estimates
- **Monitoring**: Basic monitoring considerations

#### Needs Improvement (8-10 points)
- **Horizontal Scaling**: Limited scaling strategy
- **Caching Strategy**: Basic caching without clear strategy
- **Load Balancing**: Minimal load balancing consideration
- **Performance Optimization**: Few performance considerations
- **Capacity Planning**: Poor capacity estimates
- **Monitoring**: Limited monitoring awareness

#### Poor (0-7 points)
- **Horizontal Scaling**: No scaling strategy
- **Caching Strategy**: No caching considerations
- **Load Balancing**: No load balancing
- **Performance Optimization**: No performance considerations
- **Capacity Planning**: No capacity planning
- **Monitoring**: No monitoring considerations

### 4. Reliability and Fault Tolerance (10 points)

#### Excellent (9-10 points)
- **Failure Modes**: Comprehensive analysis of potential failure scenarios
- **Redundancy**: Appropriate redundancy and replication strategies
- **Circuit Breakers**: Implements circuit breaker and bulkhead patterns
- **Graceful Degradation**: System degrades gracefully under failure conditions
- **Recovery Strategies**: Clear disaster recovery and business continuity plans
- **Testing**: Chaos engineering and failure testing strategies

#### Good (7-8 points)
- **Failure Modes**: Identifies major failure scenarios
- **Redundancy**: Basic redundancy implementation
- **Circuit Breakers**: Some resilience patterns
- **Graceful Degradation**: Basic degradation strategies
- **Recovery Strategies**: Basic recovery planning
- **Testing**: Some failure testing considerations

#### Needs Improvement (5-6 points)
- **Failure Modes**: Limited failure analysis
- **Redundancy**: Minimal redundancy
- **Circuit Breakers**: Few resilience patterns
- **Graceful Degradation**: Poor degradation handling
- **Recovery Strategies**: Limited recovery planning
- **Testing**: No failure testing

#### Poor (0-4 points)
- **Failure Modes**: No failure analysis
- **Redundancy**: No redundancy considerations
- **Circuit Breakers**: No resilience patterns
- **Graceful Degradation**: No degradation strategy
- **Recovery Strategies**: No recovery planning
- **Testing**: No testing considerations

---

## Problem-Solving Process (20 points total)

### 5. Requirements Analysis (10 points)

#### Excellent (9-10 points)
- **Clarifying Questions**: Asks comprehensive, insightful questions about functional and non-functional requirements
- **Assumption Validation**: Validates assumptions and identifies edge cases
- **Prioritization**: Effectively prioritizes features and requirements
- **Scope Management**: Clearly defines what's in and out of scope
- **Stakeholder Consideration**: Considers different user types and their needs

#### Good (7-8 points)
- **Clarifying Questions**: Asks relevant questions about most requirements
- **Assumption Validation**: Validates some assumptions
- **Prioritization**: Basic feature prioritization
- **Scope Management**: Generally clear scope definition
- **Stakeholder Consideration**: Considers primary users

#### Needs Improvement (5-6 points)
- **Clarifying Questions**: Asks few clarifying questions
- **Assumption Validation**: Limited assumption validation
- **Prioritization**: Poor prioritization
- **Scope Management**: Unclear scope boundaries
- **Stakeholder Consideration**: Limited user consideration

#### Poor (0-4 points)
- **Clarifying Questions**: No clarifying questions
- **Assumption Validation**: No assumption validation
- **Prioritization**: No prioritization
- **Scope Management**: No scope definition
- **Stakeholder Consideration**: No user consideration

### 6. Systematic Approach (10 points)

#### Excellent (9-10 points)
- **Structured Methodology**: Follows systematic approach (like SCALE framework)
- **Incremental Building**: Builds solution incrementally from simple to complex
- **Time Management**: Efficiently manages time across different phases
- **Iteration**: Iterates and refines solution based on feedback
- **Documentation**: Creates clear diagrams and documentation

#### Good (7-8 points)
- **Structured Methodology**: Generally systematic approach
- **Incremental Building**: Mostly incremental building
- **Time Management**: Reasonable time management
- **Iteration**: Some iteration and refinement
- **Documentation**: Basic diagrams and notes

#### Needs Improvement (5-6 points)
- **Structured Methodology**: Somewhat disorganized approach
- **Incremental Building**: Limited incremental building
- **Time Management**: Poor time management
- **Iteration**: Little iteration
- **Documentation**: Poor documentation

#### Poor (0-4 points)
- **Structured Methodology**: No systematic approach
- **Incremental Building**: No incremental building
- **Time Management**: Very poor time management
- **Iteration**: No iteration
- **Documentation**: No documentation

---

## Communication Skills (10 points total)

### 7. Technical Communication (10 points)

#### Excellent (9-10 points)
- **Clarity**: Explains complex technical concepts clearly and concisely
- **Visual Communication**: Uses diagrams effectively to illustrate architecture
- **Audience Awareness**: Adapts communication style to audience level
- **Engagement**: Actively engages with interviewer and responds to feedback
- **Confidence**: Demonstrates confidence while remaining open to suggestions

#### Good (7-8 points)
- **Clarity**: Generally clear explanations with minor confusion
- **Visual Communication**: Uses some diagrams effectively
- **Audience Awareness**: Generally appropriate communication level
- **Engagement**: Some engagement with interviewer
- **Confidence**: Reasonably confident presentation

#### Needs Improvement (5-6 points)
- **Clarity**: Unclear explanations of technical concepts
- **Visual Communication**: Limited or poor use of diagrams
- **Audience Awareness**: Inappropriate communication level
- **Engagement**: Limited engagement
- **Confidence**: Low confidence or overly defensive

#### Poor (0-4 points)
- **Clarity**: Very unclear or confusing explanations
- **Visual Communication**: No use of visual aids
- **Audience Awareness**: No audience consideration
- **Engagement**: No engagement
- **Confidence**: Very low confidence or arrogant

---

## Experience-Level Specific Criteria

### Junior Level Additional Considerations
- **Learning Attitude**: Shows willingness to learn and accept feedback
- **Basic Concepts**: Demonstrates understanding of fundamental concepts
- **Practical Application**: Can apply theoretical knowledge to practical problems
- **Growth Potential**: Shows potential for growth and development

### Mid Level Additional Considerations
- **Trade-off Analysis**: Effectively analyzes and communicates trade-offs
- **Technology Breadth**: Demonstrates knowledge across multiple technologies
- **Best Practices**: Applies industry best practices and patterns
- **Mentoring Potential**: Shows ability to guide and teach others

### Senior Level Additional Considerations
- **Strategic Thinking**: Demonstrates strategic and long-term thinking
- **Business Acumen**: Understands business implications of technical decisions
- **Leadership**: Shows technical leadership and decision-making skills
- **Innovation**: Proposes innovative solutions and approaches
- **Risk Management**: Identifies and mitigates technical and business risks

---

## Common Red Flags

### Technical Red Flags
- Suggests inappropriate technologies for the scale or requirements
- Designs systems that cannot handle the stated requirements
- Ignores critical non-functional requirements (security, performance, reliability)
- Makes fundamental errors in distributed systems concepts
- Cannot explain the reasoning behind technical decisions

### Process Red Flags
- Jumps to solution without understanding requirements
- Cannot manage time effectively during the interview
- Refuses to accept feedback or consider alternative approaches
- Gets stuck on minor details while ignoring major architectural decisions
- Cannot adapt when requirements change or new constraints are introduced

### Communication Red Flags
- Cannot explain technical concepts clearly
- Becomes defensive when challenged on design decisions
- Fails to engage with the interviewer or ask clarifying questions
- Uses inappropriate technical jargon without explanation
- Shows arrogance or dismissiveness toward feedback

---

## Calibration Guidelines

### Scoring Consistency
- **Compare Similar Candidates**: Use previous interviews at the same level as benchmarks
- **Focus on Relative Performance**: Compare against expectations for the experience level
- **Document Specific Examples**: Note specific examples of strong or weak performance
- **Consider Context**: Account for nervousness, unfamiliar problem domains, etc.
- **Avoid Bias**: Focus on technical merit rather than personal preferences

### Decision Framework
- **Strong Hire**: Exceeds expectations, would be valuable team member immediately
- **Hire**: Meets expectations, would be successful in the role with normal onboarding
- **No Hire**: Does not meet minimum bar for the role
- **Borderline**: Close decision, may benefit from additional evaluation

### Feedback Quality
- **Specific Examples**: Provide concrete examples of strengths and weaknesses
- **Actionable Advice**: Give specific recommendations for improvement
- **Balanced Perspective**: Highlight both strengths and areas for growth
- **Encouraging Tone**: Maintain supportive tone even for negative feedback