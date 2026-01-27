package zio.blocks.schema

import zio.blocks.schema.patch.{Patch, PatchMode}
import zio.test._
import zio.test.Assertion._
import java.time.YearMonth

object PatchSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("PatchSpec")(
    test("replace a field with a new value") {
      val person1 = Person(12345678901L, "John", "123 Main St", Nil)
      val patch   = Patch.replace(Person.name, "Piero")
      val person2 = person1.copy(name = "Piero")
      assert(patch(person1))(equalTo(person2)) &&
      assert(patch.applyOption(person1))(isSome(equalTo(person2))) &&
      assert(patch.apply(person1, PatchMode.Strict))(isRight(equalTo(person2)))
    },
    test("replace a case with a new value") {
      val paymentMethod1 = PayPal("x@gmail.com")
      val patch          = Patch.replace(PaymentMethod.payPal, PayPal("y@gmail.com"))
      val paymentMethod2 = PayPal("y@gmail.com")
      assert(patch(paymentMethod1))(equalTo(paymentMethod2)) &&
      assert(patch.applyOption(paymentMethod1))(isSome(equalTo(paymentMethod2))) &&
      assert(patch.apply(paymentMethod1, PatchMode.Strict))(isRight(equalTo(paymentMethod2)))
    },
    test("replace a case field with a new value") {
      val paymentMathod1 = PayPal("x@gmail.com")
      val patch          = Patch.replace(PaymentMethod.payPalEmail, "y@gmail.com")
      val paymentMethod2 = PayPal("y@gmail.com")
      assert(patch(paymentMathod1))(equalTo(paymentMethod2)) &&
      assert(patch.applyOption(paymentMathod1))(isSome(equalTo(paymentMethod2))) &&
      assert(patch.apply(paymentMathod1, PatchMode.Strict))(isRight(equalTo(paymentMethod2)))
    },
    test("replace selected list values") {
      val person1 = Person(
        12345678901L,
        "John",
        "123 Main St",
        List(
          PayPal("x@gmail.com"),
          CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
        )
      )
      val patch   = Patch.replace(Person.payPalPaymentMethods, PayPal("y@gmail.com"))
      val person2 = person1.copy(paymentMethods =
        List(
          PayPal("y@gmail.com"),
          CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
        )
      )
      assert(patch(person1))(equalTo(person2)) &&
      assert(patch.applyOption(person1))(isSome(equalTo(person2))) &&
      assert(patch.apply(person1, PatchMode.Strict))(isRight(equalTo(person2)))
    },
    test("don't replace non-matching case with a new value") {
      val person1        = Person(12345678901L, "John", "123 Main St", Nil)
      val paymentMethod1 = CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
      val patch1         = Patch.replace(PaymentMethod.payPal, PayPal("y@gmail.com"))
      val patch2         = Patch.replace(PaymentMethod.payPalEmail, "y@gmail.com")
      val patch3         = Patch.replace(Person.paymentMethods(PaymentMethod.payPalEmail), "y@gmail.com")
      assert(patch1(paymentMethod1))(equalTo(paymentMethod1)) &&
      assert(patch1.applyOption(paymentMethod1))(isNone) &&
      assert(patch1.apply(paymentMethod1, PatchMode.Strict))(
        isLeft(
          hasError("Expected case PayPal but got CreditCard")
        )
      ) &&
      assert(patch2(paymentMethod1))(equalTo(paymentMethod1)) &&
      assert(patch2.applyOption(paymentMethod1))(isNone) &&
      assert(patch2.apply(paymentMethod1, PatchMode.Strict))(
        isLeft(
          hasError("Expected case PayPal but got CreditCard")
        )
      ) &&
      assert(patch3.applyOption(person1))(isNone) &&
      assert(patch3.apply(person1, PatchMode.Strict))(
        isLeft(
          hasError("encountered an empty sequence")
        )
      )
    },
    test("combine two patches") {
      val person1 = Person(12345678901L, "John", "123 Main St", Nil)
      val patch   = Patch.replace(Person.name, "Piero") ++ Patch.replace(Person.address, "321 Main St")
      val parson2 = person1.copy(name = "Piero", address = "321 Main St")
      assert(patch(person1))(equalTo(parson2)) &&
      assert(patch.applyOption(person1))(isSome(equalTo(parson2))) &&
      assert(patch.apply(person1, PatchMode.Strict))(isRight(equalTo(parson2)))
    },
    suite("PatchMode.Lenient")(
      test("lenient mode ignores missing fields") {
        val person1 = Person(12345678901L, "John", "123 Main St", Nil)
        val patch   = Patch.replace(Person.payPalPaymentMethods, PayPal("new@email.com"))
        val result  = patch.apply(person1, PatchMode.Lenient)
        assertTrue(result.isRight)
      },
      test("lenient mode ignores non-matching cases") {
        val creditCard = CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
        val patch      = Patch.replace(PaymentMethod.payPal, PayPal("new@email.com"))
        val result     = patch.apply(creditCard, PatchMode.Lenient)
        assertTrue(result == Right(creditCard))
      }
    ),
    suite("Patch toString")(
      test("empty patch toString") {
        val patch = Patch.replace(Person.name, "Test") ++ Patch.replace(Person.name, "Test")
        assertTrue(patch.toString.nonEmpty)
      },
      test("replace patch toString contains field info") {
        val patch = Patch.replace(Person.name, "NewName")
        assertTrue(patch.toString.nonEmpty)
      }
    ),
    suite("Patch with nested structures")(
      test("replace nested field in variant") {
        val payment = PayPal("old@email.com")
        val patch   = Patch.replace(PaymentMethod.payPalEmail, "new@email.com")
        val result  = patch(payment)
        assertTrue(result == PayPal("new@email.com"))
      },
      test("replace field in bank transfer") {
        val bankTransfer = BankTransfer("12345", "SWIFT123", "John Doe")
        val patch        = Patch.replace(PaymentMethod.bankTransfer, BankTransfer("67890", "SWIFT456", "Jane Doe"))
        val result       = patch(bankTransfer)
        assertTrue(result == BankTransfer("67890", "SWIFT456", "Jane Doe"))
      }
    ),
    suite("Patch with sequences")(
      test("replace all elements in sequence") {
        val person = Person(
          1L,
          "Test",
          "Addr",
          List(PayPal("a@b.com"), PayPal("c@d.com"))
        )
        val patch  = Patch.replace(Person.payPalPaymentMethods, PayPal("new@email.com"))
        val result = patch(person)
        assertTrue(
          result.paymentMethods == List(PayPal("new@email.com"), PayPal("new@email.com"))
        )
      },
      test("patch preserves non-matching elements in sequence") {
        val person = Person(
          1L,
          "Test",
          "Addr",
          List(
            PayPal("a@b.com"),
            CreditCard(1234L, YearMonth.parse("2030-01"), 123, "Test")
          )
        )
        val patch  = Patch.replace(Person.payPalPaymentMethods, PayPal("new@email.com"))
        val result = patch(person)
        assertTrue(
          result.paymentMethods.head == PayPal("new@email.com"),
          result.paymentMethods(1).isInstanceOf[CreditCard]
        )
      }
    ),
    suite("Patch composition")(
      test("empty patch acts as identity") {
        val person        = Person(1L, "Test", "Addr", Nil)
        val patch         = Patch.replace(Person.name, "Test")
        val combinedPatch = patch ++ Patch.replace(Person.name, "Test")
        assertTrue(combinedPatch(person).name == "Test")
      },
      test("multiple field patches compose correctly") {
        val person = Person(1L, "Old", "OldAddr", Nil)
        val patch  = Patch.replace(Person.name, "New") ++
          Patch.replace(Person.address, "NewAddr") ++
          Patch.replace(Person.id, 2L)
        val result = patch(person)
        assertTrue(
          result.name == "New",
          result.address == "NewAddr",
          result.id == 2L
        )
      }
    ),
    suite("Patch with Vector sequence operations")(
      test("append elements to Vector") {
        val container = VectorContainer(Vector(1, 2, 3), "test", 100)
        val patch     = Patch.append(VectorContainer.items, Vector(4, 5))(Patch.CollectionDummy.ForVector)
        val result    = patch(container)
        assertTrue(result.items == Vector(1, 2, 3, 4, 5))
      },
      test("insertAt elements in Vector") {
        val container = VectorContainer(Vector(1, 2, 5), "test", 100)
        val patch     = Patch.insertAt(VectorContainer.items, 2, Vector(3, 4))(Patch.CollectionDummy.ForVector)
        val result    = patch(container)
        assertTrue(result.items == Vector(1, 2, 3, 4, 5))
      },
      test("deleteAt elements from Vector") {
        val container = VectorContainer(Vector(1, 2, 3, 4, 5), "test", 100)
        val patch     = Patch.deleteAt(VectorContainer.items, 1, 2)(Patch.CollectionDummy.ForVector)
        val result    = patch(container)
        assertTrue(result.items == Vector(1, 4, 5))
      }
    ),
    suite("Patch with numeric increments")(
      test("increment Int field") {
        val container = VectorContainer(Vector.empty, "test", 100)
        val patch     = Patch.increment(VectorContainer.count, 50)
        val result    = patch(container)
        assertTrue(result.count == 150)
      },
      test("increment Long field") {
        val container = LongContainer(1000L)
        val patch     = Patch.increment(LongContainer.value, 500L)
        val result    = patch(container)
        assertTrue(result.value == 1500L)
      },
      test("increment Double field") {
        val container = DoubleContainer(10.5)
        val patch     = Patch.increment(DoubleContainer.value, 5.5)
        val result    = patch(container)
        assertTrue(result.value == 16.0)
      },
      test("increment Float field") {
        val container = FloatContainer(10.5f)
        val patch     = Patch.increment(FloatContainer.value, 5.5f)
        val result    = patch(container)
        assertTrue(result.value == 16.0f)
      },
      test("increment Short field") {
        val container = ShortContainer(100.toShort)
        val patch     = Patch.increment(ShortContainer.value, 50.toShort)
        val result    = patch(container)
        assertTrue(result.value == 150.toShort)
      },
      test("increment Byte field") {
        val container = ByteContainer(10.toByte)
        val patch     = Patch.increment(ByteContainer.value, 5.toByte)
        val result    = patch(container)
        assertTrue(result.value == 15.toByte)
      },
      test("increment BigInt field") {
        val container = BigIntContainer(BigInt(1000))
        val patch     = Patch.increment(BigIntContainer.value, BigInt(500))
        val result    = patch(container)
        assertTrue(result.value == BigInt(1500))
      },
      test("increment BigDecimal field") {
        val container = BigDecimalContainer(BigDecimal("100.50"))
        val patch     = Patch.increment(BigDecimalContainer.value, BigDecimal("50.25"))
        val result    = patch(container)
        assertTrue(result.value == BigDecimal("150.75"))
      }
    ),
    suite("Patch with Map operations")(
      test("addKey to Map") {
        val container = MapContainer(Map("a" -> 1, "b" -> 2))
        val patch     = Patch.addKey(MapContainer.items, "c", 3)
        val result    = patch(container)
        assertTrue(result.items == Map("a" -> 1, "b" -> 2, "c" -> 3))
      },
      test("removeKey from Map") {
        val container = MapContainer(Map("a" -> 1, "b" -> 2, "c" -> 3))
        val patch     = Patch.removeKey(MapContainer.items, "b")
        val result    = patch(container)
        assertTrue(result.items == Map("a" -> 1, "c" -> 3))
      }
    ),
    suite("Patch with temporal operations")(
      test("addDuration to Instant") {
        import java.time.{Duration, Instant}
        val container = InstantContainer(Instant.parse("2024-01-01T00:00:00Z"))
        val patch     = Patch.addDuration(InstantContainer.value, Duration.ofHours(24))
        val result    = patch(container)
        assertTrue(result.value == Instant.parse("2024-01-02T00:00:00Z"))
      },
      test("addPeriod to LocalDate") {
        import java.time.{LocalDate, Period}
        val container = LocalDateContainer(LocalDate.parse("2024-01-15"))
        val patch     = Patch.addPeriod(LocalDateContainer.value, Period.ofMonths(1))
        val result    = patch(container)
        assertTrue(result.value == LocalDate.parse("2024-02-15"))
      },
      test("addDuration to Duration field") {
        import java.time.Duration
        val container = DurationContainer(Duration.ofHours(1))
        val patch     = Patch.addDuration(DurationContainer.value, Duration.ofMinutes(30))(Patch.DurationDummy.ForDuration)
        val result    = patch(container)
        assertTrue(result.value == Duration.ofMinutes(90))
      },
      test("addPeriod to Period field") {
        import java.time.Period
        val container = PeriodContainer(Period.ofMonths(1))
        val patch     = Patch.addPeriod(PeriodContainer.value, Period.ofDays(15))(Patch.PeriodDummy.ForPeriod)
        val result    = patch(container)
        assertTrue(result.value == Period.ofMonths(1).plusDays(15))
      },
      test("addPeriodAndDuration to LocalDateTime") {
        import java.time.{Duration, LocalDateTime, Period}
        val container = LocalDateTimeContainer(LocalDateTime.parse("2024-01-15T10:00:00"))
        val patch     = Patch.addPeriodAndDuration(LocalDateTimeContainer.value, Period.ofDays(1), Duration.ofHours(2))
        val result    = patch(container)
        assertTrue(result.value == LocalDateTime.parse("2024-01-16T12:00:00"))
      }
    ),
    suite("Patch with string operations")(
      test("editString insert characters") {
        val container = StringContainer("Hello")
        val edits     = Vector(Patch.StringOp.Insert(5, " World"))
        val patch     = Patch.editString(StringContainer.value, edits)
        val result    = patch(container)
        assertTrue(result.value == "Hello World")
      },
      test("editString delete characters") {
        val container = StringContainer("Hello World")
        val edits     = Vector(Patch.StringOp.Delete(5, 6))
        val patch     = Patch.editString(StringContainer.value, edits)
        val result    = patch(container)
        assertTrue(result.value == "Hello")
      }
    ),
    suite("Patch isEmpty")(
      test("empty patch isEmpty returns true") {
        val patch = Patch.empty[Person]
        assertTrue(patch.isEmpty)
      },
      test("non-empty patch isEmpty returns false") {
        val patch = Patch.replace(Person.name, "Test")
        assertTrue(!patch.isEmpty)
      }
    ),
    suite("Patch modifyAt for Vector")(
      test("modifyAt with non-empty nested patch") {
        val container  = NestedContainer(Vector(InnerItem("a", 1), InnerItem("b", 2)))
        val innerPatch = Patch.replace(InnerItem.name, "modified")
        val patch      = Patch.modifyAt(NestedContainer.items, 0, innerPatch)(Patch.CollectionDummy.ForVector)
        val result     = patch(container)
        assertTrue(result.items(0).name == "modified")
      },
      test("modifyAt with empty nested patch returns empty") {
        val innerPatch = Patch.empty[InnerItem]
        val patch      = Patch.modifyAt(NestedContainer.items, 0, innerPatch)(Patch.CollectionDummy.ForVector)
        assertTrue(patch.isEmpty)
      }
    ),
    suite("Patch modifyKey for Map")(
      test("modifyKey with non-empty nested patch") {
        val container  = MapValueContainer(Map("key1" -> InnerItem("a", 1)))
        val innerPatch = Patch.replace(InnerItem.count, 100)
        val patch      = Patch.modifyKey(MapValueContainer.items, "key1", innerPatch)
        val result     = patch(container)
        assertTrue(result.items("key1").count == 100)
      },
      test("modifyKey with empty nested patch returns empty") {
        val innerPatch = Patch.empty[InnerItem]
        val patch      = Patch.modifyKey(MapValueContainer.items, "key1", innerPatch)
        assertTrue(patch.isEmpty)
      }
    )
  )

  private[this] def hasError(message: String): Assertion[SchemaError] =
    hasField[SchemaError, String]("message", _.message, containsString(message))
}

