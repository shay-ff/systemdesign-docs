# System Design Primer (C++ focus)

A compact, interview-focused **System Design** repository targeted at:
- Teaching core HLD concepts and introducing LLD.
- Hands-on **C++** low-level design examples.
- Illustrated diagrams (PlantUML sources included).
- Common interview questions with worked solutions.

## Highlights
- Language: **C++** (examples & small demos)
- Diagrams: PlantUML (`.puml`) + exported PNG placeholders
- Study plan, flashcards, and interview checklist

## Structure
- `docs/` — theory, cheat sheets, patterns
- `lld/` — C++ hands-on examples with UML `.puml` and code
- `hld/` — HLD worked examples and templates
- `interview/` — common questions + solutions
- `assets/` — PlantUML sources and exported PNG placeholders
 

## Usage
1. Clone or unzip.
2. Read `docs/concepts.md` then try examples in `lld/`.
3. Render diagrams: install PlantUML (or use an online renderer) and open `.puml` files.

## Quickstart
1. Ensure a modern C++ compiler is installed (e.g., `g++` with `-std=c++17`).
2. Compile and run an example:
   - LRU Cache:
     - `cd lld/lru_cache/src`
     - `g++ -std=c++17 lru_cache.cpp -o lru && ./lru`
   - URL Shortener:
     - `cd lld/url_shortener/src`
     - `g++ -std=c++17 url_shortener.cpp -o url && ./url`
   - Rate Limiter (Token Bucket simulation):
     - `cd lld/rate_limiter/src`
     - `g++ -std=c++17 token_bucket.cpp -o token && ./token`

## Rendering Diagrams (PlantUML)
Options:
- VS Code: install "PlantUML" extension, open `.puml` and preview.
- CLI: install Java + PlantUML jar, then:
  - `plantuml hld/design_twitter_clone/architecture.puml`
  - `plantuml lld/lru_cache/design.puml`
- Web: copy `.puml` into any online PlantUML renderer.

Outputs (`.png`) will be generated alongside sources.

## Suggested Study Path
1. Read `docs/concepts.md` for HLD vs LLD and glossary.
2. Skim `docs/cheatsheet.md` for rules of thumb and sizing.
3. Walk through `lld/` examples (LRU, URL shortener, rate limiter): compile and run.
4. Review patterns in `docs/design-patterns.md` to understand trade-offs.
5. Open `hld/design_twitter_clone/` diagrams and requirements; reason about trade-offs in `tradeoffs.md`.
6. Practice with prompts in `interview/`.

## Notes
- Examples are intentionally small and focused on clarity over completeness.
- Some demos include `#include <bits/stdc++.h>` for brevity; replace with explicit headers if your toolchain lacks it.
