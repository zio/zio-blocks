package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object StructuralMigrationApplySpec extends SchemaBaseSpec {

  type Old = { val a: Int }
  given Schema[Old] = Schema.structural[Old]

  final class OldValue(val a: Int)

  final case class New(a: Int)
  object New {
    given Schema[New] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("StructuralMigrationApplySpec")(
    test("Migration.apply fails with clear message for structural source schemas") {
      val migration = Migration.newBuilder[Old, New].buildPartial
      val old: Old  = new OldValue(1)

      migration(old) match {
        case Left(err) => assertTrue(err.message.contains("structural source schema"))
        case Right(_)  => assertTrue(false)
      }
    },
    test("Migration.apply fails with clear message for structural target schemas") {
      val migration = Migration.newBuilder[New, Old].buildPartial

      migration(New(1)) match {
        case Left(err) => assertTrue(err.message.contains("structural target schema"))
        case Right(_)  => assertTrue(false)
      }
    }
  )
}
