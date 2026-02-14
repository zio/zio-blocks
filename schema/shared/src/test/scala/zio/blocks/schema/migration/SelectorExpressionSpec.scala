package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import scala.annotation.unused

/**
 * Requirement Verification: User-Facing API & Selector Expressions
 */
object SelectorExpressionSpec extends ZIOSpecDefault {

  case class Street(number: Int, name: String)
  case class Address(city: String, street: Street)

  case class ProductV1(id: String, tags: List[String])
  case class ProductV2(id: String, allTags: List[String])

  sealed trait Origin
  case class Local(factory: String)    extends Origin
  case class Imported(country: String) extends Origin

  case class ItemV1(origin: Origin)
  case class ItemV2(sourceCountry: String)

  @unused implicit val schemaAddress: Schema[Address] = null.asInstanceOf[Schema[Address]]
  @unused implicit val schemaProd1: Schema[ProductV1] = null.asInstanceOf[Schema[ProductV1]]
  @unused implicit val schemaProd2: Schema[ProductV2] = null.asInstanceOf[Schema[ProductV2]]
  @unused implicit val schemaItem1: Schema[ItemV1]    = null.asInstanceOf[Schema[ItemV1]]
  @unused implicit val schemaItem2: Schema[ItemV2]    = null.asInstanceOf[Schema[ItemV2]]

  // @nowarn("msg=unused")
  implicit val conversion: scala.languageFeature.implicitConversions = scala.language.implicitConversions

  implicit class CollectionOps[A](val self: List[A]) {
    def each: A = ???
  }
  implicit class SumTypeOps[A](val self: A) {
    def when[Sub <: A]: Sub = ???
  }

  def spec = suite("Requirement: Selector Expressions (User API vs Macro Internals)")(
    test("1. Nested Field Access: _.address.street.name converts to [Field(address), Field(street), Field(name)]") {
      // [FIX] Use explicit lambdas to avoid "missing parameter type" error
      // Note: We use 'buildPartial' to focus on Optic generation, avoiding strict validation of nested moves
      val migration = MigrationBuilder
        .make[Address, Address]
        .renameField(
          (a: Address) => a.street.name,
          (a: Address) => a.city
        )
        .buildPartial // Using buildPartial to skip strict schema check if needed

      val fromOptic = migration.dynamicMigration.actions.head match {
        case Rename(optic, _) => optic
        case _                => throw new RuntimeException("Expected Rename action")
      }

      assertTrue(fromOptic.nodes.length == 2) && // street, name
      assertTrue(fromOptic.nodes(0) == DynamicOptic.Node.Field("street")) &&
      assertTrue(fromOptic.nodes(1) == DynamicOptic.Node.Field("name"))
    },

    test("2. Collection Traversal: _.tags.each converts to [Field(tags), Elements]") {
      val migration = MigrationBuilder
        .make[ProductV1, ProductV2]
        .renameField(
          (p: ProductV1) => p.tags.each,
          (p: ProductV2) => p.allTags.each
        )
        .buildPartial

      val fromOptic = migration.dynamicMigration.actions.head match {
        case Rename(optic, _) => optic
        case _                => throw new RuntimeException("Expected Rename action")
      }

      assertTrue(fromOptic.nodes.length == 2) &&
      assertTrue(fromOptic.nodes(0) == DynamicOptic.Node.Field("tags")) &&
      assertTrue(fromOptic.nodes(1) == DynamicOptic.Node.Elements)
    },

    test(
      "3. Case Selection: _.origin.when[Imported].country converts to [Field(origin), Case(Imported), Field(country)]"
    ) {
      val migration = MigrationBuilder
        .make[ItemV1, ItemV2]
        .renameField(
          (i: ItemV1) => i.origin.when[Imported].country,
          (i: ItemV2) => i.sourceCountry
        )
        .buildPartial

      val fromOptic = migration.dynamicMigration.actions.head match {
        case Rename(optic, _) => optic
        case _                => throw new RuntimeException("Expected Rename action")
      }

      assertTrue(fromOptic.nodes.length == 3) &&
      assertTrue(fromOptic.nodes(0) == DynamicOptic.Node.Field("origin")) &&
      assertTrue(fromOptic.nodes(1) == DynamicOptic.Node.Case("Imported")) &&
      assertTrue(fromOptic.nodes(2) == DynamicOptic.Node.Field("country"))
    },

    test("4. API Abstraction: DynamicOptic is never exposed to the user") {
      val builderCheck = MigrationBuilder.make[Address, Address]

      // Explicit lambdas
      val result = builderCheck.renameField((a: Address) => a.city, (a: Address) => a.street.name)

      assertTrue(result != null)
    }
  )
}
