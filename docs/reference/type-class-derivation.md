---
id: type-class-derivation
title: "Type Class Derivation"
---

Type classes are one of the most powerful abstraction mechanisms in functional programming. Originating from Haskell, they enable ad-hoc polymorphism—the ability to define generic behavior that can be extended to new types without modifying those types. ZIO Blocks has a robust type class derivation system built around the `Deriver` trait, which allows automatic generation of type class instances for any data type with an associated `Schema`.

The `Deriver` trait is a cornerstone of ZIO Blocks' type class derivation system. It provides a unified, elegant mechanism for automatically generating type class instances (such as codecs) for any data type that has a `Schema`. Unlike traditional macro-based derivation approaches, `Deriver` requires implementing only a few methods to enable full type class derivation with rich reflective metadata support for every use case.

## The Problem

In functional programming, type classes allow us to define generic behavior that can be extended to new types without modifying those types. However, manually writing type class instances for every data type can be tedious and error-prone, especially as the number of types grows. This is where automatic derivation comes in.

Consider a typical application with 50 domain types that needs 4 type classes (JSON codec, Avro codec, hashing, ordering). That's 200 type class instances to write and maintain manually (50 types × 4 type classes). 

Each instance requires understanding both the type's structure and the type class's semantics, then correctly implementing encoding, decoding, or whatever operation is required. This quickly becomes unmanageable as the codebase grows.

Assume we have a simple `JsonCodec` type class for JSON serialization and deserialization:

```scala mdoc:silent
import zio.blocks.schema.json._

trait JsonCodec[A] {
  def encode(a: A): Json
  def decode(j: Json): Either[JsonError, A]
}
```

A single manual codec for a simple type like `Person` looks like the following code. You can imagine how complex it gets for larger types and more type classes:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val customerCodec: JsonCodec[Person] =
    new JsonCodec[Person] {
      def encode(c: Person): Json = Json.obj(
        "name"  -> Json.str(c.name),
        "email" -> Json.number(c.age)
      )

      def decode(j: Json): Either[JsonError, Person] =
        for {
          name <- j.get("name").asString.string
          age  <- j.get("age").asNumber.int
        } yield Person(name, age)
    }
}
```

This manual approach is not only time-consuming but also prone to errors and inconsistencies. As the number of types and type classes increases, the maintenance burden grows significantly.

## The Solution: Automatic Derivation with `Deriver`

The `Deriver` trait provides a powerful and flexible way to automatically derive type class instances for any data type with an associated `Schema`. By implementing just seven methods, you can enable full derivation for primitive types, records, variants, sequences, maps, dynamic values, and wrappers.

### The Core Insight

ZIO Blocks recognizes that all data types reduce to a small set of structural patterns (as outlined in the `Reflect` documentation):

| Pattern       | Description                     | Examples                           |
|---------------|---------------------------------|------------------------------------|
| **Primitive** | Atomic values                   | `String`, `Int`, `UUID`, `Instant` |
| **Record**    | Product types with named fields | Case classes, tuples               |
| **Variant**   | Sum types with named cases      | Sealed traits, enums               |
| **Sequence**  | Ordered collections             | `List`, `Vector`, `Array`          |
| **Map**       | Key-value collections           | `Map`, `HashMap`                   |
| **Dynamic**   | Schema-less data                | `DynamicValue`, arbitrary JSON     |
| **Wrapper**   | Newtypes and opaque types       | `opaque type Age = Int`            |

If you define how to derive type-class instances for all these patterns, then ZIO Blocks has all the pieces needed to build type-class instances for any data type. This is what the `Deriver[TC[_]]` is responsible for. A `Deriver[TC[_]]` defines how to create `TC[A]` instances for each kind of schema node:

```scala
trait Deriver[TC[_]] {
  def derivePrimitive[A](...)                     : Lazy[TC[A]]
  def deriveRecord   [F[_, _], A](...)            : Lazy[TC[A]]
  def deriveVariant  [F[_, _], A](...)            : Lazy[TC[A]]
  def deriveSequence [F[_, _], C[_], A](...)      : Lazy[TC[C[A]]]
  def deriveMap      [F[_, _], M[_, _], K, V](...): Lazy[TC[M[K, V]]]
  def deriveDynamic  [F[_, _]](...)               : Lazy[TC[DynamicValue]]
  def deriveWrapper  [F[_, _], A, B](...)         : Lazy[TC[A]]
}
```

Conceptually, the `Deriver` interface operates at the meta level, acting as a type class for type class derivation. It takes a higher-kinded type parameter `TC[_]`, which represents the type class to be derived (e.g., `JsonCodec`, `Ordering`, `Eq`, etc.), and defines seven methods, each corresponding to the derivation of the type class for one of the structural patterns.

That's it. As a developer who wants to implement automatic derivation for a new type class, you only need to implement these 7 methods. Each receives all the information needed to build a type class instance such as field names, type names, bindings for construction/deconstruction, documentation, and modifiers.

Looking at the return type of each method, you'll notice they all return the type class wrapped in a `Lazy` container, i.e., `Lazy[TC[_]]`, not just `TC[_]`. This is crucial for handling recursive data types safely. While the `Deriver` system traverses the schema structure to generate type-class instances or codecs, it may encounter recursive data types. To prevent stack overflows caused by unbounded recursion and infinite loops, ZIO Blocks uses the `Lazy` data type, which is a trampolined, memoizing lazy evaluation monad that defers computation until `Lazy#force` is called. It provides stack-safe evaluation through continuation-passing style (CPS), along with error-handling capabilities and composable operations.