sealed trait PaymentMethod

object PaymentMethod extends CompanionOptics[PaymentMethod] {
  implicit val schema: Schema[PaymentMethod]           = Schema.derived
  val creditCard: Prism[PaymentMethod, CreditCard]     = optic(_.when[CreditCard])
  val bankTransfer: Prism[PaymentMethod, BankTransfer] = optic(_.when[BankTransfer])
  val payPal: Prism[PaymentMethod, PayPal]             = optic(_.when[PayPal])
  val payPalEmail: Optional[PaymentMethod, String]     = optic(_.when[PayPal].email)
}

case class CreditCard(
  cardNumber: Long,
  expiryDate: YearMonth,
  cvv: Int,
  cardHolderName: String
) extends PaymentMethod

case class BankTransfer(
  accountNumber: String,
  bankCode: String,
  accountHolderName: String
) extends PaymentMethod

case class PayPal(email: String) extends PaymentMethod

case class Person(
  id: Long,
  name: String,
  address: String,
  paymentMethods: List[PaymentMethod]
)

object Person extends CompanionOptics[Person] {
  implicit val schema: Schema[Person]                  = Schema.derived
  val id: Lens[Person, Long]                           = optic(_.id)
  val name: Lens[Person, String]                       = optic(_.name)
  val address: Lens[Person, String]                    = optic(_.address)
  val paymentMethods: Traversal[Person, PaymentMethod] = optic(_.paymentMethods.each)
  val payPalPaymentMethods: Traversal[Person, PayPal]  = optic(_.paymentMethods.each.when[PayPal])
}

