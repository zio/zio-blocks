package typeclassderivation

import typeclassderivation.DeriveShowApproachTwo.DeriveShow.HasInstance
import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import zio.blocks.schema.binding.*
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.typeid.TypeId

/**
 * Recursive ShowDeriver Key Patterns:
 *   1. All derive* methods delegate to a single `deriveCodec` method
 *   2. `deriveCodec` recursively traverses the Reflect structure
 *   3. A ThreadLocal cache handles recursive types
 *
 * IMPORTANT: We used type member aliases for existential types to avoid type
 * erasure issues.
 */
object DeriveShowApproachTwo extends App {

  trait Show[A] {
    def show(value: A): String
  }

  object DeriveShow extends Deriver[Show] {

    // Cache for recursive type derivation
    private val cache = new ThreadLocal[java.util.HashMap[TypeId[?], Show[?]]] {
      override def initialValue(): java.util.HashMap[TypeId[?], Show[?]] = new java.util.HashMap
    }

    // Type member aliases for existential type handling
    private type Elem
    private type Col[_]
    private type Key
    private type Value
    private type Map[_, _]
    private type Wrapped

    // ===========================================
    // Deriver trait method implementations
    // All delegate to deriveCodec
    // ===========================================

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Show[A]] =
      Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      deriveCodec(
        new Reflect.Record(
          fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
          typeId,
          binding,
          doc,
          modifiers
        )
      )
    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      deriveCodec(
        new Reflect.Variant(
          cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
          typeId,
          binding,
          doc,
          modifiers
        )
      )
    }

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeId: TypeId[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[C[A]]] = Lazy {
      deriveCodec(
        new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeId, binding, doc, modifiers)
      )
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[M[K, V]]] = Lazy {
      deriveCodec(
        new Reflect.Map(
          key.asInstanceOf[Reflect[Binding, K]],
          value.asInstanceOf[Reflect[Binding, V]],
          typeId,
          binding,
          doc,
          modifiers
        )
      )
    }

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[DynamicValue]] =
      Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeId.of[DynamicValue], doc, modifiers)))

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeId: TypeId[A],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      deriveCodec(
        new Reflect.Wrapper(
          wrapped.asInstanceOf[Reflect[Binding, B]],
          typeId,
          binding,
          doc,
          modifiers
        )
      )
    }

    // Core recursive derivation method
    private def deriveCodec[A](reflect: Reflect[Binding, A]): Show[A] = {
      // Handle Deferred by unwrapping to actual Reflect
      val actualReflect = reflect match {
        case d: Reflect.Deferred[Binding, A] => d.value
        case other                           => other
      }

      if (actualReflect.isPrimitive) {
        val prim = actualReflect.asPrimitive.get
        new Show[A] {
          def show(value: A): String = prim.primitiveType match {
            case _: PrimitiveType.String => "\"" + value + "\""
            case _: PrimitiveType.Char   => "'" + value + "'"
            // Add more pretty-printing for other primitive types as needed here
            // We just use default toString for others for simplicity
            case _ => String.valueOf(value)
          }
        }
      } else if (actualReflect.isRecord) {
        val record = actualReflect.asRecord.get
        val typeId = record.typeId

        // Check if binding is raw Binding or wrapped in BindingInstance
        if (!record.recordBinding.isInstanceOf[Binding[?, ?]]) {
          return record.recordBinding.asInstanceOf[BindingInstance[Show, ?, A]].instance.force
        }

        val fields        = record.fields
        val recordBinding = record.recordBinding.asInstanceOf[Binding.Record[A]]

        // Check cache for recursive types
        val cached = cache.get.get(typeId)
        if (cached != null) return cached.asInstanceOf[Show[A]]

        // Create placeholder and put in cache BEFORE deriving fields
        var instance: Show[A] = null
        val placeholder       = new Show[A] {
          def show(value: A): String = instance.show(value)
        }
        cache.get.put(typeId, placeholder)

        // Derive Show instances for all fields
        val fieldCodecs = fields.map(f => deriveCodec(f.value.asInstanceOf[Reflect[Binding, Any]]))

        // Create the actual Show instance
        instance = new Show[A] {
          def show(value: A): String = {
            val registers = Registers(record.usedRegisters)
            recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
            val fieldStrings = fields.indices.map { i =>
              val fieldValue = record.registers(i).get(registers, RegisterOffset.Zero)
              s"${fields(i).name} = ${fieldCodecs(i).show(fieldValue)}"
            }
            s"${typeId.name}(${fieldStrings.mkString(", ")})"
          }
        }
        instance
      } else if (actualReflect.isVariant) {
        val variant = actualReflect.asVariant.get
        val typeId  = variant.typeId

        // Check if binding is raw Binding or wrapped in BindingInstance
        if (!variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
          return variant.variantBinding.asInstanceOf[BindingInstance[Show, ?, A]].instance.force
        }

        val cases          = variant.cases
        val variantBinding = variant.variantBinding.asInstanceOf[Binding.Variant[A]]

        // Check cache for recursive types
        val cached = cache.get.get(typeId)
        if (cached != null) return cached.asInstanceOf[Show[A]]

        // Create placeholder and put in cache
        var instance: Show[A] = null
        val placeholder       = new Show[A] {
          def show(value: A): String = instance.show(value)
        }
        cache.get.put(typeId, placeholder)

        // Derive Show instances for all cases (lazily via array of functions)
        val caseCodecs = new Array[Show[Any]](cases.length)

        instance = new Show[A] {
          def show(value: A): String = {
            val idx       = variantBinding.discriminator.discriminate(value)
            val caseValue = variantBinding.matchers(idx).downcastOrNull(value)
            // Derive case codec lazily on first use
            if (caseCodecs(idx) == null) {
              caseCodecs(idx) = deriveCodec(cases(idx).value.asInstanceOf[Reflect[Binding, Any]])
            }
            caseCodecs(idx).show(caseValue)
          }
        }
        instance
      } else if (actualReflect.isSequence) {
        // Use type member aliases with helper method to preserve proper types
        deriveSeqCodec(actualReflect.asSequenceUnknown.get.sequence.asInstanceOf[Reflect.Sequence[Binding, Elem, Col]])
          .asInstanceOf[Show[A]]
      } else if (actualReflect.isMap) {
        // Use type member aliases with helper method
        deriveMapCodec(actualReflect.asMapUnknown.get.map.asInstanceOf[Reflect.Map[Binding, Key, Value, Map]])
          .asInstanceOf[Show[A]]
      } else if (actualReflect.isWrapper) {
        val wrapper = actualReflect.asWrapperUnknown.get.wrapper
        val typeId  = wrapper.typeId
        if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
          val wrapperBinding = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
          val wrappedCodec   = deriveCodec(wrapper.wrapped.asInstanceOf[Reflect[Binding, Wrapped]])
          new Show[A] {
            def show(value: A): String = wrapperBinding.unwrap(value) match {
              case Right(unwrapped) => s"${typeId.name}(${wrappedCodec.show(unwrapped)})"
              case Left(error)      => s"${typeId.name}(<error: ${error.message}>)"
            }
          }
        } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[Show, ?, A]].instance.force
      } else if (actualReflect.isDynamic) {
        new Show[A] {
          def show(value: A): String = showDynamic(value.asInstanceOf[DynamicValue])

          private def showDynamic(dv: DynamicValue): String = dv match {
            case DynamicValue.Primitive(pv)  => pv.toString
            case DynamicValue.Record(fields) =>
              s"Record(${fields.map { case (n, v) => s"$n = ${showDynamic(v)}" }.mkString(", ")})"
            case DynamicValue.Variant(name, v) => s"$name(${showDynamic(v)})"
            case DynamicValue.Sequence(elems)  => s"[${elems.map(showDynamic).mkString(", ")}]"
            case DynamicValue.Map(entries)     =>
              s"Map(${entries.map { case (k, v) => s"${showDynamic(k)} -> ${showDynamic(v)}" }.mkString(", ")})"
            case DynamicValue.Null => "null"
          }
        }.asInstanceOf[Show[A]]

      } else throw new IllegalArgumentException(s"Unsupported Reflect type: $actualReflect")
    }

    // Helper method for sequences - preserves proper type parameters
    private def deriveSeqCodec[E, C[_]](sequence: Reflect.Sequence[Binding, E, C]): Show[C[E]] =
      if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
        val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[C, E]]
        val elementCodec = deriveCodec(sequence.element)
        new Show[C[E]] {
          def show(value: C[E]): String = {
            val it       = binding.deconstructor.deconstruct(value)
            val elements = it.map(e => elementCodec.show(e)).mkString(", ")
            s"[$elements]"
          }
        }
      } else sequence.seqBinding.asInstanceOf[BindingInstance[Show, ?, C[E]]].instance.force

    // Helper method for maps - preserves proper type parameters
    private def deriveMapCodec[K, V, M[_, _]](map: Reflect.Map[Binding, K, V, M]): Show[M[K, V]] =
      if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
        val binding    = map.mapBinding.asInstanceOf[Binding.Map[M, K, V]]
        val keyCodec   = deriveCodec(map.key)
        val valueCodec = deriveCodec(map.value)
        new Show[M[K, V]] {
          def show(value: M[K, V]): String = {
            val it      = binding.deconstructor.deconstruct(value)
            val entries = it.map { kv =>
              val k = binding.deconstructor.getKey(kv)
              val v = binding.deconstructor.getValue(kv)
              s"${keyCodec.show(k)} -> ${valueCodec.show(v)}"
            }.mkString(", ")
            s"Map($entries)"
          }
        }
      } else map.mapBinding.asInstanceOf[BindingInstance[Show, ?, M[K, V]]].instance.force
  }

  // ===========================================
  // Examples
  // ===========================================

  def printHeader(title: String): Unit = {
    println("=" * 60)
    println(title)
  }

  /**
   * Example 1: Simple Person Record with two primitive fields
   */
  printHeader("Example 1: Simple Person Record with two primitive fields")
  case class Person(name: String, age: Int, bestFriend: Option[Person] = None)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val show: Show[Person]     = schema.derive(DeriveShow)
  }
  println(Person.show.show(Person("Alice", 30, bestFriend = Some(Person("Bob", 25)))))

