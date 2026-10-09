# Snake & Ladder — Design Explanation

A walkthrough of every entity, why it exists in that shape, what was rejected, and where the classic edge cases bite.

## Entity-by-Entity Rationale

### `Board` — positions, not cells

`Board` holds only three things: the board dimension, the finish position (dimension²), and a `Map<Integer, MovementConsequence>` from start-cell to consequence.

There is deliberately **no `Cell` class and no `N x N` array**. A cell in Snake & Ladder has no state and no behaviour of its own — the only thing that distinguishes cell 17 from cell 18 is whether a consequence starts there. Once you accept that, a sparse map *is* the board. A 100-cell array would allocate 100 slots to say nothing 90 times.

`Board` owns all validation in its constructor: snakes must strictly descend (`tail < head`), ladders must strictly ascend, endpoints must lie in `[1, finish]`, start and end must differ, and **no two consequences may share a start cell**. Fail fast at construction — a malformed board must never make it into a game loop.

`resolve(int position)` walks the chain: while the current cell is the start of a consequence, jump to its end. A `Set<Integer> visited` breaks cycles: if the same start cell is entered twice, the board is cyclic (snake head ↔ ladder foot loop) and we throw with the loop's cells spelled out, rather than looping forever.

### `MovementConsequence` — composition over inheritance

The classic wrong move is an `abstract class BoardPiece` with `Snake extends BoardPiece` and `Ladder extends BoardPiece`. Ask what each subclass adds: **nothing**. Both have a start and an end; both do exactly one thing — carry the player from start to end. The only differences are data (which of start/end is larger) and presentation (arrow direction, past tense verb: "climbs" vs "slides").

So we keep one class with a `Direction` enum field and let `toString()` vary by direction. Fewer classes, no duplicated `jump()` contract, and the map holds a single value type. Note we still model direction *as an enum* rather than a bare boolean — `Direction.SNAKE` reads correctly in validation and output where `isLadder` invites double-negative bugs.

When *would* a type hierarchy pay for itself? If snakes and ladders diverged in behaviour — e.g., snakes that swallow a turn, ladders that only work once, or pieces with state (already-used). Then polymorphic `onLanding(player)` methods would be worth it. Until then, inheritance here is ceremony.

### `Player` — deliberately thin

Name + position, starting at 1. Identity is by name (used for the duplicate-player check). No references to dice or board: a player is a token, and `Game` is the only thing that knows how to move tokens. Thin domain objects keep the orchestration visible in one place, which is what an interviewer wants to read.

### `Dice` / `StandardDice` / `CrookedDiceDecorator` — the OCP showcase

`Dice` is a one-method interface. `StandardDice` wraps an injected `java.util.Random` — injecting it (rather than calling `new Random()` inside) is what makes games deterministic: seed 42 replays seed 42 forever, across JVM versions, because `Random`'s LCG is specified by the Javadoc as a fixed algorithm.

`CrookedDiceDecorator` implements `Dice` by *holding* a `Dice`. On `roll()`, it asks the wrapped die and re-rolls while the result is 6. Two properties worth stating in an interview:

- It decorates **any** `Dice`, not just the standard one — `CrookedDiceDecorator(new SumOfTwoDice(...))` works unmodelled.
- `Game` is never edited to support it. The crooked-dice question is the round's OCP test: if your `Game` code contains `if (crooked)`, you failed the extensibility probe.

The decorator's `toString()` is preserved so game logs still say which die is in play.

### `WinRule` — the overshoot rule as a strategy

House rules differ: exact-roll-to-finish (overshoot = stay put) versus bounce-back off the finish line. Both are `int destination(int current, int roll, int finish)`:

- `ExactRollWinRule`: `current + roll == finish` → finish; otherwise move normally unless the move would pass the finish, in which case stay.
- `BounceBackWinRule`: if the roll passes the finish, reflect the excess back (`finish - excess`).

The point is not that both rules are hard — it's that the rule lives **outside** `Game`. When the interviewer says "actually, in my version you bounce back," the answer is a constructor argument, not a patch to the engine.

### `Game` — orchestrator only

`Game` holds players, board, dice, rule and a round counter. Its `playTurn()` does one turn: roll → ask the rule for the destination → resolve chains → detect the win. `playUntilWinner()` loops turns round-robin and stops the moment a player lands exactly on the finish. It contains **no** game-specific branches: no `if snake`, no `if crooked`, no `if bounce`. Everything varying lives in injected collaborators — that's the whole design.

