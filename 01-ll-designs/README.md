# Low-Level Design (C++) examples

Each folder contains:
- `design.puml` — PlantUML source for class/sequence diagrams.
- `src/` — C++ source files (small, runnable examples).
- `README.md` — design decisions and instructions.

## Build & Run
Examples are single-file and build with a modern compiler:

```bash
# LRU Cache
cd 01-ll-designs/lru_cache/src
g++ -std=c++17 lru_cache.cpp -o lru && ./lru

# URL Shortener
cd ../../url_shortener/src
g++ -std=c++17 url_shortener.cpp -o url && ./url

# Rate Limiter (Token Bucket)
cd ../../rate_limiter/src
g++ -std=c++17 token_bucket.cpp -o token && ./token
```

If `bits/stdc++.h` is unavailable, replace with explicit headers (iostream, vector, unordered_map, etc.).

## Diagrams
Open `design.puml` with a PlantUML viewer or run:

```bash
plantuml 01-ll-designs/*/design.puml
```
