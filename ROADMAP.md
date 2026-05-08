# Roadmap (Draft)

> This roadmap is intentionally **not final**. It captures current ideas we can validate, measure, and revise.

## Goals
- Improve generation speed and responsiveness.
- Improve reliability when model output is inconsistent.
- Build confidence through repeatable simulation-based testing.
- Keep architecture flexible across `copilot`, `gemini`, and `local` providers.

## Guiding Principles
- Measure first, then optimize.
- Prefer reversible changes and feature flags.
- Keep provider behavior consistent through shared policies.
- Treat safety and reliability as product features.

## Near-Term Themes

### 1) Caching Strategies for Speed
- Cache by `prompt + provider + model + settings` hash.
- Start in-memory; add optional Redis for multi-instance deployments.
- Use route-based TTLs and invalidate on template/model/instruction changes.
- Add stale-while-revalidate; track hit rate, p50, and p95 latency.

### 2) Local Distilled Models Optimized for Speed
- Benchmark small/medium distilled models for HTML/UI tasks.
- Support `fast`, `balanced`, and `quality` model profiles.
- Default to low-latency for exploration; allow opt-in quality mode.
- Set acceptance gates: validity, latency, and usefulness; document hardware + quantization presets.

### 3) Simulation Environments for Testing
- Create deterministic fixture runs with expected constraints.
- Add provider mocks for timeouts, malformed HTML, and partial output.
- Maintain scenario packs: happy path, adversarial, long-context, and concurrency.
- Run CI checks for latency, validity rate, and retry regression.

### 4) Context Saving + Consistency Preservation
- Persist context snapshots (prompt, provider/model, metadata, navigation parent).
- Version snapshots for traceable regeneration.
- Detect drift with lightweight validators + context fingerprints.
- Offer restore points to return to the last coherent state.

### 5) Retry Generation on Inconsistency Detection
- Apply bounded retries with exponential backoff on validation failures.
- Use targeted retry prompts that describe the exact failure.
- Fallback order: same model -> alternate profile -> alternate provider (if enabled).
- Log retry reasons/outcomes and expose telemetry for tuning.

## Milestones (Flexible)

### A) Baseline
- Add telemetry (latency + quality), failure taxonomy, and first simulation fixtures.

### B) Reliability
- Implement context snapshots, inconsistency detection, bounded retries, and baseline cache.

### C) Speed
- Expand cache strategy, roll out model profiles, and benchmark simulation vs real sessions.

### D) Hardening
- Add load simulations, tune fallback/retry thresholds, and publish an operational playbook.

## Open Questions
- Should cache be per-session, global, or hybrid?
- Which quality checks should block response vs. warn only?
- What is the minimum acceptable quality for "fast" model profile?
- Should fallback across providers be automatic or user-confirmed?

## Definition of Progress
- Lower median and p95 generation latency.
- Fewer invalid/incoherent responses reaching users.
- Lower manual retry rate by users.
- Stable or improved task completion in simulation scenarios.

---

If priorities change, this document should be updated first and treated as a living plan.
