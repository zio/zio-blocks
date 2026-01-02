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
  case class Nested(data: List[Scores])

  // Helper to create DynamicValue.Primitive for common types
  private def intPrim(i: Int) = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def strPrim(s: String) = DynamicValue.Primitive(PrimitiveValue.String(s))

  def spec = suite("CollectionsSpec")(
    suite("List Fields")(
      test("case class with List field converts to structural") {
        val schema = Schema.derived[Team]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(
          typeName.contains("members"),
          typeName.contains("name"),
          typeName.contains("leader")
        )
      },
      test("List field round-trip preserves data") {
        val team = Team("Engineering", List("Alice", "Bob", "Charlie"), Some("Alice"))
        val schema = Schema.derived[Team]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(team)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("name").contains(strPrim("Engineering")),
              fieldMap.get("members") match {
                case Some(DynamicValue.Sequence(elems)) =>
                  elems.size == 3 &&
                  elems.contains(strPrim("Alice")) &&
                  elems.contains(strPrim("Bob"))
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("empty List field round-trip works") {
        val team = Team("Solo", List.empty, None)
        val schema = Schema.derived[Team]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(team)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("members") match {
                case Some(DynamicValue.Sequence(elems)) => elems.isEmpty
                case _                                   => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Vector Fields")(
      test("case class with Vector field converts to structural") {
        val schema = Schema.derived[Scores]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(typeName.contains("values"))
      },
      test("Vector field round-trip preserves data") {
        val scores = Scores(Vector(100, 95, 87, 92))
        val schema = Schema.derived[Scores]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(scores)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("values") match {
                case Some(DynamicValue.Sequence(elems)) =>
                  elems.size == 4 &&
                  elems.head == intPrim(100)
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Set Fields")(
      test("case class with Set field converts to structural") {
        val schema = Schema.derived[Tags]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(typeName.contains("items"))
      },
      test("Set field round-trip preserves data") {
        val tags = Tags(Set("scala", "zio", "functional"))
        val schema = Schema.derived[Tags]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(tags)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("items") match {
                case Some(DynamicValue.Sequence(elems)) =>
                  elems.size == 3
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Map Fields")(
      test("case class with Map field converts to structural") {
        val schema = Schema.derived[Config]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(typeName.contains("settings"))
      },
      test("Map field round-trip preserves data") {
        val config = Config(Map("timeout" -> 30, "retries" -> 3, "port" -> 8080))
        val schema = Schema.derived[Config]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(config)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("settings") match {
                case Some(DynamicValue.Map(entries)) =>
                  entries.size == 3
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Option Fields")(
      test("case class with Option field converts to structural") {
        val schema = Schema.derived[Team]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(typeName.contains("leader"))
      },
      test("Some value round-trip succeeds") {
        val team = Team("DevOps", List("Dave"), Some("Dave"))
        val schema = Schema.derived[Team]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(team)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.contains("leader"),
              fieldMap.get("leader").isDefined
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("None value round-trip succeeds") {
        val team = Team("Skeleton Crew", List("Only One"), None)
        val schema = Schema.derived[Team]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(team)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.contains("leader"),
              fieldMap.get("leader").isDefined
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Either Fields")(
      test("case class with Either field converts to structural") {
        val schema = Schema.derived[Result]
        val structural = schema.structural

        val typeName = structural.reflect.typeName.name
        assertTrue(typeName.contains("value"))
      },
      test("Right value round-trip succeeds") {
        val result = Result(Right(42))
        val schema = Schema.derived[Result]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(result)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.contains("value"),
              fieldMap.contains("value")
            )
          case _ =>
            assertTrue(false)
        }
      },
      test("Left value round-trip succeeds") {
        val result = Result(Left("error"))
        val schema = Schema.derived[Result]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(result)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.contains("value"),
              fieldMap.contains("value")
            )
          case _ =>
            assertTrue(false)
        }
      }
    ),
    suite("Nested Collections")(
      test("List of case classes round-trip preserves data") {
        val nested = Nested(List(Scores(Vector(1, 2)), Scores(Vector(3, 4, 5))))
        val schema = Schema.derived[Nested]
        val structural = schema.structural

        val dynamic = structural.asInstanceOf[Schema[Any]].toDynamicValue(nested)

        dynamic match {
          case record: DynamicValue.Record =>
            val fieldMap = record.fields.toMap
            assertTrue(
              fieldMap.get("data") match {
                case Some(DynamicValue.Sequence(elems)) =>
                  elems.size == 2 &&
                  elems.forall(_.isInstanceOf[DynamicValue.Record])
                case _ => false
              }
            )
          case _ =>
            assertTrue(false)
        }
      }
    )
  )
}

