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

object ConfigSourceCompositionSpec extends ConfigBaseSpec {

  case class DbConfig(host: String, port: Int)
  object DbConfig {
    implicit val schema: Schema[DbConfig] = Schema.derived[DbConfig]
  }

  case class AppConfig(db: DbConfig, name: String)
  object AppConfig {
    implicit val schema: Schema[AppConfig] = Schema.derived[AppConfig]
  }

  def spec = suite("ConfigSourceCompositionSpec")(
    provenanceSuite,
    chainedCompositionSuite,
    combinedTransformSuite,
    endToEndCompositionSuite
  )

  private val provenanceSuite = suite("orElse provenance attribution")(
    test("provenance tracks primary source when value comes from primary") {
      val env     = ConfigSource.fromMap(Map("db.host" -> "env-host"), "env")
      val props   = ConfigSource.fromMap(Map("db.host" -> "prop-host"), "sysprop")
      val source  = env.orElse(props)
      val result  = source.get("db.host")
      assertTrue(
        result.get.provenance == Provenance.Resolved("env", "db.host", Some("env-host"))
      )
    },
    test("provenance tracks fallback source when value comes from fallback") {
      val env     = ConfigSource.fromMap(Map.empty, "env")
      val props   = ConfigSource.fromMap(Map("db.host" -> "prop-host"), "sysprop")
      val source  = env.orElse(props)
      val result  = source.get("db.host")
      assertTrue(
        result.get.provenance == Provenance.Resolved("sysprop", "db.host", Some("prop-host"))
      )
    },
    test("three-level chain attributes to correct source") {
      val env     = ConfigSource.fromMap(Map.empty, "env")
      val props   = ConfigSource.fromMap(Map.empty, "sysprop")
      val defaults = ConfigSource.fromMap(Map("timeout" -> "30"), "defaults")
      val source  = env.orElse(props).orElse(defaults)
      val result  = source.get("timeout")
      assertTrue(
        result.get.value == "30",
        result.get.provenance == Provenance.Resolved("defaults", "timeout", Some("30"))
      )
    },
    test("three-level chain picks first available source") {
      val env      = ConfigSource.fromMap(Map.empty, "env")
      val props    = ConfigSource.fromMap(Map("timeout" -> "60"), "sysprop")
      val defaults = ConfigSource.fromMap(Map("timeout" -> "30"), "defaults")
      val source   = env.orElse(props).orElse(defaults)
      val result   = source.get("timeout")
      assertTrue(
        result.get.value == "60",
        result.get.provenance == Provenance.Resolved("sysprop", "timeout", Some("60"))
      )
    },
    test("getAll merges from all sources with correct provenance per key") {
      val env      = ConfigSource.fromMap(Map("db.host" -> "env-host"), "env")
      val props    = ConfigSource.fromMap(Map("db.port" -> "5432"), "sysprop")
      val defaults = ConfigSource.fromMap(Map("db.host" -> "default-host", "db.name" -> "mydb"), "defaults")
      val source   = env.orElse(props).orElse(defaults)
      val result   = source.getAll("db")
      assertTrue(
        result.size == 3,
        result("db.host").provenance == Provenance.Resolved("env", "db.host", Some("env-host")),
        result("db.port").provenance == Provenance.Resolved("sysprop", "db.port", Some("5432")),
        result("db.name").provenance == Provenance.Resolved("defaults", "db.name", Some("mydb"))
      )
    }
  )

  private val chainedCompositionSuite = suite("chained composition")(
    test("sourceId reflects full chain") {
      val a = ConfigSource.fromMap(Map.empty, "a")
      val b = ConfigSource.fromMap(Map.empty, "b")
      val c = ConfigSource.fromMap(Map.empty, "c")
      val source = a.orElse(b).orElse(c)
      assertTrue(source.sourceId == "a|b|c")
    },
    test("empty primary delegates all keys to fallback") {
      val primary  = ConfigSource.fromMap(Map.empty, "empty")
      val fallback = ConfigSource.fromMap(Map("a" -> "1", "b" -> "2"), "data")
      val source   = primary.orElse(fallback)
      assertTrue(
        source.get("a").get.value == "1",
        source.get("b").get.value == "2"
      )
    }
  )

