package zio.blocks.schema.binding

import zio.blocks.schema.{SchemaBaseSpec, SchemaError}
import zio.prelude.Newtype
import zio.test._

object BindingOfVersionSpecificSpec extends SchemaBaseSpec {
  private def isSeq(b: Any): Boolean = b match {
    case _: Binding.Seq[?, ?] => true
    case _                    => false
  }

  private def isWrapper(b: Any): Boolean = b match {
    case _: Binding.Wrapper[?, ?] => true
    case _                        => false
  }

  case class Email private (value: String)
  object Email {
    def apply(v: String): Either[String, Email] =
      if (v.contains("@")) Right(new Email(v)) else Left(s"Invalid email: $v")
  }

  case class PositiveDouble private (value: Double)
  object PositiveDouble {
    def apply(d: Double): Either[SchemaError, PositiveDouble] =
      if (d > 0) Right(new PositiveDouble(d))
      else Left(SchemaError.validationFailed("must be positive"))
  }

  case class RegularCaseClass(name: String, age: Int)

  enum Color {
    case Red, Green, Blue
  }

  enum Shape {
    case Circle(radius: Double)
    case Rectangle(width: Double, height: Double)
  }

  enum LinkedList[+A] {
    case Nil
    case Cons(head: A, tail: LinkedList[A])
  }

  object OpaqueAge {
    opaque type OpaqueAge = Int

    def apply(value: Int): OpaqueAge = value

    def unwrap(age: OpaqueAge): Int = age
  }
  type OpaqueAge = OpaqueAge.OpaqueAge

  object ValidatedEmail {
    opaque type ValidatedEmail = String

    def apply(s: String): Either[String, ValidatedEmail] =
      if (s.contains("@")) Right(s) else Left(s"Invalid email: $s")

    def unwrap(e: ValidatedEmail): String = e
  }
  type ValidatedEmail = ValidatedEmail.ValidatedEmail

  object PersonName {
    opaque type PersonName = String

    def apply(s: String): PersonName = s

    def unwrap(n: PersonName): String = n
  }
  type PersonName = PersonName.PersonName

  object PositiveInt {
    opaque type PositiveInt = Int

    def apply(value: Int): Either[SchemaError, PositiveInt] =
      if (value > 0) Right(value) else Left(SchemaError.validationFailed("must be positive"))

    def unwrap(p: PositiveInt): Int = p
  }
  type PositiveInt = PositiveInt.PositiveInt

  case class PersonWithOpaqueName(name: PersonName, age: Int)

  object UserId extends Newtype[Long]
  type UserId = UserId.Type

  object Username extends Newtype[String]
  type Username = Username.Type

