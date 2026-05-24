---
id: path-interpolator
title: "Path Interpolator"
---

The path interpolator `p"..."` is a compile-time string interpolator for constructing `DynamicOptic` instances in ZIO Blocks. It provides a clean, concise syntax for building optic paths that navigate through complex data structures, with all parsing and validation happening at compile time for zero runtime overhead.

**Why use the path interpolator?**

Instead of manually constructing optics like this:

```scala mdoc:silent
import zio.blocks.schema._

DynamicOptic(Vector(
  DynamicOptic.Node.Field("users"),
  DynamicOptic.Node.Elements,
  DynamicOptic.Node.Field("email")
))
```

You can write:

```scala mdoc:silent
import zio.blocks.schema._

p".users[*].email"
```

The interpolator is **type-safe**, **compile-time validated**, and **performance-optimized** with zero runtime parsing overhead.

## Getting Started

Import the schema package to enable the path interpolator:

```scala mdoc:silent
import zio.blocks.schema._

// Now you can use p"..." anywhere
val path = p".users[0].name"
```

## Key Features

- **✅ Zero Runtime Overhead**: All parsing happens at compile time
- **✅ Cross-Platform**: Works on Scala 2.13.x and Scala 3.x
- **✅ Compile-Time Safety**: Invalid paths are rejected during compilation
- **✅ No Runtime Interpolation**: Prevents accidental use of runtime values
- **✅ Rich Syntax**: Supports all `DynamicOptic` operations

## Syntax Reference

### Field Access

Access fields in records using dot notation. The leading dot is optional.

```scala mdoc:silent
import zio.blocks.schema._

// With leading dot
p".name"           // Field("name")
p".firstName"      // Field("firstName")

// Without leading dot
p"name"            // Field("name")
p"firstName"       // Field("firstName")

// Chained fields
p".user.address.street"
// Equivalent to: Field("user") → Field("address") → Field("street")
```

**Special cases:**

```scala mdoc:silent
import zio.blocks.schema._

p"._private"       // Fields starting with underscore
p".field123"       // Fields with digits
p".café"           // Unicode field names
p".true"           // Keywords as field names (true, false, null)
```

### Index Access

Access sequence elements by index, multiple indices, or ranges.

**Single index:**

```scala mdoc:silent
import zio.blocks.schema._

p"[0]"             // AtIndex(0)
p"[42]"            // AtIndex(42)
p"[2147483647]"    // AtIndex(Int.MaxValue)
```

**Multiple indices:**

```scala mdoc:silent
import zio.blocks.schema._

p"[0,1,2]"         // AtIndices(Seq(0, 1, 2))
p"[0, 2, 5]"       // AtIndices(Seq(0, 2, 5)) - spaces allowed
p"[5,2,8,1]"       // Order preserved
```

**Ranges:**

```scala mdoc:silent
import zio.blocks.schema._

p"[0:5]"           // AtIndices(Seq(0, 1, 2, 3, 4))
p"[5:8]"           // AtIndices(Seq(5, 6, 7))
p"[3:4]"           // AtIndices(Seq(3)) - single element
p"[5:5]"           // AtIndices(Seq.empty) - empty range
p"[10:5]"          // AtIndices(Seq.empty) - inverted range
```

### Element Selectors

Select all elements in a sequence using wildcard syntax.

```scala mdoc:silent
import zio.blocks.schema._

p"[*]"             // Elements - all elements
p"[:*]"            // Elements - alternative syntax
```

**Chained selectors:**

```scala mdoc:silent
import zio.blocks.schema._

p"[*][*]"          // Nested sequences: all elements of all elements
p"[*][0]"          // First element of each sequence
```

### Map Access

Access map values by key, where keys can be strings, integers, booleans, or characters.

**String keys:**

```scala mdoc:silent
import zio.blocks.schema._

p"""{"host"}"""              // AtMapKey(String("host"))
p"""{"foo bar"}"""           // Keys with spaces
p"""{"日本語"}"""             // Unicode keys
p"""{"🎉"}"""                 // Emoji keys
p"""{""}"""                  // Empty string key
```

**Integer keys:**

