import zio.blocks.chunk.Chunk
import zio.blocks.schema.*
import zio.blocks.schema.binding.*
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.typeid.TypeId

/**
 * Recursive ShowDeriver - demonstrates the same pattern used by
 * JsonBinaryCodecDeriver.
 *
 * Key Pattern:
 *   1. All derive* methods delegate to a single `deriveCodec` method
 *   2. `deriveCodec` recursively traverses the Reflect structure
 *   3. A ThreadLocal cache handles recursive types
 *
 * IMPORTANT: Use type member aliases for existential types to avoid type
 * erasure issues.
 */
object DeriveShowRecursive extends App {

  trait Show[A] {
    def show(value: A): String
  }

  object DeriveShow extends Deriver[Show] {

    // Cache for recursive type derivation (keyed by TypeId)
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
            case _                       => String.valueOf(value)
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
        if (cached != null) return {
          println("Using cached Show for Record: " + typeId.name)
          cached.asInstanceOf[Show[A]]
        }

        // Create placeholder and put in cache BEFORE deriving fields
        var instance: Show[A] = null
        val placeholder       = null
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
        if (cached != null) {
          println("Using cached Show for Variant: " + typeId.name)
          return cached.asInstanceOf[Show[A]]
        }

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
  // Test Examples
  // ===========================================

//  println("=" * 60)
//  println("Test 1: Simple Record")
//  println("=" * 60)

//  case class Person(name: String, age: Int)
//  object Person {
//    implicit val schema: Schema[Person] = Schema.derived[Person]
//    implicit val show: Show[Person]     = schema.derive(DeriveShow)
//  }
//
//  println(Person.show.show(Person("Alice", 30)))
//
//  println()
//  println("=" * 60)
//  println("Test 2: Recursive Tree")
//  println("=" * 60)

  case class Tree(value: Int, children: List[Tree])
  object Tree {
    implicit val schema: Schema[Tree] = Schema.derived[Tree]
    implicit val show: Show[Tree]     = schema.derive(DeriveShow)
  }

  val tree = Tree(1, List(Tree(2, List(Tree(4, Nil))), Tree(3, Nil)))
  println(Tree.show.show(tree))

  println()
  println("=" * 60)
  println("Test 3: Recursive Sealed Trait (Expr)")
  println("=" * 60)

  sealed trait Expr
  case class Num(n: Int)           extends Expr
  case class Add(a: Expr, b: Expr) extends Expr

  object Expr {
    implicit val schema: Schema[Expr] = Schema.derived[Expr]
    implicit val show: Show[Expr]     = schema.derive(DeriveShow)
  }

  val expr: Expr = Add(Num(1), Add(Num(2), Num(3)))
  println(Expr.show.show(expr))

  println()
  println("=" * 60)
  println("All tests passed!")
  println("=" * 60)

  // Example 1: Convert a typed value to DynamicValue and show it
  case class Person(name: String, age: Int, active: Boolean)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  val person                      = Person("Alice", 30, active = true)
  val personDynamic: DynamicValue = Person.schema.toDynamicValue(person)

  implicit val dynamicShow: Show[DynamicValue] = Schema.dynamic.derive(DeriveShow)

  println("Person as DynamicValue:")
  println(dynamicShow.show(personDynamic))
  // Output: Record(name = "Alice", age = 30, active = true)

  // Example 2: Manually construct a DynamicValue
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

  println("\nManual Record:")
  println(dynamicShow.show(manualRecord))
  // Output: Record(id = 42, title = "Hello World", tags = ["scala", "zio"])

  // Example 3: Variant (sum type) as DynamicValue
  sealed trait Status
  case class Active(since: String) extends Status
  case object Inactive             extends Status

  object Status {
    implicit val schema: Schema[Status] = Schema.derived[Status]
  }

  val status: Status              = Active("2024-01-01")
  val statusDynamic: DynamicValue = Status.schema.toDynamicValue(status)

  println("\nStatus as DynamicValue:")
  println(dynamicShow.show(statusDynamic))
  // Output: Active(Record(since = "2024-01-01"))

  // Example 4: Map as DynamicValue
  val mapDynamic = DynamicValue.Map(
    Chunk(
      DynamicValue.Primitive(PrimitiveValue.String("key1")) ->
        DynamicValue.Primitive(PrimitiveValue.Int(100)),
      DynamicValue.Primitive(PrimitiveValue.String("key2")) ->
        DynamicValue.Primitive(PrimitiveValue.Int(200))
    )
  )

  println("\nMap DynamicValue:")
  println(dynamicShow.show(mapDynamic))
  // Output: Map("key1" -> 100, "key2" -> 200)

  // Example 5: Nested structure
  case class Order(id: Int, items: List[String], metadata: Map[String, Int])
  object Order {
    implicit val schema: Schema[Order] = Schema.derived[Order]
  }

  val order                      = Order(1, List("apple", "banana"), Map("quantity" -> 5, "price" -> 100))
  val orderDynamic: DynamicValue = Order.schema.toDynamicValue(order)

  println("\nOrder as DynamicValue:")
  println(dynamicShow.show(orderDynamic))
  // Output: Record(id = 1, items = ["apple", "banana"], metadata = Map("quantity" -> 5, "price" -> 100))

  // Use DeriveShow from earlier (with all methods implemented including deriveWrapper)

  // ==========================================
  // Scala 2/3 Compatible: Case Class Wrappers
  // ==========================================

  // Example 1: Simple newtype wrapper using Schema.transform (total)
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
  // Output: Email: Email("alice@example.com")

