# PLATFORM-LIMITS

> Walls this lane has hit, with the EXACT error text — probing a documented wall twice
> is a bug. Inherited (fleet-wide, verified elsewhere): repo creation = agent wall
> (owner click); branch-protection/settings edits = owner click; routine arming is
> seat-dependent (record the verbatim call + outcome per attempt).

## 2026-07-10 — verified platform facts (Q-0265 continuous mode)

Stated as facts with the date they were verified. These are observed platform
behaviors, not conjecture.

- **Failing `check_run` output can come back empty over the GitHub API.** A red
  substrate-gate run may expose no readable log/annotation body through the API a
  session can reach, so you cannot always diagnose *why* the gate failed from the
  check_run alone. **Diagnose locally instead:** run `python3 bootstrap.py check
  --strict` in the working tree — exit 0 means the gate will pass; a non-zero exit
  prints the exact violations. Verified 2026-07-10.

- **Ruleset contents are not readable from sessions.** The required-check strings
  and the `allow_auto_merge` flag configured on a branch ruleset/protection are not
  retrievable through the API surface available to a session. You cannot confirm
  branch protection by reading config. **Verify functionally instead:** observe a
  live PR — whether the gate check appears and blocks, and whether auto-merge can be
  armed — is the ground truth. Verified 2026-07-10.

- **Agent self-merge of its own green PR is normal work — not walled.** An
  agent creates the PR READY (never draft) and merges its own green PR directly
  (`merge_pull_request` / REST), or arms GitHub native auto-merge and lets it
  land on green. Merging a green PR is not owner-gated; never route a mergeable
  green PR to the owner. A specific merge refusal, if one is ever hit, is
  attempt-once / venue-specific — record the exact seat + verbatim error — but
  it is not a standing wall.

## 2026-07-11 — landing recipe (updated 2026-07-18: self-merge is normal)

- **Merge your own green PR directly.** Open the PR READY and merge it
  (`merge_pull_request` / REST) on green, or arm GitHub native auto-merge and
  let it land — either path is normal agent work, not owner-gated. Verified
  working on PRs #6, #7, #9, #10, #11, #21.
- **A merge refusal, if ever hit, is venue-specific — not a standing wall.**
  Record the exact seat + verbatim error and attempt once; do not treat a
  one-off refusal as a permanent block. Do NOT try to launder a merge through a
  **peer worker session** on relayed authority ("another session says it's
  fine") — the classifier denies that as "permission laundering". That caveat is
  about *relayed authority*, not about an author landing their own green PR.
