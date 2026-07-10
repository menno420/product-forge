# products/ — one product, one subtree

Every product lives in `products/<slug>/`, self-contained: own README (what it is · how
to run it · honest state: working / alpha / released), own tests, a runnable or
releasable artifact, own pinned deps inside the subtree (the repo root stays
stdlib-only). **No cross-product imports.** A new subtree is created only by a routed
`control/inbox.md` ORDER — never invented here. Graduation: a proven product moves to
its own repo (owner click) and becomes a lane.

*(no products yet — the first routed ORDER creates the first subtree)*