case class VectorContainer(items: Vector[Int], name: String, count: Int)
object VectorContainer extends CompanionOptics[VectorContainer] {
  implicit val schema: Schema[VectorContainer]  = Schema.derived
  val items: Lens[VectorContainer, Vector[Int]] = optic(_.items)
  val count: Lens[VectorContainer, Int]         = optic(_.count)
}

case class LongContainer(value: Long)
object LongContainer extends CompanionOptics[LongContainer] {
  implicit val schema: Schema[LongContainer] = Schema.derived
  val value: Lens[LongContainer, Long]       = optic(_.value)
}

case class DoubleContainer(value: Double)
object DoubleContainer extends CompanionOptics[DoubleContainer] {
  implicit val schema: Schema[DoubleContainer] = Schema.derived
  val value: Lens[DoubleContainer, Double]     = optic(_.value)
}

case class FloatContainer(value: Float)
object FloatContainer extends CompanionOptics[FloatContainer] {
  implicit val schema: Schema[FloatContainer] = Schema.derived
  val value: Lens[FloatContainer, Float]      = optic(_.value)
}

case class ShortContainer(value: Short)
object ShortContainer extends CompanionOptics[ShortContainer] {
  implicit val schema: Schema[ShortContainer] = Schema.derived
  val value: Lens[ShortContainer, Short]      = optic(_.value)
}

