# Snake & Ladder — Low Level Design

A classic interview LLD: model a Snake & Ladder game supporting arbitrary board sizes, chained snakes/ladders, pluggable dice behaviour (the "crooked dice" twist), and a pluggable win rule — all without the game engine knowing about any of them.

## Problem Statement

Design a Snake & Ladder game for 2 or more players on an N x N board (10x10 => positions 1..100). Players take turns rolling a six-sided die and moving forward. Landing on a snake's head slides the player down to its tail; landing on a ladder's foot climbs the player to its top. The first player to reach the final cell wins.

## Clarifying Questions to Ask the Interviewer

Asking these up front is half the round — they each change the design:

1. **Board size**: fixed 10x10 or arbitrary N x N? → dimension is a `Board` constructor parameter; nothing in the engine assumes 100.
2. **Numbering**: 1-based positions with players starting at 1? (Yes — 0-based makes "cell 0" a weird start state.)
3. **Overshoot rule**: must the final cell be reached by an *exact* roll (overshoot = no move), or does the player bounce back? → pluggable `WinRule` strategy; we ship both.
4. **Chained jumps**: can a ladder's top be a snake's head (land → climb → immediately slide)? Any cap on chain length? → resolution loop with cycle protection.
5. **Overlapping pieces**: can two snakes/ladders start on the same cell? → no; one consequence per cell, validated at board construction.
6. **Dice behaviour**: is the die fair? What if it is crooked (never rolls 6)? Multiple dice summed? → `Dice` interface + decorator so new behaviours drop in without touching `Game`.
7. **Extra turn on 6**: does rolling a 6 grant another roll? → not modelled by default; the extension is a one-line hook (see explanation.md).
8. **Reproducibility**: do we need deterministic games for tests/demo? → inject a seeded `Random`.

## Functional Requirements

- Create a game with an N x N board (positions 1..N²), 2+ players, a die and a win rule.
- Configure arbitrary snakes (`head → tail`, moves down) and ladders (`foot → top`, moves up).
- Take turns in round-robin order: roll, move, resolve snake/ladder chains, detect a winner.
- Exact-roll-to-finish by default; overshoot results in no move (swap in a bounce-back rule without code changes).
- Support a crooked die that never shows 6, wrapped around a normal die.
- Reject invalid board configurations with meaningful error messages (snake that goes up, ladder off the board, two pieces on one cell, cyclic chains).

## Non-Functional Requirements

- **Deterministic**: seeded `Random` injection makes games reproducible for tests and demos.
- **Extensible (OCP)**: new dice behaviours, win rules and board sizes arrive as new classes; `Game`, `Board` and `Dice` are never edited.
- **Readable output**: every turn prints a one-line narrative (interviewers ask you to run the game and eyeball the log).
- **Small footprint**: board is a flat map of consequences — O(S + L) memory, not O(N²) cells.

## Core Entities

| Entity | Role |
|---|---|
| `Board` | N x N track of positions 1..N²; owns the map of `MovementConsequence`s; resolves chains |
| `MovementConsequence` | One snake or ladder: start, end, direction; `jump(from)` returns the destination |
| `Player` | Name + current position |
| `Dice` | Interface: `roll()` |
| `StandardDice` | Six-sided die over an injectable (seedable) `Random` |
| `CrookedDiceDecorator` | Wraps any `Dice`; re-rolls whenever the wrapped die shows 6 |
| `WinRule` | Strategy: where does a roll take the player, given the finish cell? |
| `Game` | Orchestrates players, dice, board and win rule; turn loop and winner detection |

## Design Patterns Used (and why)

- **Decorator** — `CrookedDiceDecorator` wraps a `Dice` and suppresses 6s. `Game` keeps calling `roll()` and never knows. This is the pattern interviewers probe with the crooked-dice twist; it is the cleanest demonstration of OCP in the whole problem.
- **Strategy** — `WinRule` (`ExactRollWinRule`, `BounceBackWinRule`). House rules differ across families and interviewers; the rule is data injected into `Game`, not a branch inside it.
- **Composition over inheritance** — snakes and ladders are one `MovementConsequence` class on the board's map, not an `abstract BoardPiece` with two hollow subclasses. Both pieces behave identically (move player from A to B); they differ only in data (direction) and presentation. See explanation.md for the full argument and when you *would* introduce a type hierarchy.
- **Dependency injection** — `Game` receives its dice, board, rule and players; nothing is constructed internally, so every collaborator is substitutable and testable.

## How to Run

```bash
cd solutions/java

# Java 22+ (single-file source launcher handles sibling classes):
java SnakeAndLadderDemo.java

# Java 11+ (classic):
javac *.java && java SnakeAndLadderDemo
```

The demo prints six sections: the standard 10x10 board, validation guardrails, a seeded game played to completion on a compact 5x5 board (small board keeps the log readable), chain resolution (ladder foot that leads straight into a snake head), win-rule strategy comparison, and crooked-dice behaviour.

## Extension Questions Interviewers Ask

1. **"Add a crooked die that never rolls 6."** — Already in: `new CrookedDiceDecorator(new StandardDice(42))`. `Game` is untouched (OCP).
2. **"Two dice summed, or re-roll on 6 for an extra turn?"** — Another `Dice` decorator or implementation; `Game` unchanged.
3. **"How do you make tests deterministic?"** — Seed the `Random` you inject into `StandardDice`; `java.util.Random`'s algorithm is specified, so the sequence is stable across JVM runs and versions.
4. **"1-D or 2-D board?"** — 1-D: snakes and ladders are pure position mappings and never need row/col arithmetic; 2-D only pays off if you must *draw* a boustrophedon board or support 2-D movement (e.g., chess-like). Trade-off discussed in explanation.md.
5. **"What if a ladder's top is a snake's head?"** — Resolve in a loop: keep jumping until a plain cell; guard against cycles with a visited-set so a misconfigured board throws instead of hanging.
6. **"Millions of games in parallel?"** — The domain is tiny; the real answer is that `Game` is stateless-per-instance and shareable, and the seeded dice lets you shard deterministic replays. Discuss, don't over-engineer.

## Learning Objectives

- Decorating a dependency without touching its consumer (OCP in practice)
- Strategy for rules that vary by household/interviewer
- Knowing when *not* to use inheritance (snake/ladder as one class)
- Loop-with-cycle-guard as the safe way to chase pointer-like chains

For the entity-by-entity walkthrough, rejected alternatives and complexity analysis, see [explanation.md](explanation.md).
