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

## Example: Deriving a `Show` Type Class Instance

Let's say we want to derive a `Show` type class instance for any type of type `A`:

```scala
trait Show[A] {
  def show(value: A): String
}
```

The implementation of the `Deriver[Show]` would look like the following code. Don't worry about understanding every detail right now; we'll break down the derivation process step by step afterward.

```scala mdoc:compile-only
import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import zio.blocks.schema.DynamicValue.Null
import zio.blocks.schema.binding.*
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

object DeriveShow extends Deriver[Show] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding[BindingType.Primitive, A],
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
    binding: Binding[BindingType.Record, A],
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
    cases: IndexedSeq[Term[F, A, _]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Variant, A],
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
    binding: Binding[BindingType.Seq[C], C[A]],
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
    binding: Binding[BindingType.Map[M], M[K, V]],
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
    binding: Binding[BindingType.Dynamic, DynamicValue],
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
    binding: Binding[BindingType.Wrapper[A, B], A],
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
      def show(value: A): String =
        // Unwrap returns Either[SchemaError, B] now
        wrapperBinding.unwrap(value) match {
          case Right(unwrapped) =>
            // Show the underlying value with the wrapper type name
            // Force the wrapped Show instance only when actually showing
            s"${typeId.name}(${wrappedShowLazy.force.show(unwrapped)})"
          case Left(error) =>
            // Handle unwrap failure - show error information
            s"${typeId.name}(<unwrap failed: ${error.message}>)"
        }
    }
  }
}
```

Now let's see how the derivation process works step by step.

### Primitive Derivation

When the derivation process encounters a primitive type (e.g., `String`, `Int`), it calls the `derivePrimitive` method of the `Deriver`. This method receives the `PrimitiveType[A]` information, which allows it to determine how to encode and decode values of that type:

```scala
override def derivePrimitive[A](
  primitiveType: PrimitiveType[A],
  typeId: TypeId[A],
  binding: Binding[BindingType.Primitive, A],
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

```scala
override def deriveRecord[F[_, _], A](
  fields: IndexedSeq[Term[F, A, ?]],
  typeId: TypeId[A],
  binding: Binding[BindingType.Record, A],
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

```scala
override def deriveVariant[F[_, _], A](
  cases: IndexedSeq[Term[F, A, _]],
  typeId: TypeId[A],
  binding: Binding[BindingType.Variant, A],
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

The derivation process for variants is similar to records, but instead of fields, we have cases. We extract the type class instances for each case, and at runtime we use the discriminator to determine which case the value belongs to. Then we use the matcher to downcast the value to that specific case type. Finally, we extract the corresponding type class instance for that case by applying the case index to the indexed sequence of type class instances. Now we have the correct type class instance for the specific case, but it's deferred in a `Lazy` container. We force it to retrieve the actual type class instance, and then we can call the `show` method on that case value to get the string representation.

## Derivation Process Overview including Internal Mechanics

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