## Class Relationships

- `Game` ◆→ `Player` list, `Board`, `Dice`, `WinRule` (composition; collaborators injected)
- `Board` ◆→ `Map<Integer, MovementConsequence>` (sparse 1-D representation)
- `CrookedDiceDecorator` ◆→ `Dice` (decorator: has-a plus is-a)
- `MovementConsequence` → `Direction` enum
- `WinRule` implemented by two strategies; `Game` depends only on the interface

## Pattern Choices and Rejected Alternatives

| Choice | Rejected alternative | Why |
|---|---|---|
| `MovementConsequence` single class | `Snake`/`Ladder` subclasses | No behaviour difference to justify a hierarchy; direction is data |
| Sparse map board | `N x N` `Cell[][]` | Cells are stateless; array allocates 100 slots to say nothing; 1-D vs 2-D discussed below |
| Decorator for crooked dice | `if (crooked)` in `Game`, or a `CrookedDice extends StandardDice` subclass | Subclassing the standard die fails when the "crooked" behaviour must wrap *other* dice; the branch in `Game` fails OCP |
| `WinRule` strategy | a `boolean exactRoll` flag | Booleans scale terribly — the second house rule turns the flag into an if-else ladder; strategy scales to N rules |
| Seeded `Random` injected | `ThreadLocalRandom` or `new Random()` inside `StandardDice` | Determinism is a requirement for tests and demos; inject the seed |

## Trade-offs

- **Sparse map (O(S+L) space) vs full grid (O(N²))**: the grid is only worth it if cells gain state (visit counts, per-cell events) or you must render the boustrophedon board. For rules evaluation, map lookup is O(1) either way.
- **Determinism vs realism**: `new Random(42)` makes demos reproducible but not representative. Fine for tests; mention that a production game would take an entropy source.
- **Cycle guard cost**: the visited set allocates per resolution, but chains are 1–3 jumps long in practice; the set is what turns "infinite loop in prod" into "constructive error message at board build time."
- **Fail-fast validation vs lenient runtime**: rejecting bad boards in the constructor means every later frame of the demo operates on a known-good board. Debugging a mid-game illegal jump is far worse.

## Complexity

| Operation | Time | Space |
|---|---|---|
| Board construction | O(S + L) | O(S + L) |
| One turn (roll + move + resolve chain) | O(chain length) worst O(S + L) | O(chain) for the visited set |
| Full game | O(turns × chain) | O(P + S + L) |

## Classic Edge Cases

1. **Chain: ladder foot → snake head.** You land on a ladder, climb, and the top of the ladder is a snake's head. The resolution loop keeps jumping until a plain cell is reached. The demo prints such a chain explicitly (5x5 board: ladder 3→22 whose top is snake 22→8).
2. **Cycle: snake head → ladder foot → snake head.** Impossible to resolve by jumping — the visited set detects the repeat and throws naming the loop. Validated eagerly in the demo's guardrail section.
3. **Overshoot.** With `ExactRollWinRule`, standing at 24 of 25 and rolling 4 means **no move** — you must land exactly. The demo's strategy section shows the same (position, roll) pair producing different outcomes under the two rules.
4. **Snake at the finish / ladder past the finish.** A consequence whose endpoint exceeds the finish is invalid by construction; a consequence *starting* at the finish is invalid too (the game would end before any jump could occur — and the winner's landing is terminal).
5. **Two consequences on one cell.** Ambiguous jump; rejected at construction.
6. **A snake that climbs.** `head < tail` is the definition of a ladder sneaking in as a snake; rejected by direction validation.
7. **Duplicate player names.** Round-robin bookkeeping and the winner announcement would be ambiguous; rejected in `Game`'s constructor.

## Testing Strategy

- Determinism first: with seed 42 the entire game is one golden-log assertion.
- Board validation: every rejection branch deserves a test asserting the exact message.
- Chain resolution: boards built purely to force 2- and 3-hop chains, plus the cycle throw.
- Win rules: property-style checks — exact roll wins, overshoot under exact rule stays, bounce rule reflects, no destination ever exceeds finish.
- Crooked die: roll 10,000 times, assert `results ∈ [1,5]` and every face 1–5 appears (distribution sanity, not just suppression).
