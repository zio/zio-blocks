package zio.blocks.schema

import zio.test._
import zio.test.Assertion._
import java.time.YearMonth

object PatchSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("PatchSpec")(
    test("replace a field with a new value") {
      val person1 = Person(12345678901L, "John", "123 Main St", Nil)
      val patch   = Patch.set(Person.name, "Piero")
      val person2 = person1.copy(name = "Piero")

      assert(patch(person1))(isRight(equalTo(person2)))
    },
    test("replace a case with a new value") {
      val paymentMethod1 = PayPal("x@gmail.com")
      val patch          = Patch.set(PaymentMethod.payPal, PayPal("y@gmail.com"))
      val paymentMethod2 = PayPal("y@gmail.com")

      assert(patch(paymentMethod1))(isRight(equalTo(paymentMethod2)))
    },
    test("replace a case field with a new value") {
      val paymentMethod1 = PayPal("x@gmail.com")
      val patch          = Patch.set(PaymentMethod.payPalEmail, "y@gmail.com")
      val paymentMethod2 = PayPal("y@gmail.com")

      assert(patch(paymentMethod1))(isRight(equalTo(paymentMethod2)))
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
      val patch   = Patch.set(Person.payPalPaymentMethods, PayPal("y@gmail.com"))
      val person2 = person1.copy(paymentMethods =
        List(
          PayPal("y@gmail.com"),
          CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
        )
      )

      assert(patch(person1))(isRight(equalTo(person2)))
    },
    test("don't replace non-matching case with a new value (Strict Mode)") {
      val person1        = Person(12345678901L, "John", "123 Main St", Nil)
      val paymentMethod1 = CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")

      val patch1 = Patch.set(PaymentMethod.payPal, PayPal("y@gmail.com"))
      val patch2 = Patch.set(PaymentMethod.payPalEmail, "y@gmail.com")
      val patch3 = Patch.set(Person.paymentMethods(PaymentMethod.payPalEmail), "y@gmail.com")

      // Prism semantics: setting a value on a missing case is a no-op (Right(original)).
      assert(patch1(paymentMethod1))(isRight(equalTo(paymentMethod1))) &&
      assert(patch2(paymentMethod1))(isRight(equalTo(paymentMethod1))) &&
      assert(patch3(person1))(isRight(equalTo(person1)))
    },
    test("combine two patches") {
      val person1 = Person(12345678901L, "John", "123 Main St", Nil)
      val patch   = Patch.set(Person.name, "Piero") ++ Patch.set(Person.address, "321 Main St")
      val person2 = person1.copy(name = "Piero", address = "321 Main St")

      assert(patch(person1))(isRight(equalTo(person2)))
    }
  )
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

object CreditCard {
  implicit val schema: Schema[CreditCard] = Schema.derived
}

case class BankTransfer(
  accountNumber: String,
  bankCode: String,
  accountHolderName: String
) extends PaymentMethod

object BankTransfer {
  implicit val schema: Schema[BankTransfer] = Schema.derived
}

case class PayPal(email: String) extends PaymentMethod

object PayPal {
  implicit val schema: Schema[PayPal] = Schema.derived
}

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