Each method (except the `derivePrimitive` method) also receives implicit parameters of type class instances for `HasBinding` and `HasInstance`:
1. **`HasBinding[F]`**: Provides access to the structural binding information (constructors, deconstructors, matchers, discriminators, etc.) for the contained types, e.g., fields of a record or cases of a variant, allowing us to understand how to construct and deconstruct values of those types.
2. **`HasInstance[F, TC]`**: Provides access to already-(provided/derived) type class instances for nested types or fields. This allows you to build type class instances for complex types by composing instances of their constituent parts.

As an example, the `deriveRecord` method signature looks like this:

```scala
trait Deriver[TC[_]] {
  // other methods...
  
  def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]
  
  // other methods...
}
```

The other methods follow a similar pattern, each tailored to the specific structural pattern they handle.

The underlying derivation engine takes care of traversing the schema structure, applying the appropriate derivation method for each structural pattern, and composing the resulting type class instances together. This means that once you've implemented a `Deriver` for a specific type class, you can automatically derive instances for any data type with a schema, without writing any additional boilerplate code.

## Derivation Process Overview

### PHASE 1: Deriving the Schema for the Target Type

The first step in deriving a type class instance is deriving a `Schema[A]` for the target type `A`. The `Schema[A]` contains a tree of `Reflect[Binding, A]` nodes that represent the structure of `A` using structural bindings:

For example, assume a case class of `Person(name: String, age: Int)`. The derived schema would look like this:

```
Schema[Person]
  └── Reflect.Record[Binding, Person]
        ├── Term("name", Reflect.Primitive[Binding, String])
        └── Term("age", Reflect.Primitive[Binding, Int])
```

Each node of the derived schema tree, carries two pieces of information:
- **Type Metadata**: Structural representation of the type (e.g., record, variant, primitive).
- **Binding Metadata**: Structural binding information for constructing/deconstructing values of that type.

This schema derivation is typically done using `Schema.derived[A]`, which uses Scala's compile-time reflection capabilities to inspect the structure of type `A` and build the corresponding schema.

For example, the following code derives the schema for `Person`:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}
```

### PHASE 2: Schema Tree Transformation

After generating the schema, by calling `Schema[A]#derive(deriver: Deriver[TC])`, the derivation process begins. This process involves transforming the schema tree from one that contains only structural bindings to one that also includes derived type class instances.

Initially, a `Schema[A]` contains `Reflect[Binding, A]` nodes that represent the structure of the type `A` using structural bindings. During derivation, the `Deriver` transforms these nodes into `Reflect[BindingInstance[TC, _, _], A]` nodes, where each node now contains both the structural binding and the derived type class instance for that part of the structure.

