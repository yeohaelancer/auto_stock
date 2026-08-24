# Working Principles

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State assumptions explicitly; if uncertain, ask.
- Multiple interpretations? Present them — don't pick silently.
- Simpler approach exists? Say so. Push back when warranted.
- Something unclear? Stop. Name what's confusing. Ask.

### Unanswered Is Not Delegation

A skipped, unanswered, ambiguous, or preference-free response means the decision remains open, not delegated. Never make a user-dependent decision without an explicit user choice or explicit delegation.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features, abstractions, flexibility, or config beyond what was asked.
- No error handling for impossible scenarios.
- If it could be substantially shorter, rewrite it.

Would a senior engineer call this overcomplicated? If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

- Don't "improve", refactor, or reformat code that isn't broken. Match existing style.
- Notice unrelated dead code? Mention it — don't delete it. If it blocks your change, surface it first.
- Remove only the orphans *your* change created (now-unused imports/vars/functions).

The test: every changed line traces directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Turn tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass."
- "Fix the bug" → "Write a test that reproduces it, then make it pass."
- "Refactor X" → "Ensure tests pass before and after."
- No tests yet → define a manual repro/check, run it before and after.

For multi-step tasks, state a brief plan as `step → verify: check` lines. Strong criteria let you loop independently; weak ones ("make it work") need constant clarification.

## 5. Single Source of Truth

**Same fact in one place. Other locations cross-ref.**

Define each fact — constant, config key, catalog entry, doc — once; reference it elsewhere, don't duplicate. Found duplication? Fix the duplication, not just the symptom.

## 6. Verify Before Asserting

**Treat factual statements in docs, comments, reviews, and explanations as claims that require evidence.**

- Verify behavioral guarantees, invariants, performance, concurrency, fallback, persistence, and API contracts from authoritative sources before asserting them.
- Don't infer specifics from names, conventions, or assumptions.
- If verification is unavailable or too costly, qualify the statement explicitly or omit it.

## 7. Cite Rules, Don't Invent Them

**Don't invent rules or policies and present them as established. Cite the source — or admit you're applying spirit, not letter.**

- Letter: "Per AGENTS.md § Verify Before Asserting..." / "RFC 6749 § 4.1..." — verifiable, exact. Cite this file's rules by section title, not number; numbers shift as rules are added.
- Spirit: "Applying the principle from X to this case..." — flagged interpretation.

## 8. Explicit Over Implicit

**Loud beats silent. Explicit beats inferred.**

- Prefer explicit code over framework magic. Read explicit config — don't infer environment (dev/prod) or one artifact's version from another.
- On a security- or correctness-critical path, fail loudly (startup fail / exception) on an ambiguous case rather than silently skip. Silent fallbacks hide bugs and security holes.

## 9. Use Proven Standards

**Don't reinvent wrappers around well-tested standards.**

For cross-cutting concerns — security, auth, time, encoding, JSON, OAuth, crypto — use the ecosystem's standard library or framework. Thin wrappers add maintenance burden; re-implementing security-critical code reproduces well-known bugs.

## 10. Code Style

- No unexplained policy or contract numbers/strings in source code — extract repeated or behavior-bearing values to constants, configuration, or named variables. Literal examples, test fixtures, i18n values/keys, and framework annotation values may stay inline when inlining is clearer.
- One method, one responsibility. Split when a method grows multiple concerns.

## 11. Match the User's Language

Reply in whatever language the user writes in; keep code, identifiers, and technical terms in their canonical form.

**Source comments are dual in Language Korea and English** — every line, block, and doc comment, regardless of the programming or chat language. Other artifacts (log/exception messages, i18n message values, test fixtures) follow their own conventions.
**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.
