# Running datawatch-app changes as Automata

You can run changes to this repo as datawatch **Automata** (PRDs) instead of as hand-driven sessions. This page records the setup and the current limits. The datawatch agent confirmed them on 2026-10-14 for datawatch v9.0.65–v9.0.75; its full answer is in the datawatch discussion scope `datawatch-app/prd-cycle`.

## Setup

- **Type:** `mobile-app` ("Mobile app"), registered in the daemon's Automaton type registry. A type is only a label and colour. It does **not** carry default checks, guardrails or models, so set those on each Automaton (see below).
- **Verification script:** [`scripts/automata/verify-app.sh`](../scripts/automata/verify-app.sh). It runs the same gate as CI:
  1. version parity between `gradle.properties` and `Version.kt`;
  2. the locale / Automata-terminology gate;
  3. `./gradlew check detekt ktlintCheck`.

  A task passes only if the script exits 0.

## Creating an Automaton for an app change

1. **Create it.** Call `autonomous_prd_create` with `project_dir` set to this repo's local path and type `mobile-app`, then `autonomous_prd_decompose`.
2. **Point every task's verification at the script.** Put `scripts/automata/verify-app.sh` in each task's spec as its `verification_script`.
3. **Quality gates.** Optionally, set the same command with `autonomous_prd_set_quality_gates`.
4. **Models.**
   - **Execution:** which model does the work. Set it with `autonomous_prd_set_llm`, or per story or task with `_set_story_llm` / `_set_task_llm` (precedence: task > story > PRD > global).
   - **Verification:** which model checks the work (new in v9.0.67). Set it per task, story or PRD with `set_task_verification_llm` / `set_story_verification_llm` / `set_verification_llm`, using the same precedence. Using a **different** model to verify than to execute gives a multi-model review.
5. **Worktree mode.** Prefer worktree mode. In direct mode, the foreign-file-drift guardrail (v9.0.75) flags a task's commit that picks up files that were already modified. Turn on `block_on_file_drift` if hand edits and Automata share the tree.
6. **iOS.** The Swift build needs macOS, so tasks don't verify it. Each Automaton's work goes to a PR, and the CI **Build (iOS Simulator)** check is the gate. To check faster before pushing, run `~/dev/mac-typecheck.sh` on the Mac build host.
7. **Releases** follow AGENT.md as usual: CI must be green before any tag, and the release freezes the operator sets still apply.

## Not available yet

- **Council review of a task's work:** multi-model debate is declared but not wired into task approval.
- **Automation:** a graph of Automata with its own model and budget policy (BL431). This is a design only, so don't build app UI for it until it's approved.
- **Packaged skill for driving Automata over MCP:** paused on the datawatch side.

## App parity follow-up

The new Automaton fields should appear in the app's PRD detail, as they do in the PWA:

- `verification_backend`, `verification_model` and `verification_effort` on Task, Story and PRD (settable);
- `verifier_*` and `verifier_source` on each verification result (read-only);
- `foreign_file_drift` on Task, and `block_on_file_drift`.
