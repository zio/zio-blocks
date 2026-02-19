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

sealed abstract class JsonError(msg: String) extends Exception(msg)

case class ParseError(details: String) 
  extends JsonError(s"Parse Error: $details")

case class DecodeError(details: String, path: String) 
  extends JsonError(s"Decode Error at '$path': $details")

trait JsonCodec[A] {
  def encode(a: A): Json
  def decode(j: Json): Either[JsonError, A]
}
```

A single manual codec for a simple type like `Person` looks like the following code. You can imagine how complex it gets for larger types and more type classes:

```scala
case class Person(name: String, age: Int)

object Person {
  implicit val personCodec: JsonCodec[Person] =
    new JsonCodec[Person] {
      def encode(c: Person): Json = Json.obj(
        "name" -> Json.str(c.name),
        "age"  -> Json.number(c.age)
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
    binding: Binding.Record[A],
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

## Using the `Deriver` to Derive Type Class Instances

Given a `Schema[A]`, you can call the `derive` method to get an instance of the type class `TC[A]`:

```scala
case class Schema[A](reflect: Reflect.Bound[A]) {
  def derive[TC[_]](deriver: Deriver[TC]): TC[A] = ???
}
```

It takes a `Deriver[TC]` as a parameter and returns a type class instance of type `TC[A]`. For example, in the following code snippet, we derive a `JsonBinaryCodec[Person]` instance for the `Person` case class using the `JsonBinaryCodecDeriver`:

```scala mdoc:silent
import zio.blocks.schema._
import zio.blocks.schema.json.JsonBinaryCodecDeriver

case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
}

val jsonCodec: JsonBinaryCodec[Person] =
  Person.schema.derive(JsonBinaryCodecDeriver)

val result: Either[SchemaError, Person] = 
  jsonCodec.decode(
    """
      |{
      |  "name": "Alice",
      |  "age": 30
      |}
      |""".stripMargin
  )
```

There is another overloaded version of the `Schema#derive` method that takes a `Format` instead of a `Deriver`:

```scala
case class Schema[A](reflect: Reflect.Bound[A]) {
  def derive[F <: codec.Format](format: F): format.TypeClass[A] = derive(format.deriver)
}
```

For example, by calling `Person.schema.derive(JsonFormat)`, we can derive a `JsonCodec[Person]` instance:

```scala mdoc:silent:nest
import zio.blocks.schema.json._

val jsonCodec = Person.schema.derive(JsonFormat)
```

## Example 1: Deriving a `Show` Type Class Instance

Let's say we want to derive a `Show` type class instance for any type of type `A`:

```scala mdoc:silent
trait Show[A] {
  def show(value: A): String
}
```

The implementation of the `Deriver[Show]` would look like the following code. Don't worry about understanding every detail right now; we'll break down the derivation process step by step afterward.

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import zio.blocks.schema.DynamicValue.Null
import zio.blocks.schema.binding.*
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

object DeriveShow extends Deriver[Show] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[Show[A]] =
    Lazy {
      new Show[A] {
        def show(value: A): String = primitiveType match {
          case _: PrimitiveType.String => "\"" + value + "\""
          case _: PrimitiveType.Char   => "'" + value + "'"
          case _                       => String.valueOf(value)
        }
      }
    }

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] =
    Lazy {
      // Collecting Lazy[Show] instances for each field from the transformed metadata
      val fieldShowInstances: IndexedSeq[(String, Lazy[Show[Any]])] = fields.map { field =>
        val fieldName = field.name
        // Get the Lazy[Show] instance for this field's type, but we won't force it yet
        // We'll force it later when we actually need to show a value of this field
        val fieldShowInstance = D.instance(field.value.metadata).asInstanceOf[Lazy[Show[Any]]]
        (fieldName, fieldShowInstance)
      }

      // Cast fields to use Binding as F (we are going to create Reflect.Record with Binding as F)
      val recordFields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]

      // Cast to Binding.Record to access constructor/deconstructor
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]

