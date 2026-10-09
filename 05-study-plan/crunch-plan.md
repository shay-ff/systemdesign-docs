# The 4-Week Crunch Plan — LLD-Heavy, Interview-Ready

> 🧭 **Navigation**: [← Study Plan](README.md) | [📍 Full Navigation](../NAVIGATION.md)

This is the **accelerated plan for someone interviewing in under 4 weeks**, with
limited hours and a primary focus on **low-level design (LLD)** plus enough HLD
to survive a system design round. It is deliberately different from the
[6-week curriculum](study_plan.md): fewer topics, more repetition, and a bias
toward *doing* (designing and coding) over *reading*.

**Assumptions:** ~2 years of experience, ~2 hours on weekdays and ~4–5 hours on
weekends (roughly 18–20 hours/week). Adjust proportionally if your availability
differs.

**The core principle: spaced repetition over coverage.** Seeing 17 LLD problems
once is worth far less than deeply solving 8 and revising them. This plan trades
breadth for retention.

---

## The strategy in one paragraph

Weeks 1–2 build the LLD muscle (framework, patterns, and the classic problems,
solved by hand). Week 3 adds HLD breadth via the quick-reference and two deep
dives. Week 4 is pure mock interviews and revision — **no new material**. The
most common failure mode is studying new topics in the final week instead of
drilling the ones you already know. Don't do that.

---

## Week 1 — LLD foundations + the core problems

**Goal:** internalise the LLD round framework and solve the 5 highest-frequency
problems.

| Day | Focus | Do this | Time |
|---|---|---|---|
| Mon | Framework | Read [lld-round-guide.md](../04-interview-prep/lld-round-guide.md) and [lld-patterns-cheatsheet.md](../04-interview-prep/lld-patterns-cheatsheet.md). Internalise the 5-phase framework. | 2h |
| Tue | Parking Lot | Solve from scratch (45-min timer), then read `../01-ll-designs/parking_lot/`. Note where you differed. | 2h |
| Wed | Splitwise | Solve from scratch, then read `../01-ll-designs/splitwise/`. Focus on the money-arithmetic edge case. | 2h |
| Thu | BookMyShow | Solve from scratch, then read `../01-ll-designs/bookmyshow/`. Focus on seat locking + concurrency. | 2h |
| Fri | Elevator | Solve from scratch, then read `../01-ll-designs/elevator/`. Focus on the state machine + scheduling strategy. | 2h |
| Sat | Vending Machine + revision | Solve Vending Machine from scratch. Then re-solve (on paper, 20 min) the two problems that felt hardest this week. | 4h |
| Sun | Patterns drill | For each of the 6 problems so far, state aloud: the varying seam, the pattern, the concurrency risk. Then read [design-patterns.md](../00-foundations/design-patterns.md). | 3h |

**Checkpoint:** can you take any of these 6 problems and produce a clean class
diagram + a strategy for the varying part in under 15 minutes?

**The habit to build:** always solve *before* reading the solution. Reading
first creates an illusion of competence.

---

## Week 2 — LLD depth + the remaining classics

**Goal:** finish the classic LLD set, then drill machine-coding workflow.

