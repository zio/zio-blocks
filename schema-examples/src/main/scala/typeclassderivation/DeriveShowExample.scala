package typeclassderivation

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.DynamicValue.Null
import zio.blocks.schema.binding._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

/**
 * Implementation of Deriver to derive Show type-class instances for various
 * schema types.
 *
 * Implementation pattern (from SchemaDerivationShowSpec):
 *   - Record/Variant (potentially recursive): pre-compute structural setup
 *     (field names, bindings, reflects) outside the Lazy block; inside Lazy,
 *     use `private lazy val resolvedShows` to defer forcing child instances
 *     until first show() call — this breaks initialization cycles for recursive
 *     types like `sealed trait Expr; case class Add(a: Expr, b: Expr)`.
 *   - Sequence/Map/Wrapper (non-recursive): use `instance(...).map` / `.zip`
 *     monadic composition — no explicit `.force` needed.
 *   - Primitive/Dynamic: simple `Lazy { new Show { ... } }`.
 */
object DeriveShowExample extends App {
  trait Show[A] {
    def show(value: A): String
  }

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
      fields: IndexedSeq[Term[F, A, _]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = {
      // Pre-compute structural setup outside Lazy — none of this touches Deferred nodes
      val fieldNames = fields.map(_.name)
      // Cast fields to use Binding as F (we are going to create Reflect.Record with Binding as F)
      val recordFields = fields.asInstanceOf[IndexedSeq[Term[Binding, A, _]]]
      // Cast to Binding.Record to access constructor/deconstructor
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      // Build a Reflect.Record to get access to the computed registers for each field
      val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)
      Lazy {
        new Show[A] {
          // Defer child-instance resolution to first show() call via lazy val.
          // For recursive types (e.g. case class Tree(children: List[Tree])), accessing
          // field.value.metadata during derivation would re-enter a Deferred node that is
          // still initialising, causing an infinite loop. By using a lazy val here we
          // guarantee the access only happens after the framework has finished deriving
          // all instances and every Lazy is fully memoised.
          private lazy val resolvedShows: IndexedSeq[Show[Any]] =
            fields.map(field => D.instance(field.value.metadata).asInstanceOf[Lazy[Show[Any]]].force)
          def show(value: A): String = {
            // Create registers with space for all used registers to hold deconstructed field values
            val registers = Registers(recordReflect.usedRegisters)
            // Deconstruct field values of the record into the registers
            recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
            // Build string representations for all fields
            val fieldStrings = fields.indices.map { i =>
              val fieldValue = recordReflect.registers(i).get(registers, RegisterOffset.Zero)
              s"${fieldNames(i)} = ${resolvedShows(i).show(fieldValue)}"
            }
            s"${typeId.name}(${fieldStrings.mkString(", ")})"
          }
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
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = {
      // Collect the Lazy[Show] references for each case outside the Lazy block.
      // Capturing Lazy refs is safe here; we only .force them later inside the lazy val.
      val caseShowLazies = cases.map(c => D.instance(c.value.metadata).asInstanceOf[Lazy[Show[Any]]])

      // Cast binding to Binding.Variant to access discriminator and matchers
      val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
      Lazy {
        new Show[A] {
          // Force child instances lazily — same recursive-safety rationale as deriveRecord
          private lazy val resolvedShows: IndexedSeq[Show[Any]] = caseShowLazies.map(_.force)
          // Implement show by using discriminator and matchers to find the right case
          // The `value` parameter is of type A (the variant type), e.g. a Shape value
          def show(value: A): String = {
            // Use discriminator to determine which case this value belongs to
            val caseIndex = variantBinding.discriminator.discriminate(value)
            // Use matcher to downcast to the specific case type
            val caseValue = variantBinding.matchers(caseIndex).downcastOrNull(value)
            // Delegate to the case's Show instance — it already knows its own name
            resolvedShows(caseIndex).show(caseValue)
          }
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
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[C[A]]] = {
      // Cast binding to Binding.Seq to access the deconstructor
      val deconstructor = binding.asInstanceOf[Binding.Seq[C, A]].deconstructor
      // Sequences are structurally non-recursive, so we can use monadic .map composition.
      // instance(...).map { elementShow => ... } returns a Lazy that, when forced, builds
      // a Show[C[A]] with elementShow already resolved — no .force needed at show()-time.
      D.instance(element.metadata).map { elementShow =>
        new Show[C[A]] {
          def show(value: C[A]): String = {
            // Use deconstructor to iterate over elements and show each one
            val elements = deconstructor.deconstruct(value).map(elementShow.show)
            s"[${elements.mkString(", ")}]"
          }
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
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[M[K, V]]] = {
      // Cast binding to Binding.Map to access the deconstructor
      val deconstructor = binding.asInstanceOf[Binding.Map[M, K, V]].deconstructor
      // Maps are non-recursive: use .zip to pair the two child Lazy instances, then .map
      // to build the Show[M[K,V]] with both keyShow and valueShow already resolved.
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (keyShow, valueShow) =>
        new Show[M[K, V]] {
          def show(m: M[K, V]): String = {
            // Use deconstructor to iterate over key-value pairs
            val entries = deconstructor
              .deconstruct(m)
              .map { kv =>
                val k = deconstructor.getKey(kv)
                val v = deconstructor.getValue(kv)
                s"${keyShow.show(k)} -> ${valueShow.show(v)}"
              }
              .mkString(", ")
            s"Map($entries)"
          }
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
            case DynamicValue.Primitive(_) =>
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
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = {
      // Cast binding to Binding.Wrapper to access the unwrap function
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      // Wrappers are non-recursive: use .map so wrappedShow is already resolved
      // when show() is called — no .force needed.
      D.instance(wrapped.metadata).map { wrappedShow =>
        new Show[A] {
          def show(value: A): String = {
            // Unwrap the value to access the underlying type B, then delegate to its Show
            val unwrapped = wrapperBinding.unwrap(value)
            s"${typeId.name}(${wrappedShow.show(unwrapped)})"
          }
        }
      }
    }
  }

  // ===========================================
  // Examples
  // ===========================================

  def printHeader(title: String): Unit = {
    println()
    println("=" * 80)
    println(title)
    println("=" * 80)
  }

  /**
   * Example 1: Simple Person Record with two primitive fields
   */
  printHeader("Example 1: Simple Person Record with two primitive fields")

  case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val show: Show[Person]     = schema.derive(DeriveShow)
  }

  println(Person.show.show(Person("Alice", 30)))

  /**
   * Example 2: Simple Shape Variant (Circle, Rectangle)
   */
  printHeader("Example 2: Simple Shape Variant (Circle, Rectangle)")
  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived[Shape]
    implicit val show: Show[Shape]     = schema.derive(DeriveShow)
  }

  val shape1: Shape = Circle(5.0)
  val shape2: Shape = Rectangle(4.0, 6.0)
  println(Shape.show.show(shape1))
  println(Shape.show.show(shape2))

  /**
   * Example 3: Recursive Tree and Expr
   */
  printHeader("Example 3: Recursive Tree")
  case class Tree(value: Int, children: List[Tree])
  object Tree {
    implicit val schema: Schema[Tree] = Schema.derived[Tree]
    implicit val show: Show[Tree]     = schema.derive(DeriveShow)
  }

  val tree = Tree(1, List(Tree(2, List(Tree(4, Nil))), Tree(3, Nil)))
  println(Tree.show.show(tree))

  /**
   * Example 4: Recursive Sealed Trait (Expr)
   */
  printHeader("Example 4: Recursive Sealed Trait (Expr)")
  sealed trait Expr
  case class Num(n: Int)           extends Expr
  case class Add(a: Expr, b: Expr) extends Expr

  object Expr {
    implicit val schema: Schema[Expr] = Schema.derived[Expr]
    implicit val show: Show[Expr]     = schema.derive(DeriveShow)
  }

  val expr: Expr = Add(Num(1), Add(Num(2), Num(3)))
  println(Expr.show.show(expr))

  /**
   * Example 5: DynamicValue Example
   */
  printHeader("Example 5: DynamicValue Example")
  implicit val dynamicShow: Show[DynamicValue] = Schema.dynamic.derive(DeriveShow)

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

  println(dynamicShow.show(manualRecord))

  /**
   * Example 6: Simple Email Wrapper Type
   */
  printHeader("Example 6: Simple Email Wrapper Type")
  case class Email(value: String)
  object Email {
    implicit val schema: Schema[Email] = Schema[String].transform(
      Email(_),
      _.value
    )
    implicit val show: Show[Email] = schema.derive(DeriveShow)
  }

  val email = Email("alice@example.com")
  println(s"Email: ${Email.show.show(email)}")

}