  private val combinedTransformSuite = suite("combined transforms")(
    test("withPrefix composes with orElse") {
      val env      = ConfigSource.fromMap(Map("app.db.host" -> "env-host"), "env")
      val defaults = ConfigSource.fromMap(Map("app.db.host" -> "default-host", "app.db.port" -> "5432"), "defaults")
      val source   = env.orElse(defaults).withPrefix("app")
      val result   = source.getAll("db")
      assertTrue(
        result.size == 2,
        result("app.db.host").value == "env-host",
        result("app.db.port").value == "5432"
      )
    },
    test("withKeyMapper composes with orElse") {
      val env      = ConfigSource.fromMap(Map("DATABASE_URL" -> "env-url"), "env")
      val defaults = ConfigSource.fromMap(Map("DATABASE_URL" -> "default-url"), "defaults")
      val source   = env.orElse(defaults).withKeyMapper(KeyMapper.default, KeyFormat.UpperSnakeCase)
      val result   = source.get("databaseUrl")
      assertTrue(
        result.get.value == "env-url",
        result.get.provenance == Provenance.Resolved("env", "DATABASE_URL", Some("env-url"))
      )
    }
  )

  private val endToEndCompositionSuite = suite("end-to-end composition with Config.load")(
    test("multi-source composition decodes correctly with per-field provenance") {
      val env      = ConfigSource.fromMap(Map("db.host" -> "env-host"), "env")
      val yaml     = ConfigSource.fromMap(Map("db.port" -> "5432", "name" -> "yaml-app"), "yaml")
      val defaults = ConfigSource.fromMap(Map("db.host" -> "default-host", "db.port" -> "3306", "name" -> "default"), "defaults")
      val source   = env.orElse(yaml).orElse(defaults)
      val result   = Config.load[AppConfig](source)
      assertTrue(result == Right(AppConfig(DbConfig("env-host", 5432), "yaml-app")))
    },
    test("loadWithProvenance tracks per-field source across composite source") {
      val env      = ConfigSource.fromMap(Map("db.host" -> "env-host"), "env")
      val yaml     = ConfigSource.fromMap(Map("db.port" -> "5432", "name" -> "yaml-app"), "yaml")
      val defaults = ConfigSource.fromMap(Map("db.host" -> "default-host", "db.port" -> "3306", "name" -> "default"), "defaults")
      val source   = env.orElse(yaml).orElse(defaults)
      val pm       = Config.loadWithProvenance[AppConfig](source).toOption.get
      assertTrue(
        pm.value == AppConfig(DbConfig("env-host", 5432), "yaml-app"),
        pm.provenanceOf("db.host") == Some(Provenance.Resolved("env", "db.host", Some("env-host"))),
        pm.provenanceOf("db.port") == Some(Provenance.Resolved("yaml", "db.port", Some("5432"))),
        pm.provenanceOf("name") == Some(Provenance.Resolved("yaml", "name", Some("yaml-app")))
      )
    },
    test("four-level chain with each field from different source") {
      val s1 = ConfigSource.fromMap(Map("db.host" -> "s1-host"), "s1")
      val s2 = ConfigSource.fromMap(Map("db.port" -> "9999"), "s2")
      val s3 = ConfigSource.fromMap(Map("name" -> "s3-app"), "s3")
      val s4 = ConfigSource.fromMap(Map("db.host" -> "s4-host", "db.port" -> "1111", "name" -> "s4-app"), "s4")
      val source = s1.orElse(s2).orElse(s3).orElse(s4)
      val pm     = Config.loadWithProvenance[AppConfig](source).toOption.get
      assertTrue(
        pm.value == AppConfig(DbConfig("s1-host", 9999), "s3-app"),
        pm.provenanceOf("db.host").get.sourceId == "s1",
        pm.provenanceOf("db.port").get.sourceId == "s2",
        pm.provenanceOf("name").get.sourceId == "s3"
      )
    }
  )
}
