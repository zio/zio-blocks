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

import zio.blocks.context.Context
import zio.blocks.maybe.Maybe
import zio.blocks.schema.Schema
import zio.blocks.scope.{Resource, Scope, Unscoped, Wire}
import zio.test._

object ConfigSpec extends ConfigBaseSpec {

  case class Db(host: String, port: Int)
  object Db {
    implicit val schema: Schema[Db]     = Schema.derived[Db]
    implicit val unscoped: Unscoped[Db] = Unscoped.derived[Db]
  }

  case class App(db: Db, name: String)
  object App {
    implicit val schema: Schema[App] = Schema.derived[App]
  }

  final class DbService(val config: Db)

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
          case Left(errors) =>
            errors.head match {
              case c: ConfigError.Composite => assertTrue(c.errors.length >= 2)
              case _                        => assertTrue(false)
            }
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("Config.wire")(
      test("loads typed config from an injected ConfigSource") {
        val source = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "wire-test")
        val result = Scope.global.scoped { scope =>
          Config.wire[Db].make(scope, Context[ConfigSource](source))
        }
        assertTrue(result == Db("localhost", 5432))
      },
      test("loads typed config from an injected prefixed ConfigSource") {
        val source = ConfigSource.fromMap(Map("db.host" -> "localhost", "db.port" -> "5432"), "wire-test")
        val result = Scope.global.scoped { scope =>
          Config.wire[Db]("db").make(scope, Context[ConfigSource](source))
        }
        assertTrue(result == Db("localhost", 5432))
      },
      test("respects composed source precedence") {
        val primary  = ConfigSource.fromMap(Map("host" -> "primary-host"), "primary")
        val fallback = ConfigSource.fromMap(Map("host" -> "fallback-host", "port" -> "5432"), "fallback")
        val source   = primary.orElse(fallback)
        val result   = Scope.global.scoped { scope =>
          Config.wire[Db].make(scope, Context[ConfigSource](source))
        }
        assertTrue(result == Db("primary-host", 5432))
      },
      test("throws ConfigLoadException with accumulated errors on failure") {
        val source = ConfigSource.fromMap(Map.empty[String, String], "wire-failure")
        val threw  =
          try {
            Scope.global.scoped { scope =>
              Config.wire[Db].make(scope, Context[ConfigSource](source))
            }
            false
          } catch {
            case e: ConfigLoadException =>
              e.errors.head.isInstanceOf[ConfigError.Composite] && e.report.contains("error(s)")
          }
        assertTrue(threw)
      },
      test("integrates with Resource.from by injecting ConfigSource as a leaf") {
        val source   = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"), "wire-graph")
        val resource = Resource.from[DbService](Wire(source), Config.wire[Db])
        val config   = Scope.global.scoped { scope =>
          import scope._
          val service = allocate(resource)
          $(service)(_.config)
        }
        assertTrue(config == Db("localhost", 5432))
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
            e.errors.head.isInstanceOf[ConfigError.Composite] && e.report.contains("error(s)")
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
              pm.provenanceOf("host") == Maybe.present(
                Provenance.Resolved("test-src", "host", Maybe.present("localhost"))
              ),
              pm.provenanceOf("port") == Maybe.present(Provenance.Resolved("test-src", "port", Maybe.present("5432"))),
              pm.provenanceOf("missing") == Maybe.absent
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
      },
      test("dump redacts sensitive values by key name") {
        val source = ConfigSource.fromMap(
          Map(
            "db.host"     -> "localhost",
            "db.password" -> "super-secret-password"
          ),
          "test-src"
        )
        val dumped = ProvenanceMap((), source).dump()
        assertTrue(
          dumped.contains("db.password"),
          dumped.contains("<secret>"),
          !dumped.contains("super-secret-password")
        )
      }
    )
  )
}
