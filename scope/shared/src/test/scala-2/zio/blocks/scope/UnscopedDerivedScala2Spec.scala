package zio.blocks.scope

import zio.test._

object UnscopedDerivedScala2Spec extends ZIOSpecDefault {

  case class Config(host: String, port: Int)
  object Config {
    implicit val unscoped: Unscoped[Config] = Unscoped.derived[Config]
  }

  case class DatabaseConfig(url: String, username: String, password: String)
  object DatabaseConfig {
    implicit val unscoped: Unscoped[DatabaseConfig] = Unscoped.derived[DatabaseConfig]
  }

  case class NestedConfig(config: Config, db: DatabaseConfig)
  object NestedConfig {
    implicit val unscoped: Unscoped[NestedConfig] = Unscoped.derived[NestedConfig]
  }

  case class Point(x: Double, y: Double)
  object Point {
    implicit val unscoped: Unscoped[Point] = Unscoped.derived[Point]
  }

  case object Singleton {
    implicit val unscoped: Unscoped[Singleton.type] = Unscoped.derived[Singleton.type]
  }

  sealed trait Status
  case object Active                        extends Status
  case object Inactive                      extends Status
  case class Pending(reason: String)        extends Status
  case class Failed(code: Int, msg: String) extends Status
  object Status {
    implicit val unscoped: Unscoped[Status] = Unscoped.derived[Status]
  }

  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  object Shape {
    implicit val unscoped: Unscoped[Shape] = Unscoped.derived[Shape]
  }

  case class ConfigWithOption(name: String, debug: Option[Boolean])
  object ConfigWithOption {
    implicit val unscoped: Unscoped[ConfigWithOption] = Unscoped.derived[ConfigWithOption]
  }

  case class ConfigWithList(names: List[String], ports: Vector[Int])
  object ConfigWithList {
    implicit val unscoped: Unscoped[ConfigWithList] = Unscoped.derived[ConfigWithList]
  }

  case class ConfigWithMap(settings: Map[String, Int])
  object ConfigWithMap {
    implicit val unscoped: Unscoped[ConfigWithMap] = Unscoped.derived[ConfigWithMap]
  }

  case class ConfigWithEither(result: Either[String, Int])
  object ConfigWithEither {
    implicit val unscoped: Unscoped[ConfigWithEither] = Unscoped.derived[ConfigWithEither]
  }

  case class ConfigWithChunk(data: zio.blocks.chunk.Chunk[String])
  object ConfigWithChunk {
    implicit val unscoped: Unscoped[ConfigWithChunk] = Unscoped.derived[ConfigWithChunk]
  }

  def verifyUnscoped[A](value: A)(implicit ev: Unscoped[A]): Boolean = {
    val escape = implicitly[ScopeEscape[A, String]]
    escape(value) == value
  }

  def spec = suite("Unscoped.derived (Scala 2)")(
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