  // Example 2: Wrapper with validation using Schema.transformOrFail
  case class PositiveInt(value: Int)
  object PositiveInt {
    implicit val schema: Schema[PositiveInt] = Schema[Int].transformOrFail(
      n => if (n > 0) Right(PositiveInt(n)) else Left(SchemaError.validationFailed("Must be positive")),
      _.value
    )
    implicit val show: Show[PositiveInt] = schema.derive(DeriveShow)
  }

  val posInt = PositiveInt(42)
  println(s"PositiveInt: ${PositiveInt.show.show(posInt)}")
  // Output: PositiveInt: PositiveInt(42)

  // ==========================================
  // Scala 3 Only: Opaque Types
  // ==========================================

  // Example 3: Simple opaque type (Scala 3)
  // Define in a separate file or object to hide the underlying type
  object Types {
    opaque type UserId = Long

    object UserId {
      def apply(id: Long): UserId = id

      // Extension to access the underlying value (only visible inside Types)
      extension (userId: UserId) def value: Long = userId

      // Schema uses transform - the opaque type IS the underlying type at runtime
      given Schema[UserId] = Schema[Long].transform(
        UserId(_),
        _.value
      )

      given Show[UserId] = summon[Schema[UserId]].derive(DeriveShow)
    }

    // Example 4: Validated opaque type
    opaque type Age = Int

    object Age {
      def apply(n: Int): Either[String, Age] =
        if (n >= 0 && n <= 150) Right(n) else Left("Age must be between 0 and 150")

      def unsafe(n: Int): Age = n

      extension (age: Age) def value: Int = age

      given Schema[Age] = Schema[Int].transformOrFail(
        n =>
          if (n >= 0 && n <= 150) Right(n: Age)
          else Left(SchemaError.validationFailed("Age must be between 0 and 150")),
        _.value
      )

      given Show[Age] = summon[Schema[Age]].derive(DeriveShow)
    }

    // Example 5: Opaque type with type parameter
    opaque type NonEmptyList[+A] = List[A]

    object NonEmptyList {
      def apply[A](head: A, tail: A*): NonEmptyList[A] = head :: tail.toList

      def fromList[A](list: List[A]): Option[NonEmptyList[A]] =
        if (list.nonEmpty) Some(list) else None

      extension [A](nel: NonEmptyList[A]) {
        def toList: List[A] = nel
        def head: A         = nel.head
      }

      given [A](using Schema[A]): Schema[NonEmptyList[A]] =
        Schema[List[A]].transformOrFail(
          list =>
            if (list.nonEmpty) Right(list: NonEmptyList[A])
            else Left(SchemaError.validationFailed("List must not be empty")),
          _.toList
        )

      given [A](using Schema[A], Show[A]): Show[NonEmptyList[A]] =
        summon[Schema[NonEmptyList[A]]].derive(DeriveShow)
    }

    // Example 6: Opaque type for percentage (0.0 to 1.0)
    opaque type Percentage = Double

    object Percentage {
      def apply(value: Double): Either[String, Percentage] =
        if (value >= 0.0 && value <= 1.0) Right(value)
        else Left("Percentage must be between 0.0 and 1.0")

      def unsafe(value: Double): Percentage = value

      extension (p: Percentage) def value: Double = p

      given Schema[Percentage] = Schema[Double].transformOrFail(
        d =>
          if (d >= 0.0 && d <= 1.0) Right(d: Percentage)
          else Left(SchemaError.validationFailed("Percentage must be between 0.0 and 1.0")),
        _.value
      )

      given Show[Percentage] = summon[Schema[Percentage]].derive(DeriveShow)
    }
  }

  import Types._
  import Types.UserId.given
  import Types.Age.given
  import Types.Percentage.given

  val userId = UserId(123456789L)
  println(s"UserId: ${summon[Show[UserId]].show(userId)}")
  // Output: UserId: UserId(123456789)

  val age = Age.unsafe(30)
  println(s"Age: ${summon[Show[Age]].show(age)}")
  // Output: Age: Age(30)

  val percentage = Percentage.unsafe(0.75)
  println(s"Percentage: ${summon[Show[Percentage]].show(percentage)}")
  // Output: Percentage: Percentage(0.75)

  // ==========================================
  // Scala 3: Using neotype library
  // ==========================================

  // Example 7: Using neotype Newtype (if neotype library is available)
  /*
    import neotype._

    type Username = Username.Type
    object Username extends Newtype[String]

    // Automatic schema derivation for neotype
    inline given newTypeSchema[A, B](using newType: Newtype.WithType[A, B], schema: Schema[A]): Schema[B] =
      schema.transform(
        a => newType.make(a).getOrElse(throw new IllegalArgumentException("Invalid value")),
        newType.unwrap
      )

    val username = Username("alice123")
    println(s"Username: ${summon[Show[Username]].show(username)}")
   */

  // ==========================================
  // Nested structures with opaque types
  // ==========================================

  // Example 8: Record containing opaque types
  case class User(id: UserId, age: Age, completionRate: Percentage)
  object User {
    given Schema[User] = Schema.derived[User]
    given Show[User]   = summon[Schema[User]].derive(DeriveShow)
  }

  val user = User(UserId(1L), Age.unsafe(25), Percentage.unsafe(0.85))
  println(s"User: ${summon[Show[User]].show(user)}")
  // Output: User: User(id = UserId(1), age = Age(25), completionRate = Percentage(0.85))

}
