---
id: patch
title: "Patching"
---

The Patching system in ZIO Blocks provides a type-safe, serializable way to describe and apply transformations to data structures. Unlike direct mutations or lens-based updates, patches are **first-class values** that can be serialized, transmitted over the network, stored for audit logs, and composed together.

## Overview

A `Patch[S]` represents a sequence of operations that transform a value of type `S`. Because patches use serializable operations and reflective optics for navigation, they enable powerful use cases:

- **Remote Patching** — Send patches over the network to update remote state without transmitting entire objects
- **Audit Logs** — Record patches as a log of changes for compliance, debugging, or undo functionality
- **CRDT-like Operations** — Use commutative operations like `Patch.increment` that can be safely applied in any order
- **Optimistic Updates** — Apply patches locally while syncing with a server
- **Schema Evolution** — Patches address fields by name, so a stored patch still applies after a field is added, but fails in `Strict` mode once a field it touches is renamed or removed

:::note
For **untyped JSON patching** without a schema, use [`JsonPatch`](./built-in-codecs/json/json-patch.md) instead. `JsonPatch` is optimized for diff-and-apply workflows on raw JSON values and provides compact delta representations without requiring typed optics.

For an end-to-end workflow (diffing two versions, storing patches as JSON, choosing a `PatchMode` when the data changed, and replaying an edit history), see [How to Compute, Store and Replay Changes with Diff and Patch](../../guides/diff-and-patch.md).
:::

To build a patch, we point smart constructors at fields through [optics](./optics.md), compose the results with `Patch#++`, and apply the composed patch to a value:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Person(name: String, age: Int)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val name: Lens[Person, String] = optic(_.name)
  val age: Lens[Person, Int] = optic(_.age)
}

// Create patches using smart constructors
val patch1 = Patch.set(Person.name, "John")
val patch2 = Patch.increment(Person.age, 1)

// Compose patches
val combined = patch1 ++ patch2

val jane = Person("Jane", 25)
```

Applying the composed patch to `jane` sets the name and adds one to the age:

```scala mdoc
combined(jane)
```

## Creating Patches

The `Patch` companion object provides smart constructors for patches. Each constructor takes an optic to specify the target location and the operation parameters.

### Setting Values

`Patch.set` replaces the value at the specified location:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Address(street: String, city: String, zip: String)
object Address extends CompanionOptics[Address] {
  implicit val schema: Schema[Address] = Schema.derived
  val street: Lens[Address, String] = optic(_.street)
  val city: Lens[Address, String] = optic(_.city)
}

case class Person(name: String, address: Address)
object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived
  val name: Lens[Person, String] = optic(_.name)
  val address: Lens[Person, Address] = optic(_.address)
  val city: Lens[Person, String] = optic(_.address.city)
}

// Set a simple field
val setName = Patch.set(Person.name, "Alice")

// Set a nested field
val setCity = Patch.set(Person.city, "San Francisco")

// Set an entire nested record
val newAddress = Address("123 Main St", "NYC", "10001")
val setAddress = Patch.set(Person.address, newAddress)
```

Setting the nested `city` field leaves the rest of the address untouched:

```scala mdoc
setCity(Person("Bob", Address("1 Market St", "Boston", "02108")))
```

