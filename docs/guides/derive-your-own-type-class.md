---
id: derive-your-own-type-class
title: "How to Derive Your Own Type Class from a Schema"
description: "Write a Deriver for your own type class, here a log redactor that masks sensitive fields, and derive it for any type with a Schema."
keywords:
  - "Type Class Derivation"
  - "Log Redaction"
  - "Deriver"
  - "Modifier.config"
  - "withInstance"
  - "Library Integration"
---

In this guide, we write `Redact[A]`, a type class that renders a value for a log line with its sensitive fields replaced by `***`, and we derive it for a small Scala bookstore from each type's `Schema`. By the end, any case class or sealed trait with a schema gets a redacting renderer for free, sensitive fields are marked with one annotation, and the type class is packaged so its users write `derives Schema, Redact`.

The [Type-Class Derivation](../reference/schema/type-class-derivation.md) reference already builds a `Show` and a `Gen` type class and walks through every `Deriver` method in depth; that is the place to learn each method's parameters. This guide is the short recipe for your own type class, and it adds the parts a real library needs: reading modifiers during derivation, overriding one type, and packaging the result.

We cover:

- Defining the type class
- Handling primitives, records and variants
- Handling collections, wrappers and dynamic values
- Deriving the type class and printing the result
- Masking fields through modifiers
- Overriding the instance for one type
- Packaging the type class for reuse

## The Problem

Logs must not contain payment tokens, card numbers or email addresses, but a case class's `toString` prints every field. The usual fix is a hand-written log renderer for each type, which must mask the right fields and recurse into every nested type.

Every new type needs another renderer, and every new field needs an edit to an existing one. Nothing fails when someone adds a sensitive field and forgets to mask it; the value goes straight into the logs. Here is that approach written by hand:

```scala mdoc:compile-only
final case class LoggedBook(title: String, firstAuthor: String, year: Int)
final case class LoggedCustomer(name: String, email: String)
final case class LoggedOrder(id: Long, customer: LoggedCustomer, books: List[LoggedBook], paymentToken: String)

def bookForLog(b: LoggedBook): String =
  s"""LoggedBook(title = "${b.title}", firstAuthor = "${b.firstAuthor}", year = ${b.year})"""

def customerForLog(c: LoggedCustomer): String =
  s"""LoggedCustomer(name = "${c.name}", email = ***)"""

def orderForLog(o: LoggedOrder): String =
  s"""LoggedOrder(id = ${o.id}, customer = ${customerForLog(o.customer)}, books = ${o.books
      .map(bookForLog)
      .mkString("[", ", ", "]")}, paymentToken = ***)"""
```

This doesn't scale past a handful of types. The masking rules live in string templates far from the fields they protect, and a library cannot ship such renderers for its users' types at all.

In ZIO Blocks, a `Schema[A]` describes the structure of `A`, and a `Deriver[TC]` turns that structure into a `TC[A]` for any type. We write one `Deriver[Redact]`, mark the sensitive fields in the case class, and derive every renderer from the schema.

## Prerequisites

This guide builds on [Your First Schema](./your-first-schema.md): you should know how to derive a `Schema` for a case class. Add the schema module to your `build.sbt`:

```scala
// Scala 2.13 and Scala 3.3 LTS
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "@VERSION@"

// Scala 3.7 and newer
libraryDependencies += "dev.zio" %% "zio-blocks-next-schema" % "@VERSION@"
```

We use these imports throughout:

```scala mdoc:silent
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId
```

## The Bookstore Model

Our bookstore sells books in several formats, and an `Order` holds a customer, the books bought, and a payment token. The token is the field that must never reach a log, so we mark it with `Modifier.config("log.redact", "true")`; the deriver reads that marker later. `Email` is a wrapper type with its own schema, built with `transform`:

```scala mdoc:silent
sealed trait Format
object Format {
  case object Hardcover                  extends Format
  case object Paperback                  extends Format
  final case class Ebook(sizeMb: Double) extends Format

  implicit val schema: Schema[Format] = Schema.derived[Format]
}

final case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], format: Format)
object Book {
  implicit val schema: Schema[Book] = Schema.derived[Book]
}

final case class Email(value: String)
object Email {
  implicit val schema: Schema[Email] = Schema[String].transform(Email(_), _.value)
}

final case class Customer(name: String, email: Email)
object Customer {
  implicit val schema: Schema[Customer] = Schema.derived[Customer]
}

final case class Order(
  id: Long,
  customer: Customer,
  books: List[Book],
  @Modifier.config("log.redact", "true") paymentToken: String
)
object Order {
  implicit val schema: Schema[Order] = Schema.derived[Order]
}
```