```scala mdoc:silent
import zio.blocks.schema._

p"{42}"                      // AtMapKey(Int(42))
p"{0}"                       // AtMapKey(Int(0))
p"{-42}"                     // AtMapKey(Int(-42))
p"{2147483647}"              // AtMapKey(Int.MaxValue)
p"{-2147483648}"             // AtMapKey(Int.MinValue)
```

**Boolean keys:**

```scala mdoc:silent
import zio.blocks.schema._

p"{true}"                    // AtMapKey(Boolean(true))
p"{false}"                   // AtMapKey(Boolean(false))
```

**Char keys:**

```scala mdoc:silent
import zio.blocks.schema._

p"{'a'}"                     // AtMapKey(Char('a'))
p"{' '}"                     // AtMapKey(Char(' '))
p"{'9'}"                     // AtMapKey(Char('9'))
```

**Multiple keys:**

```scala mdoc:silent
import zio.blocks.schema._

p"""{"foo", "bar", "baz"}""" // AtMapKeys(Seq(...))
p"{1, 2, 3}"                 // Multiple integer keys
p"{true, false}"             // Multiple boolean keys

// Mixed types
p"""{"foo", 42}"""           // AtMapKeys(Seq(String("foo"), Int(42)))
p"""{"s", 'c', 42, true}"""  // All supported types
```

### Map Selectors

Select all keys or all values in a map.

```scala mdoc:silent
import zio.blocks.schema._

p"{*}"             // MapValues - all values
p"{:*}"            // MapValues - alternative syntax
p"{*:}"            // MapKeys - all keys
```

**Examples:**

```scala mdoc:silent
import zio.blocks.schema._

p"{*}{*}"          // Nested maps: all values of all values
p"{*:}{*:}"        // All keys of all keys
```

### Variant Case Access

Navigate into a specific variant case using angle brackets.

```scala mdoc:silent
import zio.blocks.schema._

p"<Left>"          // Case("Left")
p"<Right>"         // Case("Right")
p"<Some>"          // Case("Some")
p"<None>"          // Case("None")
```

**Special cases:**

```scala mdoc:silent
import zio.blocks.schema._

p"<_Empty>"        // Cases starting with underscore
p"<Case1>"         // Cases with digits
p"<café>"          // Unicode case names
```

**Chained cases:**

```scala mdoc:silent
import zio.blocks.schema._

p"<A><B><C>"       // Nested variants
```

### Schema Search

Search for values matching a schema pattern anywhere in a data structure using the `#` prefix.

**Nominal types:**

```scala mdoc:silent
import zio.blocks.schema._

p"#Person"         // Find all values of type Person
p"#User"           // Find all values of type User
p"#Address"        // Find all values of type Address
```

**Primitive types:**

```scala mdoc:silent
import zio.blocks.schema._

p"#string"         // Find all string values
p"#int"            // Find all integer values
p"#boolean"        // Find all boolean values
p"#uuid"           // Find all UUID values
```

**Structural records:**

```scala mdoc:silent
import zio.blocks.schema._

p"#record { name: string }"                // Find records with a string 'name' field
p"#record { name: string, age: int }"      // Find records with both fields
p"#record { items: list(Person) }"         // Nested schema
```

**Structural variants:**

```scala mdoc:silent
import zio.blocks.schema._

p"#variant { Left: int, Right: string }"   // Find Either-like variants
```

**Collections:**

```scala mdoc:silent
import zio.blocks.schema._

p"#list(string)"                           // Find lists of strings
p"#list(Person)"                           // Find lists of Person
p"#map(string, int)"                       // Find maps from string to int
p"#option(Person)"                         // Find optional Person values
```

**Wildcard:**

```scala mdoc:silent
import zio.blocks.schema._

p"#_"              // Find any value (matches everything)
```

**Combined paths with search:**

```scala mdoc:silent
import zio.blocks.schema._

p".users#Person"               // Search for Person in users field
p"#Person.name"                // Find all Person values, then get name
p".items[*]#Person.email"      // Elements then search then field
p"#list(Person)#Person"        // Chained searches
```

## Escape Sequences

String and character literals support standard escape sequences:

