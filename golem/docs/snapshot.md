# Snapshot Helpers

Golem components that opt into snapshot-based state updates must expose two top-level functions:

- `saveSnapshot(): Promise<ArrayBuffer>`: Captures the current state
- `loadSnapshot(bytes: ArrayBuffer): Promise<void>`: Restores from a previous snapshot

Implementing these exports manually in every module is tedious and error-prone. The `SnapshotExports` helper automates
this wiring.

## Table of Contents

- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Working with Typed Data](#working-with-typed-data)
- [Examples in the Codebase](#examples-in-the-codebase)

---

## Quick Start

Call `SnapshotExports.configure` once during module initialization to install your save/load handlers:

```scala
import golem.runtime.snapshot.SnapshotExports
import scala.concurrent.Future
import scala.scalajs.js.typedarray.Uint8Array

// Configure snapshot handlers
SnapshotExports.configure(
  save = () => {
    // Serialize your state to bytes
    val stateBytes = serializeState()
    Future.successful(stateBytes)
  },
  load = bytes => {
    // Deserialize and restore state
    restoreState(bytes)
    Future.successful(())
  }
)
```

The helper publishes both host-facing functions automatically. If you don't configure handlers, the default behavior
returns empty snapshots and ignores incoming payloads.

---

## API Reference

### `SnapshotExports.configure`

```scala
def configure(save: () => Future[Uint8Array],
              load: Uint8Array => Future[Unit]): Unit
```

Registers Scala `Future`-based handlers for snapshot save/load operations.

**Parameters:**

- `save` - Function that serializes current state to bytes
- `load` - Function that restores state from bytes

### `SnapshotExports.configureJs`

```scala
def configureJs(save: () => js.Promise[Uint8Array],
                load: Uint8Array => js.Promise[Unit]): Unit
```

Same as `configure`, but accepts JavaScript `Promise`-based handlers for interop scenarios.

---

## Working with Typed Data

For typed data instead of raw byte arrays, leverage the existing data codecs:

### Binary Segments

Use `BinarySegment` with `AllowedMimeTypes` for binary payloads with MIME type enforcement:

```scala
import golem.data.unstructured.{BinarySegment, AllowedMimeTypes}
import golem.runtime.annotations.mimeType

enum SnapshotMime:
  @mimeType("application/octet-stream")
  case OctetStream

object SnapshotMime:
  given AllowedMimeTypes[SnapshotMime] =
    golem.runtime.macros.AllowedMimeTypesDerivation.derived

// Create a typed binary segment
val snapshot = BinarySegment.inline[SnapshotMime](stateBytes, "application/octet-stream")
```

### Text Segments

Use `TextSegment` with `AllowedLanguages` for text-based snapshots:

```scala
import golem.data.unstructured.{TextSegment, AllowedLanguages}

val jsonSnapshot = TextSegment.inline[AllowedLanguages.Any](
  stateJson,
  languageCode = None
)
```

### Case Class Serialization

For structured data, use `GolemSchema` derivation:

```scala
import golem.data.GolemSchema
import zio.blocks.schema.Schema

final case class MyState(counter: Int, name: String)

object MyState {
  implicit val schema: Schema[MyState] = Schema.derived
}

// The GolemSchema is automatically derived from the ZIO Schema
val schema = summon[GolemSchema[MyState]]
```

---

## Examples in the Codebase

The snapshot helper is used throughout the codebase:

| Module        | Usage                                   |
|---------------|-----------------------------------------|
| `examples/js` | Demo agents with configurable snapshots |

### Complete Example

```scala
import golem.runtime.snapshot.SnapshotExports
import scala.concurrent.Future
import scala.scalajs.js.typedarray.Uint8Array
import java.nio.charset.StandardCharsets

// State to persist
private var state: String = ""

SnapshotExports.configure(
  save = () => {
    val bytes = state.getBytes(StandardCharsets.UTF_8)
    val array = new Uint8Array(bytes.length)
    bytes.zipWithIndex.foreach { case (b, i) =>
      array(i) = (b & 0xff).toShort
    }
    Future.successful(array)
  },
  load = bytes => {
    val byteArray = new Array[Byte](bytes.length)
    for (i <- 0 until bytes.length) {
      byteArray(i) = bytes(i).toByte
    }
    state = new String(byteArray, StandardCharsets.UTF_8)
    Future.successful(())
  }
)
```

---

## Best Practices

1. **Initialize early** - Call `configure` at module load time before any agent methods execute
2. **Handle errors gracefully** - Return failed futures for serialization errors rather than throwing
3. **Keep snapshots small** - Large snapshots impact component startup time
4. **Use typed schemas** - Leverage `GolemSchema` for type-safe serialization
5. **Test round-trips** - Verify that `save` → `load` produces equivalent state