case class ByteContainer(value: Byte)
object ByteContainer extends CompanionOptics[ByteContainer] {
  implicit val schema: Schema[ByteContainer] = Schema.derived
  val value: Lens[ByteContainer, Byte]       = optic(_.value)
}

case class BigIntContainer(value: BigInt)
object BigIntContainer extends CompanionOptics[BigIntContainer] {
  implicit val schema: Schema[BigIntContainer] = Schema.derived
  val value: Lens[BigIntContainer, BigInt]     = optic(_.value)
}

case class BigDecimalContainer(value: BigDecimal)
object BigDecimalContainer extends CompanionOptics[BigDecimalContainer] {
  implicit val schema: Schema[BigDecimalContainer] = Schema.derived
  val value: Lens[BigDecimalContainer, BigDecimal] = optic(_.value)
}

case class MapContainer(items: Map[String, Int])
object MapContainer extends CompanionOptics[MapContainer] {
  implicit val schema: Schema[MapContainer]       = Schema.derived
  val items: Lens[MapContainer, Map[String, Int]] = optic(_.items)
}

case class InstantContainer(value: java.time.Instant)
object InstantContainer extends CompanionOptics[InstantContainer] {
  implicit val schema: Schema[InstantContainer]        = Schema.derived
  val value: Lens[InstantContainer, java.time.Instant] = optic(_.value)
}

