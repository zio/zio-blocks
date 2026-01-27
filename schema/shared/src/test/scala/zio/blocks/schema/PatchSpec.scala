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