| Escape | Result  | Description     |
|--------|---------|-----------------|
| `\n`   | newline | Line feed       |
| `\t`   | tab     | Horizontal tab  |
| `\r`   | return  | Carriage return |
| `\'`   | `'`     | Single quote    |
| `\"`   | `"`     | Double quote    |
| `\\`   | `\`     | Backslash       |

**Examples:**

```scala mdoc:silent
import zio.blocks.schema._

p"""{"foo\nbar"}"""          // String key with newline
p"""{"foo\tbar"}"""          // String key with tab
p"""{'\n'}"""                // Char key with newline
p"""{"foo\"bar"}"""          // Escaped quote in string
p"""{"foo\\bar"}"""          // Escaped backslash in string
```

## Combined Paths

Combine different path elements to navigate complex nested structures.

### Field → Sequence

```scala mdoc:silent
import zio.blocks.schema._

p".items[0]"                 // First item
p".items[*]"                 // All items
p".items[0,1,2]"             // Items at indices 0, 1, 2
p".items[0:5]"               // Items 0 through 4
```

### Field → Map

```scala mdoc:silent
import zio.blocks.schema._

p""".config{"host"}"""       // Map lookup
p".settings{42}"             // Integer key
p".lookup{*}"                // All map values
p".lookup{*:}"               // All map keys
```

### Field → Variant

```scala mdoc:silent
import zio.blocks.schema._

p".result<Success>"          // Variant case
p".response<Ok>"             // HTTP response variant
```

### Nested Structures

```scala mdoc:silent
import zio.blocks.schema._

// Record in sequence
p".users[0].name"
// Equivalent to: Field("users") → AtIndex(0) → Field("name")

// All elements then field
p".users[*].email"
// Equivalent to: Field("users") → Elements → Field("email")

// Map values then field
p".lookup{*}.value"
// Equivalent to: Field("lookup") → MapValues → Field("value")

// Variant then field
p".response<Ok>.body"
// Equivalent to: Field("response") → Case("Ok") → Field("body")
```

### Deeply Nested Paths

```scala mdoc:silent
import zio.blocks.schema._

// Complex nested navigation
p""".root.children[*].metadata{"tags"}[0]"""
// Field("root") → Field("children") → Elements → 
// Field("metadata") → AtMapKey("tags") → AtIndex(0)

// All node types in one path
p""".a[0]{"k"}<V>.b[*]{*}.c{*:}"""
// Field("a") → AtIndex(0) → AtMapKey("k") → Case("V") →
// Field("b") → Elements → MapValues → Field("c") → MapKeys
```

## Root and Empty Paths

```scala mdoc:silent
import zio.blocks.schema._

p""                          // Empty path = root
// Equivalent to: DynamicOptic.root
// Equivalent to: DynamicOptic(Vector.empty)
```

## Compile-Time Safety

The path interpolator **rejects runtime interpolation** to prevent unsafe dynamic path construction.

**❌ This will fail to compile:**

```scala mdoc:fail
import zio.blocks.schema._

val fieldName = "email"
val path = p".$fieldName"
// Error: Path interpolator does not support runtime arguments.
//        Use only literal strings like p".field[0]"
```

**❌ This will also fail:**

```scala mdoc:fail
import zio.blocks.schema._

val idx = 5
val pathWithIdx = p"[$idx]"
// Error: Path interpolator does not support runtime arguments.
//        Use only literal strings like p".field[0]"
```

**✅ Use only literal strings:**

```scala mdoc:silent
import zio.blocks.schema._

object LiteralPathExample {
  val path = p".users[0].email"  // ✓ Works
}
```

### Parse Error Examples

Invalid syntax is caught at compile time:

```scala mdoc:fail
import zio.blocks.schema._

// Unterminated string
p"""{"foo"""
// Error: Unterminated string literal starting at position 1

// Invalid escape sequence
p"""{"foo\x"}"""
// Error: Invalid escape sequence '\x' at position 6

// Unexpected character
p".field@"
// Error: Unexpected character '@' at position 6

// Invalid identifier
p"."
// Error: Invalid identifier at position 1
```

## Performance

**Zero Runtime Overhead**

All path parsing and validation occurs at **compile time**. The interpolator generates the exact same bytecode as manual `DynamicOptic` construction:

```scala mdoc:silent
import zio.blocks.schema._

// These produce identical bytecode:
p".users[*].email"

