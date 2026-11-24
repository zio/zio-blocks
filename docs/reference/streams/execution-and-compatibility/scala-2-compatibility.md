---
id: scala-2-compatibility
title: "Scala 2 Compatibility (History)"
sidebar_label: "Scala 2 Compatibility"
description: "Streams supported Scala 2.13 until the 2.13 lane was reduced to the foundation modules. This note records the design constraints that shaped that support."
keywords:
  - "Scala 2 Compatibility"
  - "Cross Compilation"
  - "History"
  - "Shared Source Set"
---

Streams ships for Scala 3.9 and 3.10 only. Scala 2.13 support ended when the 2.13 lane in
`zio-blocks` was reduced to the five foundation modules — `chunk`, `typeid`, `maybe`, `markdown`
and `schema` — which do not include streams. No 2.13 artifact is published for this module.

## History

When Scala 2.13 support was introduced it was a hard requirement, not a convenience. HTTP data
types in `zio-blocks` are built on streams, and `zio-http` 4 depended on those types, so a Scala 2
HTTP stack needed a Scala 2 streams build.

The support was built so that the Scala 3 hot path stayed structurally unchanged:

- No moving hot instance methods out of the Scala 3 class body into a shared trait.
- No weakening of `inline` definitions to accommodate Scala 2.
- No extra trait boundaries that would alter generated Scala 3 bytecode.
- Only surface syntax differed between the two trees (`implicit` versus `using`, `_` versus `*`).

Streams compiled from a single shared source set; only `LowPriorityJvmTypeInferPlatform.scala` and
`internal/InternalVersionSpecific.scala` had `scala-2` and `scala-3` copies. Those copies have since
been folded into the shared sources.