`Patch.set` accepts every optic type. What happens when the optic does not match the value depends on the [application mode](#application-modes):

- **Lens** — Sets a single field.
- **Optional** — Sets the value when the path exists. When it does not (for example, the path goes through a case the value is not), `Strict` mode fails and `Lenient` and `Clobber` leave the value unchanged.
- **Traversal** — Sets every matching element to the same value. On an empty sequence, `Strict` mode fails with `encountered an empty sequence` and the other modes leave the value unchanged.
- **Prism** — Replaces the value only when it is already that case. When the value is another case, `Strict` mode fails and `Lenient` and `Clobber` leave the value unchanged; `Patch.set` never switches a value from one case to another.

To see the prism rule, we define a sum type and a patch that sets the `Circle` case:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

sealed trait Shape
case class Circle(radius: Double) extends Shape
case class Square(side: Double) extends Shape

object Shape extends CompanionOptics[Shape] {
  implicit val schema: Schema[Shape] = Schema.derived
  val circle: Prism[Shape, Circle] = optic(_.when[Circle])
}

val setCircle = Patch.set(Shape.circle, Circle(5.0))
```

The patch replaces a circle, fails on a square in `Strict` mode, and leaves the square alone in `Lenient` mode:

```scala mdoc
setCircle(Circle(1.0), PatchMode.Strict)
println(setCircle(Square(2.0), PatchMode.Strict))
setCircle(Square(2.0), PatchMode.Lenient)
```

### Numeric Increments

`Patch.increment` adds a delta to numeric fields. This is a **commutative operation** — applying increments in any order produces the same result, making it ideal for distributed systems:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Counter(count: Int, total: Long, balance: BigDecimal)
object Counter extends CompanionOptics[Counter] {
  implicit val schema: Schema[Counter] = Schema.derived
  val count: Lens[Counter, Int] = optic(_.count)
  val total: Lens[Counter, Long] = optic(_.total)
  val balance: Lens[Counter, BigDecimal] = optic(_.balance)
}

// Increment various numeric types
val addOne = Patch.increment(Counter.count, 1)
val addTen = Patch.increment(Counter.count, 10)
val subtractFive = Patch.increment(Counter.count, -5)

// Works with all numeric types
val addToTotal = Patch.increment(Counter.total, 1000L)
val addToBalance = Patch.increment(Counter.balance, BigDecimal("99.99"))

// Compose multiple increments
val combined = addOne ++ addTen ++ subtractFive
val counter = Counter(0, 0L, BigDecimal(0))
```

The three increments on `count` add up to 6, and the other fields stay as they were:

```scala mdoc
combined(counter)
```

Supported numeric types:

- `Int`, `Long`, `Short`, `Byte`
- `Float`, `Double`
- `BigInt`, `BigDecimal`

### Temporal Operations

Patches support temporal arithmetic with `Patch.addDuration`, `Patch.addPeriod` and `Patch.addPeriodAndDuration`:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._
import java.time._

case class Event(
  timestamp: Instant,
  scheduledDate: LocalDate,
  scheduledTime: LocalDateTime,
  duration: Duration
)
object Event extends CompanionOptics[Event] {
  implicit val schema: Schema[Event] = Schema.derived
  val timestamp: Lens[Event, Instant] = optic(_.timestamp)
  val scheduledDate: Lens[Event, LocalDate] = optic(_.scheduledDate)
  val scheduledTime: Lens[Event, LocalDateTime] = optic(_.scheduledTime)
  val duration: Lens[Event, Duration] = optic(_.duration)
}

// Add duration to an Instant
val postpone1Hour = Patch.addDuration(Event.timestamp, Duration.ofHours(1))

// Add period to a LocalDate
val postpone1Week = Patch.addPeriod(Event.scheduledDate, Period.ofWeeks(1))

// Add both period and duration to a LocalDateTime
val postpone = Patch.addPeriodAndDuration(
  Event.scheduledTime,
  Period.ofDays(1),
  Duration.ofHours(2)
)

// Add duration to a Duration field
val extendDuration = Patch.addDuration(Event.duration, Duration.ofMinutes(30))

val event = Event(
  Instant.parse("2026-03-01T09:00:00Z"),
  LocalDate.of(2026, 3, 1),
  LocalDateTime.of(2026, 3, 1, 9, 0),
  Duration.ofMinutes(60)
)
```

Applying all four patches shifts every field by its own amount:

```scala mdoc
(postpone1Hour ++ postpone1Week ++ postpone ++ extendDuration)(event)
```

### String Edits

`Patch.editString` applies character-level edits to strings. It takes the edits as a [`Chunk`](../chunk.md) of `Patch.StringOp` values:

```scala mdoc:silent:reset
import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Document(content: String)
object Document extends CompanionOptics[Document] {
  implicit val schema: Schema[Document] = Schema.derived
  val content: Lens[Document, String] = optic(_.content)
}

// Insert text at position
val insertHello = Patch.editString(
  Document.content,
  Chunk(Patch.StringOp.Insert(0, "Hello "))
)

// Delete characters
val deleteFirst5 = Patch.editString(
  Document.content,
  Chunk(Patch.StringOp.Delete(0, 5))
)

// Append text
val appendBang = Patch.editString(
  Document.content,
  Chunk(Patch.StringOp.Append("!"))
)

// Replace a range (delete then insert)
val replaceWorld = Patch.editString(
  Document.content,
  Chunk(Patch.StringOp.Modify(6, 5, "Universe"))
)

// Combine multiple edits (applied in sequence)
val edits = Patch.editString(
  Document.content,
  Chunk(
    Patch.StringOp.Delete(5, 6),
    Patch.StringOp.Append(" there!")
  )
)

val doc = Document("Hello World")
```

Each patch edits the content of the document in place:

```scala mdoc
insertHello(Document("World"))
deleteFirst5(doc)
appendBang(doc)
replaceWorld(doc)
edits(doc)
```

String edit operations:

- `Patch.StringOp.Insert(index, text)` — Insert text at the given position
- `Patch.StringOp.Delete(index, length)` — Delete characters starting at index
- `Patch.StringOp.Append(text)` — Append text to the end
- `Patch.StringOp.Modify(index, length, text)` — Replace a range with new text

## Working with Collections

The sequence operations add, insert, delete and modify elements of `Vector`, `List`, `Seq` and `IndexedSeq` fields.

### Appending Elements

`Patch.append` adds elements to the end of a sequence:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class TodoList(items: Vector[String])
object TodoList extends CompanionOptics[TodoList] {
  implicit val schema: Schema[TodoList] = Schema.derived
  val items: Lens[TodoList, Vector[String]] = optic(_.items)
}

val addItems = Patch.append(
  TodoList.items,
  Vector("Buy groceries", "Walk the dog")
)
```

The new items land after the existing one:

```scala mdoc
addItems(TodoList(Vector("Existing task")))
```

`Patch.append`, `Patch.insertAt`, `Patch.deleteAt` and `Patch.modifyAt` each have an overload for `Vector`, `List`, `Seq` and `IndexedSeq` fields. The compiler picks the overload from the field's type, so no import is needed. `LazyList` fields are not supported: `Schema.derived` cannot derive a schema for a case class with a `LazyList` field.

### Inserting at Index

`Patch.insertAt` inserts elements at a specific position:

```scala mdoc:silent
val insertAtStart = Patch.insertAt(
  TodoList.items,
  0,
  Vector("First priority")
)

val insertInMiddle = Patch.insertAt(
  TodoList.items,
  1,
  Vector("Second item", "Third item")
)
```

Inserting at index 1 shifts `"B"` and `"C"` to the right:

```scala mdoc
insertInMiddle(TodoList(Vector("A", "B", "C")))
```

### Deleting Elements

`Patch.deleteAt` removes `count` elements starting at an index:

```scala mdoc:silent
// Delete one element at index 1
val deleteOne = Patch.deleteAt(TodoList.items, 1, 1)

// Delete three elements starting at index 0
val deleteThree = Patch.deleteAt(TodoList.items, 0, 3)

val abcd = TodoList(Vector("A", "B", "C", "D"))
```

Applying both patches to the same four-element list:

```scala mdoc
deleteOne(abcd)
deleteThree(abcd)
```

### Modifying Elements

`Patch.modifyAt` applies a nested patch to the element at a specific index:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Task(title: String, priority: Int)
object Task extends CompanionOptics[Task] {
  implicit val schema: Schema[Task] = Schema.derived
  val title: Lens[Task, String] = optic(_.title)
  val priority: Lens[Task, Int] = optic(_.priority)
}

case class Project(tasks: Vector[Task])
object Project extends CompanionOptics[Project] {
  implicit val schema: Schema[Project] = Schema.derived
  val tasks: Lens[Project, Vector[Task]] = optic(_.tasks)
}

// Create a patch for the nested Task type
val increasePriority = Patch.increment(Task.priority, 1)

// Modify the task at index 0
val modifyFirst = Patch.modifyAt(Project.tasks, 0, increasePriority)

val project = Project(Vector(
  Task("Build feature", 1),
  Task("Write tests", 2)
))
```

Only the first task's priority changes:

```scala mdoc
modifyFirst(project)
```

## Working with Maps

The map operations add, remove and modify entries of `Map` fields by key.

### Adding Keys

`Patch.addKey` adds a new key-value pair to a map:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Config(settings: Map[String, Int])
object Config extends CompanionOptics[Config] {
  implicit val schema: Schema[Config] = Schema.derived
  val settings: Lens[Config, Map[String, Int]] = optic(_.settings)
}

val addTimeout = Patch.addKey(Config.settings, "timeout", 30)
val addRetries = Patch.addKey(Config.settings, "retries", 3)

val combined = addTimeout ++ addRetries
```

Both keys are added next to the existing `port` entry:

```scala mdoc
combined(Config(Map("port" -> 8080)))
```

### Removing Keys

`Patch.removeKey` removes a key from a map:

```scala mdoc:silent
val removePort = Patch.removeKey(Config.settings, "port")
```

Removing `port` keeps the other entry:

```scala mdoc
removePort(Config(Map("port" -> 8080, "timeout" -> 30)))
```

### Modifying Values

`Patch.modifyKey` applies a nested patch to the value at a specific key. The nested patch is a `Patch[Int]` here; the simplest way to get one that adds 10 is to diff `0` against `10`, which produces a numeric delta:

```scala mdoc:silent
case class UserSettings(preferences: Map[String, Int])
object UserSettings extends CompanionOptics[UserSettings] {
  implicit val schema: Schema[UserSettings] = Schema.derived
  val preferences: Lens[UserSettings, Map[String, Int]] = optic(_.preferences)
}

// A Patch[Int] that adds 10
val incrementValue: Patch[Int] = Schema[Int].diff(0, 10)

val increaseVolume = Patch.modifyKey(UserSettings.preferences, "volume", incrementValue)

val settings = UserSettings(Map("volume" -> 50, "brightness" -> 80))
```

The nested patch is a delta of `+10`, and only the `volume` entry changes:

```scala mdoc
println(incrementValue)
increaseVolume(settings)
```

## Composing Patches

Patches compose with the `Patch#++` operator. The result applies the first patch, then the second:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Account(name: String, balance: Int, active: Boolean)
object Account extends CompanionOptics[Account] {
  implicit val schema: Schema[Account] = Schema.derived
  val name: Lens[Account, String] = optic(_.name)
  val balance: Lens[Account, Int] = optic(_.balance)
  val active: Lens[Account, Boolean] = optic(_.active)
}

val rename = Patch.set(Account.name, "Premium Account")
val deposit = Patch.increment(Account.balance, 100)
val activate = Patch.set(Account.active, true)

// Compose all patches
val upgrade = rename ++ deposit ++ activate

val account = Account("Basic", 50, false)
```

The composed patch renames, deposits and activates in one step:

```scala mdoc
upgrade(account)
```

Composition is associative: `(a ++ b) ++ c` equals `a ++ (b ++ c)`.

`Patch.empty` is the identity element, and `Patch#isEmpty` reports whether a patch has no operations:

```scala mdoc:silent
val noChange = Patch.empty[Account]
```

Composing with the empty patch on either side changes nothing:

```scala mdoc
(deposit ++ noChange)(account) == deposit(account)
(noChange ++ deposit)(account) == deposit(account)
noChange.isEmpty
```

## Applying Patches

We apply a patch with `Patch#apply`, with or without an explicit `PatchMode`, or with `Patch#applyOption`.

### Basic Application

The simplest way to apply a patch is `Patch#apply(value)`, which applies in `Lenient` mode: it skips operations whose preconditions fail and applies the rest. It returns the original value only when it cannot rebuild a value of the type from the result:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Account(name: String, balance: Int, active: Boolean)
object Account extends CompanionOptics[Account] {
  implicit val schema: Schema[Account] = Schema.derived
  val balance: Lens[Account, Int] = optic(_.balance)
}

val addHundred = Patch.increment(Account.balance, 100)
val testAccount = Account("Test", 50, true)
```

Applying the patch returns the updated value directly:

```scala mdoc
addHundred(testAccount)
```

### Application Modes

For more control, use the overload that takes a `PatchMode`. It returns an `Either` of a [`SchemaError`](./schema-error.md) or the patched value:

```scala mdoc
addHundred(testAccount, PatchMode.Strict)
```

The three modes differ only in how they treat an operation whose precondition fails.

#### PatchMode.Strict

`Strict` fails on the first operation whose structural precondition fails: an index out of range, a missing map key, a map key that already exists, a case mismatch, or a string index out of range:

```scala mdoc:silent
case class Data(items: Vector[Int])
object Data extends CompanionOptics[Data] {
  implicit val schema: Schema[Data] = Schema.derived
  val items: Lens[Data, Vector[Int]] = optic(_.items)
}

// Try to delete at an invalid index
val badDelete = Patch.deleteAt(Data.items, 10, 1)
val data = Data(Vector(1, 2, 3))
```

`Strict` reports the out-of-range delete, while `Lenient` skips it:

```scala mdoc
println(badDelete(data, PatchMode.Strict))
badDelete(data, PatchMode.Lenient)
```

Use `Strict` when you need to know if every operation succeeded.

:::warning
`Strict` checks structure, not content. It does not detect that the target value changed since the patch was made: an index-based delete removes whatever sits at that index now, and a numeric delta adds to the current number. For example, a diff that removes the element `2` from `Vector(1, 2, 3)` records "delete index 1":

```scala mdoc:silent
val removeTwo = Schema[Data].diff(Data(Vector(1, 2, 3)), Data(Vector(1, 3)))
```

If someone has meanwhile prepended `0`, index 1 now holds `1`, and `Strict` deletes it without complaint:

```scala mdoc
println(removeTwo)
removeTwo(Data(Vector(0, 1, 2, 3)), PatchMode.Strict)
```
:::

#### PatchMode.Lenient

`Lenient` skips operations that fail preconditions and continues with the rest:

```scala mdoc:silent
val patch1 = Patch.deleteAt(Data.items, 10, 1)    // out of range for 3 elements
val patch2 = Patch.append(Data.items, Vector(4))

val combined = patch1 ++ patch2
```

`Lenient` skips the invalid delete and still appends, while `Strict` stops at the delete:

```scala mdoc
combined(data, PatchMode.Lenient)
println(combined(data, PatchMode.Strict))
```

Use `Lenient` for best-effort patching where partial success is acceptable.

#### PatchMode.Clobber

`Clobber` behaves like `Lenient` except in three places:

- `Patch.addKey` for a key that already exists overwrites the existing value instead of failing.
- `Patch.removeKey` for a missing key is a no-op, which is the same observable result as `Lenient`.
- Out-of-range `Patch.insertAt` and `Patch.deleteAt` are clamped to the sequence instead of failing: inserting past the end appends, and deleting past the end removes only the elements that exist.

Everything else that fails a precondition, such as `Patch.modifyAt` out of range, `Patch.modifyKey` on a missing key, an out-of-range string edit, or a prism case mismatch, is skipped exactly as in `Lenient`. To compare the modes, we add a key that already exists and insert past the end of a sequence:

```scala mdoc:silent
case class Config(settings: Map[String, Int])
object Config extends CompanionOptics[Config] {
  implicit val schema: Schema[Config] = Schema.derived
  val settings: Lens[Config, Map[String, Int]] = optic(_.settings)
}

// Adding a key that already exists
val addExisting = Patch.addKey(Config.settings, "port", 9000)
val config = Config(Map("port" -> 8080))

// Inserting past the end of a three-element sequence
val insertFar = Patch.insertAt(Data.items, 10, Vector(4))
```

`Strict` rejects the existing key, `Lenient` keeps the old value, and `Clobber` overwrites it; the out-of-range insert becomes an append under `Clobber`:

```scala mdoc
println(addExisting(config, PatchMode.Strict))
addExisting(config, PatchMode.Lenient)
addExisting(config, PatchMode.Clobber)
insertFar(data, PatchMode.Clobber)
```

Use `Clobber` when the patch should win over the current state of map keys and when out-of-range sequence edits should be clamped rather than skipped.

### Option-Based Application

`Patch#applyOption` applies in `Strict` mode and returns `None` if any operation fails:

```scala mdoc
addHundred.applyOption(testAccount)
combined.applyOption(data)
```

## Diffing Values

[`Schema`](./schema.md) provides `Schema#diff(oldValue: A, newValue: A): Patch[A]`, which computes a minimal patch that transforms one value into another, and `Schema#patch(value: A, patch: Patch[A]): Either[SchemaError, A]`, which applies a patch in `Strict` mode. We diff two versions of a person whose age changed:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Person(name: String, age: Int)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

val oldValue = Person("Alice", 25)
val newValue = Person("Alice", 26)

val patch: Patch[Person] = Schema[Person].diff(oldValue, newValue)
```

The patch holds a single delta on `age`, and applying it to the old value yields the new one:

```scala mdoc
println(patch)
Schema[Person].patch(oldValue, patch)
```

The same diff is available one level down, on [`DynamicValue`](./dynamic-value.md), where `DynamicValue#diff` returns a `DynamicPatch`:

```scala mdoc:silent
val dynamicOld = Schema[Person].toDynamicValue(oldValue)
val dynamicNew = Schema[Person].toDynamicValue(newValue)
val dynamicPatch: DynamicPatch = dynamicOld.diff(dynamicNew)
```

Applying the `DynamicPatch` to the old dynamic value yields the new one:

```scala mdoc
dynamicPatch(dynamicOld, PatchMode.Strict) == Right(dynamicNew)
```

The differ produces minimal patches using type-appropriate operations:

- **Numeric fields** — Uses delta operations (`+1` instead of `set 26`)
- **Strings** — Uses edit operations when more compact than replacement
- **Records** — Only includes changed fields
- **Sequences** — Uses LCS algorithm to compute minimal insert/delete operations
- **Maps** — Produces add/remove/modify operations for changed entries

A user whose scores and metadata both changed shows the sequence and map operations together:

```scala mdoc:silent
case class User(
  name: String,
  scores: Vector[Int],
  metadata: Map[String, String]
)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val v1 = User(
  "Alice",
  Vector(10, 20, 30),
  Map("level" -> "1", "status" -> "active")
)

val v2 = User(
  "Alice",
  Vector(10, 25, 30, 40),
  Map("level" -> "2", "status" -> "active")
)
```

The `scores` edit deletes index 1, inserts `25` at index 1 and appends `40`. The changed map value is a string, and strings never get a numeric delta; for a one-character string an edit is not shorter than a replacement, so the patch sets `"level"` to `"2"`:

```scala mdoc
println(Schema[User].diff(v1, v2))
```

## Serializable Operations

All patch operations are serializable through the schema system: `DynamicPatch` has an implicit `Schema`, so it converts to `DynamicValue` and to any format with a schema-based codec (JSON, Avro, MessagePack, and so on). There is no `Schema[Patch[S]]`; we store `Patch#dynamicPatch`, the untyped part of the patch:

```scala mdoc:silent:reset
import zio.blocks.schema._
import zio.blocks.schema.patch._

case class Account(name: String, balance: Int, active: Boolean)
object Account extends CompanionOptics[Account] {
  implicit val schema: Schema[Account] = Schema.derived
  val name: Lens[Account, String] = optic(_.name)
  val balance: Lens[Account, Int] = optic(_.balance)
}

val patch = Patch.increment(Account.balance, 100)

// Patches can be converted to DynamicValue
val asDynamicValue: DynamicValue = Schema[DynamicPatch].toDynamicValue(patch.dynamicPatch)

// Serialize to JSON
val json: String = Schema[DynamicPatch].jsonCodec.encodeToString(patch.dynamicPatch)
```

The JSON names the path and the operation:

```scala mdoc
println(json)
```

To apply a decoded patch, we convert the target to a `DynamicValue`, apply the `DynamicPatch`, and convert the result back:

```scala mdoc
val restored = for {
  decoded <- Schema[DynamicPatch].jsonCodec.decode(json)
  patched <- decoded(Schema[Account].toDynamicValue(Account("Test", 50, true)), PatchMode.Strict)
  account <- Schema[Account].fromDynamicValue(patched)
} yield account
```

This enables storing patches in databases, sending them over APIs, or logging them for audit purposes.

:::caution
Patches address fields by name. A stored patch does not adapt when the data type evolves: if a field it touches is renamed, `Strict` mode fails with `Missing field` and `Lenient` and `Clobber` skip the operation. Here a patch that sets `name` meets a later version of the type in which `name` became `owner`:

```scala mdoc:silent
case class AccountV2(owner: String, balance: Int, active: Boolean)
object AccountV2 {
  implicit val schema: Schema[AccountV2] = Schema.derived
}

val setName = Patch.set(Account.name, "Savings")
val storedValue = Schema[AccountV2].toDynamicValue(AccountV2("Basic", 50, true))
```

Applying the stored patch to the new shape fails in `Strict` mode:

```scala mdoc
println(setName.dynamicPatch(storedValue, PatchMode.Strict))
```
:::

## Operation Reference

The tables below list every smart constructor on the `Patch` companion object, grouped by the kind of value they target.

### Value Operations

| Operation                     | Description                   |
|-------------------------------|-------------------------------|
| `Patch.set(optic, value)`     | Set a value at the optic path |
| `Patch.replace(optic, value)` | Alias for `Patch.set`         |
| `Patch.empty[S]`              | Empty patch (identity)        |

### Numeric Operations

| Operation                       | Description                |
|---------------------------------|----------------------------|
| `Patch.increment(optic, delta)` | Add delta to numeric field |

### Temporal Operations

| Operation                                             | Description                             |
|-------------------------------------------------------|-----------------------------------------|
| `Patch.addDuration(optic, duration)`                  | Add duration to `Instant` or `Duration` |
| `Patch.addPeriod(optic, period)`                      | Add period to `LocalDate` or `Period`   |
| `Patch.addPeriodAndDuration(optic, period, duration)` | Add both to `LocalDateTime`             |

### String Operations

| Operation                                               | Description                  |
|---------------------------------------------------------|------------------------------|
| `Patch.editString(optic, edits: Chunk[Patch.StringOp])` | Apply string edit operations |
| `Patch.StringOp.Insert(index, text)`                    | Insert text at position      |
| `Patch.StringOp.Delete(index, length)`                  | Delete characters            |
| `Patch.StringOp.Append(text)`                           | Append text                  |
| `Patch.StringOp.Modify(index, length, text)`            | Replace range                |

### Sequence Operations

| Operation                                | Description                 |
|------------------------------------------|-----------------------------|
| `Patch.append(optic, elements)`          | Append elements to end      |
| `Patch.insertAt(optic, index, elements)` | Insert at index             |
| `Patch.deleteAt(optic, index, count)`    | Delete elements             |
| `Patch.modifyAt(optic, index, patch)`    | Apply nested patch at index |

### Map Operations

| Operation                            | Description                 |
|--------------------------------------|-----------------------------|
| `Patch.addKey(optic, key, value)`    | Add key-value pair          |
| `Patch.removeKey(optic, key)`        | Remove key                  |
| `Patch.modifyKey(optic, key, patch)` | Apply nested patch to value |

## Best Practices

1. **Use increment for counters** — Increment operations are commutative and merge-friendly
2. **Prefer fine-grained patches** — Instead of replacing entire records, patch individual fields
3. **Check `Patch#isEmpty` before applying** — Skip no-op patches for efficiency
4. **Use Strict mode for validation** — Catch errors early in development
5. **Use Lenient mode for resilience** — Handle partial updates gracefully in production
6. **Serialize patches for audit** — Store the patch representation, not just before/after snapshots
7. **Revisit stored patches after renaming fields** — Patches address fields by name, so a rename makes `Strict` fail and `Lenient` skip the operation
