package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DerivedOpticsSpec extends ZIOSpecDefault {

  case class Person(name: String, age: Int, email: Option[String])
  object Person extends DerivedOptics[Person] {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zipCode: Int)
  object Address extends DerivedOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Employee(id: Long, person: Person, address: Address)
  object Employee extends DerivedOptics[Employee] {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  sealed trait PaymentMethod
  object PaymentMethod extends DerivedOptics[PaymentMethod] {
    implicit val schema: Schema[PaymentMethod] = Schema.derived
  }
  case class CreditCard(number: String, expiry: String) extends PaymentMethod
  case class BankTransfer(iban: String, bic: String)    extends PaymentMethod
  case object Cash                                      extends PaymentMethod

  case class Conflicting(optics: String, name: String, value: Int)
  object Conflicting extends DerivedOptics_[Conflicting] {
    implicit val schema: Schema[Conflicting] = Schema.derived
  }

  def spec = suite("DerivedOpticsSpec")(
    suite("Lens")(
      test("get field value") {
        val person = Person("Alice", 30, Some("alice@example.com"))
        assert(Person.optics.name.get(person))(equalTo("Alice")) &&
        assert(Person.optics.age.get(person))(equalTo(30)) &&
        assert(Person.optics.email.get(person))(equalTo(Some("alice@example.com")))
      },
      test("replace field value (immutable)") {
        val person  = Person("Alice", 30, Some("alice@example.com"))
        val updated = Person.optics.name.replace(person, "Bob")

        assert(updated.name)(equalTo("Bob")) &&
        assert(updated.age)(equalTo(30)) &&
        assert(person.name)(equalTo("Alice"))
      },
      test("modify field value") {
        val person = Person("Alice", 30, None)
        val older  = Person.optics.age.modify(person, _ + 1)
        assert(older.age)(equalTo(31))
      }
    ),
    suite("Prism")(
      test("getOption success (matching variant)") {
        val payment: PaymentMethod = CreditCard("1234", "12/25")
        val result                 = PaymentMethod.optics.creditCard.getOption(payment)
        assert(result)(equalTo(Some(CreditCard("1234", "12/25"))))
      },
      test("getOption failure (non-matching variant)") {
        val payment: PaymentMethod = BankTransfer("IBAN", "BIC")
        val result                 = PaymentMethod.optics.creditCard.getOption(payment)
        assert(result)(isNone)
      },
      test("reverseGet") {
        val cc                     = CreditCard("9999", "01/30")
        val payment: PaymentMethod = PaymentMethod.optics.creditCard.reverseGet(cc)
        assert(payment)(equalTo(cc))
      },
      test("naming convention (camelCase)") {
        val cash: PaymentMethod = Cash
        assert(PaymentMethod.optics.cash.getOption(cash))(isSome(equalTo(Cash)))
      }
    ),
    suite("Edge Cases")(
      test("Underscore prefix (DerivedOptics_)") {
        val conf = Conflicting("opticsVal", "nameVal", 42)
        assert(Conflicting.optics._optics.get(conf))(equalTo("opticsVal")) &&
        assert(Conflicting.optics._name.get(conf))(equalTo("nameVal"))
      },
      test("Caching (Identity)") {
        val o1 = Person.optics
        val o2 = Person.optics
        assert(o1.asInstanceOf[AnyRef] eq o2.asInstanceOf[AnyRef])(isTrue)
      },
      test("Lens Composition") {
        val employee = Employee(1L, Person("Alice", 30, None), Address("St", "City", 1))
        val pLens    = Employee.optics.person
        val nLens    = Person.optics.name
        val name     = nLens.get(pLens.get(employee))
        assert(name)(equalTo("Alice"))
      }
    )
  )
}