      // Build a Reflect.Record to get access to the computed registers for each field
      val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)

      new Show[A] {
        def show(value: A): String = {

          // Create registers with space for all used registers to hold deconstructed field values
          val registers = Registers(recordReflect.usedRegisters)

          // Deconstruct field values of the record into the registers
          recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)

          // Build string representations for all fields
          val fieldStrings = fields.indices.map { i =>
            val (fieldName, showInstanceLazy) = fieldShowInstances(i)
            val fieldValue                    = recordReflect.registers(i).get(registers, RegisterOffset.Zero)
            val result                        = s"$fieldName = ${showInstanceLazy.force.show(fieldValue)}"
            result
          }

          s"${typeId.name}(${fieldStrings.mkString(", ")})"
        }
      }
    }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
    // Get Show instances for all cases LAZILY
    val caseShowInstances: IndexedSeq[Lazy[Show[Any]]] = cases.map { case_ =>
      D.instance(case_.value.metadata).asInstanceOf[Lazy[Show[Any]]]
    }

    // Cast binding to Binding.Variant to access discriminator and matchers
    val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
    val discriminator  = variantBinding.discriminator
    val matchers       = variantBinding.matchers

    new Show[A] {
      // Implement show by using discriminator and matchers to find the right case
      // The `value` parameter is of type A (the variant type), e.g. an Option[Int] value
      def show(value: A): String = {
        // Use discriminator to determine which case this value belongs to
        val caseIndex = discriminator.discriminate(value)

        // Use matcher to downcast to the specific case type
        val caseValue = matchers(caseIndex).downcastOrNull(value)

        // Just delegate to the case's Show instance - it already knows its own name
        caseShowInstances(caseIndex).force.show(caseValue)
      }
    }
  }

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[C[A]]] = Lazy {
    // Get Show instance for element type LAZILY
    val elementShowLazy: Lazy[Show[A]] = D.instance(element.metadata)

    // Cast binding to Binding.Seq to access the deconstructor
    val seqBinding    = binding.asInstanceOf[Binding.Seq[C, A]]
    val deconstructor = seqBinding.deconstructor

    new Show[C[A]] {
      def show(value: C[A]): String = {
        // Use deconstructor to iterate over elements
        val iterator = deconstructor.deconstruct(value)
        // Force the element Show instance only when actually showing
        val elements = iterator.map(elem => elementShowLazy.force.show(elem)).mkString(", ")
        s"[$elements]"
      }
    }
  }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[M[K, V]]] = Lazy {
    // Get Show instances for key and value types LAZILY
    val keyShowLazy: Lazy[Show[K]]   = D.instance(key.metadata)
    val valueShowLazy: Lazy[Show[V]] = D.instance(value.metadata)

    // Cast binding to Binding.Map to access the deconstructor
    val mapBinding    = binding.asInstanceOf[Binding.Map[M, K, V]]
    val deconstructor = mapBinding.deconstructor

    new Show[M[K, V]] {
      def show(m: M[K, V]): String = {
        // Use deconstructor to iterate over key-value pairs
        val iterator = deconstructor.deconstruct(m)
        // Force the Show instances only when actually showing
        val entries = iterator.map { kv =>
          val k = deconstructor.getKey(kv)
          val v = deconstructor.getValue(kv)
          s"${keyShowLazy.force.show(k)} -> ${valueShowLazy.force.show(v)}"
        }.mkString(", ")
        s"Map($entries)"
      }
    }
  }

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[DynamicValue]] = Lazy {
    new Show[DynamicValue] {
      def show(value: DynamicValue): String =
        value match {
          case DynamicValue.Primitive(pv) =>
            value.toString

          case DynamicValue.Record(fields) =>
            val fieldStrings = fields.map { case (name, v) =>
              s"$name = ${show(v)}"
            }
            s"Record(${fieldStrings.mkString(", ")})"

          case DynamicValue.Variant(caseName, v) =>
            s"$caseName(${show(v)})"

          case DynamicValue.Sequence(elements) =>
            val elemStrings = elements.map(show)
            s"[${elemStrings.mkString(", ")}]"

          case DynamicValue.Map(entries) =>
            val entryStrings = entries.map { case (k, v) =>
              s"${show(k)} -> ${show(v)}"
            }
            s"Map(${entryStrings.mkString(", ")})"
          case Null =>
            "null"
        }
    }
  }

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
    // Get Show instance for the wrapped (underlying) type B LAZILY
    val wrappedShowLazy: Lazy[Show[B]] = D.instance(wrapped.metadata)

    // Cast binding to Binding.Wrapper to access unwrap function
    val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]

    new Show[A] {
      def show(value: A): String = {
        val unwrapped = wrapperBinding.unwrap(value)
        s"${typeId.name}(${wrappedShowLazy.force.show(unwrapped)})"
      }
    }
  }
}
```

Now let's see how the derivation process works step by step.

### Primitive Derivation

When the derivation process encounters a primitive type (e.g., `String`, `Int`), it calls the `derivePrimitive` method of the `Deriver`. This method receives the `PrimitiveType[A]` information, which allows it to determine how to encode and decode values of that type:

```scala mdoc:invisible
import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import zio.blocks.schema.DynamicValue.Null
import zio.blocks.schema.binding.*
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc
```

```scala mdoc:compile-only
def derivePrimitive[A](
  primitiveType: PrimitiveType[A],
  typeId: TypeId[A],
  binding: Binding.Primitive[A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
): Lazy[Show[A]] =
  Lazy {
    new Show[A] {
      def show(value: A): String = primitiveType match {
        case _: PrimitiveType.String => "\"" + value + "\""
        case _: PrimitiveType.Char   => "'" + value + "'"
        case _                       => String.valueOf(value)
      }
    }
  }
```

Please note that for our simple `Show` type class, we only need to know the `PrimitiveType` to determine how to show the value. However, for more complex type classes you might require additional information from the other parameters (e.g., documentation, modifiers, default values, examples) to build a more sophisticated type class instance.

To make it simple, we only handle `String` and `Char` differently by adding quotes around them, while for all other primitive types we simply call `String.valueOf(value)` to get their string representation. You can easily extend this logic to handle other primitive types differently if needed.

### Record Derivation

When the derivation process encounters a record type (e.g., a case class), it calls the `deriveRecord` method of the `Deriver`. This method receives an `IndexedSeq[Term[F, A, ?]]` representing the fields of the record, along with other metadata such as the type ID, binding information, documentation, modifiers, default values, and examples. It also receives implicit parameters for accessing structural bindings and already-derived type class instances for nested types:

```scala mdoc:invisible
import DeriveShow._
```

```scala mdoc:compile-only
def deriveRecord[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeId: TypeId[A],
  binding: Binding.Record[A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
)(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] =
  Lazy {
    // Collecting Lazy[Show] instances for each field from the transformed metadata
    val fieldShowInstances: IndexedSeq[(String, Lazy[Show[Any]])] = fields.map { field =>
      val fieldName = field.name
      // Get the Lazy[Show] instance for this field's type, but we won't force it yet
      // We'll force it later when we actually need to show a value of this field
      val fieldShowInstance = D.instance(field.value.metadata).asInstanceOf[Lazy[Show[Any]]]
      (fieldName, fieldShowInstance)
    }

    // Cast fields to use Binding as F (we are going to create Reflect.Record with Binding as F)
    val recordFields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]

    // Cast to Binding.Record to access constructor/deconstructor
    val recordBinding = binding.asInstanceOf[Binding.Record[A]]

    // Build a Reflect.Record to get access to the computed registers for each field
    val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)

    new Show[A] {
      def show(value: A): String = {

        // Create registers with space for all used registers to hold deconstructed field values
        val registers = Registers(recordReflect.usedRegisters)

        // Deconstruct field values of the record into the registers
        recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)

        // Build string representations for all fields
        val fieldStrings = fields.indices.map { i =>
          val (fieldName, showInstanceLazy) = fieldShowInstances(i)
          val fieldValue                    = recordReflect.registers(i).get(registers, RegisterOffset.Zero)
          val result                        = s"$fieldName = ${showInstanceLazy.force.show(fieldValue)}"
          result
        }

        s"${typeId.name}(${fieldStrings.mkString(", ")})"
      }
    }
  }
```

The `deriveRecord` method demonstrates derivation mechanics for record types such as case classes and tuples. To derive the type class for a record type, we follow these steps:
1. First, we extract the type class instances for each field of the record. 
2. Second, we have to deconstruct the record value at runtime to access individual field values.
3. Third, we assemble the final string representation of the record by combining field names and their corresponding representations using the extracted type class instances.

During the first step, the method gathers `Lazy[Show]` instances for each field by calling `D.instance(field.value.metadata)`. This method extracts the derived type class instance for the field's type from the transformed schema metadata. Again, the transformed metadata contains `Reflect[BindingInstance[TC, _, _], A]` nodes, where each node has a `BindingInstance` that bundles together the structural binding and the derived type class instance. By calling `D.instance`, we retrieve the `Lazy[Show]` instance for each field's type.

These instances are wrapped in `Lazy` to support recursive data types—if a `Person` contains a `List[Person]`, we need to delay forcing the inner `Show[Person]` until runtime to avoid infinite loops during derivation.

Our goal is to build a `String` representation of the record in the format `TypeName(field1 = value1, field2 = value2, ...)`. To achieve this, we need to access the individual field values of the record at runtime. To do this, we have to deconstruct the record value, which is given to the `show(value: A)` method, into its individual fields.

To deconstruct the record, we use the `Binding.Record[A]` that was provided as a parameter to the `deriveRecord` method. This binding contains a `deconstructor` that knows how to extract all field values from a record of type `A`. To perform the deconstruction, we should first allocate register buffers to hold the deconstructed field values. But how do we know what the size of the register buffer should be? This is where the `Reflect.Record` comes in. By building a `Reflect.Record[Binding, A]` from the field definitions, we can compute the number of registers needed to hold all field values through `Reflect#usedRegisters`. The `Registers(recordReflect.usedRegisters)` call allocates a register buffer with the appropriate size to hold all field values of the record.

Now we are ready to deconstruct the `A` value, using the `Binding.Record#deconstructor.deconstruct(registers, RegisterOffset.Zero, value)` call, which extracts the field values of the record into this register buffer in a single pass. Now the field values are stored in `registers`.

The next question is how we can access the field values from the registers? The `Reflect.Record` we built earlier also computes the register layout for each field, which allows us to retrieve each field value from the appropriate register slot using `recordReflect.registers(i).get(registers, RegisterOffset.Zero)`. This call accesses the `i`-th field's value from the registers based on the register layout computed by `Reflect.Record`. 

Finally, we can iterate through each field, retrieve its value from the registers, force the corresponding `Lazy[Show]` instance for that field's type, and format the result as `fieldName = fieldValue`. The output assembles into the familiar `TypeName(field1 = value1, field2 = value2)` representation.

