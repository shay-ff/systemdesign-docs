# systemdesign-docs — A practical system design learning roadmap

This repository is a curated, student-friendly for learning system design from first principles to advanced architectures. It combines short theory notes, hands-on low-level examples (C++), and interview-focused practice — all organized as a guided learning path.

Who this is for:
- Students or early-career engineers who want a practical, interview-ready grasp of system design.
- Developers who prefer running small LLD examples (C++) while understanding HLD trade-offs.

What you'll find here:
- 00-foundations/ — concise core concepts, glossary, cheat-sheet and FAQs.
- 01-ll-designs/ — implementable low-level systems (LRU cache, rate limiter, URL shortener, parking lot) with PlantUML + C++ examples.
- 02-hl-designs/ — end-to-end high-level designs (Twitter clone example) with requirements and trade-offs.
- 03-implementations/ — runnable microservice prototypes demonstrating key concepts.
- 04-interview-prep/ — most-asked questions, checklist and worked solutions.
- 05-study-plan/ — weekly roadmap, milestones and flashcards (JSON) for spaced repetition.
- assets/ — diagrams and exported images used by the docs.

Quick getting started
1. Start in `00-foundations/` — read `README.md` and `concepts.md` to get the mental model.
2. Try a small LLD example from `01-ll-designs/` (compile and run the C++ demo).
3. Study the HLD templates in `02-hl-designs/`, then practice interview prompts in `04-interview-prep/`.

How to run the examples (short)
- LRU cache example:
  - `cd 01-ll-designs/lru_cache/src`
  - `g++ -std=c++17 lru_cache.cpp -O2 -o lru && ./lru`
- Token bucket rate limiter:
  - `cd 01-ll-designs/rate_limiter/src`
  - `g++ -std=c++17 token_bucket.cpp -O2 -o token && ./token`

Rendering diagrams
- Use the PlantUML VS Code extension or run the PlantUML CLI to render `.puml` files. Diagrams are referenced from `/assets/` and `assets/diagrams/`.

Contributing (short)
- Contributions are welcome. See `CONTRIBUTING.md` for guidelines. Keep changes small, explain the learning goal, and include a short example or test where relevant.

License & credits
- This project is licensed under the MIT License (see `LICENSE`).
- Many small code examples were written for education; diagrams use PlantUML and images in `assets/`.

Drop a star if you liked this repo!
