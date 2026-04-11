package zio.blocks.schema.migration

import zio.blocks.schema.Schema
import zio.test._

object MigrationSpec extends ZIOSpecDefault {
  case class PersonV0(name: String)
  object PersonV0 { implicit val schema: Schema = Schema.derived }

  case class Person(name: String, age: Int)
  object Person { implicit val schema: Schema[Person] = Schema.derived }

  def spec = suite("MigrationSpec")(
    test("addField with Const") {
      val migration = Migration.newBuilder[PersonV0, Person]
       .addField(_.age, SchemaExpr.Const(0))
       .build
      val result = migration(PersonV0("Eliene"))
      assertTrue(result == Right(Person("Eliene", 0)))
    },
    test("dropField") {
      val migration = Migration.newBuilder[Person, PersonV0]
       .dropField(_.age)
       .build
      val result = migration(Person("Eliene", 30))
      assertTrue(result == Right(PersonV0("Eliene")))
    },
    test("rename field") {
      case class Old(n: String)
      case class New(name: String)
      implicit val s1: Schema[Old] = Schema.derived
      implicit val s2: Schema[New] = Schema.derived

      val migration = Migration.newBuilder[Old, New]
       .rename(_.n, "name")
       .build
      val result = migration(Old("test"))
      assertTrue(result == Right(New("test")))
    }
  )
}
