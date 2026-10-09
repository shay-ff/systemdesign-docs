# Snake & Ladder — Java Implementation

Java 11, no external libraries, no `package` declarations (repo convention: one top-level class per file, compiled side by side).

## Design Patterns

- **Decorator** — `CrookedDiceDecorator` wraps any `Dice` and never shows 6; `Game` is untouched.
- **Strategy** — `WinRule` (`ExactRollWinRule`, `BounceBackWinRule`): the overshoot rule is injected, not branched on.
- **Composition over inheritance** — snakes and ladders are one `MovementConsequence` class with a `Direction`; no hollow `Snake`/`Ladder` subclasses.
- **Dependency injection** — seeded `Random` flows into `StandardDice`; the whole game is reproducible.

## Class-by-Class

| File | Class | Responsibility |
|---|---|---|
| `Board.java` | `Board` | N x N positions 1..N² as a sparse map of consequences; constructor validation (bounds, direction, overlap, cycles); chain resolution with visited-set cycle guard; boustrophedon `prettyPrint()` |
| `MovementConsequence.java` | `MovementConsequence` | One snake/ladder: start, end, direction; validates its own direction rule; `jump(from)` |
| `Direction.java` | `Direction` | Enum `SNAKE`/`LADDER` (an enum, not a boolean, so validation and logs read cleanly) |
| `Player.java` | `Player` | Name + position; thin token, moved only by `Game` |
| `Dice.java` | `Dice` | One-method interface: `roll()`, `describe()` |
| `StandardDice.java` | `StandardDice` | Fair six-sided die over an injected `Random`; `StandardDice(seed)` is deterministic |
| `CrookedDiceDecorator.java` | `CrookedDiceDecorator` | Re-rolls while the wrapped die shows 6; decorates *any* `Dice` |
| `WinRule.java` | `WinRule` | Strategy: `destination(current, roll, finish)` |
| `ExactRollWinRule.java` | `ExactRollWinRule` | Exact roll to finish; overshoot = no move |
| `BounceBackWinRule.java` | `BounceBackWinRule` | Excess reflects back off the finish line |
| `Game.java` | `Game` | Round-robin turn loop; roll → rule → chain → winner. No game-specific branching |
| `SnakeAndLadderDemo.java` | `SnakeAndLadderDemo` | Six-section demo with `===` headers |

## Run

```bash
# Java 22+ single-file source launcher (handles sibling types):
java SnakeAndLadderDemo.java

# Java 11+ classic:
javac *.java && java SnakeAndLadderDemo
```

## Demo Sections

1. **Standard 10x10 board** — full pretty-printed board (S = snake head, L = ladder foot, W = finish) plus every snake/ladder listed.
2. **Validation guardrails** — climbing snake, off-board ladder, overlapping consequences, cyclic chain, single-player game: each rejected with the exact reason.
3. **Seeded game to completion** — 5x5 board (stated in the log; small board keeps output readable), 3 players, seed-7 die, exact-roll rule, played to a winner with per-turn narration including fired chains.
4. **Multi-hop chain resolution** — ladder 3→22 whose top is snake 22→8 (climb then slide), and a two-ladder chain 4→11→19.
5. **Win-rule strategies** — the same (position, roll) under exact-roll vs bounce-back rules.
6. **Crooked dice** — 12 fair vs 12 crooked rolls from the same seed, then 10,000 crooked rolls proving face 6 never appears while faces 1–5 all do.

Rerunning the demo reproduces the identical game log (seeded `Random`; the algorithm is JVM-spec-stable).
