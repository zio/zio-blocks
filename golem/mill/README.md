# zio-golem mill plugin (auto-registration)

This directory contains a Mill plugin that mirrors the sbt `GolemPlugin` behavior: it scans Scala sources for
`@agentImplementation` classes and generates a Scala source file that registers them at module load time (Scala.js).

This plugin is intentionally small and self-contained.

## Status

- See `golem/docs/supported-versions.md` for the intended supported Mill versions.

