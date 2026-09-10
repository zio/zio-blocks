---
id: specialization-migration
title: "Specialization Migration Notes"
---

These notes cover source-visible changes from the representation-propagation update.

- **Evidence follows transformed results.** Do not pass or require `JvmType.Infer` for an existing stream/pipeline input. `map`, `collect`, zip, concat, and recovery ask for evidence for the resulting element type; type-preserving pipeline stages reuse the representation already present.
- **Byte sources are genuinely byte-specialized.** `Stream.succeed(a: Byte)` now uses the Byte reader and records `JvmType.Byte`. Code that accidentally depended on Byte values taking a boxed/reference path should remove that assumption.
- **Concat, zip, and recovery have variance-safe result signatures.** Concat uses lower-bounded left input plus separate right and output types (`A0 >: A, A2, A3`) and `Infer[A3]`; zip uses separate right/result types (`B, C`) and `Infer[C]`; recovery widens to `A1 >: A` and uses `Infer[A1]`. Let these infer normally. Remove casts, invariant wrappers, or input-side evidence added to compensate for older signatures.
- **Sink contramap evidence describes the value fed to the original sink.** For `contramap[A0 <: A, A2](A2 => A0)`, the required evidence is `JvmType.Infer[A0]`, not evidence for the new external input `A2`.
- **Long/Double bulk EOF is out of band.** Bulk reads return a positive count, `-1` at EOF, and `0` only for a zero-length request. Every `Long` and `Double` bit pattern remains valid data; do not reserve a data value as an EOF sentinel.

These rules apply through both pipeline application routes: `stream.via(pipe)` and `pipe.andThenSink(sink)` / `pipe.applyToSink(sink)`.