DynamicOptic(Vector(
  DynamicOptic.Node.Field("users"),
  DynamicOptic.Node.Elements,
  DynamicOptic.Node.Field("email")
))
```

There is **no runtime parsing**, **no reflection**, and **no performance penalty**.

## Examples

### Accessing Nested Fields

```scala mdoc:silent
import zio.blocks.schema._

case class Address(street: String, city: String, zipCode: String)
case class Person(name: String, age: Int, address: Address)

// Access nested street field
val streetPath = p".address.street"
// Equivalent to: Field("address") → Field("street")
```

### Working with Collections

```scala mdoc:silent
import zio.blocks.schema._

object Example1 {
  case class User(id: Int, email: String, tags: Seq[String])
  case class Company(name: String, users: Seq[User])

  // Get all user emails
  val emailsPath = p".users[*].email"

  // Get first user's first tag
  val firstTagPath = p".users[0].tags[0]"

  // Get specific users by index
  val specificUsersPath = p".users[0,2,5]"
}
```

### Map Lookups

```scala mdoc:silent
import zio.blocks.schema._

case class Config(
  settings: Map[String, String],
  ports: Map[Int, String]
)

// Lookup by string key
val hostPath = p"""settings{"host"}"""

// Lookup by integer key
val httpPortPath = p"ports{80}"

// Get all config values
val allValuesPath = p"settings{*}"

// Get all port numbers (keys)
val allPortsPath = p"ports{*:}"
```

### Variant Case Handling

```scala mdoc:silent
import zio.blocks.schema._

object Example2 {
  sealed trait Result[+A]
  case class Success[A](value: A) extends Result[A]
  case class Failure(error: String) extends Result[Nothing]

  case class User(name: String)
  case class Response(result: Result[User])

  // Navigate into Success case
  val successValuePath = p".result<Success>.value"

  // Navigate into Failure case
  val errorPath = p".result<Failure>.error"
}
```

### Real-World Example: API Response

```scala mdoc:silent
import zio.blocks.schema._

case class Metadata(tags: Seq[String], version: Int)
case class Item(id: String, data: String, metadata: Metadata)
case class ApiResponse(
  status: String,
  items: Seq[Item],
  config: Map[String, String]
)

// Get the version from the first item's metadata
val versionPath = p".items[0].metadata.version"

// Get all item IDs
val allIdsPath = p".items[*].id"

// Get the first tag of each item
val firstTagsPath = p".items[*].metadata.tags[0]"

// Lookup config value
val apiKeyPath = p"""config{"api_key"}"""
```

## Before & After Comparison

### Manual Construction (Before)

```scala mdoc:compile-only
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicOptic.Node
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue

// Simple path - verbose and error-prone
val path1 = DynamicOptic(Vector(
  Node.Field("users"),
  Node.AtIndex(0),
  Node.Field("email")
))

// Complex path - extremely verbose
val path2 = DynamicOptic(Vector(
  Node.Field("root"),
  Node.Field("children"),
  Node.Elements,
  Node.Field("metadata"),
  Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("tags"))),
  Node.AtIndex(0)
))

// Map with multiple keys
val path3 = DynamicOptic(Vector(
  Node.Field("data"),
  Node.AtMapKeys(Seq(
    DynamicValue.Primitive(PrimitiveValue.String("foo")),
    DynamicValue.Primitive(PrimitiveValue.String("bar")),
    DynamicValue.Primitive(PrimitiveValue.Int(42))
  ))
))
```

### Path Interpolator (After)

```scala mdoc:silent
import zio.blocks.schema._

// Simple path - clean and readable
val path1 = p".users[0].email"

// Complex path - still clean and readable
val path2 = p""".root.children[*].metadata{"tags"}[0]"""

// Map with multiple keys - concise
val path3 = p"""data{"foo", "bar", 42}"""
```

**Benefits:**

- **90% less code** for typical paths
- **Easier to read** and understand intent
- **Easier to write** and maintain
- **Compile-time validated** - catches errors immediately
- **No performance difference** - identical bytecode

## Practical Usage Patterns

### Building Paths Dynamically (at Compile Time)

```scala mdoc:silent
import zio.blocks.schema._

