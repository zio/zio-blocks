package zio.blocks.schema

import zio.blocks.schema.patch.{DynamicPatch, Patch, PatchMode}
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
    suite("Patch[S] failure branches")(
      test("apply returns original on internal failure") {
        case class TestPerson(name: String, age: Int)
        implicit val testPersonSchema: Schema[TestPerson] = Schema.derived

        val person = TestPerson("John", 30)
        val dp     = DynamicPatch(
          Vector(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              Patch.Operation.Set(DynamicValue.int(99))
            )
          )
        )
        val patch  = new Patch[TestPerson](dp, testPersonSchema)
        val result = patch(person)
        assertTrue(result == person)
      },
      test("applyOption returns None on failure") {
        case class TestPerson(name: String, age: Int)
        implicit val testPersonSchema: Schema[TestPerson] = Schema.derived

        val person = TestPerson("John", 30)
        val dp     = DynamicPatch(
          Vector(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              Patch.Operation.Set(DynamicValue.int(99))
            )
          )
        )
        val patch  = new Patch[TestPerson](dp, testPersonSchema)
        val result = patch.applyOption(person)
        assertTrue(result.isEmpty)
      },
      test("apply with mode returns Left on failure") {
        case class TestPerson(name: String, age: Int)
        implicit val testPersonSchema: Schema[TestPerson] = Schema.derived

        val person = TestPerson("John", 30)
        val dp     = DynamicPatch(
          Vector(
            DynamicPatch.DynamicPatchOp(
              DynamicOptic.root.field("nonexistent"),
              Patch.Operation.Set(DynamicValue.int(99))
            )
          )
        )
        val patch  = new Patch[TestPerson](dp, testPersonSchema)
        val result = patch(person, PatchMode.Strict)
        assertTrue(result.isLeft)
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