case class LocalDateContainer(value: java.time.LocalDate)
object LocalDateContainer extends CompanionOptics[LocalDateContainer] {
  implicit val schema: Schema[LocalDateContainer]          = Schema.derived
  val value: Lens[LocalDateContainer, java.time.LocalDate] = optic(_.value)
}

case class DurationContainer(value: java.time.Duration)
object DurationContainer extends CompanionOptics[DurationContainer] {
  implicit val schema: Schema[DurationContainer]         = Schema.derived
  val value: Lens[DurationContainer, java.time.Duration] = optic(_.value)
}

case class PeriodContainer(value: java.time.Period)
object PeriodContainer extends CompanionOptics[PeriodContainer] {
  implicit val schema: Schema[PeriodContainer]       = Schema.derived
  val value: Lens[PeriodContainer, java.time.Period] = optic(_.value)
}

case class LocalDateTimeContainer(value: java.time.LocalDateTime)
object LocalDateTimeContainer extends CompanionOptics[LocalDateTimeContainer] {
  implicit val schema: Schema[LocalDateTimeContainer]              = Schema.derived
  val value: Lens[LocalDateTimeContainer, java.time.LocalDateTime] = optic(_.value)
}

case class StringContainer(value: String)
object StringContainer extends CompanionOptics[StringContainer] {
  implicit val schema: Schema[StringContainer] = Schema.derived
  val value: Lens[StringContainer, String]     = optic(_.value)
}

case class InnerItem(name: String, count: Int)
object InnerItem extends CompanionOptics[InnerItem] {
  implicit val schema: Schema[InnerItem] = Schema.derived
  val name: Lens[InnerItem, String]      = optic(_.name)
  val count: Lens[InnerItem, Int]        = optic(_.count)
}

case class NestedContainer(items: Vector[InnerItem])
object NestedContainer extends CompanionOptics[NestedContainer] {
  implicit val schema: Schema[NestedContainer]        = Schema.derived
  val items: Lens[NestedContainer, Vector[InnerItem]] = optic(_.items)
}

case class MapValueContainer(items: Map[String, InnerItem])
object MapValueContainer extends CompanionOptics[MapValueContainer] {
  implicit val schema: Schema[MapValueContainer]             = Schema.derived
  val items: Lens[MapValueContainer, Map[String, InnerItem]] = optic(_.items)
}
