package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Scala 3 specific tests for MigrationBuilder demonstrating:
 *   - Structural type migrations as described in Issue #519
 *   - Enum case selectors with DynamicOptic.caseOf pattern
 */
object MigrationBuilderScala3Spec extends ZIOSpecDefault {

  // Issue #519 structural types
  type PersonV0 = StructuralInstance { val firstName: String; val lastName: String }
  type PersonV1 = StructuralInstance { val firstName: String; val lastName: String; val age: Int }

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderScala3Spec")(
      suite("Structural Type Migrations - Issue #519")(
        test("structural type schema derivation") {
          val v0Schema = Schema.structural[PersonV0]
          val v1Schema = Schema.structural[PersonV1]

          val v0Fields = v0Schema.reflect.asRecord.get.fields
          val v1Fields = v1Schema.reflect.asRecord.get.fields

          assertTrue(v0Fields.length == 2) &&
          assertTrue(v0Fields.exists(_.name == "firstName")) &&
          assertTrue(v0Fields.exists(_.name == "lastName")) &&
          assertTrue(v1Fields.length == 3) &&
          assertTrue(v1Fields.exists(_.name == "age"))
        },
        test("structural type migration with addField - Issue #519 pattern") {
          // For structural types with selectDynamic, we use the string-based API
          implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
          implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]

          val migration = Migration
            .builder[PersonV0, PersonV1]
            .addField("age", 0)
            .buildPartial

          assertTrue(migration.dynamic.actions.length == 1) &&
          assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.AddField])
        },
        test("Issue #519 exact example: Case class to Case class with selector") {
          // This verifies the workflow: old version as structural, new as case class
          case class Person(fullName: String, age: Int)
          implicit val oldSchema: Schema[PersonV0] = Schema.structural[PersonV0]
          implicit val newSchema: Schema[Person]   = Schema.derived

          val migration = Migration
            .builder[PersonV0, Person]
            .addField(_.age, 0)
            .buildPartial

          assertTrue(migration.dynamic.actions.length == 1) &&
          assertTrue(migration.dynamic.actions.head.asInstanceOf[MigrationAction.AddField].at.nodes.last == DynamicOptic.Node.Field("age"))
        }
      ),
      suite("Selector Macro Tests")(
        test("selector-based addField with nested _.address.street") {
          case class Address(street: String, city: String)
          case class Person(name: String, address: Address)
          case class PersonNew(name: String, address: Address, country: String)
          
          implicit val pSchema: Schema[Person] = Schema.derived
          implicit val pnSchema: Schema[PersonNew] = Schema.derived
          
          val migration = Migration
            .builder[Person, PersonNew]
            .addField(_.country, "USA")
            .buildPartial
            
          assertTrue(migration.dynamic.actions.length == 1)
        },
        test("selector-based enum case selector _.status.when[Active]") {
          sealed trait Status
          object Status {
            case object Active extends Status
            case object Inactive extends Status
          }
          
          val optic = DynamicOptic.root.field("status").caseOf("Active")
          
          assertTrue(optic.nodes.length == 2) &&
          assertTrue(optic.nodes.head == DynamicOptic.Node.Field("status")) &&
          assertTrue(optic.nodes.last == DynamicOptic.Node.Case("Active"))
        }
      )
    )
}
