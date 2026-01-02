# ZIO Blocks - Chunk Module Port

## Overview
This PR introduces a new, pure `zio.blocks.chunk` module. It replicates the high-performance `Chunk` data structure from ZIO Core but removes all dependencies on the ZIO effect system (`ZIO`, `UIO`, `Task`, etc.) and other runtime libraries (like Izumi).

## Key Features
- **Pure Implementation**: `Chunk` is now a completely pure data structure. All effectful methods (e.g., `mapZIO`, `filterZIO`) have been removed.
- **Single-File Source**: The entire `Chunk` implementation (including `ChunkBuilder`, `NonEmptyChunk`, etc.) is consolidated into a single file: `chunk/shared/src/main/scala/zio/blocks/chunk/Chunk.scala`.
- **Zero Runtime Dependencies**: The `chunk` module has no library dependencies in the `Compile` scope. It only depends on `zio-test` for testing.
- **Cross-Platform**: Supports both JVM and Scala.js.
- **Scala Versions**: Configured for Scala 2.13.18 and Scala 3.7.4 (satisfying the 3.5+ requirement).
- **Benchmarks**: Includes a JVM-only JMH benchmark suite to ensure performance parity.

## Build Targets
- `chunkJVM`: JVM artifact.
- `chunkJS`: Scala.js artifact.
- `chunkBenchmarks`: JMH benchmarks (JVM only).

## Verification
To verify the changes, run the following commands (requires SBT):

1.  **Format Code**:
    ```bash
    sbt fmt
    ```

2.  **Run JVM Tests**:
    ```bash
    sbt chunkJVM/test
    ```

3.  **Run JS Tests**:
    ```bash
    sbt chunkJS/test
    ```

4.  **Run Benchmarks**:
    ```bash
    sbt chunkBenchmarks/jmh:run
    ```

## Migration Notes
- The package has changed from `zio` to `zio.blocks.chunk`.
- `NonEmptyChunk` now extends `Iterable[A]` directly.
- All ZIO-related methods are gone; users should use standard functional transformations (`map`, `flatMap`, `foldLeft`) and wrap in effects at the usage site if necessary.
