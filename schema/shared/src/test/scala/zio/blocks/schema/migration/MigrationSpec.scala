package zio.blocks.schema.migration


import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.Schema

object MigrationSpec extends ZIOSpecDefault {

  case class PersonV0(name: String)
  object PersonV0 {
    implicit val schema: Schema[PersonV0] = Schema.derived
  }

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  def spec = suite("MigrationSpec")(
    test("Can add a field to a record") {
      val migration = Migration
        .newBuilder[PersonV0, PersonV1]
        .addField(
          DynamicOptic.root.field("age"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        .build

      val v0       = PersonV0("John")
      val expected = PersonV1("John", 0)

      val result = migration(v0)

      assert(result)(isRight(equalTo(expected)))
    },

    test("Can rename a field in a record") {
      case class OldInfo(n: String)
      object OldInfo { implicit val schema: Schema[OldInfo] = Schema.derived }

      case class NewInfo(name: String)
      object NewInfo { implicit val schema: Schema[NewInfo] = Schema.derived }

      val migration = Migration
        .newBuilder[OldInfo, NewInfo]
        .renameField(DynamicOptic.root.field("n"), "name")
        .build

      val old      = OldInfo("Alice")
      val expected = NewInfo("Alice")

      assert(migration(old))(isRight(equalTo(expected)))
    }
  )
}