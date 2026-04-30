---
id: scala-2-compatibility
title: "Scala 2 Compatibility Design Note"
sidebar_label: "Scala 2 Compatibility"
---

This note captures the current status of Scala 2.13 support for `zio.blocks.streams` and the constraints any compatibility draft must respect.

## Constraint: Scala 3 performance is non-negotiable

The streams implementation relies on Scala 3 features in places that sit directly on hot combinator paths, especially around `Stream` and `Sink` methods that participate in specialization, error-channel elimination, and zero-boxing-friendly code generation.

That means a Scala 2 compatibility layer is only acceptable if it preserves the current Scala 3 shape of hot combinators. In practice, this rules out refactors that introduce an extra trait boundary, move key instance methods out of the Scala 3 class body, or remove performance-critical `inline` definitions just to make signatures compile on Scala 2.

By contrast, adapting surface syntax from Scala 3 `using` to Scala 2 `implicit` is not automatically a problem. The risk is not the spelling of contextual parameters itself — the risk is changing the generated Scala 3 hot path, especially by weakening or removing `inline` behavior on performance-sensitive helpers.

## Rejected experiment: version-specific trait extraction on `Stream` / `Sink`

The first draft extracted several instance methods into `StreamVersionSpecific` and `SinkVersionSpecific` traits under `scala-2/` and `scala-3/`, with the shared classes extending those traits.

The experiment moved or routed methods such as:

- `Stream.++`
- `Stream.catchAll`
- `Stream.catchDefect`
- `Stream.concat`
- `Stream.flatMap`
- `Stream.mapError`
- `Stream.orElse`
- `Stream.&&`
- `Sink.mapError`

This shape looked attractive because it localized syntax differences between Scala 2 and Scala 3, but it changed the structure of the Scala 3 hot path enough to be measurable.

## Benchmark results

Benchmarks were run with `streams-benchmark` on Scala 3.8.3 and JDK 25, comparing the trait-extraction attempt with the `main@origin` baseline.

| Benchmark | Baseline (`main@origin`) | Trait extraction attempt | Result |
|---|---:|---:|---|
| `StreamPipelineBench.zb_flatMap` | 14924.328 ops/s | 1178.997 ops/s | unacceptable regression |
| `StreamPipelineBench.zb_concat` | 26003.818 ops/s | 22044.497 ops/s | regression |
| `StreamPipelineBench.zb_filterMap` | 54812.709 ops/s | 50349.471 ops/s | regression |

The `flatMap` result is the clearest signal: the trait-extraction version dropped from about 14.9k ops/s to about 1.18k ops/s. That regression is large enough to reject the approach outright.

## Accepted conclusion

The version-specific trait-extraction experiment should not be used as the foundation for Scala 2 support.

The current safe direction is:

1. keep Scala 3 hot combinators exactly where they are today;
2. treat removal or weakening of Scala 3 `inline` hot helpers as suspicious and benchmark any such change explicitly;
3. allow `using` / `implicit` compatibility shims where they do not alter the Scala 3 hot path;
4. avoid shared abstractions that change the runtime shape of `Stream` and `Sink` for Scala 3;
5. prefer a fuller per-version split, or another compatibility layer that leaves the Scala 3 implementation structurally unchanged on hot paths.

## Next implementation direction

The next draft should start from one of these shapes:

- a fuller per-version split for `Stream` and `Sink`, where Scala 3 keeps the existing method bodies in place and Scala 2 gets its own implementation surface;
- a package-level compatibility layer for syntax or type encoding differences, but only if the Scala 3 classes remain unchanged on performance-critical paths.

Before accepting any future draft, re-run `streams-benchmark`, with special attention to:

- `StreamPipelineBench.zb_flatMap`
- `StreamPipelineBench.zb_concat`
- `StreamPipelineBench.zb_filterMap`

## Benchmark log locations

The measurements backing this note were recorded in:

- `.git/agent-logs/streams-bench-baseline-pipeline.log`
- `.git/agent-logs/streams-bench-current-pipeline.log`
