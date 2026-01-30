package zio.blocks.schema.structural.common
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/** Tests for collections in structural types. */
object CollectionsSpec extends SchemaBaseSpec {

  case class Team(name: String, members: List[String], leader: Option[String])
  case class Config(settings: Map[String, Int])

  def spec: Spec[Any, Nothing] = suite("CollectionsSpec")(
    test("List field round-trip preserves data") {
      val team    = Team("Engineering", List("Alice", "Bob"), Some("Alice"))
      val schema  = Schema.derived[Team]
      val dynamic = schema.toDynamicValue(team)
      val result  = schema.fromDynamicValue(dynamic)
      assertTrue(result == Right(team))
    },
    test("Team with List and Option converts to structural type") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.structural.common.CollectionsSpec._
        val schema = Schema.derived[Team]
        val structural: Schema[{def leader: Option[String]; def members: List[String]; def name: String}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("Config with Map converts to structural type") {
      typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.structural.common.CollectionsSpec._
        val schema = Schema.derived[Config]
        val structural: Schema[{def settings: Map[String, Int]}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
