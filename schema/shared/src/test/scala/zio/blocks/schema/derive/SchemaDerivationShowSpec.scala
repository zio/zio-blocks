package zio.blocks.schema.derive

import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.test._

object SchemaDerivationShowSpec extends SchemaBaseSpec {

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
    ): Lazy[Show[A]] = Lazy {
      new Show[A] {
        def show(value: A): String = String.valueOf(value)
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      val fieldShows    = fields.map(field => (field.name, instance(field.value.metadata).asInstanceOf[Lazy[Show[Any]]]))
      val recordFields  = fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]]
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)
      new Show[A] {
        def show(value: A): String = {
          val registers = Registers(recordReflect.usedRegisters)
          recordBinding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
          val fieldStrings = fields.indices.map { i =>
            val fieldValue = recordReflect.registers(i).get(registers, RegisterOffset.Zero)
            s"${fieldShows(i)._1} = ${fieldShows(i)._2.force.show(fieldValue)}"
          }
          s"${typeId.name}(${fieldStrings.mkString(", ")})"
        }
      }
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
      val caseShows      = cases.map(c => instance(c.value.metadata).asInstanceOf[Lazy[Show[Any]]])
      val variantBinding = binding.asInstanceOf[Binding.Variant[A]]
      new Show[A] {
        def show(value: A): String = {
          val caseIndex = variantBinding.discriminator.discriminate(value)
          val caseValue = variantBinding.matchers(caseIndex).downcastOrNull(value)
          caseShows(caseIndex).force.show(caseValue)
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[C[A]]] = Lazy {
      val elementShow   = instance(element.metadata)
      val deconstructor = binding.asInstanceOf[Binding.Seq[C, A]].deconstructor
      new Show[C[A]] {
        def show(value: C[A]): String = {
          val elements = deconstructor.deconstruct(value).map(elementShow.force.show)
          s"[${elements.mkString(", ")}]"
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[M[K, V]]] = Lazy {
      val keyShow       = instance(key.metadata)
      val valueShow     = instance(value.metadata)
      val deconstructor = binding.asInstanceOf[Binding.Map[M, K, V]].deconstructor
      new Show[M[K, V]] {
        def show(m: M[K, V]): String = {
          val entries = deconstructor.deconstruct(m).map { kv =>
            s"${keyShow.force.show(deconstructor.getKey(kv))} -> ${valueShow.force.show(deconstructor.getValue(kv))}"
          }
          s"Map(${entries.mkString(", ")})"
        }
      }
    }

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[DynamicValue]] = Lazy {
      new Show[DynamicValue] {
        def show(value: DynamicValue): String = value.toString
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Show[A]] = Lazy {
      val wrappedShow    = instance(wrapped.metadata)
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]
      new Show[A] {
        def show(value: A): String =
          s"${typeId.name}(${wrappedShow.force.show(wrapperBinding.unwrap(value))})"
      }
    }
  }

  case class Person(name: String, age: Int)
  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Point(x: Double, y: Double)
  object Point extends CompanionOptics[Point] {
    implicit val schema: Schema[Point] = Schema.derived
  }

  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  object Shape                                        extends CompanionOptics[Shape] {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  sealed trait Expr
  case class Num(n: Int)           extends Expr
  case class Add(a: Expr, b: Expr) extends Expr
  object Expr                      extends CompanionOptics[Expr] {
    implicit val schema: Schema[Expr] = Schema.derived
  }

  case class Basket(items: List[String])
  object Basket extends CompanionOptics[Basket] {
    implicit val schema: Schema[Basket] = Schema.derived
  }

  case class Scores(values: Map[String, Int])
  object Scores extends CompanionOptics[Scores] {
    implicit val schema: Schema[Scores] = Schema.derived
  }

  case class Email(value: String)
  object Email {
    implicit val typeId: TypeId[Email] = TypeId.nominal[Email]("Email", zio.blocks.typeid.Owner.Root)
    implicit val schema: Schema[Email] = Schema[String].transform(Email(_), _.value)
  }

  def spec: Spec[TestEnvironment, Any] = suite("SchemaDerivationShowSpec")(
    suite("Primitive")(
      test("shows an Int") {
        val show = Schema[Int].derive(DeriveShow)
        assertTrue(show.show(42) == "42")
      },
      test("shows a String") {
        val show = Schema[String].derive(DeriveShow)
        assertTrue(show.show("hello") == "hello")
      },
      test("shows a Boolean") {
        val show = Schema[Boolean].derive(DeriveShow)
        assertTrue(show.show(true) == "true")
      },
      test("shows a Double") {
        val show = Schema[Double].derive(DeriveShow)
        assertTrue(show.show(3.14) == String.valueOf(3.14))
      }
    ),
    suite("Record")(
      test("shows a simple record") {
        val show = Person.schema.derive(DeriveShow)
        assertTrue(show.show(Person("Alice", 30)) == "Person(name = Alice, age = 30)")
      },
      test("shows a record with Double fields") {
        val show = Point.schema.derive(DeriveShow)
        val result = show.show(Point(1.5, 2.5))
        assertTrue(result == "Point(x = 1.5, y = 2.5)")
      }
    ),
    suite("Variant")(
      test("shows first case") {
        val show = Shape.schema.derive(DeriveShow)
        assertTrue(show.show(Circle(5.5)) == "Circle(radius = 5.5)")
      },
      test("shows second case") {
        val show = Shape.schema.derive(DeriveShow)
        assertTrue(show.show(Rectangle(4.5, 6.5)) == "Rectangle(width = 4.5, height = 6.5)")
      },
      test("shows recursive variant") {
        val show = Expr.schema.derive(DeriveShow)
        assertTrue(show.show(Add(Num(1), Num(2))) == "Add(a = Num(n = 1), b = Num(n = 2))")
      },
      test("shows a leaf case of a recursive variant") {
        val show = Expr.schema.derive(DeriveShow)
        assertTrue(show.show(Num(42)) == "Num(n = 42)")
      },
      test("shows a deeply nested recursive variant") {
        val show       = Expr.schema.derive(DeriveShow)
        val expr: Expr = Add(Num(1), Add(Num(2), Num(3)))
        assertTrue(show.show(expr) == "Add(a = Num(n = 1), b = Add(a = Num(n = 2), b = Num(n = 3)))")
      }
    ),
    suite("Sequence")(
      test("shows a list of strings") {
        val show = Schema[List[String]].derive(DeriveShow)
        assertTrue(show.show(List("a", "b", "c")) == "[a, b, c]")
      },
      test("shows an empty list") {
        val show = Schema[List[Int]].derive(DeriveShow)
        assertTrue(show.show(Nil) == "[]")
      },
      test("shows a record containing a list") {
        val show = Basket.schema.derive(DeriveShow)
        assertTrue(show.show(Basket(List("apple", "banana"))) == "Basket(items = [apple, banana])")
      }
    ),
    suite("Map")(
      test("shows a map") {
        val show = Schema[scala.collection.immutable.Map[String, Int]].derive(DeriveShow)
        assertTrue(show.show(scala.collection.immutable.Map("a" -> 1)) == "Map(a -> 1)")
      },
      test("shows an empty map") {
        val show = Schema[scala.collection.immutable.Map[String, Int]].derive(DeriveShow)
        assertTrue(show.show(scala.collection.immutable.Map.empty[String, Int]) == "Map()")
      },
      test("shows a record containing a map") {
        val show = Scores.schema.derive(DeriveShow)
        assertTrue(
          show.show(Scores(scala.collection.immutable.Map("math" -> 100))) == "Scores(values = Map(math -> 100))"
        )
      }
    ),
    suite("Dynamic")(
      test("shows a primitive dynamic value") {
        val show = Schema.dynamic.derive(DeriveShow)
        val dv   = DynamicValue.Primitive(PrimitiveValue.Int(42))
        assertTrue(show.show(dv) == dv.toString)
      },
      test("shows a record dynamic value") {
        val show = Schema.dynamic.derive(DeriveShow)
        val dv   = DynamicValue.Record(zio.blocks.chunk.Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        assertTrue(show.show(dv) == dv.toString)
      }
    ),
    suite("Wrapper")(
      test("shows a wrapper type") {
        val show = Email.schema.derive(DeriveShow)
        assertTrue(show.show(Email("alice@example.com")) == "Email(alice@example.com)")
      }
    )
  )
}
