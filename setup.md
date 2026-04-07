# ZIO Blocks — Setup Guide

## Prerequisites

- **JDK 11+** (JDK 17 recommended)
- **sbt** (Scala Build Tool)

Install via sdkman:

```bash
sdk install java 17.0.9-tem
sdk install sbt
```

Or on Arch Linux:

```bash
sudo pacman -S jdk17-openjdk sbt
```

---

## Clone & Enter

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

---

## Common sbt Commands

Start the sbt shell (recommended — avoids JVM startup overhead on each command):

```bash
sbt
```

Compile everything:

```bash
sbt compile
```

Run all tests:

```bash
sbt test
```

Run tests for a specific module (e.g. schema on JVM):

```bash
sbt schemaJVM/test
```

Cross-build for both Scala 2.13 and 3.x:

```bash
sbt +test
```

Format code:

```bash
sbt scalafmt
```

Check formatting without applying:

```bash
sbt scalafmtCheck
```

Run benchmarks (JMH):

```bash
sbt "schemaJVM/jmh:run -i 3 -wi 3 -f1 -t1"
```

Check for dependency updates:

```bash
sbt ";dependencyUpdates; reload plugins; dependencyUpdates; reload return"
```

---

## Module Structure

Most modules have JVM and JS variants via `sbt-scalajs-crossproject`:

- `schemaJVM` / `schemaJS`
- `chunkJVM` / `chunkJS`
- `scopeJVM` / `scopeJS`

---

## Docs

The docs use `zio-sbt-website` with mdoc. The `docs/package.json` is a placeholder only — there is no local Docusaurus dev server in this repo.

To compile and validate Scala code blocks in the docs:

```bash
sbt docs/mdoc
```
