package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationBuilderSyntax._
import zio.test._

object SelectorMacrosSpec extends SchemaBaseSpec {

  final case class Address(streetNumber: Int)
  final case class Person(addresses: List[Address])

  sealed trait PaymentMethod
  final case class CreditCard(number: String) extends PaymentMethod
  final case class WireTransfer(account: String) extends PaymentMethod
  final case class Buyer(payment: PaymentMethod)

  final case class Box(value: Int)
  final case class Holder(box: Box)

  final case class SeqHolder(items: Vector[Int])

  final case class MapHolder(map: Map[String, Int])

  def spec: Spec[TestEnvironment, Any] = suite("SelectorMacrosSpec")(
    test("field access") {
      val optic = MigrationBuilder.paths.from[Address, Int](_.streetNumber)
      assertTrue(optic == DynamicOptic.root.field("streetNumber"))
    },
    test(".each") {
      val optic = MigrationBuilder.paths.from[Person, Int](_.addresses.each.streetNumber)
      assertTrue(optic == DynamicOptic.root.field("addresses").elements.field("streetNumber"))
    },
    test(".when[T]") {
      val optic = MigrationBuilder.paths.from[Buyer, String](_.payment.when[CreditCard].number)
      assertTrue(optic == DynamicOptic.root.field("payment").caseOf("CreditCard").field("number"))
    },
    test(".wrapped[T]") {
      val optic = MigrationBuilder.paths.from[Holder, Int](_.box.wrapped[Int])
      assertTrue(optic == DynamicOptic.root.field("box").wrapped)
    },
    test(".at(index)") {
      val optic = MigrationBuilder.paths.from[SeqHolder, Int](_.items.at(1))
      assertTrue(optic == DynamicOptic.root.field("items").at(1))
    },
    test(".atIndices(indices*)") {
      val optic = MigrationBuilder.paths.from[SeqHolder, Int](_.items.atIndices(0, 2))
      assertTrue(optic == DynamicOptic.root.field("items").atIndices(0, 2))
    },
    test(".atKey(key)") {
      val optic = MigrationBuilder.paths.from[MapHolder, Int](_.map.atKey("a"))
      assertTrue(optic == DynamicOptic.root.field("map").atKey("a"))
    },
    test(".atKeys(keys*)") {
      val optic = MigrationBuilder.paths.from[MapHolder, Int](_.map.atKeys("a", "b"))
      assertTrue(optic == DynamicOptic.root.field("map").atKeys("a", "b"))
    },
    test(".eachKey") {
      val optic = MigrationBuilder.paths.from[MapHolder, String](_.map.eachKey)
      assertTrue(optic == DynamicOptic.root.field("map").mapKeys)
    },
    test(".eachValue") {
      val optic = MigrationBuilder.paths.from[MapHolder, Int](_.map.eachValue)
      assertTrue(optic == DynamicOptic.root.field("map").mapValues)
    }
  )
}
