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

  def spec = suite("Unscoped.derived (Scala 2)")(
    suite("case classes")(
      test("simple case class with primitives") {
        implicitly[Unscoped[Config]]; assertCompletes
      },
      test("case class with multiple String fields") {
        implicitly[Unscoped[DatabaseConfig]]; assertCompletes
      },
      test("nested case classes") {
        implicitly[Unscoped[NestedConfig]]; assertCompletes
      },
      test("case class with Double fields") {
        implicitly[Unscoped[Point]]; assertCompletes
      }
    ),
    suite("case objects")(
      test("simple case object") {
        implicitly[Unscoped[Singleton.type]]; assertCompletes
      }
    ),
    suite("sealed traits")(
      test("sealed trait with case objects") {
        implicitly[Unscoped[Status]]; assertCompletes
      },
      test("sealed trait with case classes") {
        implicitly[Unscoped[Status]]; assertCompletes
      },
      test("sealed trait with mixed cases") {
        implicitly[Unscoped[Status]]; assertCompletes
      },
      test("sealed trait with only case classes") {
        implicitly[Unscoped[Shape]]; assertCompletes
      }
    ),
    suite("case classes with collections")(
      test("case class with Option") {
        implicitly[Unscoped[ConfigWithOption]]; assertCompletes
      },
      test("case class with List and Vector") {
        implicitly[Unscoped[ConfigWithList]]; assertCompletes
      },
      test("case class with Map") {
        implicitly[Unscoped[ConfigWithMap]]; assertCompletes
      },
      test("case class with Either") {
        implicitly[Unscoped[ConfigWithEither]]; assertCompletes
      },
      test("case class with Chunk") {
        implicitly[Unscoped[ConfigWithChunk]]; assertCompletes
      }
    )
  )
}