### Variant Derivation

When the derivation process encounters a variant type (e.g., a sealed trait with case classes), it calls the `deriveVariant` method of the `Deriver`. This method receives an `IndexedSeq[Term[F, A, _]]` representing the cases of the variant, along with other metadata such as the type ID, binding information, documentation, modifiers, default values, and examples:

```scala mdoc:compile-only
def deriveVariant[F[_, _], A](
  cases: IndexedSeq[Term[F, A, ?]],
  typeId: TypeId[A],
  binding: Binding.Variant[A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
)(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
  // Get Show instances for all cases LAZILY
  val caseShowInstances: IndexedSeq[Lazy[Show[Any]]] = cases.map { case_ =>
    D.instance(case_.value.metadata).asInstanceOf[Lazy[Show[Any]]]
  }
  // Cast binding to Binding.Variant to access discriminator and matchers
  val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
  val discriminator  = variantBinding.discriminator
  val matchers       = variantBinding.matchers
  new Show[A] {
    // Implement show by using discriminator and matchers to find the right case
    // The `value` parameter is of type A (the variant type), e.g. an Option[Int] value
    def show(value: A): String = {
      // Use discriminator to determine which case this value belongs to
      val caseIndex = discriminator.discriminate(value)
      // Use matcher to downcast to the specific case type
      val caseValue = matchers(caseIndex).downcastOrNull(value)
      // Just delegate to the case's Show instance - it already knows its own name
      caseShowInstances(caseIndex).force.show(caseValue)
    }
  }
}
```

The derivation process for variants is similar to records, but instead of fields, we have cases. We extract the type class instances for each case, and at runtime we use the discriminator to determine which case the value belongs to. Then we use the matcher to downcast the value to the specific case type.

Finally, we extract the corresponding type class instance for that case by applying the case index to the indexed sequence of type class instances. Now we have the correct type class instance for the specific case, wrapped in a `Lazy` data type. We force the lazy wrapper to retrieve the actual type class instance, and then we call the `show` method on that case value to get the string representation.

### Sequence Derivation

When the derivation process encounters a sequence type (e.g., `List[A]`), it calls the `deriveSequence` method of the `Deriver`. This method receives a `Reflect[F, A]` representing the element type of the sequence, along with other metadata such as the type ID, binding information, documentation, modifiers, default values, and examples:

```scala mdoc:compile-only
def deriveSequence[F[_, _], C[_], A](
  element: Reflect[F, A],
  typeId: TypeId[C[A]],
  binding: Binding.Seq[C, A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[C[A]],
  examples: Seq[C[A]]
)(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[C[A]]] = Lazy {
  // Get Show instance for element type (lazily)
  val elementShowLazy: Lazy[Show[A]] = D.instance(element.metadata)
  // Cast binding to Binding.Seq to access the deconstructor
  val seqBinding    = binding.asInstanceOf[Binding.Seq[C, A]]
  val deconstructor = seqBinding.deconstructor
  new Show[C[A]] {
    def show(value: C[A]): String = {
      // Use the deconstructor to iterate over elements
      val iterator = deconstructor.deconstruct(value)
      // Force the element Show instance only when actually showing
      val elements = iterator.map(elem => elementShowLazy.force.show(elem)).mkString(", ")
      s"[$elements]"
    }
  }
}
```

The derivation process for sequences is straightforward. We extract the type class instance for the element type, and at runtime we use the deconstructor to iterate over the elements of the sequence. For each element, we force the `Lazy[Show[A]]` instance to get the actual `Show[A]` instance, and then call `show` on each element to get its string representation. Finally, we combine all element representations into a single string that represents the entire sequence.

### Map Derivation

When the derivation process encounters a map type (e.g., `Map[K, V]`), it calls the `deriveMap` method of the `Deriver`. This method receives `Reflect[F, K]` and `Reflect[F, V]` representing the key and value types of the map, along with other metadata such as the type ID, binding information, documentation, modifiers, default values, and examples:

```scala mdoc:compile-only
def deriveMap[F[_, _], M[_, _], K, V](
  key: Reflect[F, K],
  value: Reflect[F, V],
  typeId: TypeId[M[K, V]],
  binding: Binding.Map[M, K, V],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[M[K, V]],
  examples: Seq[M[K, V]]
)(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[M[K, V]]] = Lazy {
  // Get Show instances for key and value types LAZILY
  val keyShowLazy: Lazy[Show[K]]   = D.instance(key.metadata)
  val valueShowLazy: Lazy[Show[V]] = D.instance(value.metadata)

  // Cast binding to Binding.Map to access the deconstructor
  val mapBinding    = binding.asInstanceOf[Binding.Map[M, K, V]]
  val deconstructor = mapBinding.deconstructor

  new Show[M[K, V]] {
    def show(m: M[K, V]): String = {
      // Use deconstructor to iterate over key-value pairs
      val iterator = deconstructor.deconstruct(m)
      // Force the Show instances only when actually showing
      val entries = iterator.map { kv =>
        val k = deconstructor.getKey(kv)
        val v = deconstructor.getValue(kv)
        s"${keyShowLazy.force.show(k)} -> ${valueShowLazy.force.show(v)}"
      }.mkString(", ")
      s"Map($entries)"
    }
  }
}
```

The derivation process for maps is similar to sequences, but we have to handle both keys and values. We extract the type class instances for the key and value types, and at runtime we use the deconstructor to iterate over the key-value pairs of the map. For each pair, we force the `Lazy[Show[K]]` and `Lazy[Show[V]]` instances to get the actual `Show[K]` and `Show[V]` instances, and then call `show` on both the key and value to get their string representations. Finally, we combine all entries into a single string that represents the entire map.

### Dynamic Derivation

When the derivation process encounters a dynamic type (e.g., `DynamicValue`), it calls the `deriveDynamic` method of the `Deriver`. This method receives a `Binding.Dynamic` representing the dynamic type, along with other metadata such as documentation, modifiers, default values, and examples:

```scala mdoc:compile-only
def deriveDynamic[F[_, _]](
  binding: Binding.Dynamic,
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[DynamicValue],
  examples: Seq[DynamicValue]
)(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[DynamicValue]] = Lazy {
  new Show[DynamicValue] {
    def show(value: DynamicValue): String =
      value match {
        case DynamicValue.Primitive(pv) =>
          value.toString
        case DynamicValue.Record(fields) =>
          val fieldStrings = fields.map { case (name, v) =>
            s"$name = ${show(v)}"
          }
          s"Record(${fieldStrings.mkString(", ")})"
        case DynamicValue.Variant(caseName, v) =>
          s"$caseName(${show(v)})"
        case DynamicValue.Sequence(elements) =>
          val elemStrings = elements.map(show)
          s"[${elemStrings.mkString(", ")}]"
        case DynamicValue.Map(entries) =>
          val entryStrings = entries.map { case (k, v) =>
            s"${show(k)} -> ${show(v)}"
          }
          s"Map(${entryStrings.mkString(", ")})"
        case Null =>
          "null"
      }
  }
}
```

The derivation process for dynamic types is more complex because the data structure is not known at compile time. Instead, we must handle different cases based on the runtime type of `DynamicValue` using pattern matching. For each subtype: `Primitive` values are converted via `toString`, `Record` fields are recursively shown, `Variant` cases display the name and contained value, `Sequence` elements are shown in bracket notation, `Map` entries are displayed as key-value pairs, and `Null` returns the string "null".

