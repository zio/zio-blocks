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

  case class WithList(items: List[String])
  object WithList {
    implicit val schema: Schema[WithList] = Schema.derived[WithList]
  }

  sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color
    implicit val schema: Schema[Color] = Schema.derived[Color]
  }

  case class WithFloat(ratio: Float)
  object WithFloat {
    implicit val schema: Schema[WithFloat] = Schema.derived[WithFloat]
  }

  case class WithMap(counts: Map[String, Int])
  object WithMap {
    implicit val schema: Schema[WithMap] = Schema.derived[WithMap]
  }

  case class WithDatabaseUrlMap(databaseUrl: Map[String, Int])
  object WithDatabaseUrlMap {
    implicit val schema: Schema[WithDatabaseUrlMap] = Schema.derived[WithDatabaseUrlMap]
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
        val source = ConfigSource.fromMap(
          Map(
            "db.host"   -> "dbhost",
            "db.port"   -> "5432",
            "http.host" -> "0.0.0.0",
            "http.port" -> "8080"
          )
        )
        val decoder = ConfigDecoder.derive[App]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(App(Db("dbhost", 5432), Http("0.0.0.0", 8080))))
      },
      test("decode App with outer prefix") {
        val source = ConfigSource.fromMap(
          Map(
            "app.db.host"   -> "dbhost",
            "app.db.port"   -> "5432",
            "app.http.host" -> "0.0.0.0",
            "app.http.port" -> "8080"
          )
        )
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
      test("multiple missing fields produce Composite error") {
        val source  = ConfigSource.fromMap(Map.empty[String, String])
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) =>
            errors.head match {
              case c: ConfigError.Composite => assertTrue(c.errors.length >= 2)
              case _                        => assertTrue(false)
            }
          case Right(_) => assertTrue(false)
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
      test("mixed missing and invalid errors preserve field order") {
        val source  = ConfigSource.fromMap(Map("port" -> "abc"))
        val decoder = ConfigDecoder.derive[Db]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) =>
            errors.head match {
              case c: ConfigError.Composite =>
                val inner = c.errors.toList.map {
                  case ConfigError.MissingKey(path, _)            => s"missing:$path"
                  case ConfigError.InvalidValue(path, _, _, _, _) => s"invalid:$path"
                  case other                                      => other.getClass.getSimpleName
                }
                assertTrue(inner == List("missing:host", "invalid:port"))
              case _ => assertTrue(false)
            }
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
    ),
    suite("List[String] decoding")(
      test("decodes indexed keys to list") {
        val source  = ConfigSource.fromMap(Map("items.0" -> "a", "items.1" -> "b", "items.2" -> "c"))
        val decoder = ConfigDecoder.derive[WithList]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithList(List("a", "b", "c"))))
      },
      test("decodes comma-separated fallback") {
        val source  = ConfigSource.fromMap(Map("items" -> "a,b,c"))
        val decoder = ConfigDecoder.derive[WithList]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithList(List("a", "b", "c"))))
      },
      test("decodes empty source to empty list") {
        val source  = ConfigSource.fromMap(Map.empty[String, String])
        val decoder = ConfigDecoder.derive[WithList]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithList(Nil)))
      }
    ),
    suite("sealed trait variant decoding")(
      test("decodes known variant by type key") {
        val source  = ConfigSource.fromMap(Map("type" -> "Red"))
        val decoder = ConfigDecoder.derive[Color]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(Color.Red))
      },
      test("returns error for unknown variant") {
        val source  = ConfigSource.fromMap(Map("type" -> "Unknown"))
        val decoder = ConfigDecoder.derive[Color]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) => assertTrue(errors.exists(_.isInstanceOf[ConfigError.UnknownDiscriminator]))
          case Right(_)     => assertTrue(false)
        }
      },
      test("returns error for missing type key") {
        val source  = ConfigSource.fromMap(Map.empty[String, String])
        val decoder = ConfigDecoder.derive[Color]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) => assertTrue(errors.exists(_.isInstanceOf[ConfigError.MissingDiscriminatorKey]))
          case Right(_)     => assertTrue(false)
        }
      }
    ),
    suite("Float decoding")(
      test("decodes valid float string") {
        val source  = ConfigSource.fromMap(Map("ratio" -> "0.5"))
        val decoder = ConfigDecoder.derive[WithFloat]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithFloat(0.5f)))
      },
      test("returns error for non-float string") {
        val source  = ConfigSource.fromMap(Map("ratio" -> "not-a-float"))
        val decoder = ConfigDecoder.derive[WithFloat]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) => assertTrue(errors.exists(_.isInstanceOf[ConfigError.InvalidValue]))
          case Right(_)     => assertTrue(false)
        }
      }
    ),
    suite("Boolean variant values")(
      test("'no' and 'off' parse as false") {
        val source  = ConfigSource.fromMap(Map("enabled" -> "no", "debug" -> "off"))
        val decoder = ConfigDecoder.derive[WithBoolean]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithBoolean(enabled = false, debug = false)))
      },
      test("'on' and '1' parse as true") {
        val source  = ConfigSource.fromMap(Map("enabled" -> "on", "debug" -> "1"))
        val decoder = ConfigDecoder.derive[WithBoolean]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithBoolean(enabled = true, debug = true)))
      },
      test("invalid boolean value returns error") {
        val source  = ConfigSource.fromMap(Map("enabled" -> "maybe", "debug" -> "false"))
        val decoder = ConfigDecoder.derive[WithBoolean]
        val result  = decoder.decode(source, "")
        result match {
          case Left(errors) => assertTrue(errors.exists(_.isInstanceOf[ConfigError.InvalidValue]))
          case Right(_)     => assertTrue(false)
        }
      }
    ),
    suite("ConfigDecoder.apply")(
      test("apply retrieves implicit decoder and works") {
        implicit val dec: ConfigDecoder[Db] = ConfigDecoder.derive[Db]
        val source                          = ConfigSource.fromMap(Map("host" -> "localhost", "port" -> "5432"))
        val result                          = ConfigDecoder[Db].decode(source, "")
        assertTrue(result == Right(Db("localhost", 5432)))
      }
    ),
    suite("Map[String,Int] decoding")(
      test("decodes dot-keyed source to map entries") {
        val source  = ConfigSource.fromMap(Map("counts.a" -> "1", "counts.b" -> "2"))
        val decoder = ConfigDecoder.derive[WithMap]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithMap(Map("a" -> 1, "b" -> 2))))
      },
      test("decodes dot-keyed source through prefix") {
        val source = ConfigSource
          .fromMap(Map("app.counts.a" -> "1", "app.counts.b" -> "2"))
          .prefix("app")
        val decoder = ConfigDecoder.derive[WithMap]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithMap(Map("a" -> 1, "b" -> 2))))
      },
      test("decodes mapped map keys through keyFormat") {
        val source = ConfigSource
          .fromMap(Map("DATABASE_URL.A" -> "1", "DATABASE_URL.B" -> "2"))
          .keyFormat(KeyFormat.UpperSnakeCase)
        val decoder = ConfigDecoder.derive[WithDatabaseUrlMap]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithDatabaseUrlMap(Map("a" -> 1, "b" -> 2))))
      }
    ),
    suite("List[String] wrapper integration")(
      test("decodes indexed keys from prefixed source") {
        val source = ConfigSource
          .fromMap(Map("app.items.0" -> "a", "app.items.1" -> "b"))
          .prefix("app")
        val decoder = ConfigDecoder.derive[WithList]
        val result  = decoder.decode(source, "")
        assertTrue(result == Right(WithList(List("a", "b"))))
      }
    )
  )
}
