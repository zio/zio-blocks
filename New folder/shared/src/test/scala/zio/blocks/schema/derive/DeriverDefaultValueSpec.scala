package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.test._

object DeriverDefaultValueSpec extends SchemaBaseSpec {

  case class CapturedValues[A](
    defaultValue: Option[A],
    examples: Seq[A]
  )

  case class Person(name: String, age: Int)

  object Person extends CompanionOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
    val name: Lens[Person, String]      = optic(_.name)
    val age: Lens[Person, Int]          = optic(_.age)
  }

  sealed trait Animal
  case class Dog(breed: String) extends Animal
  case class Cat(color: String) extends Animal

  object Animal extends CompanionOptics[Animal] {
    implicit val schema: Schema[Animal] = Schema.derived
  }

  case class Container(items: List[String])

  object Container extends CompanionOptics[Container] {
    implicit val schema: Schema[Container]   = Schema.derived
    val items: Lens[Container, List[String]] = optic(_.items)
  }

  case class Dictionary(entries: Map[String, Int])

  object Dictionary extends CompanionOptics[Dictionary] {
    implicit val schema: Schema[Dictionary]         = Schema.derived
    val entries: Lens[Dictionary, Map[String, Int]] = optic(_.entries)
  }

  case class StringWrapper(value: String)

  object StringWrapper {
    implicit val typeId: TypeId[StringWrapper] =
      TypeId.nominal[StringWrapper]("StringWrapper", zio.blocks.typeid.Owner.Root)
    implicit val schema: Schema[StringWrapper] =
      Schema[String]
        .transform(s => StringWrapper(s), (w: StringWrapper) => w.value)
  }

  class CapturingDeriver extends Deriver[CapturedValues] {
    @volatile var lastRecordCapture: Option[(Option[Any], Seq[Any])]    = None
    @volatile var lastVariantCapture: Option[(Option[Any], Seq[Any])]   = None
    @volatile var lastSequenceCapture: Option[(Option[Any], Seq[Any])]  = None
    @volatile var lastMapCapture: Option[(Option[Any], Seq[Any])]       = None
    @volatile var lastPrimitiveCapture: Option[(Option[Any], Seq[Any])] = None
    @volatile var allPrimitiveCaptures: List[(Option[Any], Seq[Any])]   = Nil
    @volatile var lastDynamicCapture: Option[(Option[Any], Seq[Any])]   = None
    @volatile var lastWrapperCapture: Option[(Option[Any], Seq[Any])]   = None

    def reset(): Unit = {
      lastRecordCapture = None
      lastVariantCapture = None
      lastSequenceCapture = None
      lastMapCapture = None
      lastPrimitiveCapture = None
      allPrimitiveCaptures = Nil
      lastDynamicCapture = None
      lastWrapperCapture = None
    }

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[CapturedValues[A]] = {
      lastPrimitiveCapture = Some((defaultValue, examples))
      allPrimitiveCaptures = allPrimitiveCaptures :+ (defaultValue, examples)
      Lazy(CapturedValues(defaultValue, examples))
    }

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CapturedValues[A]] = {
      lastRecordCapture = Some((defaultValue, examples))
      Lazy(CapturedValues(defaultValue, examples))
    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CapturedValues[A]] = {
      lastVariantCapture = Some((defaultValue, examples))
      Lazy(CapturedValues(defaultValue, examples))
    }

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeId: TypeId[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CapturedValues[C[A]]] = {
      lastSequenceCapture = Some((defaultValue, examples))
      Lazy(CapturedValues(defaultValue, examples))
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
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CapturedValues[M[K, V]]] = {
      lastMapCapture = Some((defaultValue, examples))
      Lazy(CapturedValues(defaultValue, examples))
    }

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CapturedValues[DynamicValue]] = {
      lastDynamicCapture = Some((defaultValue, examples))
      Lazy(CapturedValues(defaultValue, examples))
    }

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeId: TypeId[A],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[CapturedValues[A]] = {
      lastWrapperCapture = Some((defaultValue, examples))
      Lazy(CapturedValues(defaultValue, examples))
    }
  }

  def spec: Spec[TestEnvironment, Any] = suite("DeriverDefaultValueSpec")(
    suite("Record")(
      test("receives None when no default value set") {
        val deriver = new CapturingDeriver
        Person.schema.derive(deriver)
        assertTrue(deriver.lastRecordCapture.exists(_._1.isEmpty))
      },
      test("receives empty Seq when no examples set") {
        val deriver = new CapturingDeriver
        Person.schema.derive(deriver)
        assertTrue(deriver.lastRecordCapture.exists(_._2.isEmpty))
      },
      test("receives default value when set on schema") {
        val deriver       = new CapturingDeriver
        val defaultPerson = Person("John", 30)
        val schema        = Person.schema.defaultValue(defaultPerson)
        schema.derive(deriver)
        assertTrue(deriver.lastRecordCapture.exists(_._1 == Some(defaultPerson)))
      },
      test("receives examples when set on schema") {
        val deriver  = new CapturingDeriver
        val example1 = Person("Alice", 25)
        val example2 = Person("Bob", 35)
        val schema   = Person.schema.examples(example1, example2)
        schema.derive(deriver)
        assertTrue(deriver.lastRecordCapture.exists(_._2 == Seq(example1, example2)))
      },
      test("receives both default value and examples when both set") {
        val deriver       = new CapturingDeriver
        val defaultPerson = Person("Default", 0)
        val example       = Person("Example", 99)
        val schema        = Person.schema.defaultValue(defaultPerson).examples(example)
        schema.derive(deriver)
        assertTrue(
          deriver.lastRecordCapture.exists(_._1 == Some(defaultPerson)) &&
            deriver.lastRecordCapture.exists(_._2 == Seq(example))
        )
      }
    ),
    suite("Variant")(
      test("receives None when no default value set") {
        val deriver = new CapturingDeriver
        Animal.schema.derive(deriver)
        assertTrue(deriver.lastVariantCapture.exists(_._1.isEmpty))
      },
      test("receives empty Seq when no examples set") {
        val deriver = new CapturingDeriver
        Animal.schema.derive(deriver)
        assertTrue(deriver.lastVariantCapture.exists(_._2.isEmpty))
      },
      test("receives default value when set on schema") {
        val deriver       = new CapturingDeriver
        val defaultAnimal = Dog("Labrador"): Animal
        val schema        = Animal.schema.defaultValue(defaultAnimal)
        schema.derive(deriver)
        assertTrue(deriver.lastVariantCapture.exists(_._1 == Some(defaultAnimal)))
      },
      test("receives examples when set on schema") {
        val deriver  = new CapturingDeriver
        val example1 = Dog("Poodle"): Animal
        val example2 = Cat("Black"): Animal
        val schema   = Animal.schema.examples(example1, example2)
        schema.derive(deriver)
        assertTrue(deriver.lastVariantCapture.exists(_._2 == Seq(example1, example2)))
      }
    ),
    suite("Sequence")(
      test("receives None when no default value set") {
        val deriver = new CapturingDeriver
        Container.schema.derive(deriver)
        assertTrue(deriver.lastSequenceCapture.exists(_._1.isEmpty))
      },
      test("receives empty Seq when no examples set") {
        val deriver = new CapturingDeriver
        Container.schema.derive(deriver)
        assertTrue(deriver.lastSequenceCapture.exists(_._2.isEmpty))
      },
      test("receives default value when set on nested sequence field") {
        val deriver     = new CapturingDeriver
        val defaultList = List("a", "b", "c")
        val schema      = Container.schema.defaultValue(Container.items, defaultList)
        schema.derive(deriver)
        assertTrue(deriver.lastSequenceCapture.exists(_._1 == Some(defaultList)))
      },
      test("receives examples when set on nested sequence field") {
        val deriver  = new CapturingDeriver
        val example1 = List("x")
        val example2 = List("y", "z")
        val schema   = Container.schema.examples(Container.items, example1, example2)
        schema.derive(deriver)
        assertTrue(deriver.lastSequenceCapture.exists(_._2 == Seq(example1, example2)))
      }
    ),
    suite("Map")(
      test("receives None when no default value set") {
        val deriver = new CapturingDeriver
        Dictionary.schema.derive(deriver)
        assertTrue(deriver.lastMapCapture.exists(_._1.isEmpty))
      },
      test("receives empty Seq when no examples set") {
        val deriver = new CapturingDeriver
        Dictionary.schema.derive(deriver)
        assertTrue(deriver.lastMapCapture.exists(_._2.isEmpty))
      },
      test("receives default value when set on nested map field") {
        val deriver    = new CapturingDeriver
        val defaultMap = Map("key" -> 42)
        val schema     = Dictionary.schema.defaultValue(Dictionary.entries, defaultMap)
        schema.derive(deriver)
        assertTrue(deriver.lastMapCapture.exists(_._1 == Some(defaultMap)))
      },
      test("receives examples when set on nested map field") {
        val deriver  = new CapturingDeriver
        val example1 = Map("a" -> 1)
        val example2 = Map("b" -> 2, "c" -> 3)
        val schema   = Dictionary.schema.examples(Dictionary.entries, example1, example2)
        schema.derive(deriver)
        assertTrue(deriver.lastMapCapture.exists(_._2 == Seq(example1, example2)))
      }
    ),
    suite("Primitive")(
      test("receives None when no default value set on primitive field") {
        val deriver = new CapturingDeriver
        Person.schema.derive(deriver)
        assertTrue(deriver.lastPrimitiveCapture.exists(_._1.isEmpty))
      },
      test("receives default value when set on primitive field") {
        val deriver = new CapturingDeriver
        val schema  = Person.schema.defaultValue(Person.name, "DefaultName")
        schema.derive(deriver)
        assertTrue(deriver.lastPrimitiveCapture.exists(_._1 == Some("DefaultName")))
      },
      test("receives examples when set on primitive field") {
        val deriver = new CapturingDeriver
        val schema  = Person.schema.examples(Person.age, 20, 30, 40)
        schema.derive(deriver)
        assertTrue(deriver.allPrimitiveCaptures.exists(_._2 == Seq(20, 30, 40)))
      }
    ),
    suite("Dynamic")(
      test("receives default value for dynamic schema") {
        val deriver      = new CapturingDeriver
        val defaultValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val schema       = Schema.dynamic.defaultValue(defaultValue)
        schema.derive(deriver)
        assertTrue(deriver.lastDynamicCapture.exists(_._1 == Some(defaultValue)))
      },
      test("receives examples for dynamic schema") {
        val deriver  = new CapturingDeriver
        val example1 = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val example2 = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        val schema   = Schema.dynamic.examples(example1, example2)
        schema.derive(deriver)
        assertTrue(deriver.lastDynamicCapture.exists(_._2 == Seq(example1, example2)))
      }
    ),
    suite("Wrapper")(
      test("receives None when no default value set") {
        val deriver = new CapturingDeriver
        StringWrapper.schema.derive(deriver)
        assertTrue(deriver.lastWrapperCapture.exists(_._1.isEmpty))
      },
      test("receives empty Seq when no examples set") {
        val deriver = new CapturingDeriver
        StringWrapper.schema.derive(deriver)
        assertTrue(deriver.lastWrapperCapture.exists(_._2.isEmpty))
      },
      test("receives default value when set on wrapper schema") {
        val deriver        = new CapturingDeriver
        val defaultWrapper = StringWrapper("default")
        val schema         = StringWrapper.schema.defaultValue(defaultWrapper)
        schema.derive(deriver)
        assertTrue(deriver.lastWrapperCapture.exists(_._1 == Some(defaultWrapper)))
      },
      test("receives examples when set on wrapper schema") {
        val deriver  = new CapturingDeriver
        val example1 = StringWrapper("example1")
        val example2 = StringWrapper("example2")
        val schema   = StringWrapper.schema.examples(example1, example2)
        schema.derive(deriver)
        assertTrue(deriver.lastWrapperCapture.exists(_._2 == Seq(example1, example2)))
      }
    )
  )
}