### Wrapper Derivation

When the derivation process encounters a wrapper type (e.g., a value class, opaque type, or any type that wraps another type), it calls the `deriveWrapper` method of the `Deriver`. This method receives a `Reflect[F, B]` representing the wrapped (underlying) type, along with other metadata such as the type ID, binding information, documentation, modifiers, default values, and examples:

```scala mdoc:compile-only
def deriveWrapper[F[_, _], A, B](
  wrapped: Reflect[F, B],
  typeId: TypeId[A],
  binding: Binding.Wrapper[A, B],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
)(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
  // Get Show instance for the wrapped (underlying) type B LAZILY
  val wrappedShowLazy: Lazy[Show[B]] = D.instance(wrapped.metadata)

  // Cast binding to Binding.Wrapper to access unwrap function
  val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]

  new Show[A] {
    def show(value: A): String = {
      val unwrapped = wrapperBinding.unwrap(value)
      s"${typeId.name}(${wrappedShowLazy.force.show(unwrapped)})"
    }
  }
}
```

The derivation process for wrapper types involves unwrapping the value to access the underlying type. We extract the type class instance for the wrapped type, and at runtime we use the `unwrap` function from the binding to get the underlying value, then show it using its type class instance.

### Example Usages

To see how this derivation works in practice, we can define some simple data types and then derive `Show` instances for them using the `DeriveShow` object we implemented.

1. Example 1: Simple `Person` Record with Two Primitive Fields:

```scala mdoc:silent:nest
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  implicit val show: Show[Person]     = schema.derive(DeriveShow)
}
```

Now we can use the derived `Show[Person]` instance to convert `Person` values to strings:

```scala mdoc
Person.show.show(Person("Alice", 30))
```

2. Simple Shape Variant (Circle, Rectangle)

```scala mdoc:silent
sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
  implicit val show: Show[Shape]     = schema.derive(DeriveShow)
}
```

To show a `Shape` value, we can do the following:

```scala mdoc
val shape1: Shape = Circle(5.0)
Shape.show.show(shape1)

val shape2: Shape = Rectangle(4.0, 6.0)
Shape.show.show(shape2)
```

3. Recursive Tree and Expr

```scala mdoc:silent
case class Tree(value: Int, children: List[Tree])
object Tree {
  implicit val schema: Schema[Tree] = Schema.derived[Tree]
  implicit val show: Show[Tree]     = schema.derive(DeriveShow)
}
```

The `Tree` is a record with a recursive field `children` of type `List[Tree]`. Let's see how the derived `Show[Tree]` instance handles this recursive structure:

```scala mdoc
val tree = Tree(1, List(Tree(2, List(Tree(4, Nil))), Tree(3, Nil)))
Tree.show.show(tree)
```

4. Example 4: Recursive Sealed Trait (Expr)

```scala mdoc:silent
sealed trait Expr
case class Num(n: Int)           extends Expr
case class Add(a: Expr, b: Expr) extends Expr

object Expr {
  implicit val schema: Schema[Expr] = Schema.derived[Expr]
  implicit val show: Show[Expr]     = schema.derive(DeriveShow)
}
```

Similar to `Tree`, `Expr` is a recursive variant type. The derived `Show[Expr]` instance can handle this recursive structure as well:

```scala mdoc
val expr: Expr = Add(Num(1), Add(Num(2), Num(3)))
Expr.show.show(expr)
```

5. Example 5: DynamicValue Example

```scala mdoc:silent
implicit val dynamicShow: Show[DynamicValue] = Schema.dynamic.derive(DeriveShow)
```

Let's define a `DynamicValue` that represents a record with some primitive fields and a sequence field, then show it using the derived `Show[DynamicValue]` instance:

```scala mdoc
val manualRecord = DynamicValue.Record(
  Chunk(
    "id"    -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
    "title" -> DynamicValue.Primitive(PrimitiveValue.String("Hello World")),
    "tags"  -> DynamicValue.Sequence(
      Chunk(
        DynamicValue.Primitive(PrimitiveValue.String("scala")),
        DynamicValue.Primitive(PrimitiveValue.String("zio"))
      )
    )
  )
)

dynamicShow.show(manualRecord)
```

6. Example 6: Simple Email Wrapper Type

```scala mdoc:silent
case class Email(value: String)
object Email {
  implicit val schema: Schema[Email] = Schema[String].transform(
    Email(_),
    _.value
  )
  implicit val show: Show[Email] = schema.derive(DeriveShow)
}
```

The `Email` type is a simple wrapper around `String`. Let's see how it shows an `Email` value:

```scala mdoc
val email = Email("alice@example.com")
println(s"Email: ${Email.show.show(email)}")
```

## Example 2: Deriving a `Gen` Type Class Instance

Let's say we want to derive a `Gen` type class instance for any type `A`:

```scala mdoc:silent
import scala.util.Random

trait Gen[A] {
  def generate(random: Random): A
}
```

Unlike `Show`, which is a type class for converting values of type `A` to something else (a `String`)—so you can think of it as a function of type `A => Output (String)`—the `Gen` type class is for generating values of type `A`. You can think of it as a function of type `Input (Random) => A`.

To implement the `Show` type class, we need to know what components type `A` is made up of, so we can convert each component to a `String` and combine them to form the final `String` representation of `A`. To do this, we need to be able to deconstruct a value of type `A` into its components. On the other hand, to implement the `Gen` type class, we need to know how to generate each component of type `A` using a `Random` input, and then combine those generated components to form a complete value of type `A`. This means that for `Gen`, we need to be able to construct a value of type `A` from its components, rather than deconstructing it. Therefore, in the derivation methods for `Gen`, we will use the `constructor` from the `Binding` to create values of type `A` from generated components.

Here is a simple pedagogical implementation of a `GenDeriver` that can derive `Gen` instances for various types:

```scala mdoc:silent
import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import zio.blocks.schema.binding.*
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

object DeriveGen extends Deriver[Gen] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[Gen[A]] =
    Lazy {
      new Gen[A] {
        def generate(random: Random): A = primitiveType match {
          case _: PrimitiveType.String  => random.alphanumeric.take(random.nextInt(10) + 1).mkString.asInstanceOf[A]
          case _: PrimitiveType.Char    => random.alphanumeric.head.asInstanceOf[A]
          case _: PrimitiveType.Boolean => random.nextBoolean().asInstanceOf[A]
          case _: PrimitiveType.Int     => random.nextInt().asInstanceOf[A]
          case _: PrimitiveType.Long    => random.nextLong().asInstanceOf[A]
          case _: PrimitiveType.Double  => random.nextDouble().asInstanceOf[A]
          case PrimitiveType.Unit       => ().asInstanceOf[A]
          // For brevity, other primitives default to their zero/empty value
          // In a real implementation, you'd want to handle all primitives and possibly use modifiers for ranges, etc.
          case _ =>
            defaultValue.getOrElse {
              throw new IllegalArgumentException(
                s"Gen derivation not implemented for primitive type $primitiveType " +
                        s"(typeId = $typeId) and no default value provided."
              )
            }
        }
      }
    }

  /**
   * Strategy:
   *   1. Get Gen type class instances for each field
   *   2. Generate random values for each field
   *   3. Use the constructor to build the record
   */
  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] =
    Lazy {
      // Get Gen instances for each field
      val fieldGens: IndexedSeq[Lazy[Gen[Any]]] = fields.map { field =>
        D.instance(field.value.metadata).asInstanceOf[Lazy[Gen[Any]]]
      }

      // Build Reflect.Record to access registers and constructor
      val recordFields  = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)

      new Gen[A] {
        def generate(random: Random): A = {
          // Create registers to hold field values
          val registers = Registers(recordReflect.usedRegisters)

          // Generate each field and store in registers
          fields.indices.foreach { i =>
            val value = fieldGens(i).force.generate(random)
            recordReflect.registers(i).set(registers, RegisterOffset.Zero, value)
          }

          // Construct the record from registers
          recordBinding.constructor.construct(registers, RegisterOffset.Zero)
        }
      }
    }

  /**
   * Strategy:
   *   1. Get Gen type class instances for all cases
   *   2. Randomly pick a case
   *   3. Generate a value for that case
   */
  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] = Lazy {
    // Get Gen instances for all cases
    val caseGens: IndexedSeq[Lazy[Gen[A]]] = cases.map { c =>
      D.instance(c.value.metadata).asInstanceOf[Lazy[Gen[A]]]
    }

    new Gen[A] {
      def generate(random: Random): A = {
        // Pick a random case and generate its value
        val caseIndex = random.nextInt(cases.length)
        caseGens(caseIndex).force.generate(random)
      }
    }
  }

  /**
   * Strategy:
   *   1. Get Gen type class instances for the element type
   *   2. Generate 0-5 elements
   *   3. Build the collection using the constructor
   */
  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[C[A]]] = Lazy {
    val elementGen  = D.instance(element.metadata)
    val seqBinding  = binding.asInstanceOf[Binding.Seq[C, A]]
    val constructor = seqBinding.constructor

    new Gen[C[A]] {
      def generate(random: Random): C[A] = {
        val length = random.nextInt(6) // 0 to 5 elements
        implicit val ct: scala.reflect.ClassTag[A] = scala.reflect.ClassTag.Any.asInstanceOf[scala.reflect.ClassTag[A]]

        if (length == 0) {
          constructor.empty[A]
        } else {
          val builder = constructor.newBuilder[A](length)
          (0 until length).foreach { _ =>
            constructor.add(builder, elementGen.force.generate(random))
          }
          constructor.result(builder)
        }
      }
    }
  }

  /**
   * Strategy:
   *   1. Get Gen type class instances for key and value types
   *   2. Generate 0-5 key-value pairs
   *   3. Build the map using the constructor
   */
  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[M[K, V]]] = Lazy {
    val keyGen      = D.instance(key.metadata)
    val valueGen    = D.instance(value.metadata)
    val mapBinding  = binding.asInstanceOf[Binding.Map[M, K, V]]
    val constructor = mapBinding.constructor

    new Gen[M[K, V]] {
      def generate(random: Random): M[K, V] = {
        val size = random.nextInt(6) // 0 to 5 entries

        if (size == 0) {
          constructor.emptyObject[K, V]
        } else {
          val builder = constructor.newObjectBuilder[K, V](size)
          (0 until size).foreach { _ =>
            constructor.addObject(builder, keyGen.force.generate(random), valueGen.force.generate(random))
          }
          constructor.resultObject(builder)
        }
      }
    }
  }

  /**
   * Since DynamicValue can represent any schema type, we generate random
   * dynamic values by randomly choosing a variant and generating appropriate
   * content.
   */
  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[DynamicValue]] = Lazy {
    new Gen[DynamicValue] {
      // Helper to generate a random primitive value
      private def randomPrimitive(random: Random): DynamicValue.Primitive = {
        val primitiveType = random.nextInt(5)
        primitiveType match {
          case 0 => DynamicValue.Primitive(PrimitiveValue.Int(random.nextInt()))
          case 1 => DynamicValue.Primitive(PrimitiveValue.String(random.alphanumeric.take(10).mkString))
          case 2 => DynamicValue.Primitive(PrimitiveValue.Boolean(random.nextBoolean()))
          case 3 => DynamicValue.Primitive(PrimitiveValue.Double(random.nextDouble()))
          case 4 => DynamicValue.Primitive(PrimitiveValue.Long(random.nextLong()))
        }
      }

      def generate(random: Random): DynamicValue = {
        // Randomly choose what kind of DynamicValue to generate
        // Weight towards primitives and simpler structures to avoid deep nesting
        val valueType = random.nextInt(10)
        valueType match {
          case 0 | 1 | 2 | 3 | 4 =>
            // 50% chance: generate a primitive
            randomPrimitive(random)

          case 5 | 6 =>
            // 20% chance: generate a record with 1-3 fields
            val numFields = random.nextInt(3) + 1
            val fields    = (0 until numFields).map { i =>
              val fieldName  = s"field$i"
              val fieldValue = randomPrimitive(random)
              (fieldName, fieldValue: DynamicValue)
            }
            DynamicValue.Record(Chunk.from(fields))

          case 7 | 8 =>
            // 20% chance: generate a sequence of 0-3 primitives
            val numElements = random.nextInt(4)
            val elements    = (0 until numElements).map(_ => randomPrimitive(random): DynamicValue)
            DynamicValue.Sequence(Chunk.from(elements))

          case 9 =>
            // 10% chance: generate null
            DynamicValue.Null
        }
      }
    }
  }

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] = Lazy {
    val wrappedGen     = D.instance(wrapped.metadata)
    val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]

    new Gen[A] {
      def generate(random: Random): A =
        wrapperBinding.wrap(wrappedGen.force.generate(random))
    }
  }
}
```

### Primitive Derivation

The `derivePrimitive` method is responsible for deriving a `Gen` instance for primitive types. It matches on the specific primitive type and generates random values accordingly. For example, for `String`, it generates a random alphanumeric string of random length; for `Int`, it generates a random integer; and so on. The generated value is then cast to the appropriate type `A` and returned:

```scala mdoc:silent
def derivePrimitive[A](
  primitiveType: PrimitiveType[A],
  typeId: TypeId[A],
  binding: Binding.Primitive[A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
): Lazy[Gen[A]] =
  Lazy {
    new Gen[A] {
      def generate(random: Random): A = primitiveType match {
        case _: PrimitiveType.String  => random.alphanumeric.take(random.nextInt(10) + 1).mkString.asInstanceOf[A]
        case _: PrimitiveType.Char    => random.alphanumeric.head.asInstanceOf[A]
        case _: PrimitiveType.Boolean => random.nextBoolean().asInstanceOf[A]
        case _: PrimitiveType.Int     => random.nextInt(100).asInstanceOf[A]
        case _: PrimitiveType.Long    => random.nextLong().asInstanceOf[A]
        case _: PrimitiveType.Double  => random.nextDouble().asInstanceOf[A]
        case PrimitiveType.Unit       => ().asInstanceOf[A]
        // For brevity, other primitives default to their zero/empty value
        // In a real implementation, you would want to handle all primitives and possibly use modifiers for ranges, etc.
        case _ => defaultValue.getOrElse(null.asInstanceOf[A])
      }
    }
  }
```

To handle all primitive types, you would want to implement cases for each primitive type defined in your schema system. In a real implementation, you might also want to consider using modifiers to allow users to specify constraints on the generated values (e.g., string length, numeric ranges, etc.).

### Record Derivation