// You can't use runtime variables, but you can compose literal paths:
val basePath = p".data.items"
val emailPath = basePath(p"[*].email")
// Same as: p".data.items[*].email"
```

### Working with DynamicValue

```scala mdoc:silent
import zio.blocks.schema._

object Example3 {
  // Define paths for navigation
  val emailPath = p".users[0].email"
  val agePath = p".users[0].age"

  // These paths can be used with DynamicValue operations:
  // val value = data.get(emailPath)
  // val updated = data.set(agePath, newAgeValue)
}
```

### Integration with Schema Optics

```scala mdoc:silent
import zio.blocks.schema._

object Example4 {
  case class User(name: String, email: String)
  object User {
    // Use path interpolator to define optics
    val emailPath = p".email"
    val namePath = p".name"
  }

  // The paths can be used as DynamicOptic instances
  val paths = Vector(User.emailPath, User.namePath)
}
```

## Tips and Best Practices

1. **Use the leading dot for clarity**: While optional, `p".field"` is more explicit than `p"field"`

2. **Leverage compile-time validation**: Let the compiler catch typos and syntax errors early

3. **Compose paths when needed**: Break complex paths into reusable components
   ```scala
import zio.blocks.schema._

val userPath = p".users[0]"
val emailPath = userPath(p".email")
   ```

4. **Use raw strings for map keys**: Triple-quoted strings avoid escape hell
   ```scala
import zio.blocks.schema._

p"""config{"api.key"}"""  // Better than p"config{\"api.key\"}"
   ```

5. **Document complex paths**: Add comments explaining what nested paths navigate
   ```scala mdoc:silent
import zio.blocks.schema._

   // Get the first tag from each user's metadata
   val tagsPath = p".users[*].metadata.tags[0]"
   ```

## Limitations

- **No runtime interpolation**: You cannot use variables in paths (this is by design for safety)
- **No arithmetic in ranges**: Ranges must be literal integers (e.g., `[0:5]` not `[0:n]`)
- **No string interpolation**: Only literal strings work with the interpolator
- **Map keys limited to primitives**: Only String, Int, Char, and Boolean keys are supported

These limitations ensure compile-time safety and zero runtime overhead.

## Debug-Friendly toString

`DynamicOptic` instances have a custom `toString` that produces output matching the `p"..."` interpolator syntax. This makes debugging easier because you can copy the output directly into your code:

```scala mdoc:silent
import zio.blocks.schema._

val optic = DynamicOptic.root.field("users").elements.field("email")
println(optic)  // Output: .users[*].email

// The output can be copy-pasted into p"..."
val same = p".users[*].email"
```

**Examples:**

| DynamicOptic Construction                            | toString Output   |
|------------------------------------------------------|-------------------|
| `DynamicOptic.root.field("name")`                    | `.name`           |
| `DynamicOptic.root.field("address").field("street")` | `.address.street` |
| `DynamicOptic.root.caseOf("Some")`                                      | `<Some>`          |
| `DynamicOptic.root.at(0)`                                               | `[0]`             |
| `DynamicOptic.root.atIndices(0, 2, 5)`                                  | `[0,2,5]`         |
| `DynamicOptic.elements`                                                 | `[*]`             |
| `DynamicOptic.root.atKey("host")`                                       | `{"host"}`        |
| `DynamicOptic.root.atKey(80)`                                           | `{80}`            |
| `DynamicOptic.mapValues`                                                | `{*}`             |
| `DynamicOptic.mapKeys`                                                  | `{*:}`            |
| `DynamicOptic.wrapped`                                                  | `.~`              |
| `DynamicOptic.root.searchSchema(SchemaRepr.Nominal("Person"))`          | `#Person`         |
| `DynamicOptic.root.searchSchema(SchemaRepr.Primitive("string"))`        | `#string`         |

## Summary

The `p"..."` path interpolator provides:

- **Concise syntax** for building optic paths
- **Compile-time parsing** with zero runtime overhead  
- **Type-safe navigation** through complex data structures
- **Cross-platform support** for Scala 2 and Scala 3
- **Rich feature set** covering all DynamicOptic operations

Use it whenever you need to construct `DynamicOptic` paths for navigating dynamic data structures in ZIO Blocks.
