package zio.blocks.schema

import zio.Scope
import zio.test._
import zio.test.Assertion._
import java.time.YearMonth

object PatchSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("PatchSpec")(
    test("replace a field with a new value") {
      val person1 = Person(12345678901L, "John", "123 Main St", Nil)
      val patch   = Patch.replace(Person.name, "Piero")
      val person2 = person1.copy(name = "Piero")
      assert(patch(person1))(equalTo(person2)) &&
      assert(patch.applyOption(person1))(isSome(equalTo(person2))) &&
      assert(patch.applyOrFail(person1))(isRight(equalTo(person2)))
    },
    test("replace a case with a new value") {
      val paymentMethod1 = PayPal("x@gmail.com")
      val patch          = Patch.replace(PaymentMethod.payPal, PayPal("y@gmail.com"))
      val paymentMethod2 = PayPal("y@gmail.com")
      assert(patch(paymentMethod1))(equalTo(paymentMethod2)) &&
      assert(patch.applyOption(paymentMethod1))(isSome(equalTo(paymentMethod2))) &&
      assert(patch.applyOrFail(paymentMethod1))(isRight(equalTo(paymentMethod2)))
    },
    test("replace a case field with a new value") {
      val paymentMathod1 = PayPal("x@gmail.com")
      val patch          = Patch.replace(PaymentMethod.payPalEmail, "y@gmail.com")
      val paymentMethod2 = PayPal("y@gmail.com")
      assert(patch(paymentMathod1))(equalTo(paymentMethod2)) &&
      assert(patch.applyOption(paymentMathod1))(isSome(equalTo(paymentMethod2))) &&
      assert(patch.applyOrFail(paymentMathod1))(isRight(equalTo(paymentMethod2)))
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
      val patch = Patch.replace(Person.payPalPaymentMethods, PayPal("y@gmail.com"))
      val person2 = person1.copy(paymentMethods =
        List(
          PayPal("y@gmail.com"),
          CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
        )
      )
      assert(patch(person1))(equalTo(person2)) &&
      assert(patch.applyOption(person1))(isSome(equalTo(person2))) &&
      assert(patch.applyOrFail(person1))(isRight(equalTo(person2)))
    },
    test("don't replace non-matching case with a new value") {
      val person1        = Person(12345678901L, "John", "123 Main St", Nil)
      val paymentMethod1 = CreditCard(1234567812345678L, YearMonth.parse("2030-12"), 123, "John")
      val patch1         = Patch.replace(PaymentMethod.payPal, PayPal("y@gmail.com"))
      val patch2         = Patch.replace(PaymentMethod.payPalEmail, "y@gmail.com")
      val patch3         = Patch.replace(Person.paymentMethods(PaymentMethod.payPalEmail), "y@gmail.com")
      assert(patch1(paymentMethod1))(equalTo(paymentMethod1)) &&
      assert(patch1.applyOption(paymentMethod1))(isNone) &&
      assert(patch1.applyOrFail(paymentMethod1))(
        isLeft(
          hasField[OpticCheck, String](
            "message",
            _.message,
            containsString(
              "During attempted access at .when[PayPal], encountered an unexpected case at .when[PayPal]: expected PayPal, but got CreditCard"
            )
          )
        )
      ) &&
      assert(patch2(paymentMethod1))(equalTo(paymentMethod1)) &&
      assert(patch2.applyOption(paymentMethod1))(isNone) &&
      assert(patch2.applyOrFail(paymentMethod1))(
        isLeft(
          hasField[OpticCheck, String](
            "message",
            _.message,
            containsString(
              "During attempted access at .when[PayPal].email, encountered an unexpected case at .when[PayPal]: expected PayPal, but got CreditCard"
            )
          )
        )
      ) &&
      assert(patch3.applyOption(person1))(isNone) &&
      assert(patch3.applyOrFail(person1))(
        isLeft(
          hasField[OpticCheck, String](
            "message",
            _.message,
            containsString(
              "During attempted access at .paymentMethods.each.when[PayPal].email, encountered an empty sequence at .paymentMethods.each"
            )
          )
        )
      )
    },
    test("combine two patches") {
      val person1 = Person(12345678901L, "John", "123 Main St", Nil)
      val patch   = Patch.replace(Person.name, "Piero") ++ Patch.replace(Person.address, "321 Main St")
      val parson2 = person1.copy(name = "Piero", address = "321 Main St")
      assert(patch(person1))(equalTo(parson2)) &&
      assert(patch.applyOption(person1))(isSome(equalTo(parson2))) &&
      assert(patch.applyOrFail(person1))(isRight(equalTo(parson2)))
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