We use three real Scala books as data. `Book` has a single author field, which holds the first author:

```scala mdoc:silent
val zionomicon         = Book("Zionomicon", "John A. De Goes", 2024, None, Format.Ebook(12.5))
val programmingInScala = Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), Format.Hardcover)
val fpInScala          = Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), Format.Paperback)

val order = Order(1001L, Customer("Ada", Email("ada@example.com")), List(zionomicon), "tok_4242")
```

## Defining the Type Class

A type class for derivation is an ordinary trait with one type parameter. `Redact[A]` turns an `A` into a log-safe string. The companion holds the mask text and a `derived` method that builds an instance from a schema with the deriver we write next:

```scala mdoc:silent
trait Redact[A] {
  def redact(value: A): String
}

object Redact {
  val Masked = "***"

  def derived[A](implicit schema: Schema[A]): Redact[A] = schema.derive(RedactDeriver)
}
```

`Schema#derive` walks the schema's structure and calls one `Deriver` method per node. We implement those methods in the next two sections, then return to `derived` in [Packaging the Type Class for Reuse](#packaging-the-type-class-for-reuse).

## Handling Primitives, Records and Variants

A `Deriver[Redact]` has one method for each kind of schema node. Primitives, records and variants carry the shape of a domain model, so we put them in a trait of their own:

```scala mdoc:silent
trait RedactStructure extends Deriver[Redact] {

  protected def isSensitive(modifiers: Seq[Modifier]): Boolean =
    modifiers.exists {
      case Modifier.config("log.redact", "true") => true
      case _                                     => false
    }

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[Redact[A]] =
    Lazy {
      primitiveType match {
        case _: PrimitiveType.String => (value: A) => "\"" + value + "\""
        case _                       => (value: A) => String.valueOf(value)
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[A]] = {
    val names     = fields.map(_.name)
    val sensitive = fields.map(field => isSensitive(field.modifiers))
    val reflect   = new Reflect.Record[Binding, A](
      fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
      typeId,
      binding,
      doc,
      modifiers
    )
    Lazy {
      new Redact[A] {
        private lazy val children: IndexedSeq[Redact[Any]] =
          fields.map(field => D.instance(field.value.metadata).force.asInstanceOf[Redact[Any]])

        def redact(value: A): String = {
          val registers = Registers(reflect.usedRegisters)
          binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
          val rendered = fields.indices.map { i =>
            val shown =
              if (sensitive(i)) Redact.Masked
              else children(i).redact(reflect.registers(i).get(registers, RegisterOffset.Zero))
            s"${names(i)} = $shown"
          }
          if (fields.isEmpty) typeId.name else rendered.mkString(s"${typeId.name}(", ", ", ")")
        }
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[A]] =
    Lazy {
      new Redact[A] {
        private lazy val children: IndexedSeq[Redact[Any]] =
          cases.map(c => D.instance(c.value.metadata).force.asInstanceOf[Redact[Any]])

        def redact(value: A): String = {
          val i = binding.discriminator.discriminate(value)
          children(i).redact(binding.matchers(i).downcastOrNull(value))
        }
      }
    }
}
```

Each method returns a `Lazy[Redact[A]]`, and the instances for child nodes come from `D.instance(...)`, also as `Lazy` values:

