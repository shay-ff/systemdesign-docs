# HLD Examples

Each HLD contains:
- requirements.md (functional & non-functional)
- architecture.puml (component diagram)
- sequence.puml (sequence diagram)
- tradeoffs.md

## How to use
1. Start with `requirements.md` to scope the problem and SLAs.
2. Open `architecture.puml` and `sequence.puml` in a PlantUML viewer.
3. Walk through reads/writes, caching, data model, failure cases.
4. Capture choices in `tradeoffs.md` and justify with expected load.

## Render diagrams
```bash
plantuml hld/**/architecture.puml
plantuml hld/**/sequence.puml
```

## Example: Twitter-like timeline
- Requirements: `hld/design_twitter_clone/requirements.md`
- Diagram: `hld/design_twitter_clone/architecture.puml`
- Trade-offs: `hld/design_twitter_clone/tradeoffs.md`