//  /**
//   * Example 2: Simple Shape Variant (Circle, Rectangle)
//   */
//  printHeader("Example 2: Simple Shape Variant (Circle, Rectangle)")
//  sealed trait Shape
//  case class Circle(radius: Double)                   extends Shape
//  case class Rectangle(width: Double, height: Double) extends Shape
//
//  object Shape {
//    implicit val schema: Schema[Shape] = Schema.derived[Shape]
//    implicit val show: Show[Shape]     = schema.derive(DeriveShow)
//  }
//
//  val shape1: Shape = Circle(5.0)
//  val shape2: Shape = Rectangle(4.0, 6.0)
//  println(Shape.show.show(shape1))
//  println(Shape.show.show(shape2))
//
//  /**
//   * Example 3: Recursive Tree and Expr
//   */
//  printHeader("Example 3: Recursive Tree")
//  case class Tree(value: Int, children: List[Tree])
//  object Tree {
//    implicit val schema: Schema[Tree] = Schema.derived[Tree]
//    implicit val show: Show[Tree]     = schema.derive(DeriveShow)
//  }
//
//  val tree = Tree(1, List(Tree(2, List(Tree(4, Nil))), Tree(3, Nil)))
//  println(Tree.show.show(tree))
//
//  /**
//   * Example 4: Recursive Sealed Trait (Expr)
//   */
//  printHeader("Example 4: Recursive Sealed Trait (Expr)")
//  sealed trait Expr
//  case class Num(n: Int)           extends Expr
//  case class Add(a: Expr, b: Expr) extends Expr
//
//  object Expr {
//    implicit val schema: Schema[Expr] = Schema.derived[Expr]
//    implicit val show: Show[Expr]     = schema.derive(DeriveShow)
//  }
//
//  val expr: Expr = Add(Num(1), Add(Num(2), Num(3)))
//  println(Expr.show.show(expr))
//
//  /**
//   * Example 5: DynamicValue Example
//   */
//  printHeader("Example 5: DynamicValue Example")
//  implicit val dynamicShow: Show[DynamicValue] = Schema.dynamic.derive(DeriveShow)
//
//  val manualRecord = DynamicValue.Record(
//    Chunk(
//      "id"    -> DynamicValue.Primitive(PrimitiveValue.Int(42)),
//      "title" -> DynamicValue.Primitive(PrimitiveValue.String("Hello World")),
//      "tags"  -> DynamicValue.Sequence(
//        Chunk(
//          DynamicValue.Primitive(PrimitiveValue.String("scala")),
//          DynamicValue.Primitive(PrimitiveValue.String("zio"))
//        )
//      )
//    )
//  )
//
//  println(dynamicShow.show(manualRecord))
//
//  /**
//   * Example 6: Simple Email Wrapper Type
//   */
//  printHeader("Example 6: Simple Email Wrapper Type")
//  case class Email(value: String)
//  object Email {
//    implicit val schema: Schema[Email] = Schema[String].transform(
//      Email(_),
//      _.value
//    )
//    implicit val show: Show[Email] = schema.derive(DeriveShow)
//  }
//
//  val email = Email("alice@example.com")
//  println(s"Email: ${Email.show.show(email)}")

}