  def spec: Spec[TestEnvironment, Any] = suite("Binding.of Scala 3 specific")(
    suite("IArray")(
      test("IArray type constructor") {
        val binding = Binding.of[IArray]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.iArrayConstructor
        )
      },
      test("IArray construct empty") {
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val result        = constructor.empty[AnyRef]
        assertTrue(deconstructor.deconstruct(result).isEmpty)
      },
      test("IArray construct from builder") {
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](3)
        constructor.add(builder, "a")
        constructor.add(builder, "b")
        constructor.add(builder, "c")
        val result = constructor.result(builder)
        assertTrue(deconstructor.deconstruct(result).toList == List("a", "b", "c"))
      },
      test("IArray deconstruct works") {
        val binding       = Binding.of[IArray]
        val deconstructor = binding.deconstructor
        val arr           = IArray("a", "b", "c")
        val result        = deconstructor.deconstruct(arr)
        assertTrue(result.toList == List("a", "b", "c"))
      },
      test("IArray builder grows correctly") {
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](1)
        (1 to 100).foreach(i => constructor.add(builder, i.toString))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list.length == 100 && list.head == "1" && list.last == "100")
      },
      test("IArray result trim works when oversized") {
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](16)
        constructor.add(builder, "42")
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List("42"))
      },
      test("IArray with enum elements") {
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](3)
        constructor.add(builder, Color.Red)
        constructor.add(builder, Color.Green)
        constructor.add(builder, Color.Blue)
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List(Color.Red, Color.Green, Color.Blue))
      },
      test("IArray of Option") {
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](3)
        constructor.add(builder, Some(1))
        constructor.add(builder, None)
        constructor.add(builder, Some(3))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List(Some(1), None, Some(3)))
      },
      test("IArray case class elements") {
        case class Person(name: String, age: Int)
        val binding       = Binding.of[IArray]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](2)
        constructor.add(builder, Person("Alice", 30))
        constructor.add(builder, Person("Bob", 25))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List(Person("Alice", 30), Person("Bob", 25)))
      }
    ),
    suite("union types")(
      test("union type creates Variant") {
        type IntOrString = Int | String
        val binding = Binding.of[IntOrString]
        assertTrue(binding.isInstanceOf[Binding.Variant[?]])
      },
      test("union type discriminator works") {
        type IntOrString = Int | String
        val binding        = Binding.of[IntOrString].asInstanceOf[Binding.Variant[IntOrString]]
        val i: IntOrString = 42
        val s: IntOrString = "hello"
        assertTrue(
          binding.discriminator.discriminate(i) >= 0 &&
            binding.discriminator.discriminate(s) >= 0 &&
            binding.discriminator.discriminate(i) != binding.discriminator.discriminate(s)
        )
      },
      test("three-way union type") {
        type ThreeWay = Int | String | Boolean
        val binding     = Binding.of[ThreeWay].asInstanceOf[Binding.Variant[ThreeWay]]
        val i: ThreeWay = 42
        val s: ThreeWay = "hello"
        val b: ThreeWay = true
        assertTrue(
          binding.discriminator.discriminate(i) != binding.discriminator.discriminate(s) &&
            binding.discriminator.discriminate(s) != binding.discriminator.discriminate(b) &&
            binding.matchers.matchers.length == 3
        )
      }
    ),
    suite("smart constructor case classes")(
      test("case class with smart constructor returns Binding.Wrapper") {
        val binding = Binding.of[Email]
        assertTrue(isWrapper(binding))
      },
      test("smart constructor wrap succeeds for valid input") {
        val binding = Binding.of[Email].asInstanceOf[Binding.Wrapper[Email, String]]
        assertTrue(scala.util.Try(binding.wrap("test@example.com")).isSuccess)
      },
      test("smart constructor wrap fails for invalid input") {
        val binding = Binding.of[Email].asInstanceOf[Binding.Wrapper[Email, String]]
        assertTrue(scala.util.Try(binding.wrap("invalid")).isFailure)
      },
      test("smart constructor unwrap extracts underlying value") {
        val binding = Binding.of[Email].asInstanceOf[Binding.Wrapper[Email, String]]
        val email   = scala.util.Try(binding.wrap("test@example.com")).toOption.get
        assertTrue(binding.unwrap(email) == "test@example.com")
      },
      test("smart constructor with SchemaError return type") {
        val binding = Binding.of[PositiveDouble].asInstanceOf[Binding.Wrapper[PositiveDouble, Double]]
        assertTrue(
          scala.util.Try(binding.wrap(1.5)).isSuccess &&
            scala.util.Try(binding.wrap(-1.0)).isFailure
        )
      },
      test("regular case class without smart constructor returns Binding.Record") {
        val binding = Binding.of[RegularCaseClass]
        assertTrue(binding.isInstanceOf[Binding.Record[?]])
      }
    ),
    suite("Scala 3 enums")(
      test("simple enum returns Binding.Variant") {
        val binding = Binding.of[Color]
        assertTrue(binding.isInstanceOf[Binding.Variant[?]])
      },
      test("enum discriminator works") {
        val binding = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        assertTrue(
          binding.discriminator.discriminate(Color.Red) == 0 &&
            binding.discriminator.discriminate(Color.Green) == 1 &&
            binding.discriminator.discriminate(Color.Blue) == 2
        )
      },
      test("enum matchers work") {
        val binding  = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        val matcher0 = binding.matchers(0).asInstanceOf[Matcher[Color]]
        val matcher1 = binding.matchers(1).asInstanceOf[Matcher[Color]]
        assertTrue(
          matcher0.downcastOrNull(Color.Red) == Color.Red &&
            matcher0.downcastOrNull(Color.Green) == null &&
            matcher1.downcastOrNull(Color.Green) == Color.Green
        )
      },
      test("enum with case class variants returns Binding.Variant") {
        val binding = Binding.of[Shape]
        assertTrue(binding.isInstanceOf[Binding.Variant[?]])
      },
      test("enum with case class variants discriminator works") {
        val binding = Binding.of[Shape].asInstanceOf[Binding.Variant[Shape]]
        assertTrue(
          binding.discriminator.discriminate(Shape.Circle(1.0)) == 0 &&
            binding.discriminator.discriminate(Shape.Rectangle(2.0, 3.0)) == 1
        )
      },
      test("parameterized enum returns Binding.Variant") {
        val binding = Binding.of[LinkedList[Int]]
        assertTrue(binding.isInstanceOf[Binding.Variant[?]])
      }
    ),
    suite("Scala 3 opaque types")(
      test("simple opaque type returns Binding.Wrapper") {
        val binding = Binding.of[OpaqueAge]
        assertTrue(isWrapper(binding))
      },
      test("opaque type wrap and unwrap work") {
        val binding = Binding.of[OpaqueAge].asInstanceOf[Binding.Wrapper[OpaqueAge, Int]]
        val wrapped = binding.wrap(25)
        assertTrue(binding.unwrap(wrapped) == 25)
      },
      test("opaque type with String error smart constructor returns Binding.Wrapper") {
        val binding = Binding.of[ValidatedEmail]
        assertTrue(isWrapper(binding))
      },
      test("opaque type smart constructor wrap succeeds for valid input") {
        val binding = Binding.of[ValidatedEmail].asInstanceOf[Binding.Wrapper[ValidatedEmail, String]]
        val result  = binding.wrap("test@example.com")
        assertTrue(binding.unwrap(result) == "test@example.com")
      },
      test("opaque type smart constructor wrap fails for invalid input") {
        val binding = Binding.of[ValidatedEmail].asInstanceOf[Binding.Wrapper[ValidatedEmail, String]]
        assertTrue(scala.util.Try(binding.wrap("invalid")).isFailure)
      },
      test("opaque type smart constructor unwrap extracts value") {
        val binding = Binding.of[ValidatedEmail].asInstanceOf[Binding.Wrapper[ValidatedEmail, String]]
        val wrapped = scala.util.Try(binding.wrap("test@example.com")).toOption.get
        assertTrue(binding.unwrap(wrapped) == "test@example.com")
      },
      test("opaque type with SchemaError smart constructor returns Binding.Wrapper") {
        val binding = Binding.of[PositiveInt]
        assertTrue(isWrapper(binding))
      },
      test("opaque type with SchemaError smart constructor wrap succeeds for valid input") {
        val binding = Binding.of[PositiveInt].asInstanceOf[Binding.Wrapper[PositiveInt, Int]]
        assertTrue(scala.util.Try(binding.wrap(42)).isSuccess)
      },
      test("opaque type with SchemaError smart constructor wrap fails for invalid input") {
        val binding = Binding.of[PositiveInt].asInstanceOf[Binding.Wrapper[PositiveInt, Int]]
        assertTrue(scala.util.Try(binding.wrap(-5)).isFailure)
      },
      test("case class with opaque type field roundtrip") {
        val binding   = Binding.of[PersonWithOpaqueName].asInstanceOf[Binding.Record[PersonWithOpaqueName]]
        val original  = PersonWithOpaqueName(PersonName("Alice"), 30)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed.name == original.name && reconstructed.age == original.age)
      }
    ),
    suite("ZIO Prelude newtypes")(
      test("newtype returns Binding.Wrapper") {
        val binding = Binding.of[UserId]
        assertTrue(isWrapper(binding))
      },
      test("newtype wrap and unwrap work for Long") {
        val binding = Binding.of[UserId].asInstanceOf[Binding.Wrapper[UserId, Long]]
        val wrapped = binding.wrap(12345L)
        assertTrue(binding.unwrap(wrapped) == 12345L)
      },
      test("newtype wrap and unwrap work for String") {
        val binding = Binding.of[Username].asInstanceOf[Binding.Wrapper[Username, String]]
        val wrapped = binding.wrap("alice")
        assertTrue(binding.unwrap(wrapped) == "alice")
      }
    ),
    suite("generic tuples")(
      test("generic Tuple3 binding roundtrip") {
        val binding   = Binding.of[(String, Int, Boolean)].asInstanceOf[Binding.Record[(String, Int, Boolean)]]
        val original  = ("hello", 42, true)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("generic tuple with mixed types") {
        type MixedTuple = (String, Int, Double, Boolean, Long)
        val binding   = Binding.of[MixedTuple].asInstanceOf[Binding.Record[MixedTuple]]
        val original  = ("test", 1, 2.5, true, 100L)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("large generic tuple (10 elements)") {
        type LargeTuple = (Int, Int, Int, Int, Int, Int, Int, Int, Int, Int)
        val binding   = Binding.of[LargeTuple].asInstanceOf[Binding.Record[LargeTuple]]
        val original  = (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      }
    ),
    suite("type aliases")(
      test("type alias to primitive resolves correctly") {
        type MyInt = Int
        val binding = Binding.of[MyInt]
        assertTrue(binding == Binding.Primitive.int)
      },
      test("type alias to String resolves correctly") {
        type MyString = String
        val binding = Binding.of[MyString]
        assertTrue(binding == Binding.Primitive.string)
      },
      test("type alias to tuple resolves correctly") {
        type MyTuple = (String, Int)
        val binding   = Binding.of[MyTuple].asInstanceOf[Binding.Record[MyTuple]]
        val original  = ("test", 42)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      }
    ),
    suite("higher-kinded type case classes")(
      test("simple HKT with one field") {
        case class SimpleHKT[F[_]](value: F[Int])
        val binding   = Binding.of[SimpleHKT[Option]].asInstanceOf[Binding.Record[SimpleHKT[Option]]]
        val original  = SimpleHKT[Option](Some(42))
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("HKT with two fields") {
        case class TwoFieldHKT[F[_]](a: F[Int], b: F[String])
        val binding   = Binding.of[TwoFieldHKT[Option]].asInstanceOf[Binding.Record[TwoFieldHKT[Option]]]
        val original  = TwoFieldHKT[Option](Some(1), Some("hello"))
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("recursive HKT (Record8-style)") {
        case class RecursiveHKT[F[_]](f: F[Int], fs: F[RecursiveHKT[F]])
        val binding   = Binding.of[RecursiveHKT[Option]].asInstanceOf[Binding.Record[RecursiveHKT[Option]]]
        val original  = RecursiveHKT[Option](Some(1), Some(RecursiveHKT[Option](Some(2), None)))
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("HKT with List type constructor") {
        case class ListHKT[F[_]](values: F[Int])
        val binding   = Binding.of[ListHKT[List]].asInstanceOf[Binding.Record[ListHKT[List]]]
        val original  = ListHKT[List](List(1, 2, 3))
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("union type discrimination is consistent with Schema.derived") {
        import zio.blocks.schema._
        type Value = Int | Boolean | String | (Int, Boolean) | List[Int] | Unit

        val schema  = Schema.derived[Value]
        val variant = schema.reflect.asInstanceOf[Reflect.Variant[Binding, Value]]
        val binding = variant.variantBinding.asInstanceOf[Binding.Variant[Value]]

        val caseNames = variant.cases.map(_.name).toList

        val testCases: List[(Value, String)] = List(
          (1: Value, "scala.Int"),
          (true: Value, "scala.Boolean"),
          ("hello": Value, "java.lang.String"),
          ((1, true): Value, "scala.Tuple2"),
          (List(1): Value, "scala.collection.immutable.List"),
          ((): Value, "scala.Unit")
        )

        val results = testCases.map { case (value, expectedCaseName) =>
          val idx      = binding.discriminator.discriminate(value)
          val caseName = caseNames(idx)
          (value, expectedCaseName, caseName, idx)
        }

        assertTrue(results.forall { case (_, expected, actual, _) => actual == expected })
      }
    ),
    suite("Binding.Variant inline creation with locally-defined sealed traits")(
      test("sealed trait Variant has correct discriminator for all subtypes") {
        sealed trait Animal
        case class Dog(name: String) extends Animal
        case class Cat(name: String) extends Animal
        case object Fish             extends Animal
        val binding = Binding.of[Animal].asInstanceOf[Binding.Variant[Animal]]
        val dog     = Dog("Buddy")
        val cat     = Cat("Whiskers")
        val fish    = Fish
        val dogIdx  = binding.discriminator.discriminate(dog)
        val catIdx  = binding.discriminator.discriminate(cat)
        val fishIdx = binding.discriminator.discriminate(fish)
        assertTrue(
          dogIdx >= 0 && catIdx >= 0 && fishIdx >= 0 &&
            dogIdx != catIdx && catIdx != fishIdx && dogIdx != fishIdx
        )
      },
      test("sealed trait Variant matchers correctly identify subtypes") {
        sealed trait Vehicle
        case class Car(model: String)  extends Vehicle
        case class Bike(brand: String) extends Vehicle
        case object Skateboard         extends Vehicle
        val binding = Binding.of[Vehicle].asInstanceOf[Binding.Variant[Vehicle]]
        val car     = Car("Tesla")
        val bike    = Bike("Trek")
        val carIdx  = binding.discriminator.discriminate(car)
        val bikeIdx = binding.discriminator.discriminate(bike)
        assertTrue(
          binding.matchers(carIdx).downcastOrNull(car) == car &&
            binding.matchers(carIdx).downcastOrNull(bike) == null &&
            binding.matchers(bikeIdx).downcastOrNull(bike) == bike
        )
      },
      test("sealed trait with deeply nested hierarchy") {
        sealed trait Expr
        sealed trait BinaryExpr        extends Expr
        case class Add(l: Int, r: Int) extends BinaryExpr
        case class Sub(l: Int, r: Int) extends BinaryExpr
        case class Literal(v: Int)     extends Expr
        val binding = Binding.of[Expr].asInstanceOf[Binding.Variant[Expr]]
        val add     = Add(1, 2)
        val sub     = Sub(3, 4)
        val lit     = Literal(42)
        assertTrue(
          binding.discriminator.discriminate(add) >= 0 &&
            binding.discriminator.discriminate(sub) >= 0 &&
            binding.discriminator.discriminate(lit) >= 0
        )
      },
      test("sealed trait with generic subtypes") {
        sealed trait Container[+A]
        case class Full[A](value: A) extends Container[A]
        case object Empty            extends Container[Nothing]
        val binding = Binding.of[Container[Int]].asInstanceOf[Binding.Variant[Container[Int]]]
        val full    = Full(42)
        val empty   = Empty
        assertTrue(
          binding.discriminator.discriminate(full) != binding.discriminator.discriminate(empty)
        )
      },
      test("sealed trait matchers count equals number of direct subtypes") {
        sealed trait Status
        case object Pending   extends Status
        case object Active    extends Status
        case object Completed extends Status
        case object Failed    extends Status
        val binding = Binding.of[Status].asInstanceOf[Binding.Variant[Status]]
        assertTrue(binding.matchers.matchers.length == 4)
      },
      test("sealed trait with single subtype") {
        sealed trait Wrapper
        case class Single(value: Int) extends Wrapper
        val binding = Binding.of[Wrapper].asInstanceOf[Binding.Variant[Wrapper]]
        val single  = Single(42)
        assertTrue(
          binding.matchers.matchers.length == 1 &&
            binding.discriminator.discriminate(single) == 0
        )
      },
      test("Option[T] Variant discriminator distinguishes None and Some") {
        val binding = Binding.of[Option[String]].asInstanceOf[Binding.Variant[Option[String]]]
        val noneIdx = binding.discriminator.discriminate(None)
        val someIdx = binding.discriminator.discriminate(Some("test"))
        assertTrue(noneIdx != someIdx && noneIdx >= 0 && someIdx >= 0)
      },
      test("Option[T] Variant matchers work correctly") {
        val binding = Binding.of[Option[Int]].asInstanceOf[Binding.Variant[Option[Int]]]
        val none    = None
        val some    = Some(42)
        val noneIdx = binding.discriminator.discriminate(none)
        val someIdx = binding.discriminator.discriminate(some)
        assertTrue(
          binding.matchers(noneIdx).downcastOrNull(none) == none &&
            binding.matchers(noneIdx).downcastOrNull(some) == null &&
            binding.matchers(someIdx).downcastOrNull(some) == some
        )
      },
      test("Either[A, B] Variant discriminator distinguishes Left and Right") {
        val binding  = Binding.of[Either[String, Int]].asInstanceOf[Binding.Variant[Either[String, Int]]]
        val leftIdx  = binding.discriminator.discriminate(Left("error"))
        val rightIdx = binding.discriminator.discriminate(Right(42))
        assertTrue(leftIdx != rightIdx && leftIdx >= 0 && rightIdx >= 0)
      },
      test("Either[A, B] Variant matchers work correctly") {
        val binding  = Binding.of[Either[String, Int]].asInstanceOf[Binding.Variant[Either[String, Int]]]
        val left     = Left("error")
        val right    = Right(42)
        val leftIdx  = binding.discriminator.discriminate(left)
        val rightIdx = binding.discriminator.discriminate(right)
        assertTrue(
          binding.matchers(leftIdx).downcastOrNull(left) == left &&
            binding.matchers(leftIdx).downcastOrNull(right) == null &&
            binding.matchers(rightIdx).downcastOrNull(right) == right
        )
      },
      test("sealed trait with mixed case objects and case classes produces correct matcher count") {
        sealed trait MixedADT
        case object Singleton1                 extends MixedADT
        case object Singleton2                 extends MixedADT
        case class WithData(x: Int)            extends MixedADT
        case class WithMore(s: String, n: Int) extends MixedADT
        val binding = Binding.of[MixedADT].asInstanceOf[Binding.Variant[MixedADT]]
        val _       = (WithData(1), WithMore("a", 2))
        assertTrue(binding.matchers.matchers.length == 4)
      },
      test("sealed trait discriminator returns stable indices for same values") {
        sealed trait Token
        case object EOF                     extends Token
        case class Identifier(name: String) extends Token
        val binding = Binding.of[Token].asInstanceOf[Binding.Variant[Token]]
        val id1     = Identifier("x")
        val id2     = Identifier("x")
        assertTrue(binding.discriminator.discriminate(id1) == binding.discriminator.discriminate(id2))
      }
    ),
    suite("Some/Left/Right binding macro coverage")(
      test("derives binding for Some[Int]") {
        val binding = Binding.of[Some[Int]].asInstanceOf[Binding.Record[Some[Int]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Some[String]") {
        val binding = Binding.of[Some[String]].asInstanceOf[Binding.Record[Some[String]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Some[Long]") {
        val binding = Binding.of[Some[Long]].asInstanceOf[Binding.Record[Some[Long]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Some[Double]") {
        val binding = Binding.of[Some[Double]].asInstanceOf[Binding.Record[Some[Double]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Left[String, Int]") {
        val binding = Binding.of[Left[String, Int]].asInstanceOf[Binding.Record[Left[String, Int]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Left[Int, String]") {
        val binding = Binding.of[Left[Int, String]].asInstanceOf[Binding.Record[Left[Int, String]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Left[Long, Boolean]") {
        val binding = Binding.of[Left[Long, Boolean]].asInstanceOf[Binding.Record[Left[Long, Boolean]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Right[String, Int]") {
        val binding = Binding.of[Right[String, Int]].asInstanceOf[Binding.Record[Right[String, Int]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Right[Int, String]") {
        val binding = Binding.of[Right[Int, String]].asInstanceOf[Binding.Record[Right[Int, String]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Right[Boolean, Double]") {
        val binding = Binding.of[Right[Boolean, Double]].asInstanceOf[Binding.Record[Right[Boolean, Double]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      },
      test("derives binding for Right[Byte, Short]") {
        val binding = Binding.of[Right[Byte, Short]].asInstanceOf[Binding.Record[Right[Byte, Short]]]
        assertTrue(binding.constructor.usedRegisters != 0L)
      }
    )
  )
}
