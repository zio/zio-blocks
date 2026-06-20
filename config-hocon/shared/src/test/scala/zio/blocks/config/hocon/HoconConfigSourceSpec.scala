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

package zio.blocks.config.hocon

import zio.blocks.config.Provenance
import zio.blocks.maybe.Maybe
import zio.test._

object HoconConfigSourceSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("HoconConfigSourceSpec")(
    test("ConfigSource.fromHocon parses simple HOCON into ConfigSource") {
      val hocon  = """
        db {
          host = "localhost"
          port = 5432
        }
      """
      val source = zio.blocks.config.ConfigSource.fromHocon(hocon).toOption.get
      assertTrue(
        source.sourceId == "hocon:string",
        source.get("db.host").get.value == "localhost",
        source.get("db.port").get.value == "5432"
      )
    },
    suite("fromString")(
      test("parses simple HOCON into ConfigSource") {
        val hocon  = """
          db {
            host = "localhost"
            port = 5432
          }
        """
        val source = HoconConfigSource.fromString(hocon).toOption.get
        assertTrue(
          source.sourceId == "hocon:string",
          source.get("db.host").get.value == "localhost",
          source.get("db.port").get.value == "5432"
        )
      },
      test("provenance tracks hocon source id") {
        val hocon  = """name = "test""""
        val source = HoconConfigSource.fromString(hocon).toOption.get
        val cv     = source.get("name").get
        assertTrue(
          cv.provenance == Provenance.Resolved("hocon:string", "name", Maybe.present("test"))
        )
      },
      test("handles nested objects with dot-separated keys") {
        val hocon  = """
          app {
            db {
              host = "dbhost"
              port = 3306
            }
            http {
              port = 8080
            }
          }
        """
        val source = HoconConfigSource.fromString(hocon).toOption.get
        assertTrue(
          source.get("app.db.host").get.value == "dbhost",
          source.get("app.db.port").get.value == "3306",
          source.get("app.http.port").get.value == "8080"
        )
      },
      test("returns Left for invalid HOCON") {
        val result = HoconConfigSource.fromString("{ invalid }")
        assertTrue(result.isLeft)
      },
      test("getAll returns all keys under prefix") {
        val hocon  = """
          db.host = "localhost"
          db.port = 5432
          app.name = "test"
        """
        val source = HoconConfigSource.fromString(hocon).toOption.get
        val all    = source.getAll("db")
        assertTrue(
          all.size == 2,
          all.contains("db.host"),
          all.contains("db.port")
        )
      },
      test("handles arrays with numeric indices") {
        val hocon  = """
          items = [1, 2, 3]
        """
        val source = HoconConfigSource.fromString(hocon).toOption.get
        assertTrue(
          source.get("items.0").get.value == "1",
          source.get("items.1").get.value == "2",
          source.get("items.2").get.value == "3"
        )
      },
      test("handles substitutions") {
        val hocon  = """
          base = "localhost"
          db.host = ${base}
        """
        val source = HoconConfigSource.fromString(hocon).toOption.get
        assertTrue(source.get("db.host").get.value == "localhost")
      },
      test("HOCON boolean value is stored as string") {
        val hocon  = """{ enabled = true }"""
        val source = HoconConfigSource.fromString(hocon).toOption.get
        assertTrue(source.get("enabled").get.value == "true")
      },
      test("HOCON null value results in None from get") {
        val hocon  = """{ missing = null }"""
        val source = HoconConfigSource.fromString(hocon).toOption.get
        assertTrue(source.get("missing").isEmpty)
      }
    ),
    suite("fromStringWithId")(
      test("uses custom source id") {
        val hocon  = """key = "value""""
        val source = HoconConfigSource.fromStringWithId(hocon, "hocon:app.conf").toOption.get
        assertTrue(
          source.sourceId == "hocon:app.conf",
          source.get("key").get.provenance == Provenance.Resolved("hocon:app.conf", "key", Maybe.present("value"))
        )
      }
    ),
    suite("composition with orElse")(
      test("hocon source composes with map source") {
        val hocon    = """db.host = "hocon-host""""
        val source   = HoconConfigSource.fromString(hocon).toOption.get
        val fallback = zio.blocks.config.ConfigSource.fromMap(Map("db.port" -> "5432"), "defaults")
        val combined = source.orElse(fallback)
        assertTrue(
          combined.get("db.host").get.value == "hocon-host",
          combined.get("db.port").get.value == "5432"
        )
      }
    )
  )
}