The `deriveRecord` method is responsible for deriving a `Gen` instance for record types, such as case classes and tuples. The strategy for deriving a record type involves three main steps:

```scala mdoc:silent
def deriveRecord[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeId: TypeId[A],
  binding: Binding.Record[A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
)(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] =
  Lazy {
    // Get Gen instances for each field
    val fieldGens: IndexedSeq[Lazy[Gen[Any]]] = fields.map { field =>
      D.instance(field.value.metadata).asInstanceOf[Lazy[Gen[Any]]]
    }

    // Build Reflect.Record to access registers and constructor
    val recordFields  = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]
    val recordBinding = binding.asInstanceOf[Binding.Record[A]]
    val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)

    new Gen[A] {
      def generate(random: Random): A = {
        // Create registers to hold field values
        val registers = Registers(recordReflect.usedRegisters)

        // Generate each field and store in registers
        fields.indices.foreach { i =>
          val value = fieldGens(i).force.generate(random)
          recordReflect.registers(i).set(registers, RegisterOffset.Zero, value)
        }

        // Construct the record from registers
        recordBinding.constructor.construct(registers, RegisterOffset.Zero)
      }
    }
  }
```

As shown above, the implementation of the `deriveRecord` method for `Gen` is structurally similar to the `deriveRecord` method used in `Show` derivation. The primary difference is the data flow: instead of deconstructing an existing record to access its fields, we generate random values for each field. We then use `Register#set` to store these values in the registers before invoking the `constructor` from the `Binding` to create an instance of type `A`.

### Variant Derivation

The `deriveVariant` method is responsible for deriving a `Gen` instance for variant types, such as sealed traits with case classes:

```scala mdoc:silent
def deriveVariant[F[_, _], A](
  cases: IndexedSeq[Term[F, A, ?]],
  typeId: TypeId[A],
  binding: Binding.Variant[A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
)(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] = Lazy {
  // Get Gen instances for all cases
  val caseGens: IndexedSeq[Lazy[Gen[A]]] = cases.map { c =>
    D.instance(c.value.metadata).asInstanceOf[Lazy[Gen[A]]]
  }

  new Gen[A] {
    def generate(random: Random): A = {
      // Pick a random case and generate its value
      val caseIndex = random.nextInt(cases.length)
      caseGens(caseIndex).force.generate(random)
    }
  }
}
```

The derivation process for `Gen` variants is simpler than for the record case because we don't need to worry about registers or constructors. Instead, we simply need to randomly select one of the type class instances for the cases and generate a value for that case.

### Sequence Derivation

The `deriveSequence` method is responsible for deriving a `Gen` instance for sequence types, such as `List[A]`:

```scala mdoc:silent
def deriveSequence[F[_, _], C[_], A](
  element: Reflect[F, A],
  typeId: TypeId[C[A]],
  binding: Binding.Seq[C, A],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[C[A]],
  examples: Seq[C[A]]
)(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[C[A]]] = Lazy {
  val elementGen  = D.instance(element.metadata)
  val seqBinding  = binding.asInstanceOf[Binding.Seq[C, A]]
  val constructor = seqBinding.constructor

  new Gen[C[A]] {
    def generate(random: Random): C[A] = {
      val length = random.nextInt(6) // 0 to 5 elements
      implicit val ct: scala.reflect.ClassTag[A] = scala.reflect.ClassTag.Any.asInstanceOf[scala.reflect.ClassTag[A]]

      if (length == 0) {
        constructor.empty[A]
      } else {
        val builder = constructor.newBuilder[A](length)
        (0 until length).foreach { _ =>
          constructor.add(builder, elementGen.force.generate(random))
        }
        constructor.result(builder)
      }
    }
  }
}
```

A sequence is an object that contains multiple elements of the same type. To derive a `Gen` instance for a sequence, we first need to retrieve the `Gen` instance for the element type. Then, at runtime, we generate a random length for the sequence (e.g., between 0 and 5). Based on this length, we either return an empty sequence using `constructor.empty` or create a new builder using `constructor.newBuilder`. We then generate random values for each element using the element's type class instance and add them to the builder using `constructor.add`. Finally, we call `constructor.result` to build the final sequence object.

### Map Derivation

The `deriveMap` method is responsible for deriving a `Gen` instance for map types, such as `Map[K, V]`:

```scala mdoc:silent
def deriveMap[F[_, _], M[_, _], K, V](
  key: Reflect[F, K],
  value: Reflect[F, V],
  typeId: TypeId[M[K, V]],
  binding: Binding.Map[M, K, V],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[M[K, V]],
  examples: Seq[M[K, V]]
)(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[M[K, V]]] = Lazy {
  val keyGen      = D.instance(key.metadata)
  val valueGen    = D.instance(value.metadata)
  val mapBinding  = binding.asInstanceOf[Binding.Map[M, K, V]]
  val constructor = mapBinding.constructor

  new Gen[M[K, V]] {
    def generate(random: Random): M[K, V] = {
      val size = random.nextInt(6) // 0 to 5 entries

      if (size == 0) {
        constructor.emptyObject[K, V]
      } else {
        val builder = constructor.newObjectBuilder[K, V](size)
        (0 until size).foreach { _ =>
          constructor.addObject(builder, keyGen.force.generate(random), valueGen.force.generate(random))
        }
        constructor.resultObject(builder)
      }
    }
  }
}
```

The derivation process for maps is similar to sequences, but it requires handling the generation of random values for both keys and values.

### Dynamic Derivation

The `deriveDynamic` method is responsible for deriving a `Gen` instance for dynamic types, such as `DynamicValue`. Since `DynamicValue` can represent any schema type, we generate random dynamic values by choosing a variant at random and generating the appropriate content for that variant. The implementation involves pattern matching on the `DynamicValue` type and generating content accordingly:

```scala mdoc:silent
def deriveDynamic[F[_, _]](
  binding: Binding.Dynamic,
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[DynamicValue],
  examples: Seq[DynamicValue]
)(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[DynamicValue]] = Lazy {
  new Gen[DynamicValue] {
    // Helper to generate a random primitive value
    private def randomPrimitive(random: Random): DynamicValue.Primitive = {
      val primitiveType = random.nextInt(5)
      primitiveType match {
        case 0 => DynamicValue.Primitive(PrimitiveValue.Int(random.nextInt()))
        case 1 => DynamicValue.Primitive(PrimitiveValue.String(random.alphanumeric.take(10).mkString))
        case 2 => DynamicValue.Primitive(PrimitiveValue.Boolean(random.nextBoolean()))
        case 3 => DynamicValue.Primitive(PrimitiveValue.Double(random.nextDouble()))
        case 4 => DynamicValue.Primitive(PrimitiveValue.Long(random.nextLong()))
      }
    }

    def generate(random: Random): DynamicValue = {
      // Randomly choose what kind of DynamicValue to generate
      // Weight towards primitives and simpler structures to avoid deep nesting
      val valueType = random.nextInt(10)
      valueType match {
        case 0 | 1 | 2 | 3 | 4 =>
          // 50% chance: generate a primitive
          randomPrimitive(random)

        case 5 | 6 =>
          // 20% chance: generate a record with 1-3 fields
          val numFields = random.nextInt(3) + 1
          val fields    = (0 until numFields).map { i =>
            val fieldName  = s"field$i"
            val fieldValue = randomPrimitive(random)
            (fieldName, fieldValue: DynamicValue)
          }
          DynamicValue.Record(Chunk.from(fields))

        case 7 | 8 =>
          // 20% chance: generate a sequence of 0-3 primitives
          val numElements = random.nextInt(4)
          val elements    = (0 until numElements).map(_ => randomPrimitive(random): DynamicValue)
          DynamicValue.Sequence(Chunk.from(elements))

        case 9 =>
          // 10% chance: generate null
          DynamicValue.Null
      }
    }
  }
}
```

