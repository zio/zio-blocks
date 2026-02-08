package zio.blocks.scope

import zio.test._

object UnscopedDerivedScala3Spec extends ZIOSpecDefault {

  case class Config(host: String, port: Int) derives Unscoped

  case class DatabaseConfig(url: String, username: String, password: String) derives Unscoped

  case class NestedConfig(config: Config, db: DatabaseConfig) derives Unscoped

  case class Point(x: Double, y: Double) derives Unscoped

  case object Singleton derives Unscoped

  sealed trait Status derives Unscoped
  case object Active                        extends Status
  case object Inactive                      extends Status
  case class Pending(reason: String)        extends Status
  case class Failed(code: Int, msg: String) extends Status

  sealed trait Shape derives Unscoped
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape

  case class ConfigWithOption(name: String, debug: Option[Boolean]) derives Unscoped

  case class ConfigWithList(names: List[String], ports: Vector[Int]) derives Unscoped

  case class ConfigWithMap(settings: Map[String, Int]) derives Unscoped

  case class ConfigWithEither(result: Either[String, Int]) derives Unscoped

  case class ConfigWithChunk(data: zio.blocks.chunk.Chunk[String]) derives Unscoped

  def verifyUnscoped[A](value: A)(using ev: Unscoped[A]): Boolean = {
    val escape = summon[ScopeEscape[A, String]]
    escape(value) == value
  }

  def spec = suite("Unscoped.derived (Scala 3)")(
    suite("case classes")(
      test("simple case class with primitives") {
        assertTrue(verifyUnscoped(Config("localhost", 8080)))
      },
      test("case class with multiple String fields") {
        assertTrue(verifyUnscoped(DatabaseConfig("jdbc://localhost", "user", "pass")))
      },
      test("nested case classes") {
        val nested = NestedConfig(Config("localhost", 8080), DatabaseConfig("jdbc://localhost", "user", "pass"))
        assertTrue(verifyUnscoped(nested))
      },
      test("case class with Double fields") {
        assertTrue(verifyUnscoped(Point(1.0, 2.0)))
      }
    ),
    suite("case objects")(
      test("simple case object") {
        assertTrue(verifyUnscoped(Singleton))
      }
    ),
    suite("sealed traits")(
      test("sealed trait with case objects") {
        assertTrue(verifyUnscoped(Active: Status) && verifyUnscoped(Inactive: Status))
      },
      test("sealed trait with case classes") {
        assertTrue(verifyUnscoped(Pending("waiting"): Status))
      },
      test("sealed trait with mixed cases") {
        assertTrue(verifyUnscoped(Failed(500, "error"): Status))
      },
      test("sealed trait with only case classes") {
        assertTrue(verifyUnscoped(Circle(5.0): Shape) && verifyUnscoped(Rectangle(10.0, 20.0): Shape))
      }
    ),
    suite("case classes with collections")(
      test("case class with Option") {
        assertTrue(
          verifyUnscoped(ConfigWithOption("test", Some(true))) &&
            verifyUnscoped(ConfigWithOption("test", None))
        )
      },
      test("case class with List and Vector") {
        assertTrue(verifyUnscoped(ConfigWithList(List("a", "b"), Vector(80, 443))))
      },
      test("case class with Map") {
        assertTrue(verifyUnscoped(ConfigWithMap(Map("port" -> 8080, "timeout" -> 30))))
      },
      test("case class with Either") {
        assertTrue(
          verifyUnscoped(ConfigWithEither(Right(42))) &&
            verifyUnscoped(ConfigWithEither(Left("error")))
        )
      },
      test("case class with Chunk") {
        assertTrue(verifyUnscoped(ConfigWithChunk(zio.blocks.chunk.Chunk("a", "b", "c"))))
      }
    )
  )
}