This tree transformation process starts at the root of the schema and recursively traverses each node until it reaches the leaf nodes (primitives). Now it can derive the type class instances for each leaf node by calling the `derivePrimitive` deriver method, which returns the derived type class instance wrapped in a `Lazy` container, i.e., `Lazy[TC[A]]`. The derivation builder now converts that schema node from `Reflect[Binding, A]` to `Reflect[BindingInstance[TC, _, _], A]`, where the `BindingInstance` contains both the structural binding and the derived type class instance. After converting all the leaf nodes, it backtracks up the tree, calling the appropriate `Deriver` methods for each structural pattern (record, variant, sequence, map, dynamic, wrapper) to derive type class instances for the composite types. At each step, it transforms the schema nodes from `Reflect[Binding, A]` to `Reflect[BindingInstance[TC, _, _], A]` accordingly. This process continues until it reaches the root of the schema tree, resulting in a final schema of type `Schema[A]` that contains `Reflect[BindingInstance[TC, _, _], A]` nodes throughout the entire structure.

The following diagram illustrates this transformation process:

```
       ┌──────────────────────────────┐
       │      Reflect[Binding,A]      │
       ├──────────────────────────────┤
       │   STRUCTURAL BINDING ONLY    │
       └──────────────────────────────┘
                      │
                      │ transform
                      ▼
       ┌──────────────────────────────┐
       │  Reflect[BindingInstance,A]  │
       ├──────────────────────────────┤
       │      STRUCTURAL BINDING      │
       │  WITH TYPE-CLASS INSTANCE    │
       └──────────────────────────────┘
                      │
                      │ extract
                      ▼
       ┌──────────────────────────────┐
       │         Lazy[TC[A]]          │
       ├──────────────────────────────┤
       │     TYPE-CLASS INSTANCE      │
       │           (TC[A])            │
       └──────────────────────────────┘
```

The `BindingInstance` is a container that bundles together a structural `Binding` and a derived type class instance `TC[A]`:

```scala
case class BindingInstance[TC[_], T, A](
  binding: Binding[T, A],    // Original runtime binding
  instance: Lazy[TC[A]]      // The derived type-class instance
)
```

For example, the transformation sequence for the `Person` data type would look like this:

```scala
case class Person(name: String, age: Int)

object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val show: Show[Person]     = schema.derive(ShowDeriver)
}
```

- Step 1: Transform Primitive "name" (String)
   - deriver.derivePrimitive(String) → Lazy[Show[String]]
   - Creating BindingInstance(Binding.Primitive, Lazy[Show[String]])
   - Converting reflect node of `String` Schema from `Reflect[Binding, String]` to `Reflect[BindingInstance, String]`

- Step 2: Transform Primitive "age" (Int)
   - deriver.derivePrimitive(Int) → Lazy[Show[Int]]
   - Creating BindingInstance(Binding.Primitive, Lazy[Show[Int]])
   - Converting reflect node of `Int` Schema from `Reflect[Binding, Int]` to `Reflect[BindingInstance, Int]`

- Step 3: Transform Record "Person"
   - deriver.deriveRecord(fields with transformed metadata) → Lazy[Show[Person]]
   - Creating BindingInstance(Binding.Record, Lazy[Show[Person]])
   - Converting reflect node of `Person` Schema from `Reflect[Binding, Person]` to `Reflect[BindingInstance, Person]`

### PHASE 3: Extracting the Derived Type Class Instance

After the schema tree has been fully transformed to contain `Reflect[BindingInstance[TC, _, _], A]` nodes, now each node has a `BindingInstance` containing the original binding and the derived type class instance. The metadata container `BindingInstance` of the root node contains the derived type class wrapped in a `Lazy` container, i.e., `Lazy[TC[A]]`. To get the final derived type class instance, we call `force` on the `Lazy[TC[A]]`, which forces the unevaluated computation and retrieves the actual type class instance `TC[A]`.

### Phase 4: Using the Derived Show Instance

After derivation is complete, you can use the derived type class instance as needed. For example, you can use the derived `Show[Person]` instance to display a `Person` object:

```scala mdoc:silent
val result = Person[Show].show(Person("Alice", 30))
// result: String = "Person(name = Alice, age = 30)"
```

The interesting part here is how the `show` method of the derived `Show[Person]` instance works. It uses the `HasInstance` type class to access the derived `Show` instances for each field of the `Person` record (i.e., `Show[String]` for the `name` field and `Show[Int]` for the `age` field). This allows it to recursively display each field using its respective `Show` instance, demonstrating the composability and reusability of type class instances in the derivation system.

Please note that this happens when either the `Deriver` implementation uses the `HasInstance` implicit parameter or uses the centralized recursive approach to access nested derived instances.