Please note that the random generation logic in this example is basic and is intended for illustrative purposes only.

### Wrapper Derivation

The `deriveWrapper` method is responsible for deriving a `Gen` instance for wrapper types, such as value classes or opaque types:

```scala mdoc:silent
def deriveWrapper[F[_, _], A, B](
  wrapped: Reflect[F, B],
  typeId: TypeId[A],
  binding: Binding.Wrapper[A, B],
  doc: Doc,
  modifiers: Seq[Modifier.Reflect],
  defaultValue: Option[A],
  examples: Seq[A]
)(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] = Lazy {
  val wrappedGen     = D.instance(wrapped.metadata)
  val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]

  new Gen[A] {
    def generate(random: Random): A =
      wrapperBinding.wrap(wrappedGen.force.generate(random))
  }
}
```

First, we retrieve the `Gen` instance for the wrapped (underlying) type `B`. Then, within the `generate` method, we generate a random value of type `B` and wrap it into type `A` using the `wrap` function provided by the binding.

### Example Usages

To see how this derivation works in practice, we can define some simple data types and then derive `Gen` instances for them using the `DeriveGen` object we implemented.

1. Example 1: Simple `Person` Record with Two Primitive Fields:

```scala mdoc:silent:nest
case class Person(name: String, age: Int)

object Person {
  implicit val schema: Schema[Person] = Schema.derived[Person]
  implicit val gen: Gen[Person]       = schema.derive(DeriveGen)
}
```

Now we can use the derived `Gen[Person]` instance to generate random `Person` values:

```scala mdoc
val random = new Random(42) // Seeded for reproducible output

Person.gen.generate(random)
Person.gen.generate(random)
Person.gen.generate(random)
```

2. Simple Shape Variant (Circle, Rectangle)

```scala mdoc:silent
sealed trait Shape
case class Circle(radius: Double)                   extends Shape
case class Rectangle(width: Double, height: Double) extends Shape

object Shape {
  implicit val schema: Schema[Shape] = Schema.derived[Shape]
  implicit val gen: Gen[Shape]       = schema.derive(DeriveGen)
}
```

To generate random `Shape` values, we can do the following:

```scala mdoc
Shape.gen.generate(random)
Shape.gen.generate(random)
Shape.gen.generate(random)
Shape.gen.generate(random)
```

3. Team with Sequence of Members (List)

```scala mdoc:silent
case class Team(members: List[String])

object Team {
  implicit val schema: Schema[Team] = Schema.derived[Team]
  implicit val gen: Gen[Team]       = schema.derive(DeriveGen)
}
```

Let's generate some random `Team` values:

```scala mdoc
Team.gen.generate(random)
Team.gen.generate(random)
Team.gen.generate(random)
```

4. Example 4: Recursive Tree

```scala mdoc:silent
case class Tree(value: Int, children: List[Tree])

object Tree {
  implicit val schema: Schema[Tree] = Schema.derived[Tree]
  implicit val gen: Gen[Tree]       = schema.derive(DeriveGen)
}
```

The `Tree` is a record with a recursive field `children` of type `List[Tree]`. Let's see how the derived `Gen[Tree]` instance handles this recursive structure:

```scala mdoc
Tree.gen.generate(random)
```

5. Example 5: DynamicValue Example

```scala mdoc:silent
implicit val dynamicGen: Gen[DynamicValue] = Schema.dynamic.derive(DeriveGen)
```

Let's generate some random `DynamicValue` instances:

```scala mdoc
dynamicGen.generate(random)
dynamicGen.generate(random)
dynamicGen.generate(random)
```

6. Example 6: Simple Email Wrapper Type

```scala mdoc:silent
case class Email(value: String)

object Email {
  implicit val schema: Schema[Email] = Schema[String].transform(
    Email(_),
    _.value
  )
  implicit val gen: Gen[Email] = schema.derive(DeriveGen)
}
```

The `Email` type is a simple wrapper around `String`. Let's see how it generates random `Email` values:

```scala mdoc
Email.gen.generate(random)
Email.gen.generate(random)
```

## Custom Type-class Instances

While automatic derivation generates type class instances for all substructures of a data type, there are times when you need to override the derived instance for a specific substructure. For example, you might want to use a custom `Show` instance for a particular field, provide a hand-written codec for a specific type that the deriver doesn't handle well, or inject a special implementation for testing purposes.

The `DerivationBuilder` provides an `instance` method that allows you to override the automatically derived type class instance for any part of the schema tree. You access the `DerivationBuilder` by calling `Schema#deriving(deriver)` instead of `Schema#derive(deriver)`:

```scala
val schema: Schema[A] = ...
val deriver: Deriver[TC] = ...

// Using derive: fully automatic, no customization
val tc: TC[A] = schema.derive(deriver)

// Using deriving: returns a DerivationBuilder for customization
val tc: TC[A] = schema.deriving(deriver)
  .instance(...)   // override specific instances
  .modifier(...)   // override specific modifiers
  .derive           // finalize the derivation
```

The `DerivationBuilder` offers two overloaded `instance` methods for providing custom type class instances:

```scala
final case class DerivationBuilder[TC[_], A](...) {
  def instance[B](optic: Optic[A, B], instance: => TC[B]): DerivationBuilder[TC, A]
  def instance[B](typeId: TypeId[B],  instance: => TC[B]): DerivationBuilder[TC, A]
}
```

### Overriding by Optic

The first overload takes an `Optic[A, B]` that precisely targets a specific location within the schema tree. This is useful when you want to override the instance for a particular field or case without affecting other occurrences of the same type:

```scala mdoc:silent:nest
import zio.blocks.schema._
import zio.blocks.typeid.TypeId

case class Person(name: String, age: Int)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person] = Schema.derived[Person]

  val name: Lens[Person, String] = $(_.name)
  val age: Lens[Person, Int]     = $(_.age)
}
```

Now we can override the `Show[String]` instance specifically for the `name` field of `Person`:

```scala mdoc:silent:nest
val customNameShow: Show[String] = new Show[String] {
  def show(value: String): String = value.toUpperCase
}

val personShow: Show[Person] = Person.schema
  .deriving(DeriveShow)
  .instance(Person.name, customNameShow)
  .derive
```

When we show a `Person`, the `name` field will use the custom `Show[String]` instance (showing it in uppercase), while the `age` field will use the automatically derived `Show[Int]` instance:

```scala mdoc
personShow.show(Person("Alice", 30))
```

You can also target deeper nested fields using composed optics. For example, if you have a `Company` that contains a `Person`, you can target the `name` field inside the nested `Person`:

```scala mdoc:silent
case class Company(ceo: Person, industry: String)

object Company extends CompanionOptics[Company] {
  implicit val schema: Schema[Company] = Schema.derived[Company]

  val ceo: Lens[Company, Person]       = $(_.ceo)
  val ceoName: Lens[Company, String]   = $(_.ceo.name)
  val industry: Lens[Company, String]  = $(_.industry)
}
```

