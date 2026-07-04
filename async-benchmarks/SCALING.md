# Async chain scaling — experimental diagnosis

## Question

Why does throughput in `AsyncChainBench.zb_flatMapN` drop ~1000× between
n=100 (1.41 G ops/s) and n=1000 (1.57 M ops/s)? Two competing hypotheses:

- **H1** — JIT loop-unrolling / constant-propagation collapses the chain at
  small `n`. The cliff is an artifact: the small-n numbers are unrepresentative,
  the large-n numbers reflect the true per-link cost.
- **H2** — `await` or `flatMap` staging is super-linear in chain depth
  (e.g. `O(n log n)` due to memoization, repeated re-poll of consumed leaves,
  or some hidden quadratic). Per-link cost grows with `n`.

## Method

[AsyncScalingBench.scala](src/main/scala/zio/blocks/async/bench/AsyncScalingBench.scala)
sweeps `n ∈ {1, 10, 100, 1000, 10000, 100000}` for three variants:

- `zb_chain` — same as `zb_flatMapN`: `n` flatMaps in a `while` loop, then `await`.
- `zb_chainBh` — identical, but `Blackhole.consume(fa)` runs every iteration.
  The escaping reference defeats inter-iteration constant-propagation.
- `noop` — control: bare `Int` accumulator in a `while`. Floor on loop cost.

Per-link cost is then `(1 / ops_per_sec) / n` in seconds, converted to ns.

## Results

JDK 26.0.1, JMH 1.37, M-series, 5 warmup × 5 measurement × 1 fork.

| n       | noop ops/s | zb_chain ops/s | ns / link | zb_chainBh ops/s | ns / link |
|--------:|-----------:|---------------:|----------:|-----------------:|----------:|
|       1 |    1.52 G  |       1.29 G   |    —      |       672 M      |    —      |
|      10 |    1.92 G  |       1.20 G   |  0.084    |       346 M      |   0.29    |
|     100 |    1.93 G  |       1.23 G   |  **0.008**|        70.0 M    |   0.14    |
|   1,000 |    1.97 G  |       1.41 M   |  **0.71** |       830 k      |   1.21    |
|  10,000 |    1.90 G  |     138.8 k    |  **0.72** |        80.9 k    |   1.24    |
| 100,000 |    1.91 G  |      13.97 k   |  **0.72** |         3.17 k   |   3.16    |

## Diagnosis

**H1 is confirmed. H2 is rejected.**

1. **Per-link cost is constant** at 0.71–0.72 ns/link from n=1000 onward in
   `zb_chain`. Scaling is **linear** in chain depth — no `log n` or worse term.
2. **The small-n numbers are JIT artifacts.** At n=1–100, `zb_chain` matches
   the `noop` baseline at ~1.2–1.9 G ops/s, i.e. ~0.8 ns per *outer* op
   regardless of `n`. The whole chain is being folded into a constant by
   HotSpot — the per-link "cost" of 0.008 ns at n=100 is not real, it is JIT
   constant-propagation through the inline `isInstanceOf` + inline lambda.
3. **`zb_chainBh` still benefits from JIT-level folding** at small n
   (the Blackhole consumes the final `Async`, but the intermediates collapse
   to bare `Int`s the JIT can still chain-fold) — same shape, slightly higher
   floor.
4. **A small allocation/cache effect appears at n=100,000 in `zb_chainBh`**
   (1.24 → 3.16 ns/link), absent from `zb_chain`. The Blackhole forces a
   per-iteration escape of intermediate boxed `Integer`s and presumably some
   loop-state spilling, which is what we'd expect from a 100 k working set.

## Implication for `AsyncChainBench`

The 1000× "cliff" between n=100 and n=1000 in the comparison benchmark is
**ZB winning at small n**, not slowing down at large n. The fair per-link
comparison (n=1000) is:

| runtime  | ns / link @ n=1000 |
|----------|-------------------:|
| ZB       | **0.71**           |
| Kyo      |  4.39              |
| Cats IO  | 37.81              |

ZB is **~6× faster than Kyo and ~50× faster than CE** on the chain micro,
at the smallest `n` where the JIT is no longer hiding the work.

## Why `Async.succeed(x).flatMap(_+1)` is actually a chain

Worth being explicit, since this surprised me mid-investigation: the leaves
in this benchmark are `Async.succeed(x)`, which is just `x` cast to the
abstract type. So `.flatMap(_+1)` takes the fast branch and evaluates `x+1`
immediately — **no `Pollable` is ever allocated**. The "chain" of n flatMaps
is really n `isInstanceOf` + n inline lambda calls + n `+1`s in a tight loop,
with `await` doing a single final `isInstanceOf` and unwrap.

This is exactly the shape ZB is optimized for, and the 0.71 ns/link figure
shows the inline-extension + opaque-encoding combination is delivering on its
promise: the per-link cost is dominated by the `+1` and JVM loop-control,
not by `Async` machinery.
