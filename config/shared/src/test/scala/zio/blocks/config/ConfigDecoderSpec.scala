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

object ConfigDecoderSpec extends ConfigBaseSpec {

  case class Db(host: String, port: Int)
  object Db {
    implicit val schema: Schema[Db] = Schema.derived[Db]
  }

  case class Http(host: String, port: Int)
  object Http {
    implicit val schema: Schema[Http] = Schema.derived[Http]
  }

  case class App(db: Db, http: Http)
  object App {
    implicit val schema: Schema[App] = Schema.derived[App]
  }

  case class WithOption(name: String, tag: Option[String])
  object WithOption {
    implicit val schema: Schema[WithOption] = Schema.derived[WithOption]
  }

  case class WithDefault(host: String, port: Int = 5432)
  object WithDefault {
    implicit val schema: Schema[WithDefault] = Schema.derived[WithDefault]
  }

  case class WithBoolean(enabled: Boolean, debug: Boolean)
  object WithBoolean {
    implicit val schema: Schema[WithBoolean] = Schema.derived[WithBoolean]
  }

  case class WithLong(timeout: Long)
  object WithLong {
    implicit val schema: Schema[WithLong] = Schema.derived[WithLong]
  }

  case class WithDouble(ratio: Double)
  object WithDouble {
    implicit val schema: Schema[WithDouble] = Schema.derived[WithDouble]
  }

  def spec = suite("ConfigDecoderSpec")(
    suite("simple case class")(
      test("decode Db from MapSource") {
        val source  = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(Db("localhost", 5432)))
      },
      test("decode Db with prefix") {
        val source  = ConfigSource.fromMap(Map("db.host" -> "localhost", "db.port" -> "5432"))
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "db")
        assertTrue(result == Right(Db("localhost", 5432)))
      }
    ),
    suite("nested case class")(
      test("decode App with nested Db and Http") {
        val source = ConfigSource.fromMap(Map(
          "db.host"   -> "dbhost",
          "db.port"   -> "5432",
          "http.host" -> "0.0.0.0",
          "http.port" -> "8080"
        ))
        val decoder = ConfigDecoder.derive[App]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(App(Db("dbhost", 5432), Http("0.0.0.0", 8080))))
      },
      test("decode App with outer prefix") {
        val source = ConfigSource.fromMap(Map(
          "app.db.host"   -> "dbhost",
          "app.db.port"   -> "5432",
          "app.http.host" -> "0.0.0.0",
          "app.http.port" -> "8080"
        ))
        val decoder = ConfigDecoder.derive[App]
        val result  = decoder.decode(source, "app")
        assertTrue(result == Right(App(Db("dbhost", 5432), Http("0.0.0.0", 8080))))
      }
    ),
    suite("Option fields")(
      test("present value becomes Some") {
        val source  = ConfigSource.fromMap(Map("name" -> "test", "tag" -> "v1"))
        val decoder = ConfigDecoder.derive[WithOption]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithOption("test", Some("v1"))))
      },
      test("missing value becomes None") {
        val source  = ConfigSource.fromMap(Map("name" -> "test"))
        val decoder = ConfigDecoder.derive[WithOption]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithOption("test", None)))
      }
    ),
    suite("schema defaults")(
      test("uses default value when key is missing") {
        val source  = ConfigSource.fromMap(Map("host" -> "localhost"))
        val decoder = ConfigDecoder.derive[WithDefault]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithDefault("localhost", 5432)))
      },
      test("overrides default when key is present") {
        val source  = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "3306"))
        val decoder = ConfigDecoder.derive[WithDefault]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithDefault("localhost", 3306)))
      }
    ),
    suite("error accumulation")(
      test("multiple missing fields produce multiple errors") {
        val source  = ConfigSource.fromMap(Map.empty[String, String])
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) => assertTrue(errors.length >= 2)
          case Right(_)     => assertTrue(false)
        }
      },
      test("type parsing error for Int field") {
        val source  = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "abc"))
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) =>
            assertTrue(
              errors.length == 1,
              errors.head.isInstanceOf[ConfigError.InvalidValue]
            )
          case Right(_) => assertTrue(false)
        }
      },
      test("mixed missing and invalid errors") {
        val source  = ConfigSource.fromMap(Map("port" -> "abc"))
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) =>
            val hasMissing = errors.exists(_.isInstanceOf[ConfigError.MissingKey])
            val hasInvalid = errors.exists(_.isInstanceOf[ConfigError.InvalidValue])
            assertTrue(errors.length == 2, hasMissing, hasInvalid)
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("primitive types")(
      test("decode Boolean") {
        val source  = ConfigSource.fromMap(Map("enabled" -> "true", "debug" -> "false"))
        val decoder = ConfigDecoder.derive[WithBoolean]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithBoolean(enabled = true, debug = false)))
      },
      test("decode Boolean with alternative values") {
        val source  = ConfigSource.fromMap(Map("enabled" -> "yes", "debug" -> "0"))
        val decoder = ConfigDecoder.derive[WithBoolean]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithBoolean(enabled = true, debug = false)))
      },
      test("decode Long") {
        val source  = ConfigSource.fromMap(Map("timeout" -> "30000"))
        val decoder = ConfigDecoder.derive[WithLong]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithLong(30000L)))
      },
      test("decode Double") {
        val source  = ConfigSource.fromMap(Map("ratio" -> "0.75"))
        val decoder = ConfigDecoder.derive[WithDouble]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithDouble(0.75)))
      }
    ),
    suite("ConfigDecoder.derive convenience")(
      test("derive from implicit schema") {
        val source  = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(Db("localhost", 5432)))
      }
    )
  )
}
