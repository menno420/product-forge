# Merge-automation verification probe (2026-07-15)

This file was added by an ordinary, non-workflow content PR to verify whether
this repo's merge-on-green mechanism lands a normal code/doc PR on green CI
with zero human click. It is safe to delete once the audit that created it
has read the result.

Context: at the time this file was added, `.github/workflows/merge-on-green.yml`
had just been proposed in PR #25 (branch `ci/merge-on-green`) but was still
awaiting the owner's one-time manual merge, because it touches
`.github/workflows/**` and the new workflow deliberately refuses to merge
PRs that touch workflow files. This probe PR touches no workflow files, so
if any merge-on-green mechanism is already live, it should be free to act
on this one.
