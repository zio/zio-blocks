package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/** Tests for collections in structural types. */
object CollectionsSpec extends ZIOSpecDefault {

  case class Team(name: String, members: List[String], leader: Option[String])
  case class Config(settings: Map[String, Int])

  def spec: Spec[Any, Nothing] = suite("CollectionsSpec")(
    test("case class with List field converts to structural") {
      val schema     = Schema.derived[Team]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("members"), typeName.contains("name"))
    },
    test("case class with Map field converts to structural") {
      val schema     = Schema.derived[Config]
      val structural = schema.structural
      val typeName   = structural.reflect.typeName.name
      assertTrue(typeName.contains("settings"))
    },
    test("List field round-trip preserves data") {
      val team    = Team("Engineering", List("Alice", "Bob"), Some("Alice"))
      val schema  = Schema.derived[Team]
      val dynamic = schema.toDynamicValue(team)
      val result  = schema.fromDynamicValue(dynamic)
      assertTrue(result == Right(team))
    }
  )
}
