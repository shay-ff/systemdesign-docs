# Foundations — core ideas for system design

> 🧭 **Navigation**: [← Back to Main](../README.md) | [📍 Full Navigation](../NAVIGATION.md) | [Next: Low-Level Designs →](../01-ll-designs/)

This folder contains short, focused notes that form the mental model you should carry into any system design discussion.

## 📚 What's Inside

- **[`concepts.md`](concepts.md)** — the essential HLD and LLD concepts explained with short analogies and diagrams
- **[`scalability.md`](scalability.md)** — horizontal vs vertical scaling strategies and patterns
- **[`load-balancing.md`](load-balancing.md)** — load balancer types, algorithms, and trade-offs
- **[`caching-strategies.md`](caching-strategies.md)** — cache patterns, invalidation, and consistency
- **[`database-basics.md`](database-basics.md)** — SQL vs NoSQL, ACID vs BASE, and selection criteria
- **[`consistency-vs-availability.md`](consistency-vs-availability.md)** — CAP theorem deep dive with practical examples
- **[`cap-theorem.md`](cap-theorem.md)** — practical CAP theorem applications in real systems
- **[`design-patterns.md`](design-patterns.md)** — common architectural and reliability patterns with when-to-use guidance
- **[`cheatsheet.md`](cheatsheet.md)** — quick rules of thumb and back-of-the-envelope formulas
- **[`glossary.md`](glossary.md)** — concise definitions you can memorize for interviews
- **[`faqs.md`](faqs.md)** — pragmatic answers to questions students frequently ask

## 🚀 Getting Started

1. **Start Here**: Read [`concepts.md`](concepts.md) first to build your mental model
2. **Build Knowledge**: Work through the core topics in this order:
   - [Scalability](scalability.md) → [Load Balancing](load-balancing.md) → [Caching](caching-strategies.md)
   - [Database Basics](database-basics.md) → [CAP Theorem](cap-theorem.md) → [Consistency vs Availability](consistency-vs-availability.md)
3. **Quick Reference**: Use [`cheatsheet.md`](cheatsheet.md) when solving sizing or trade-off questions
4. **Definitions**: Check [`glossary.md`](glossary.md) for any unfamiliar terms

## 💡 Learning Tips

- **Small Exercise**: Pick any two glossary terms and explain them to a friend in one sentence each
- **Visual Learning**: Each concept includes diagrams and real-world analogies
- **Interview Prep**: Focus on [`cheatsheet.md`](cheatsheet.md) and [`glossary.md`](glossary.md) for quick review

## 🎯 Next Steps

After completing the foundations:

- **Hands-On Learners**: Try building an [LRU Cache](../01-ll-designs/lru_cache/README.md) to see concepts in action
- **Architecture Focus**: Explore the [Twitter Clone](../02-hl-designs/twitter_clone/README.md) system design
- **Interview Prep**: Jump to [Interview Frameworks](../04-interview-prep/frameworks.md)
- **Structured Study**: Follow the [6-Week Study Plan](../05-study-plan/study_plan.md)

---

*💡 **Pro Tip**: Keep this section bookmarked as your go-to reference while working through system designs!*