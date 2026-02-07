package zio.blocks.schema.binding

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, SchemaBaseSpec, SchemaError}
import zio.test._

import scala.collection.immutable.ArraySeq

object BindingOfSpec extends SchemaBaseSpec {
  private def isSeq(b: Any): Boolean = b match {
    case _: Binding.Seq[_, _] => true
    case _                    => false
  }
  private def isMap(b: Any): Boolean = b match {
    case _: Binding.Map[_, _, _] => true
    case _                       => false
  }
  private def isWrapper(b: Any): Boolean = b match {
    case _: Binding.Wrapper[_, _] => true
    case _                        => false
  }

  case class Person(name: String, age: Int)
  case class Address(street: String, city: String)
  case class Nested(person: Person, address: Address)
  case class WithPrimitives(b: Boolean, by: Byte, s: Short, i: Int, l: Long, f: Float, d: Double, c: Char)
  case class Empty()
  case class MultiParamList(name: String, age: Int)(val city: String, val zip: Int)

  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color

  sealed trait Shape
  case class Circle(radius: Double)          extends Shape
  case class Rectangle(w: Double, h: Double) extends Shape

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

  case class HKTContainer[F[_]](value: F[Int])

  def spec: Spec[TestEnvironment, Any] = suite("Binding.of")(
    suite("primitives")(
      test("Int") {
        val binding = Binding.of[Int]
        assertTrue(binding == Binding.Primitive.int)
      },
      test("String") {
        val binding = Binding.of[String]
        assertTrue(binding == Binding.Primitive.string)
      },
      test("Boolean") {
        val binding = Binding.of[Boolean]
        assertTrue(binding == Binding.Primitive.boolean)
      },
      test("Long") {
        val binding = Binding.of[Long]
        assertTrue(binding == Binding.Primitive.long)
      },
      test("Double") {
        val binding = Binding.of[Double]
        assertTrue(binding == Binding.Primitive.double)
      },
      test("Float") {
        val binding = Binding.of[Float]
        assertTrue(binding == Binding.Primitive.float)
      },
      test("Byte") {
        val binding = Binding.of[Byte]
        assertTrue(binding == Binding.Primitive.byte)
      },
      test("Short") {
        val binding = Binding.of[Short]
        assertTrue(binding == Binding.Primitive.short)
      },
      test("Char") {
        val binding = Binding.of[Char]
        assertTrue(binding == Binding.Primitive.char)
      },
      test("Unit") {
        val binding = Binding.of[Unit]
        assertTrue(binding == Binding.Primitive.unit)
      },
      test("BigInt") {
        val binding = Binding.of[BigInt]
        assertTrue(binding == Binding.Primitive.bigInt)
      },
      test("BigDecimal") {
        val binding = Binding.of[BigDecimal]
        assertTrue(binding == Binding.Primitive.bigDecimal)
      },
      test("java.time.Instant") {
        val binding = Binding.of[java.time.Instant]
        assertTrue(binding == Binding.Primitive.instant)
      },
      test("java.time.LocalDate") {
        val binding = Binding.of[java.time.LocalDate]
        assertTrue(binding == Binding.Primitive.localDate)
      },
      test("java.time.LocalDateTime") {
        val binding = Binding.of[java.time.LocalDateTime]
        assertTrue(binding == Binding.Primitive.localDateTime)
      },
      test("java.time.Duration") {
        val binding = Binding.of[java.time.Duration]
        assertTrue(binding == Binding.Primitive.duration)
      },
      test("java.util.UUID") {
        val binding = Binding.of[java.util.UUID]
        assertTrue(binding == Binding.Primitive.uuid)
      },
      test("java.util.Currency") {
        val binding = Binding.of[java.util.Currency]
        assertTrue(binding == Binding.Primitive.currency)
      },
      test("java.time.DayOfWeek") {
        val binding = Binding.of[java.time.DayOfWeek]
        assertTrue(binding == Binding.Primitive.dayOfWeek)
      },
      test("java.time.Month") {
        val binding = Binding.of[java.time.Month]
        assertTrue(binding == Binding.Primitive.month)
      },
      test("java.time.Year") {
        val binding = Binding.of[java.time.Year]
        assertTrue(binding == Binding.Primitive.year)
      },
      test("java.time.YearMonth") {
        val binding = Binding.of[java.time.YearMonth]
        assertTrue(binding == Binding.Primitive.yearMonth)
      },
      test("java.time.MonthDay") {
        val binding = Binding.of[java.time.MonthDay]
        assertTrue(binding == Binding.Primitive.monthDay)
      },
      test("java.time.Period") {
        val binding = Binding.of[java.time.Period]
        assertTrue(binding == Binding.Primitive.period)
      },
      test("java.time.ZoneId") {
        val binding = Binding.of[java.time.ZoneId]
        assertTrue(binding == Binding.Primitive.zoneId)
      },
      test("java.time.ZoneOffset") {
        val binding = Binding.of[java.time.ZoneOffset]
        assertTrue(binding == Binding.Primitive.zoneOffset)
      },
      test("java.time.ZonedDateTime") {
        val binding = Binding.of[java.time.ZonedDateTime]
        assertTrue(binding == Binding.Primitive.zonedDateTime)
      },
      test("java.time.OffsetDateTime") {
        val binding = Binding.of[java.time.OffsetDateTime]
        assertTrue(binding == Binding.Primitive.offsetDateTime)
      },
      test("java.time.OffsetTime") {
        val binding = Binding.of[java.time.OffsetTime]
        assertTrue(binding == Binding.Primitive.offsetTime)
      },
      test("java.time.LocalTime") {
        val binding = Binding.of[java.time.LocalTime]
        assertTrue(binding == Binding.Primitive.localTime)
      }
    ),
    suite("case classes")(
      test("simple case class returns Binding.Record") {
        val binding = Binding.of[Person]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("nested case class returns Binding.Record") {
        val binding = Binding.of[Nested]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("case class with all primitive fields") {
        val binding = Binding.of[WithPrimitives]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("empty case class") {
        val binding = Binding.of[Empty]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("case class constructor works") {
        val binding   = Binding.of[Person].asInstanceOf[Binding.Record[Person]]
        val registers = Registers(binding.constructor.usedRegisters)
        registers.setObject(RegisterOffset.Zero, "Alice")
        registers.setInt(RegisterOffset(objects = 1), 30)
        val person = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(person == Person("Alice", 30))
      },
      test("case class deconstructor works") {
        val binding   = Binding.of[Person].asInstanceOf[Binding.Record[Person]]
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, Person("Bob", 25))
        assertTrue(
          registers.getObject(RegisterOffset.Zero) == "Bob" &&
            registers.getInt(RegisterOffset(objects = 1)) == 25
        )
      },
      test("primitive fields constructor/deconstructor roundtrip") {
        val binding   = Binding.of[WithPrimitives].asInstanceOf[Binding.Record[WithPrimitives]]
        val original  = WithPrimitives(true, 1.toByte, 2.toShort, 3, 4L, 5.0f, 6.0, 'x')
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      }
    ),
    suite("sealed traits")(
      test("case object sealed trait returns Binding.Variant") {
        val binding = Binding.of[Color]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("case class sealed trait returns Binding.Variant") {
        val binding = Binding.of[Shape]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("discriminator works for case objects") {
        val binding = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        assertTrue(
          binding.discriminator.discriminate(Red) == 0 &&
            binding.discriminator.discriminate(Green) == 1 &&
            binding.discriminator.discriminate(Blue) == 2
        )
      },
      test("matchers work for case objects") {
        val binding  = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        val matcher0 = binding.matchers(0).asInstanceOf[Matcher[Color]]
        val matcher1 = binding.matchers(1).asInstanceOf[Matcher[Color]]
        val matcher2 = binding.matchers(2).asInstanceOf[Matcher[Color]]
        assertTrue(
          matcher0.downcastOrNull(Red) == Red &&
            matcher0.downcastOrNull(Green) == null &&
            matcher1.downcastOrNull(Green) == Green &&
            matcher2.downcastOrNull(Blue) == Blue
        )
      },
      test("discriminator works for case classes") {
        val binding = Binding.of[Shape].asInstanceOf[Binding.Variant[Shape]]
        assertTrue(
          binding.discriminator.discriminate(Circle(1.0)) == 0 &&
            binding.discriminator.discriminate(Rectangle(2.0, 3.0)) == 1
        )
      }
    ),
    suite("Option and Either")(
      test("Option[String]") {
        val binding = Binding.of[Option[String]]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("Option[Int]") {
        val binding = Binding.of[Option[Int]]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("Either[String, Int]") {
        val binding = Binding.of[Either[String, Int]]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("None.type") {
        val binding = Binding.of[None.type]
        assertTrue(binding == Binding.Record.none)
      },
      test("Some[String]") {
        val binding = Binding.of[Some[String]]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("Some[Int] uses specialized version") {
        val binding = Binding.of[Some[Int]]
        assertTrue(binding == Binding.Record.someInt)
      },
      test("Left[String, Int]") {
        val binding = Binding.of[Left[String, Int]]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("Right[String, Int]") {
        val binding = Binding.of[Right[String, Int]]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("Left[Int, _] roundtrip") {
        val binding   = Binding.Record.leftInt[String]
        val original  = Left[Int, String](42)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Long, _] roundtrip") {
        val binding   = Binding.Record.leftLong[String]
        val original  = Left[Long, String](42L)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Double, _] roundtrip") {
        val binding   = Binding.Record.leftDouble[String]
        val original  = Left[Double, String](3.14)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Float, _] roundtrip") {
        val binding   = Binding.Record.leftFloat[String]
        val original  = Left[Float, String](3.14f)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Boolean, _] roundtrip") {
        val binding   = Binding.Record.leftBoolean[String]
        val original  = Left[Boolean, String](true)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Byte, _] roundtrip") {
        val binding   = Binding.Record.leftByte[String]
        val original  = Left[Byte, String](42.toByte)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Short, _] roundtrip") {
        val binding   = Binding.Record.leftShort[String]
        val original  = Left[Short, String](42.toShort)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Char, _] roundtrip") {
        val binding   = Binding.Record.leftChar[String]
        val original  = Left[Char, String]('x')
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Left[Unit, _] roundtrip") {
        val binding   = Binding.Record.leftUnit[String]
        val original  = Left[Unit, String](())
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Int] roundtrip") {
        val binding   = Binding.Record.rightInt[String]
        val original  = Right[String, Int](42)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Long] roundtrip") {
        val binding   = Binding.Record.rightLong[String]
        val original  = Right[String, Long](42L)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Double] roundtrip") {
        val binding   = Binding.Record.rightDouble[String]
        val original  = Right[String, Double](3.14)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Float] roundtrip") {
        val binding   = Binding.Record.rightFloat[String]
        val original  = Right[String, Float](3.14f)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Boolean] roundtrip") {
        val binding   = Binding.Record.rightBoolean[String]
        val original  = Right[String, Boolean](true)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Byte] roundtrip") {
        val binding   = Binding.Record.rightByte[String]
        val original  = Right[String, Byte](42.toByte)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Short] roundtrip") {
        val binding   = Binding.Record.rightShort[String]
        val original  = Right[String, Short](42.toShort)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Char] roundtrip") {
        val binding   = Binding.Record.rightChar[String]
        val original  = Right[String, Char]('x')
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Right[_, Unit] roundtrip") {
        val binding   = Binding.Record.rightUnit[String]
        val original  = Right[String, Unit](())
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      }
    ),
    suite("collections - type constructors")(
      test("List") {
        val binding = Binding.of[List]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.listConstructor
        )
      },
      test("Vector") {
        val binding = Binding.of[Vector]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.vectorConstructor
        )
      },
      test("Set") {
        val binding = Binding.of[Set]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.setConstructor
        )
      },
      test("Map") {
        val binding = Binding.of[Map]
        assertTrue(
          isMap(binding) &&
            binding.constructor == MapConstructor.map
        )
      },
      test("Chunk") {
        val binding = Binding.of[Chunk]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.chunkConstructor
        )
      },
      test("ArraySeq") {
        val binding = Binding.of[ArraySeq]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.arraySeqConstructor
        )
      },
      test("Array") {
        val binding = Binding.of[Array]
        assertTrue(
          isSeq(binding) &&
            binding.constructor == SeqConstructor.arrayConstructor
        )
      },
      test("covariant upcast works") {
        val listNothing: Binding.Seq[List, Nothing] = Binding.of[List]
        val listInt: Binding.Seq[List, Int]         = listNothing
        assertTrue(listInt.constructor == SeqConstructor.listConstructor)
      }
    ),
    suite("DynamicValue")(
      test("DynamicValue binding") {
        val binding = Binding.of[DynamicValue]
        assertTrue(binding.isInstanceOf[Binding.Dynamic])
      }
    ),
    suite("case objects")(
      test("case object Red") {
        val binding = Binding.of[Red.type]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("case object constructor returns singleton") {
        val binding   = Binding.of[Red.type].asInstanceOf[Binding.Record[Red.type]]
        val registers = Registers(0L)
        val result    = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(result eq Red)
      }
    ),
    suite("multiple parameter lists")(
      test("case class with multiple parameter lists returns Binding.Record") {
        val binding = Binding.of[MultiParamList]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      },
      test("multi-param list constructor/deconstructor roundtrip") {
        val binding   = Binding.of[MultiParamList].asInstanceOf[Binding.Record[MultiParamList]]
        val original  = new MultiParamList("Alice", 30)("NYC", 10001)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(
          reconstructed.name == original.name &&
            reconstructed.age == original.age &&
            reconstructed.city == original.city &&
            reconstructed.zip == original.zip
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
        val binding = Binding.of[Person]
        assertTrue(binding.isInstanceOf[Binding.Record[_]])
      }
    ),
    suite("tuples")(
      test("Tuple2 binding roundtrip") {
        val binding   = Binding.of[(String, Int)].asInstanceOf[Binding.Record[(String, Int)]]
        val original  = ("hello", 42)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Tuple3 binding roundtrip") {
        val binding   = Binding.of[(String, Int, Boolean)].asInstanceOf[Binding.Record[(String, Int, Boolean)]]
        val original  = ("hello", 42, true)
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      },
      test("Tuple with all primitives") {
        val binding =
          Binding
            .of[(Int, Long, Double, Float, Boolean, Byte, Short, Char)]
            .asInstanceOf[Binding.Record[
              (Int, Long, Double, Float, Boolean, Byte, Short, Char)
            ]]
        val original  = (1, 2L, 3.0, 4.0f, true, 5.toByte, 6.toShort, 'x')
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
      }
    ),
    suite("higher-kinded types")(
      test("case class with higher-kinded type parameter") {
        val binding   = Binding.of[HKTContainer[Option]].asInstanceOf[Binding.Record[HKTContainer[Option]]]
        val original  = HKTContainer[Option](Some(42))
        val registers = Registers(binding.deconstructor.usedRegisters)
        binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, original)
        val reconstructed = binding.constructor.construct(registers, RegisterOffset.Zero)
        assertTrue(reconstructed == original)
      }
    ),
    suite("Array bindings")(
      test("Array construct empty") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val result        = constructor.empty[AnyRef]
        assertTrue(deconstructor.deconstruct(result).isEmpty)
      },
      test("Array construct from builder") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](3)
        constructor.add(builder, "a")
        constructor.add(builder, "b")
        constructor.add(builder, "c")
        val result = constructor.result(builder)
        assertTrue(deconstructor.deconstruct(result).toList == List("a", "b", "c"))
      },
      test("Array deconstruct works") {
        val binding       = Binding.of[Array]
        val deconstructor = binding.deconstructor
        val arr           = Array("a", "b", "c")
        val result        = deconstructor.deconstruct(arr)
        assertTrue(result.toList == List("a", "b", "c"))
      },
      test("Array Person elements (case class)") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](2)
        constructor.add(builder, Person("Alice", 30))
        constructor.add(builder, Person("Bob", 25))
        val result = constructor.result(builder)
        assertTrue(deconstructor.deconstruct(result).toList == List(Person("Alice", 30), Person("Bob", 25)))
      },
      test("Array builder grows correctly") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](1)
        (1 to 100).foreach(i => constructor.add(builder, i.toString))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list.length == 100 && list.head == "1" && list.last == "100")
      }
    ),
    suite("ArraySeq bindings")(
      test("ArraySeq construct empty") {
        val binding       = Binding.of[ArraySeq]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val result        = constructor.empty[String]
        assertTrue(deconstructor.deconstruct(result).isEmpty)
      },
      test("ArraySeq construct from builder") {
        val binding       = Binding.of[ArraySeq]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[String](3)
        constructor.add(builder, "a")
        constructor.add(builder, "b")
        constructor.add(builder, "c")
        val result = constructor.result(builder)
        assertTrue(deconstructor.deconstruct(result).toList == List("a", "b", "c"))
      },
      test("ArraySeq deconstruct works") {
        val binding       = Binding.of[ArraySeq]
        val deconstructor = binding.deconstructor
        val arr           = ArraySeq("a", "b", "c")
        val result        = deconstructor.deconstruct(arr)
        assertTrue(result.toList == List("a", "b", "c"))
      },
      test("ArraySeq builder grows correctly") {
        val binding       = Binding.of[ArraySeq]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[String](1)
        (1 to 100).foreach(i => constructor.add(builder, i.toString))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list.length == 100 && list.head == "1" && list.last == "100")
      }
    ),
    suite("sealed trait Binding.Variant for Schema.derived delegation")(
      test("sealed trait Binding.of creates Variant") {
        val binding = Binding.of[Color]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("sealed trait discriminator indexes all subtypes") {
        val binding = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        assertTrue(
          binding.discriminator.discriminate(Red) >= 0 &&
            binding.discriminator.discriminate(Green) >= 0 &&
            binding.discriminator.discriminate(Blue) >= 0 &&
            binding.discriminator.discriminate(Red) != binding.discriminator.discriminate(Green) &&
            binding.discriminator.discriminate(Green) != binding.discriminator.discriminate(Blue)
        )
      },
      test("sealed trait matchers can identify each case object") {
        val binding    = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        val redIdx     = binding.discriminator.discriminate(Red)
        val greenIdx   = binding.discriminator.discriminate(Green)
        val blueIdx    = binding.discriminator.discriminate(Blue)
        val redMatch   = binding.matchers(redIdx).downcastOrNull(Red)
        val greenCheck = binding.matchers(redIdx).downcastOrNull(Green)
        val greenMatch = binding.matchers(greenIdx).downcastOrNull(Green)
        val blueMatch  = binding.matchers(blueIdx).downcastOrNull(Blue)
        assertTrue(
          redMatch == Red &&
            greenCheck == null &&
            greenMatch == Green &&
            blueMatch == Blue
        )
      },
      test("sealed trait with case class subtypes") {
        val binding = Binding.of[Shape].asInstanceOf[Binding.Variant[Shape]]
        val circle  = Circle(5.0)
        val rect    = Rectangle(3.0, 4.0)
        assertTrue(
          binding.discriminator.discriminate(circle) >= 0 &&
            binding.discriminator.discriminate(rect) >= 0 &&
            binding.discriminator.discriminate(circle) != binding.discriminator.discriminate(rect)
        )
      },
      test("sealed trait matchers identify case class subtypes") {
        val binding     = Binding.of[Shape].asInstanceOf[Binding.Variant[Shape]]
        val circle      = Circle(5.0)
        val rect        = Rectangle(3.0, 4.0)
        val circleIdx   = binding.discriminator.discriminate(circle)
        val rectIdx     = binding.discriminator.discriminate(rect)
        val circleMatch = binding.matchers(circleIdx).downcastOrNull(circle)
        val rectCheck   = binding.matchers(circleIdx).downcastOrNull(rect)
        val rectMatch   = binding.matchers(rectIdx).downcastOrNull(rect)
        assertTrue(
          circleMatch == circle &&
            rectCheck == null &&
            rectMatch == rect
        )
      },
      test("matchers count equals subtypes count") {
        val colorBinding = Binding.of[Color].asInstanceOf[Binding.Variant[Color]]
        val shapeBinding = Binding.of[Shape].asInstanceOf[Binding.Variant[Shape]]
        assertTrue(
          colorBinding.matchers.matchers.length == 3 &&
            shapeBinding.matchers.matchers.length == 2
        )
      },
      test("Option binding creates Variant") {
        val binding = Binding.of[Option[Int]]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("Option discriminator works") {
        val binding = Binding.of[Option[Int]].asInstanceOf[Binding.Variant[Option[Int]]]
        assertTrue(
          binding.discriminator.discriminate(None) >= 0 &&
            binding.discriminator.discriminate(Some(42)) >= 0 &&
            binding.discriminator.discriminate(None) != binding.discriminator.discriminate(Some(42))
        )
      },
      test("Either binding creates Variant") {
        val binding = Binding.of[Either[String, Int]]
        assertTrue(binding.isInstanceOf[Binding.Variant[_]])
      },
      test("Either discriminator works") {
        val binding = Binding.of[Either[String, Int]].asInstanceOf[Binding.Variant[Either[String, Int]]]
        assertTrue(
          binding.discriminator.discriminate(Left("err")) >= 0 &&
            binding.discriminator.discriminate(Right(42)) >= 0 &&
            binding.discriminator.discriminate(Left("err")) != binding.discriminator.discriminate(Right(42))
        )
      }
    ),
    suite("nested sealed traits")(
      test("sealed trait containing sealed trait subtypes") {
        sealed trait Outer
        sealed trait Inner extends Outer
        case object A      extends Inner
        case object B      extends Inner
        case object C      extends Outer

        val binding = Binding.of[Outer].asInstanceOf[Binding.Variant[Outer]]
        assertTrue(
          binding.discriminator.discriminate(A) >= 0 &&
            binding.discriminator.discriminate(B) >= 0 &&
            binding.discriminator.discriminate(C) >= 0
        )
      }
    ),
    suite("complex composite types")(
      test("Array with sealed trait elements") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](3)
        constructor.add(builder, Red)
        constructor.add(builder, Green)
        constructor.add(builder, Blue)
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List(Red, Green, Blue))
      },
      test("Array of case class with sealed trait field") {
        case class Container(color: Color, value: Int)
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](2)
        constructor.add(builder, Container(Red, 1))
        constructor.add(builder, Container(Blue, 2))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List(Container(Red, 1), Container(Blue, 2)))
      },
      test("ArraySeq of Option") {
        val binding       = Binding.of[ArraySeq]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](3)
        constructor.add(builder, Some(1))
        constructor.add(builder, None)
        constructor.add(builder, Some(3))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List(Some(1), None, Some(3)))
      }
    ),
    suite("Array resize and trim")(
      test("Array resize works when growing") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](1)
        (1 to 10).foreach(i => constructor.add(builder, i.toString))
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list.length == 10 && list.head == "1" && list.last == "10")
      },
      test("Array result trim works when oversized") {
        val binding       = Binding.of[Array]
        val constructor   = binding.constructor
        val deconstructor = binding.deconstructor
        val builder       = constructor.newBuilder[AnyRef](16)
        constructor.add(builder, "a")
        constructor.add(builder, "b")
        val result = constructor.result(builder)
        val list   = deconstructor.deconstruct(result).toList
        assertTrue(list == List("a", "b"))
      }
    ),
    suite("ArraySeq resize and trim")(
      test("ArraySeq resize works when growing") {
        val binding     = Binding.of[ArraySeq]
        val constructor = binding.constructor
        val builder     = constructor.newBuilder[String](1)
        (1 to 10).foreach(i => constructor.add(builder, i.toString))
        val result = constructor.result(builder)
        assertTrue(result.length == 10 && result(0) == "1" && result(9) == "10")
      },
      test("ArraySeq result trim works when oversized") {
        val binding     = Binding.of[ArraySeq]
        val constructor = binding.constructor
        val builder     = constructor.newBuilder[String](16)
        constructor.add(builder, "42")
        val result = constructor.result(builder)
        assertTrue(result.length == 1 && result(0) == "42")
      }
    )
  )
}
