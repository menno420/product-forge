# control/claims — order claim state

> **Status:** `reference`

Order claims in this project are tracked inline on the `control/status.md` `orders:` line
via a `claimed-by: <ids> <lane-or-session> <ISO8601>` annotation (see `control/README.md` →
"Claiming an order"), which the executor drops when the ids move into `done=`.

**No active claims** as of the 2026-07-11 close-out: ORDERs 001–004 are all `done=`, the
`orders:` line carries no `claimed-by:` annotation, and there are zero open PRs or in-flight
build branches.