```scala mdoc:silent:nest
val companyShow: Show[Company] = Company.schema
  .deriving(DeriveShow)
  .instance(Company.ceoName, customNameShow)
  .derive
```

In this case, the custom `Show[String]` instance only applies to the CEO's name. The `industry` field, which is also a `String`, will use the default derived `Show[String]` instance:

```scala mdoc
companyShow.show(Company(Person("Alice", 30), "tech"))
```

### Overriding by TypeId

The second overload takes a `TypeId[B]` and applies the custom instance to **all occurrences** of type `B` anywhere in the schema tree. This is useful when you want to override the instance for a type globally, without having to specify each location:

```scala mdoc:silent:nest
val customIntShow: Show[Int] = new Show[Int] {
  def show(value: Int): String = s"#$value"
}

val personShow: Show[Person] = Person.schema
  .deriving(DeriveShow)
  .instance(TypeId.int, customIntShow)
  .derive
```

All `Int` fields in the `Person` schema (in this case, just `age`) will use the custom `Show[Int]` instance:

```scala mdoc
personShow.show(Person("Alice", 30))
```

### Resolution Order

When the derivation engine encounters a schema node, it resolves the type class instance using the following priority order:

1. **Optic-based override** (most precise): If an instance override was registered using an optic that matches the current path in the schema tree, that instance is used.
2. **TypeId-based override** (more general): If no optic-based match is found, it checks for an instance override registered by type ID.
3. **Automatic derivation** (default): If no override is found, the deriver's method (e.g., `derivePrimitive`, `deriveRecord`) is called to automatically derive the instance.

This means you can set a global override by type and then selectively refine specific fields using optics:

```scala mdoc:silent:nest
val companyShow: Show[Company] = Company.schema
  .deriving(DeriveShow)
  .instance(TypeId.string, new Show[String] {
    def show(value: String): String = s"'$value'"
  })
  .instance(Company.ceoName, new Show[String] {
    def show(value: String): String = value.toUpperCase
  })
  .derive
```

In this example, all `String` fields use single quotes, except for the CEO's name which is shown in uppercase:

```scala mdoc
companyShow.show(Company(Person("Alice", 30), "tech"))
```

### Chaining Multiple Overrides

The `instance` method returns a new `DerivationBuilder`, so you can chain multiple overrides fluently:

```scala mdoc:silent:nest
val personShow: Show[Person] = Person.schema
  .deriving(DeriveShow)
  .instance(Person.name, new Show[String] {
    def show(value: String): String = s"<<$value>>"
  })
  .instance(Person.age, new Show[Int] {
    def show(value: Int): String = s"age=$value"
  })
  .derive
```

```scala mdoc
personShow.show(Person("Alice", 30))
```

## Custom Modifiers

Modifiers are metadata annotations that influence how type class instances behave at runtime. For example, the `Modifier.rename` modifier tells a JSON codec to use a different field name during serialization, and `Modifier.transient` tells it to skip a field entirely.

While modifiers can be attached to schemas directly using Scala annotations (e.g., `@Modifier.transient`) or the `Schema#modifier` method, the `DerivationBuilder` provides a way to inject modifiers **programmatically at derivation time** without modifying the schema itself. This is particularly useful when:

- You don't control the schema definition (e.g., it comes from a library)
- You need different modifiers for different derivation contexts (e.g., one JSON codec with renamed fields, another without)
- You want to keep the schema clean and push format-specific concerns into the derivation layer

The `DerivationBuilder` offers two overloaded `modifier` methods:

```scala
final case class DerivationBuilder[TC[_], A](...) {
  def modifier[B](typeId: TypeId[B],  modifier: Modifier.Reflect): DerivationBuilder[TC, A]
  def modifier[B](optic: Optic[A, B], modifier: Modifier)        : DerivationBuilder[TC, A]
}
```

### Modifier Hierarchy

ZIO Blocks has two categories of modifiers:

- **`Modifier.Reflect`**: Type-level modifiers that apply to the schema node itself (e.g., `Modifier.config`).
- **`Modifier.Term`**: Field-level or case-level modifiers that apply to a specific field of a record or case of a variant (e.g., `Modifier.transient`, `Modifier.rename`, `Modifier.alias`).

Note that `Modifier.config` extends both `Modifier.Term` and `Modifier.Reflect`, so it can be used at both levels.

### Adding Modifiers by Optic

When you pass an optic and a `Modifier.Term` to the `modifier` method, the modifier is attached to the **term** (field or case) identified by the last segment of the optic path. When you pass a `Modifier.Reflect`, it is attached to the **schema node** targeted by the optic:

```scala mdoc:silent:nest
import zio.blocks.schema.json._

case class User(
  id: Long,
  name: String,
  email: String,
  internalScore: Double
)

object User extends CompanionOptics[User] {
  implicit val schema: Schema[User] = Schema.derived[User]

  val id: Lens[User, Long]              = $(_.id)
  val name: Lens[User, String]          = $(_.name)
  val email: Lens[User, String]         = $(_.email)
  val internalScore: Lens[User, Double] = $(_.internalScore)
}
```

Now we can derive a JSON codec with custom modifiers, renaming fields and marking one as transient, without changing the schema itself:

```scala mdoc:silent:nest
val jsonCodec: JsonBinaryCodec[User] = User.schema
  .deriving(JsonBinaryCodecDeriver)
  .modifier(User.name, Modifier.rename("full_name"))
  .modifier(User.email, Modifier.alias("mail"))
  .modifier(User.internalScore, Modifier.transient())
  .derive
```

In this example:
- The `name` field will be serialized as `full_name` in JSON.
- The `email` field will accept both `email` and `mail` as keys during deserialization.
- The `internalScore` field will be excluded from serialization entirely.

```scala mdoc
val user = User(1L, "Alice", "alice@example.com", 95.5)
new String(jsonCodec.encode(user), "UTF-8")
```

### Adding Modifiers by TypeId

The `modifier` method with `TypeId` allows you to add a `Modifier.Reflect` to all schema nodes of a given type. This is useful for attaching format-specific configuration metadata to all occurrences of a type:

```scala mdoc:silent:nest
val jsonCodec: JsonBinaryCodec[User] = User.schema
  .deriving(JsonBinaryCodecDeriver)
  .modifier(TypeId.of[User], Modifier.config("json", "camelCase"))
  .modifier(User.internalScore, Modifier.transient())
  .derive
```

## Derivation Process In-Depth

Until now, we learned how to implement the `Deriver` methods for different schema patterns. But we haven't yet discussed how the overall derivation process works. In this section, we will go through the main steps of derivation in detail.

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

```scala mdoc:silent:nest
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

```scala mdoc:silent:nest
case class Person(name: String, age: Int)

object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val show: Show[Person]     = schema.derive(DeriveShow)
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
val result = Person.show.show(Person("Alice", 30))
// result: String = "Person(name = Alice, age = 30)"
```

The interesting part here is how the `show` method of the derived `Show[Person]` instance works. It uses the `HasInstance` type class to access the derived `Show` instances for each field of the `Person` record (i.e., `Show[String]` for the `name` field and `Show[Int]` for the `age` field). This allows it to recursively display each field using its respective `Show` instance, demonstrating the composability and reusability of type class instances in the derivation system.

Please note that this happens when either the `Deriver` implementation uses the `HasInstance` implicit parameter or uses the centralized recursive approach to access nested derived instances.
