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
      test("IArray[Int]") {
        val binding = Binding.of[IArray[Int]]
        assertTrue(isSeq(binding))
      },
      test("IArray[String]") {
        val binding = Binding.of[IArray[String]]
        assertTrue(isSeq(binding))
      },
      test("IArray[Double]") {
        val binding = Binding.of[IArray[Double]]
        assertTrue(isSeq(binding))
      }
    ),
    suite("smart constructor case classes")(
      test("case class with smart constructor returns Binding.Wrapper") {
        val binding = Binding.of[Email]
        assertTrue(isWrapper(binding))
      },
      test("smart constructor wrap succeeds for valid input") {
        val binding = Binding.of[Email].asInstanceOf[Binding.Wrapper[Email, String]]
        assertTrue(binding.wrap("test@example.com").isRight)
      },
      test("smart constructor wrap fails for invalid input") {
        val binding = Binding.of[Email].asInstanceOf[Binding.Wrapper[Email, String]]
        assertTrue(binding.wrap("invalid").isLeft)
      },
      test("smart constructor unwrap extracts underlying value") {
        val binding = Binding.of[Email].asInstanceOf[Binding.Wrapper[Email, String]]
        val email   = binding.wrap("test@example.com").toOption.get
        assertTrue(binding.unwrap(email) == "test@example.com")
      },
      test("smart constructor with SchemaError return type") {
        val binding = Binding.of[PositiveDouble].asInstanceOf[Binding.Wrapper[PositiveDouble, Double]]
        assertTrue(
          binding.wrap(1.5).isRight &&
            binding.wrap(-1.0).isLeft
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
        assertTrue(
          wrapped.isRight &&
            binding.unwrap(wrapped.toOption.get) == 25
        )
      },
      test("opaque type with String error smart constructor returns Binding.Wrapper") {
        val binding = Binding.of[ValidatedEmail]
        assertTrue(isWrapper(binding))
      },
      test("opaque type smart constructor wrap succeeds for valid input") {
        val binding = Binding.of[ValidatedEmail].asInstanceOf[Binding.Wrapper[ValidatedEmail, String]]
        val result  = binding.wrap("test@example.com")
        assertTrue(result.isRight)
      },
      test("opaque type smart constructor wrap fails for invalid input") {
        val binding = Binding.of[ValidatedEmail].asInstanceOf[Binding.Wrapper[ValidatedEmail, String]]
        assertTrue(binding.wrap("invalid").isLeft)
      },
      test("opaque type smart constructor unwrap extracts value") {
        val binding = Binding.of[ValidatedEmail].asInstanceOf[Binding.Wrapper[ValidatedEmail, String]]
        val wrapped = binding.wrap("test@example.com").toOption.get
        assertTrue(binding.unwrap(wrapped) == "test@example.com")
      },
      test("opaque type with SchemaError smart constructor returns Binding.Wrapper") {
        val binding = Binding.of[PositiveInt]
        assertTrue(isWrapper(binding))
      },
      test("opaque type with SchemaError smart constructor wrap succeeds for valid input") {
        val binding = Binding.of[PositiveInt].asInstanceOf[Binding.Wrapper[PositiveInt, Int]]
        assertTrue(binding.wrap(42).isRight)
      },
      test("opaque type with SchemaError smart constructor wrap fails for invalid input") {
        val binding = Binding.of[PositiveInt].asInstanceOf[Binding.Wrapper[PositiveInt, Int]]
        val result  = binding.wrap(-5)
        assertTrue(result.isLeft)
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
        assertTrue(
          wrapped.isRight &&
            binding.unwrap(wrapped.toOption.get) == 12345L
        )
      },
      test("newtype wrap and unwrap work for String") {
        val binding = Binding.of[Username].asInstanceOf[Binding.Wrapper[Username, String]]
        val wrapped = binding.wrap("alice")
        assertTrue(
          wrapped.isRight &&
            binding.unwrap(wrapped.toOption.get) == "alice"
        )
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
    )
  )
}
