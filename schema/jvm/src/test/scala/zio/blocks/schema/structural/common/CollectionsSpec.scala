package zio.blocks.schema.structural.common

import zio.blocks.schema._
import zio.test._

/**
 * Tests for collections (List, Vector, Set, Map, Option, Either) in structural types.
 */
object CollectionsSpec extends ZIOSpecDefault {

  case class Team(name: String, members: List[String], leader: Option[String])
  case class Scores(values: Vector[Int])
  case class Tags(items: Set[String])
  case class Config(settings: Map[String, Int])
  case class Result(value: Either[String, Int])

  def spec = suite("CollectionsSpec")(
    test("List field in structural type") {
      val schema = Schema.derived[Team]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName.contains("members"))
    } @@ TestAspect.ignore,
    test("Vector field in structural type") {
      val schema = Schema.derived[Scores]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName.contains("values"))
    } @@ TestAspect.ignore,
    test("Set field in structural type") {
      val schema = Schema.derived[Tags]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName.contains("items"))
    } @@ TestAspect.ignore,
    test("Map field in structural type") {
      val schema = Schema.derived[Config]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName.contains("settings"))
    } @@ TestAspect.ignore,
    test("Option field in structural type") {
      val schema = Schema.derived[Team]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName.contains("leader"))
    } @@ TestAspect.ignore,
    test("Either field in structural type") {
      val schema = Schema.derived[Result]
      val structural = schema.structural

      val typeName = structural.reflect.typeName.name
      assertTrue(typeName.contains("value"))
    } @@ TestAspect.ignore
  )
}