- **`derivePrimitive`** quotes strings and prints every other primitive with `String.valueOf`.
- **`deriveRecord`** reads each field's name and modifiers once, at derivation time. At run time it copies the field values out of the record with the binding's deconstructor and renders each one with its child instance, or prints `***` for a sensitive field. The temporary `Reflect.Record` gives us the register positions of the fields; [Record Derivation](../reference/schema/type-class-derivation.md#record-derivation) in the reference explains this pattern line by line.
- **`deriveVariant`** asks the binding's discriminator which case a value belongs to, downcasts it, and delegates to that case's instance.

Both `deriveRecord` and `deriveVariant` force their child instances inside a `private lazy val`, at the first `redact` call, not during derivation. A recursive type refers back to an instance that is still being built, and forcing it early would loop forever. See [Lazy](../reference/schema/lazy.md) for how `Lazy` breaks such cycles.

## Handling Collections, Wrappers and Dynamic Values

The remaining four methods cover containers. We implement them on the object our users will pass to `derive`:

```scala mdoc:silent
object RedactDeriver extends RedactStructure {

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[C[A]]] =
    D.instance(element.metadata).map { elem => (value: C[A]) =>
      binding.deconstructor.deconstruct(value).map(elem.redact).mkString("[", ", ", "]")
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
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[M[K, V]]] =
    D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (k, v) => (m: M[K, V]) =>
      val d = binding.deconstructor
      d.deconstruct(m).map(kv => s"${k.redact(d.getKey(kv))} -> ${v.redact(d.getValue(kv))}").mkString("{", ", ", "}")
    }

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[A]] =
    D.instance(wrapped.metadata).map(inner => (value: A) => inner.redact(binding.unwrap(value)))

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[DynamicValue]] =
    Lazy((_: DynamicValue) => Redact.Masked)
}
```

Sequences, maps and wrappers are not recursive on their own, so they combine their child instances with `Lazy#map` and `Lazy#zip` instead of a `lazy val`. A wrapper unwraps its value and renders the inner one, so `Email("ada@example.com")` prints as a plain string.

A `DynamicValue` has no schema and so no modifiers; the deriver cannot tell which of its parts are sensitive. For a log redactor the safe choice is to mask the whole value. A type class with no sensible behaviour for a node kind can fail instead, by returning `Lazy.fail(new UnsupportedOperationException("..."))`, which throws when the instance is forced.

## Deriving the Type Class and Printing the Result

`Schema#derive` takes the deriver and returns the instance. We derive `Redact[Book]` and render our three books:

```scala mdoc:silent
val redactBook: Redact[Book] = Book.schema.derive(RedactDeriver)
```

```scala mdoc
List(zionomicon, programmingInScala, fpInScala).foreach(book => println(redactBook.redact(book)))
```

The `format` field is a sealed trait, so `deriveVariant` picks the case and the case's record instance renders it; a case object is a record with no fields and prints as its name. `Option` is a variant too, with a `Some` record holding one `value` field and a field-less `None`.

Now the whole order. The nested `Customer`, the list of books and the `Email` wrapper all come from the same deriver:

```scala mdoc:silent
val redactOrder: Redact[Order] = Order.schema.derive(RedactDeriver)
```

```scala mdoc
println(redactOrder.redact(order))
```

The payment token is masked. The customer's email address is still in plain text; the next two sections fix that.

## Masking Fields Through Modifiers

The `@Modifier.config("log.redact", "true")` annotation on `paymentToken` reaches `deriveRecord` through the field's `Term#modifiers`, which is where `isSensitive` looks. `Modifier.config` takes any key and value, so a type class can define its own markers without new annotation types; [`config`](../reference/schema/modifier.md#config) in the Modifier reference describes the key convention. The annotation works on case class fields in Scala 2.13 and Scala 3.

When you cannot annotate the case class, for example because it comes from another library, attach the same modifier from outside with `Deriver#withModifier`. It takes the owning type's `TypeId`, the field name and a `Modifier.Term`, and returns a new deriver. Here we mask the customer's name as well:

```scala mdoc:silent
val strictDeriver: Deriver[Redact] =
  RedactDeriver.withModifier(TypeId.of[Customer], "name", Modifier.config("log.redact", "true"))
```

```scala mdoc
println(Order.schema.derive(strictDeriver).redact(order))
```

:::warning
`withModifier` silently ignores a field name that does not exist in the type. Check the output after renaming a field.
:::

## Overriding the Instance for One Type

Sometimes the rule belongs to a type, not a field: every `Email`, wherever it appears, should show only its first letter and its domain. Write that instance by hand and register it once on the deriver with `Deriver#withInstance`. Every derivation through the returned deriver uses it instead of calling `deriveWrapper` for `Email`:

```scala mdoc:silent
val partialEmail: Redact[Email] = (email: Email) => {
  val at = email.value.indexOf('@')
  email.value.take(1) + Redact.Masked + email.value.drop(at)
}

val logDeriver: Deriver[Redact] = RedactDeriver.withInstance[Email](partialEmail)
```

```scala mdoc
println(Order.schema.derive(logDeriver).redact(order))
```

Derivation never searches implicit scope, so declaring `implicit val partialEmail` next to `Email` has no effect on the derived instances. Register the instance on the deriver, as above.

For an override that applies to one derivation only, use `Schema#deriving` and `DerivationBuilder#instance` instead:

```scala mdoc
println(Order.schema.deriving(RedactDeriver).instance(TypeId.of[Email], partialEmail).derive.redact(order))
```

:::warning
When the deriver and the builder both override the same type, the deriver's `withInstance` wins and the builder's `instance` is silently ignored. Override a type in one place only.
:::

[How to Override the Codec for One Type Everywhere](./override-codec-for-one-type.md) applies the same tools to JSON codecs and compares every override scope.

## Packaging the Type Class for Reuse

Users of our type class should not need to know about `RedactDeriver`. The `derived` method on the `Redact` companion takes the schema as an implicit parameter and derives the instance, and in Scala 3 that is all a `derives` clause needs. A recursive category tree shows that the `lazy val` pattern from `deriveRecord` handles recursion:

```scala mdoc:silent
final case class Category(name: String, subcategories: List[Category]) derives Schema, Redact

val programming = Category("Programming", List(Category("Scala", List(Category("Functional Programming", Nil)))))
```

```scala mdoc
println(summon[Redact[Category]].redact(programming))
```

`derives Schema, Redact` generates a `Schema[Category]` and then a `Redact[Category]` from it, both in the companion object. Scala 2.13 has no `derives` clause, so declare the instances in the companion yourself:

```scala
// Scala 2.13
final case class Category(name: String, subcategories: List[Category])
object Category {
  implicit val schema: Schema[Category] = Schema.derived[Category]
  implicit val redact: Redact[Category] = Redact.derived[Category]
}
```

The deriver code itself is the same in both Scala versions, apart from wildcard syntax: Scala 2.13 writes `Term[F, A, _]` where Scala 3 writes `Term[F, A, ?]`.

Store the derived instance in a `val`, as both forms do. Each `derive` call walks the whole schema again.

## Putting It Together

This example puts the whole type class, the deriver, the bookstore model, a field marked through `withModifier` and the `Email` override into one program:

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/CompleteExample.scala
```

## Running the Examples

All examples in this guide have corresponding runnable Scala files in the `schema-examples` project. To get them, clone the repository and change into the examples project:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/schema-examples
```

The step examples share two files: `Redact.scala` holds the type class and its deriver, and `Bookstore.scala` holds the domain model and sample data.

<details>
  <summary>schema-examples/src/main/scala/deriveyourowntypeclass/Redact.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/Redact.scala:show-line-numbers
```

</details>

<details>
  <summary>schema-examples/src/main/scala/deriveyourowntypeclass/Bookstore.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/Bookstore.scala:show-line-numbers
```

</details>

Run the steps in order to follow the guide.

### Deriving the Type Class

This example derives `Redact` for `Book` and `Order` and prints the three books and the order.

<details>
  <summary>schema-examples/src/main/scala/deriveyourowntypeclass/Step1DeriveExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/Step1DeriveExample.scala:show-line-numbers
```

</details>

Run it from the `schema-examples` directory:

```bash
sbt "runMain deriveyourowntypeclass.Step1DeriveExample"
```

### Masking Fields Through Modifiers

This example masks the customer's name with `withModifier`, next to the annotated payment token.

<details>
  <summary>schema-examples/src/main/scala/deriveyourowntypeclass/Step2ModifiersExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/Step2ModifiersExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain deriveyourowntypeclass.Step2ModifiersExample"
```

### Overriding the Instance for One Type

This example masks every `Email` partially, first on the deriver with `withInstance`, then for one derivation with `DerivationBuilder#instance`.

<details>
  <summary>schema-examples/src/main/scala/deriveyourowntypeclass/Step3OverrideExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/Step3OverrideExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain deriveyourowntypeclass.Step3OverrideExample"
```

### Packaging the Type Class for Reuse

This example derives `Redact` for a recursive `Category` with a `derives Schema, Redact` clause.

<details>
  <summary>schema-examples/src/main/scala/deriveyourowntypeclass/Step4PackagingExample.scala</summary>

```scala mdoc:embed:schema-examples/src/main/scala/deriveyourowntypeclass/Step4PackagingExample.scala:show-line-numbers
```

</details>

```bash
sbt "runMain deriveyourowntypeclass.Step4PackagingExample"
```

### The Complete Example

```bash
sbt "runMain deriveyourowntypeclass.CompleteExample"
```

## Going Further

- [Type-Class Derivation](../reference/schema/type-class-derivation.md) builds `Show` and `Gen` instances and explains every `Deriver` method, including the record-register and recursion patterns used here.
- [Customizing Derivation with Instance and Modifier Overrides](../reference/schema/type-class-derivation.md#customizing-derivation-with-instance-and-modifier-overrides) covers every `withInstance` and `withModifier` overload and how overrides chain.
- [Modifier](../reference/schema/modifier.md) lists the built-in modifiers and how to read them from a schema.
- [Lazy](../reference/schema/lazy.md) documents the `Lazy` operations a deriver uses to compose child instances.
