/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  def spec = suite("Unscoped.derived (Scala 3)")(
    suite("case classes")(
      test("simple case class with primitives") {
        summon[Unscoped[Config]]; assertCompletes
      },
      test("case class with multiple String fields") {
        summon[Unscoped[DatabaseConfig]]; assertCompletes
      },
      test("nested case classes") {
        summon[Unscoped[NestedConfig]]; assertCompletes
      },
      test("case class with Double fields") {
        summon[Unscoped[Point]]; assertCompletes
      }
    ),
    suite("case objects")(
      test("simple case object") {
        summon[Unscoped[Singleton.type]]; assertCompletes
      }
    ),
    suite("sealed traits")(
      test("Status") {
        summon[Unscoped[Status]]; assertCompletes
      },
      test("Shape") {
        summon[Unscoped[Shape]]; assertCompletes
      }
    ),
    suite("case classes with collections")(
      test("case class with Option") {
        summon[Unscoped[ConfigWithOption]]; assertCompletes
      },
      test("case class with List and Vector") {
        summon[Unscoped[ConfigWithList]]; assertCompletes
      },
      test("case class with Map") {
        summon[Unscoped[ConfigWithMap]]; assertCompletes
      },
      test("case class with Either") {
        summon[Unscoped[ConfigWithEither]]; assertCompletes
      },
      test("case class with Chunk") {
        summon[Unscoped[ConfigWithChunk]]; assertCompletes
      }
    )
  )
}