| Day | Focus | Do this | Time |
|---|---|---|---|
| Mon | Logger Framework | Solve from scratch. Read `../01-ll-designs/logger_framework/`. Focus on chain-of-responsibility + observer + async backpressure. | 2h |
| Tue | Notification System | Solve from scratch. Read `../01-ll-designs/notification_system/`. Focus on retries + idempotency. | 2h |
| Wed | ATM | Solve from scratch. Read `../01-ll-designs/atm/`. Focus on state + command + the concurrency race. | 2h |
| Thu | Snake & Ladder + Car Rental | Solve both from scratch (they're lighter). Focus on decorator (dice) and interval-overlap (rental). | 2h |
| Fri | Calendar / Scheduler | Solve from scratch. Read `../01-ll-designs/calendar_scheduler/`. Focus on interval overlap + recurrence. | 2h |
| Sat | Machine-coding drill | Read [machine-coding-guide.md](../04-interview-prep/machine-coding-guide.md). Pick 2 problems and do a full 120-min timed machine-coding round each (skeleton → core → demo → tests). | 4h |
| Sun | LLD mock + revision | Do the [Splitwise mock interview](../04-interview-prep/mock-interviews/mid-level/lld-splitwise.md) under a timer. Then revise the 5 problems you feel weakest on. | 3h |

**Checkpoint:** can you run the skeleton-first workflow and deliver a running
demo with tests in 120 minutes?

**By end of Week 2:** you've solved all 10 classic problems from scratch at least
once. That's the bulk of LLD preparation done.

---

## Week 3 — HLD breadth + the systems that matter

**Goal:** get HLD-ready via breadth (quick reference) + two deep dives. LLD
stays warm through daily revision.

| Day | Focus | Do this | Time |
|---|---|---|---|
| Mon | HLD framework | Read [frameworks.md](../04-interview-prep/frameworks.md) (the SCALE framework) and [hld-quick-reference.md](../02-hl-designs/hld-quick-reference.md) sections 1–6. | 2h |
| Tue | HLD quick-ref (rest) | Read quick-ref sections 7–12 + the cross-cutting patterns table. Reconstruct 3 systems from memory. | 2h |
| Wed | Chat System deep dive | Read the full `../02-hl-designs/chat_system/` folder. Focus on the store-then-route flow + ordering + offline delivery. | 2h |
| Thu | Payment Gateway deep dive | Read the full `../02-hl-designs/payment_gateway/` folder. Focus on idempotency + the ledger + reconciliation. (Highly relevant at Razorpay.) | 2h |
| Fri | One existing HLD | Read `../02-hl-designs/twitter_clone/` or `url_shortener/`. Practice the 6-step spine on a blank page. | 2h |
| Sat | HLD practice | Design 2 systems from the quick-ref from scratch (45-min each): e.g. typeahead + a news feed. Compare to the one-pagers. | 4h |
| Sun | LLD revision | Re-solve 3 LLD problems on paper (20 min each). LLD is your focus — don't let it go cold. | 3h |

**Checkpoint:** can you take any HLD question, run the 6-step spine, and reach a
scalable architecture with trade-offs in 45 minutes?

**Note:** LLD is still the priority. HLD here is "enough to be competent," not
"expert" — your target roles weight LLD more, and the quick-ref plus two deep
dives covers the common questions.

---

## Week 4 — Mocks, revision, and no new material

**Goal:** convert knowledge into performance. **Do not learn new topics.**

| Day | Focus | Do this | Time |
|---|---|---|---|
| Mon | LLD mock #1 | Full timed LLD round (45 min) on a problem you've done, but with a partner or out loud. Self-score against a rubric. | 2h |
| Tue | LLD mock #2 + weak spots | Another timed LLD round. Then revise your two weakest problems in depth. | 2h |
| Wed | HLD mock | Full timed HLD round (45–60 min). Practice the scaling follow-up specifically. | 2h |
| Thu | Full revision sweep | For each of the 10 LLD problems: state the entities, the pattern, the concurrency risk, and one extension — in 5 min each. | 2h |
| Fri | Behavioral + gap-fill | Prepare 3 STAR stories (see [frameworks.md](../04-interview-prep/frameworks.md)). Fill the one technical gap you keep hitting. | 2h |
| Sat | Full mock day | 2 LLD rounds + 1 HLD round, timed, back to back. Simulate interview fatigue. | 4h |
| Sun | Light review + rest | Skim the cheatsheets. **Rest.** A tired brain underperforms a prepared one. | 2h |

**Checkpoint:** you can walk into any of the 10 LLD problems and a common HLD
question and perform under time pressure.

---

## The daily loop (every day)

1. **Warm-up (10 min):** re-read one cheatsheet section (patterns or HLD).
2. **Solve (main block):** one problem, timed, from scratch — *before* reading
   the solution.
3. **Compare (15 min):** read the repo's solution; note differences; decide which
   is better and why.
4. **Log (5 min):** write down one thing you got wrong and one insight. Review
   this log every Sunday.

The log is the highest-leverage artifact of the whole plan. It tells you exactly
what to revise.

---

## What to prioritise if you fall behind

If you lose days (you will), cut in this order:

1. **Cut HLD breadth** first — the quick-ref can be skimmed; the two deep dives
   matter most.
2. **Keep LLD problem-solving** — it's the primary round.
3. **Never cut Week 4 mocks** — practice under pressure is what converts
   knowledge into performance.
4. **Never cut the daily revision loop** — spaced repetition is what makes it
   stick.

---

## The 8 problems that matter most (if you only have time for 8)

1. **Parking Lot** — the canonical OOP/Strategy problem.
2. **Splitwise** — money arithmetic + algorithms.
3. **BookMyShow** — concurrency + state.
4. **Elevator** — state + scheduling strategy.
5. **Vending Machine** — pure State pattern.
6. **Logger** — chain of responsibility + observer + async.
7. **ATM** — state + command.
8. **Notification System** — retries + idempotency + queues.

These 8 cover every core pattern and every concurrency shape you'll be asked
about. The other 9 are bonus breadth.

---

## Anti-patterns for the crunch

1. **Reading solutions without solving first.** Illusion of competence.
2. **Learning new topics in Week 4.** Reinforces insecurity, not skill.
3. **Breadth over depth.** 17 problems skimmed < 8 problems mastered.
4. **Skipping mocks.** The round is a performance; practice the performance.
5. **Ignoring concurrency.** The #1 gap for ~2 YOE candidates.
6. **No sleep.** Cognitive performance drops sharply; it's counterproductive.

---

## Success metrics (track weekly)

- Problems solved from scratch (target: 10 by end of Week 2).
- Problems revised (target: all 10 revised in Week 4).
- Mock interviews done (target: 5+ across Weeks 3–4).
- Concurrency cases you can name cold (target: all).
- The log: are the same mistakes recurring? If so, that's your focus.

---

*Related: [LLD Round Guide](../04-interview-prep/lld-round-guide.md) ·
[Machine Coding Guide](../04-interview-prep/machine-coding-guide.md) ·
[HLD Quick Reference](../02-hl-designs/hld-quick-reference.md) ·
[6-Week Curriculum](study_plan.md)*
