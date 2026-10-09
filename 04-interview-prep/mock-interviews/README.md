# Mock Interview Scenarios

This directory contains realistic system design interview scenarios with timing constraints, interviewer perspectives, and evaluation criteria. Practice with these scenarios to build confidence and improve your interview performance.

## Directory Structure

```
mock-interviews/
├── README.md                    # This file
├── junior-level/               # 0-2 years experience
│   ├── url-shortener.md
│   ├── chat-app.md
│   └── cache-system.md
├── mid-level/                  # 2-5 years experience
│   ├── social-feed.md
│   ├── video-streaming.md
│   └── ride-sharing.md
├── senior-level/               # 5+ years experience
│   ├── payment-system.md
│   ├── search-engine.md
│   └── distributed-database.md
└── evaluation-rubrics/         # Scoring criteria
    ├── technical-rubric.md
    └── communication-rubric.md
```

## How to Use These Scenarios

### For Self-Practice

1. **Set a Timer**: Follow the specified time limits strictly
2. **Record Yourself**: Practice explaining your solution aloud
3. **Draw Diagrams**: Use whiteboard or digital tools
4. **Self-Evaluate**: Use the provided rubrics after each session
5. **Iterate**: Repeat scenarios with different approaches

### For Mock Interviews

1. **Find a Partner**: Ideally someone with system design experience
2. **Assign Roles**: One person plays interviewer, other plays candidate
3. **Follow the Script**: Interviewer should use provided questions and prompts
4. **Time Management**: Stick to the allocated time segments
5. **Provide Feedback**: Use evaluation criteria for constructive feedback

## Difficulty Progression

### Junior Level (45 minutes)

- **Focus**: Basic system design concepts
- **Complexity**: Single service with simple scaling
- **Evaluation**: Understanding of fundamentals, clear communication
- **Common Topics**: Caching, basic databases, simple APIs

### Mid Level (60 minutes)

- **Focus**: Distributed systems and scaling
- **Complexity**: Multiple services with complex interactions
- **Evaluation**: System thinking, trade-off analysis, scaling strategies
- **Common Topics**: Microservices, message queues, CDNs, replication

### Senior Level (75 minutes)

- **Focus**: Complex distributed systems and advanced topics
- **Complexity**: Large-scale systems with multiple constraints
- **Evaluation**: Deep technical knowledge, leadership thinking, innovation
- **Common Topics**: Consensus algorithms, advanced patterns, global scale

## Interview Simulation Guidelines

### For Interviewers

1. **Create Realistic Pressure**: Don't make it too easy
2. **Ask Follow-ups**: Push for deeper understanding
3. **Challenge Assumptions**: Question design decisions
4. **Manage Time**: Keep candidate on track
5. **Take Notes**: Document strengths and areas for improvement

### For Candidates

1. **Think Out Loud**: Verbalize your thought process
2. **Ask Questions**: Clarify requirements before designing
3. **Start Simple**: Begin with basic solution, then scale
4. **Explain Trade-offs**: Discuss pros and cons of decisions
5. **Stay Calm**: Handle pressure and unexpected questions gracefully

## Common Interview Patterns

### Opening (5-10 minutes)

- Brief introductions
- Problem statement
- Requirement clarification
- Scope definition

### Design Phase (25-45 minutes)

- High-level architecture
- Component deep-dives
- API design
- Database schema
- Scaling considerations

### Closing (5-15 minutes)

- Advanced topics
- Alternative approaches
- Questions from candidate
- Wrap-up discussion

## Evaluation Criteria Summary

### Technical Skills (60%)

- **System Design Knowledge**: Understanding of distributed systems concepts
- **Architecture Skills**: Ability to design scalable, maintainable systems
- **Technology Choices**: Appropriate selection of databases, frameworks, tools
- **Problem Solving**: Systematic approach to complex problems

### Communication Skills (25%)

- **Clarity**: Clear explanation of ideas and concepts
- **Structure**: Organized presentation of solution
- **Interaction**: Effective collaboration with interviewer
- **Adaptability**: Handling questions and feedback well

### Process and Methodology (15%)

- **Requirements Gathering**: Asking the right questions
- **Time Management**: Efficient use of interview time
- **Iterative Approach**: Building solution incrementally
- **Trade-off Analysis**: Discussing pros and cons of decisions

## Tips for Success

### Before the Interview

- Review fundamental concepts (CAP theorem, consistency models, etc.)
- Practice drawing system diagrams quickly and clearly
- Prepare questions to ask about requirements and constraints
- Study common system design patterns and when to use them

### During the Interview

- Listen carefully to the problem statement
- Ask clarifying questions before jumping into solution
- Start with simple solution and iterate
- Explain your reasoning for each decision
- Be honest about what you don't know

### After the Interview

- Reflect on what went well and what could be improved
- Research any topics you struggled with
- Practice the same scenario with different approaches
- Get feedback from your mock interviewer

## Common Mistakes to Avoid

1. **Jumping to Solution Too Quickly**: Always clarify requirements first
2. **Over-Engineering**: Start simple, add complexity gradually
3. **Ignoring Non-Functional Requirements**: Consider scale, performance, reliability
4. **Poor Time Management**: Allocate time appropriately across different phases
5. **Not Explaining Trade-offs**: Always discuss pros and cons of decisions
6. **Forgetting About Failures**: Consider what happens when components fail
7. **Not Asking Questions**: Engage with the interviewer throughout

## Practice Schedule Recommendation

### Week 1-2: Fundamentals

- Focus on junior-level scenarios
- Master basic concepts and patterns
- Practice requirement gathering and basic architecture

### Week 3-4: Intermediate Concepts

- Move to mid-level scenarios
- Practice scaling strategies and trade-off analysis
- Work on communication and presentation skills

### Week 5-6: Advanced Topics

- Tackle senior-level scenarios
- Focus on complex distributed systems
- Practice handling challenging follow-up questions

### Ongoing: Continuous Improvement

- Rotate through different difficulty levels
- Try variations of the same problem
- Stay updated with new technologies and patterns

Remember: The goal isn't to memorize solutions, but to develop systematic thinking and communication skills that apply to any system design problem.
