---
type: spec
title: LLD Practice Playground
date: 2026-10-05
status: approved
---

# LLD Practice Playground — Design

## Problem

`systemdesign-docs` has six low-level-design problems (LRU cache, rate
limiter, bloom filter, URL shortener, consistent hashing, message queue) each
with a problem statement (README.md), a design/trade-off discussion
(explanation.md), and reference solutions in Python/Java/Go/C++. There's no
way to *practice* these — write your own solution and get feedback — only to
read the reference material.

## Goals

- A LeetCode-style practice loop: read the problem, write a Python solution
  in a browser editor, run it against a hidden test suite, get pass/fail.
- Reuse the existing problem content and Python reference solutions as the
  source of truth for problem statements and test contracts.
- Keep the code-execution surface safely sandboxed from day one — this is a
  security-relevant surface (arbitrary user code execution) even for a
  personal learning tool.

## Non-goals (v1)

- Java/Go/C++ submissions — Python only.
- User accounts, login, or server-side/cross-device progress.
- Diffing a user's solution against the reference implementation.
- Live/automatic sync from `systemdesign-docs` — re-sync is a manual script.

## Repo and content

New, separate repo: `~/dev/lld-playground` (Next.js frontend + FastAPI
backend). `systemdesign-docs` stays a pure content repo.

A one-time/manually-rerun sync script in `lld-playground` copies each of the
six problem folders' `README.md`, `explanation.md`, and
`solutions/python/*.py` from `systemdesign-docs/01-ll-designs/<problem>/`
into `lld-playground`'s content directory. No submodule, no runtime fetch —
re-run the script by hand when source content changes.

## Core loop

1. User opens a problem: sees the README.md problem statement in an editor
   pane with a Python code editor alongside (Monaco-based).
2. User writes a solution implementing the problem's expected class/method
   contract (e.g. `LRUCache.get/put`, `TokenBucket.allow_request`,
   `BloomFilter.add/contains`) and clicks Run.
3. Backend runs the submission against that problem's hidden test suite and
   returns pass/fail per test case plus captured stdout/stderr on failure.
4. On a full pass, the problem's `explanation.md` (design/trade-off
   discussion) unlocks in a "Learn" tab. Before passing, only the README.md
   problem statement is visible — explanation.md contains the solution
   approach itself, so showing it upfront would spoil the exercise.
5. Progress (solved/unsolved per problem, last submitted code) persists in
   browser localStorage only — no accounts, no server-side state beyond the
   stateless execution request/response.

## Hidden test suites

Each problem's existing `solutions/python/*.py` already encodes the expected
method contract, and its `demo()`/docstring comments already assert expected
behavior (e.g. LRU: `get(1) == 1`, eviction order; Bloom filter: no false
negatives; token bucket: refill behavior). These seed each problem's hidden
test suite; extend with edge cases (capacity 0/1, empty filter, expired URL,
node removal mid-ring, etc.) where the reference demo doesn't already cover
them.

## Execution architecture

Two backend components, not one:

- **API service** (FastAPI, public-facing): serves problem content, accepts
  a submission (`problem_id`, `code`), enqueues it to the executor, returns
  the result. Never touches the Docker socket or spawns processes directly —
  this keeps the component with public exposure separate from the component
  with execution privilege.
- **Executor service**: the only component that runs user code. For each
  submission it starts a short-lived, locked-down container:
  - non-root user, `readOnlyRootFilesystem: true` (writable `/tmp` scratch
    only for the submission file + test harness)
  - `--network none` (no egress — user code has no reason to make network
    calls)
  - dropped Linux capabilities, `allowPrivilegeEscalation: false`
  - CPU, memory, and process-count limits; a hard wall-clock timeout
  - `--rm`/auto-cleanup after each run, so no state survives between
    submissions
  - the submission's source code is written to a file and passed by path —
    never interpolated into a shell command string

This mirrors how online judges (Judge0, HackerRank) isolate submissions, and
keeps the whole stack to two services plus the container runtime rather than
adding a third managed dependency.

## API surface

- `GET /problems` — list the six problems (id, title, summary).
- `GET /problems/{id}` — problem statement (README.md) always; explanation.md
  included only once the caller has a passing submission for that problem
  (tracked client-side; the API trusts a `solved=true` flag the frontend
  sends back, since there's no server-side session to track it instead — see
  Testing below for why this is an acceptable v1 trade-off, not a security
  control).
- `POST /problems/{id}/submit` — body: `{code: str}`. Runs the hidden test
  suite via the executor, returns `{passed: bool, results: [...], output:
  str}`. Rate-limited per the org's API security guidance (this endpoint
  triggers container spin-up per call — it's the one place abuse directly
  costs compute).

## Error handling

- Submission exceeding the timeout, memory limit, or crashing the container
  returns a structured failure (`{passed: false, error: "timeout"| "runtime_error"
  | ...}`), never a raw stack trace from the executor's own process.
- Executor unavailable (Docker daemon down, container failed to start)
  surfaces as a distinct `503`-style error, not conflated with a failing
  test result.

## Testing strategy

- Executor: unit tests per problem's hidden test suite against the known
  reference Python solution (must pass) and a handful of known-bad
  solutions (must fail with the right failure reason).
- Sandbox isolation itself: a test submission that tries to read outside
  `/tmp`, reach the network, or fork-bomb, confirming each is blocked/limited
  rather than trusting the container flags silently.
- API service: contract tests for `/problems`, `/problems/{id}`, and
  `/submit` independent of the executor (mock the executor boundary).

## Security guidance applied

- `validation/untrusted-input` (command execution): submission code is
  written to a file and executed by path, never concatenated into a shell
  string.
- `containers/hardening`: non-root, read-only rootfs, dropped capabilities,
  no privilege escalation, resource limits, no network egress for the
  per-submission container.
- `api/security`: rate limiting on `POST /problems/{id}/submit`.

## Open question carried forward

The `solved` flag sent by the client on `GET /problems/{id}` is a UX gate,
not a security boundary — a user can trivially set it themselves to read
explanation.md early. That's fine (it's their own learning tool, not a
multi-tenant system), but it should be called out explicitly in the repo's
README so it isn't mistaken for enforcement if accounts are ever added later.
