package zio.blocks.schema.tostring

import zio.blocks.schema._
import zio.test._
import zio.test.TestAspect.exceptNative

object OpticTypesSpec extends ZIOSpecDefault {

  case class Address(street: String, city: String)
  case class Person(name: String, age: Int, address: Address)

  sealed trait PaymentMethod
  case class CreditCard(number: String, cvv: String) extends PaymentMethod

  case class Order(id: String, items: List[String], metadata: Map[String, String])

  sealed trait Result[+A]
  case class Success[A](data: A) extends Result[A]

  case class ItemWithMeta(name: String, metadata: Map[String, String])
  case class OrderWithItemMeta(items: List[ItemWithMeta])

  implicit val addressSchema: Schema[Address]               = Schema.derived
  implicit val creditCardSchema: Schema[CreditCard]         = Schema.derived
  implicit val paymentMethodSchema: Schema[PaymentMethod]   = Schema.derived
  implicit val personSchema: Schema[Person]                 = Schema.derived
  implicit val orderSchema: Schema[Order]                   = Schema.derived
  implicit val successStringSchema: Schema[Success[String]] = Schema.derived
  implicit def resultSchema[A: Schema]: Schema[Result[A]]   = Schema.derived

  def spec = suite("Optic Types toString")(
    suite("Lens")(
      test("renders simple field access") {
        val personRecord = Schema[Person].reflect.asRecord.get
        val nameTerm     = personRecord.fields.find(_.name == "name").get.asInstanceOf[Term.Bound[Person, String]]
        val lens         = Lens(personRecord, nameTerm)
        assertTrue(lens.toString == "Lens(_.name)")
      },
      test("renders nested field access") {
        val personRecord = Schema[Person].reflect.asRecord.get
        val addressTerm  = personRecord.fields.find(_.name == "address").get.asInstanceOf[Term.Bound[Person, Address]]
        val addressLens  = Lens[Person, Address](personRecord, addressTerm)

        val addressRecord = Schema[Address].reflect.asRecord.get
        val streetTerm    = addressRecord.fields.find(_.name == "street").get.asInstanceOf[Term.Bound[Address, String]]
        val streetLens    = Lens[Address, String](addressRecord, streetTerm)

        val lens = Lens(addressLens, streetLens)
        assertTrue(lens.toString == "Lens(_.address.street)")
      },
      test("renders deeply nested fields") {
        val personRecord = Schema[Person].reflect.asRecord.get
        val addressTerm  = personRecord.fields.find(_.name == "address").get.asInstanceOf[Term.Bound[Person, Address]]
        val addressLens  = Lens[Person, Address](personRecord, addressTerm)

        val addressRecord = Schema[Address].reflect.asRecord.get
        val cityTerm      = addressRecord.fields.find(_.name == "city").get.asInstanceOf[Term.Bound[Address, String]]
        val cityLens      = Lens[Address, String](addressRecord, cityTerm)

        val lens = Lens(addressLens, cityLens)
        assertTrue(lens.toString == "Lens(_.address.city)")
      }
    ),
    suite("Prism")(
      test("renders simple case access") {
        val paymentVariant = Schema[PaymentMethod].reflect.asVariant.get
        val creditCardCase =
          paymentVariant.cases.find(_.name == "CreditCard").get.asInstanceOf[Term.Bound[PaymentMethod, CreditCard]]
        val prism = Prism(paymentVariant, creditCardCase)
        assertTrue(prism.toString == "Prism(_.when[CreditCard])")
      },
      test("renders case with field access") {
        val paymentVariant = Schema[PaymentMethod].reflect.asVariant.get
        val creditCardCase =
          paymentVariant.cases.find(_.name == "CreditCard").get.asInstanceOf[Term.Bound[PaymentMethod, CreditCard]]
        val prism = Prism[PaymentMethod, CreditCard](paymentVariant, creditCardCase)

        val creditCardRecord = Schema[CreditCard].reflect.asRecord.get
        val numberTerm       =
          creditCardRecord.fields.find(_.name == "number").get.asInstanceOf[Term.Bound[CreditCard, String]]
        val lens = Lens[CreditCard, String](creditCardRecord, numberTerm)

        val optional = Optional(prism, lens)
        assertTrue(optional.toString == "Optional(_.when[CreditCard].number)")
      }
    ),
    suite("Optional")(
      test("renders variant case with nested field") {
        val resultVariant = Schema[Result[String]].reflect.asVariant.get
        val successCase   =
          resultVariant.cases.find(_.name == "Success").get.asInstanceOf[Term.Bound[Result[String], Success[String]]]
        val prism = Prism[Result[String], Success[String]](resultVariant, successCase)

        val successRecord = Schema[Success[String]].reflect.asRecord.get
        val dataTerm      = successRecord.fields.find(_.name == "data").get.asInstanceOf[Term.Bound[Success[String], String]]
        val lens          = Lens[Success[String], String](successRecord, dataTerm)

        val optional = Optional(prism, lens)
        assertTrue(optional.toString == "Optional(_.when[Success].data)")
      }
    ),
    suite("Traversal")(
      test("renders sequence traversal with each") {
        val orderRecord    = Schema[Order].reflect.asRecord.get
        val itemsTerm      = orderRecord.fields.find(_.name == "items").get.asInstanceOf[Term.Bound[Order, List[String]]]
        val itemsLens      = Lens[Order, List[String]](orderRecord, itemsTerm)
        val itemsTraversal = Traversal.listValues(Schema[String].reflect)
        val traversal      = Traversal(itemsLens, itemsTraversal)
        assertTrue(traversal.toString == "Traversal(_.items.each)")
      },
      test("renders map key traversal") {
        val orderRecord  = Schema[Order].reflect.asRecord.get
        val metadataTerm =
          orderRecord.fields.find(_.name == "metadata").get.asInstanceOf[Term.Bound[Order, Map[String, String]]]
        val metadataLens  = Lens[Order, Map[String, String]](orderRecord, metadataTerm)
        val keysTraversal = Traversal.mapKeys(Schema[Map[String, String]].reflect.asMap.get)
        val traversal     = Traversal(metadataLens, keysTraversal)
        assertTrue(traversal.toString == "Traversal(_.metadata.eachKey)")
      },
      test("renders map value traversal") {
        val orderRecord  = Schema[Order].reflect.asRecord.get
        val metadataTerm =
          orderRecord.fields.find(_.name == "metadata").get.asInstanceOf[Term.Bound[Order, Map[String, String]]]
        val metadataLens    = Lens[Order, Map[String, String]](orderRecord, metadataTerm)
        val valuesTraversal = Traversal.mapValues(Schema[Map[String, String]].reflect.asMap.get)
        val traversal       = Traversal(metadataLens, valuesTraversal)
        assertTrue(traversal.toString == "Traversal(_.metadata.eachValue)")
      }
    ),

    suite("Deep Compositions")(
      test("renders 4-level lens composition") {
        // Person -> Address -> City -> Name (hypothetically)
        val personRecord = Schema[Person].reflect.asRecord.get
        val addressTerm  = personRecord.fields.find(_.name == "address").get.asInstanceOf[Term.Bound[Person, Address]]
        val addressLens  = Lens[Person, Address](personRecord, addressTerm)

        val addressRecord = Schema[Address].reflect.asRecord.get
        val cityTerm      = addressRecord.fields.find(_.name == "city").get.asInstanceOf[Term.Bound[Address, String]]
        val cityLens      = Lens[Address, String](addressRecord, cityTerm)

        // Compose: Person -> Address -> City
        val composedLens = Lens(addressLens, cityLens)

        assertTrue(composedLens.toString == "Lens(_.address.city)")
      },
      test("renders optional with deeply nested field access") {
        // Create deeper nesting: PaymentMethod -> CreditCard -> Number
        val paymentVariant = Schema[PaymentMethod].reflect.asVariant.get
        val creditCardCase =
          paymentVariant.cases.find(_.name == "CreditCard").get.asInstanceOf[Term.Bound[PaymentMethod, CreditCard]]
        val prism = Prism[PaymentMethod, CreditCard](paymentVariant, creditCardCase)

        val creditCardRecord = Schema[CreditCard].reflect.asRecord.get
        val numberTerm       =
          creditCardRecord.fields.find(_.name == "number").get.asInstanceOf[Term.Bound[CreditCard, String]]
        val numberLens = Lens[CreditCard, String](creditCardRecord, numberTerm)

        val cvvTerm =
          creditCardRecord.fields.find(_.name == "cvv").get.asInstanceOf[Term.Bound[CreditCard, String]]
        val cvvLens = Lens[CreditCard, String](creditCardRecord, cvvTerm)

        val optional1 = Optional(prism, numberLens)
        val optional2 = Optional(prism, cvvLens)

        assertTrue(
          optional1.toString == "Optional(_.when[CreditCard].number)",
          optional2.toString == "Optional(_.when[CreditCard].cvv)"
        )
      },
      test("renders traversal with nested each") {
        // Hypothetical: Order -> Items (List) -> each -> Metadata (Map) -> eachValue

        implicit val itemSchema: Schema[ItemWithMeta]       = Schema.derived
        implicit val orderSchema: Schema[OrderWithItemMeta] = Schema.derived

        val orderRecord = Schema[OrderWithItemMeta].reflect.asRecord.get
        val itemsTerm   =
          orderRecord.fields.find(_.name == "items").get.asInstanceOf[Term.Bound[OrderWithItemMeta, List[ItemWithMeta]]]
        val itemsLens = Lens[OrderWithItemMeta, List[ItemWithMeta]](orderRecord, itemsTerm)

        val itemsTraversal = Traversal.listValues(Schema[ItemWithMeta].reflect)

        val traversal = Traversal(itemsLens, itemsTraversal)
        assertTrue(traversal.toString == "Traversal(_.items.each)")
      },
      test("renders very long optic chain") {
        // Person -> Address -> Street
        val personRecord = Schema[Person].reflect.asRecord.get
        val addressTerm  = personRecord.fields.find(_.name == "address").get.asInstanceOf[Term.Bound[Person, Address]]
        val addressLens  = Lens[Person, Address](personRecord, addressTerm)

        val addressRecord = Schema[Address].reflect.asRecord.get
        val streetTerm    = addressRecord.fields.find(_.name == "street").get.asInstanceOf[Term.Bound[Address, String]]
        val streetLens    = Lens[Address, String](addressRecord, streetTerm)

        val composedLens = Lens(addressLens, streetLens)

        assertTrue(composedLens.toString == "Lens(_.address.street)")
      }
    )
  ) @@ exceptNative
}
