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

package zio.blocks.config

import zio.blocks.schema.Schema
import zio.test._

object ConfigSpec extends ConfigBaseSpec {

  case class Db(host: String, port: Int)
  object Db {
    implicit val schema: Schema[Db] = Schema.derived[Db]
  }

  case class App(db: Db, name: String)
  object App {
    implicit val schema: Schema[App] = Schema.derived[App]
  }

  def spec = suite("ConfigSpec")(
    suite("Config.load")(
      test("loads a simple case class from MapSource") {
        val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
        val result = Config.load[Db](source)
        assertTrue(result == Right(Db("localhost", 5432)))
      },
      test("loads nested case class") {
        val source = ConfigSource.fromMap(
          Map(
            "db.host" -> "dbhost",
            "db.port" -> "3306",
            "name"    -> "myapp"
          )
        )
        val result = Config.load[App](source)
        assertTrue(result == Right(App(Db("dbhost", 3306), "myapp")))
      },
      test("returns accumulated errors for missing keys") {
        val source = ConfigSource.fromMap(Map.empty[String, String])
        val result = Config.load[Db](source)
        result match {
          case Left(errors) => assertTrue(errors.length >= 2)
          case Right(_)     => assertTrue(false)
        }
      }
    ),
    suite("Config.loadOrThrow")(
      test("returns value on success") {
        val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
        val result = Config.loadOrThrow[Db](source)
        assertTrue(result == Db("localhost", 5432))
      },
      test("throws ConfigLoadException on failure") {
        val source = ConfigSource.fromMap(Map.empty[String, String])
        val threw  = try {
          Config.loadOrThrow[Db](source)
          false
        } catch {
          case e: ConfigLoadException =>
            e.errors.length >= 2 && e.report.contains("error(s)")
        }
        assertTrue(threw)
      }
    ),
    suite("Config.loadWithProvenance")(
      test("returns ProvenanceMap with correct value") {
        val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "test-src")
        val result = Config.loadWithProvenance[Db](source)
        result match {
          case Right(pm) =>
            assertTrue(
              pm.value == Db("localhost", 5432),
              pm.provenanceOf("host") == Some(Provenance.Resolved("test-src", "host", Some("localhost"))),
              pm.provenanceOf("port") == Some(Provenance.Resolved("test-src", "port", Some("5432"))),
              pm.provenanceOf("missing") == None
            )
          case Left(_) => assertTrue(false)
        }
      },
      test("dump produces formatted table") {
        val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "test-src")
        val pm     = Config.loadWithProvenance[Db](source).toOption.get
        val dumped = pm.dump()
        assertTrue(
          dumped.contains("host"),
          dumped.contains("localhost"),
          dumped.contains("test-src"),
          dumped.contains("\u2502"),
          dumped.contains("\u250c")
        )
      }
    )
  )
}
