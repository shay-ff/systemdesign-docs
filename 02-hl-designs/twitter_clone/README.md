# Twitter Clone — System Design

## Overview

Design a Twitter-like social media platform: post short messages (tweets),
follow other users, and consume personalized timelines at massive scale. The
system serves **500 million registered users** (200M daily active), ingests
**500 million tweets per day** (~6K/sec, 12K peak), and answers **300 million
timeline reads per day** with **sub-200ms latency (p95)** at **99.95%
availability**.

## The One Idea That Matters

"A tweet is just a row insert." The hard part is **timeline generation** —
fan-out a tweet to every follower's feed (write amplification for celebrity
users), keep reads fast, and survive hot spots where a handful of accounts
generate disproportionate load. Almost every design decision below serves
serving personalized feeds cheaply.

## Key Features

- **Tweets** — up to 280 characters, media attachments, threads, quote tweets
- **Social graph** — follow/unfollow, block, mute, suggestions
- **Timelines** — home timeline (following), user timeline, trending topics
- **Engagement** — like, reply, retweet, bookmark
- **Search** — users, tweets, hashtags
- **Notifications** — real-time likes, replies, follows via WebSocket
- **Media** — image/video upload served through a CDN

## System Requirements

### Functional Requirements
- Post, delete, and retrieve tweets with media
- Follow users and generate home/user timelines
- Like, reply, retweet, and quote tweets
- Trending topics and search
- Real-time notifications

### Non-Functional Requirements
- **Scale**: 500M users, 500M tweets/day, 300M timeline reads/day
- **Latency**: timeline reads < 200ms (p95)
- **Availability**: 99.95%
- **Consistency**: timeline eventual consistency is acceptable; tweet
  durability is not

## Architecture Components

- **Clients** — web app, mobile apps, API clients behind a CDN
- **Edge** — global load balancer → regional load balancers → API gateway
  with authentication and rate limiting
- **Services** — User, Tweet, Timeline, Notification, Search, and Media
  services (independently scalable)
- **Data** — PostgreSQL cluster (tweets, users, social graph) and Redis
  cluster (timelines, fan-out buffers, cache)

## Files in this Design

- `requirements.md` — Detailed functional and non-functional requirements with scale estimates
- `architecture.puml` — System architecture diagram
- `api-design.md` — REST + WebSocket API specifications
- `database-schema.md` — Data model, sharding, and indexing strategy
- `scaling-strategy.md` — Fan-out strategies, hot-spot mitigation, caching
- `tradeoffs.md` — Key design decisions and alternatives
- `solution.md` — Complete end-to-end walkthrough

## Next Steps

- Read the [requirements](requirements.md) first, then walk through the
  [solution](solution.md) as you would in an interview.
- For fundamentals, see [Foundations](../../00-foundations/README.md) —
  especially [caching strategies](../../00-foundations/caching-strategies.md)
  and [scalability](../../00-foundations/scalability.md).
