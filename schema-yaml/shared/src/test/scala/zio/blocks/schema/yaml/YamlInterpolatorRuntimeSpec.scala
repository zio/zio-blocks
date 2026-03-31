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

package zio.blocks.schema.yaml

import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object YamlInterpolatorRuntimeSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("YamlInterpolatorRuntime")(
    suite("validateYamlLiteral")(
      test("valid YAML with no args passes") {
        val sc = new StringContext("name: Alice")
        YamlInterpolatorRuntime.validateYamlLiteral(sc, Seq.empty)
        assertTrue(true)
      },
      test("valid YAML with key placeholder") {
        val sc = new StringContext("", ": value")
        YamlInterpolatorRuntime.validateYamlLiteral(sc, Seq(YamlInterpolationContext.Key))
        assertTrue(true)
      },
      test("valid YAML with value placeholder") {
        val sc = new StringContext("key: ", "")
        YamlInterpolatorRuntime.validateYamlLiteral(sc, Seq(YamlInterpolationContext.Value))
        assertTrue(true)
      },
      test("valid YAML with InString placeholder") {
        val sc = new StringContext("key: \"hello ", "\"")
        YamlInterpolatorRuntime.validateYamlLiteral(sc, Seq(YamlInterpolationContext.InString))
        assertTrue(true)
      },
      test("malformed YAML is handled without unrecoverable error") {
        val sc        = new StringContext("{invalid: [broken")
        val completed = try {
          YamlInterpolatorRuntime.validateYamlLiteral(sc, Seq.empty)
          true
        } catch {
          case _: YamlCodecError => true
          case _: Throwable      => false
        }
        assertTrue(completed)
      }
    ),
    suite("yamlWithContexts")(
      test("key context with String arg") {
        val sc     = new StringContext("", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(sc, Seq("mykey"), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with String arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(sc, Seq("myvalue"), Seq(YamlInterpolationContext.Value))
        assertTrue(
          result == Yaml.Mapping.fromStringKeys("key" -> Yaml.Scalar("myvalue"))
        )
      },
      test("value context with Int arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(42: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Boolean arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(true: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Long arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(123L: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Float arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(1.5f: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Double arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(3.14: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with null arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(null: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Yaml arg") {
        val sc      = new StringContext("key: ", "")
        val yamlVal = Yaml.Mapping.fromStringKeys("nested" -> Yaml.Scalar("v"))
        val result  =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(yamlVal: Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Char arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq('A': Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with BigDecimal arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(BigDecimal("3.14"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with BigInt arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(BigInt(42): Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Unit arg") {
        val sc     = new StringContext("key: ", "")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq((): Any), Seq(YamlInterpolationContext.Value))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Byte arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(42.toByte: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Short arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(42.toShort: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Option Some arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(Some("inner"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Option None arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(None: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Iterable arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(List("a", "b"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with Array arg") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(Array("a", "b"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("value context with custom type falls through to toString") {
        val sc     = new StringContext("key: ", "")
        val obj    = new Object { override def toString = "custom-value" }
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(obj: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with String arg") {
        val sc     = new StringContext("key: \"hello ", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq("world"), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Int arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(42: Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Char arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq('X': Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Boolean arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(true: Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Byte arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(5.toByte: Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Short arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(5.toShort: Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Long arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(100L: Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Float arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(1.5f: Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Double arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(3.14: Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with BigDecimal arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(BigDecimal("1.5"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with BigInt arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(BigInt(42): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("InString context with Unit arg") {
        val sc     = new StringContext("key: \"val", "\"")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq((): Any), Seq(YamlInterpolationContext.InString))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Char arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq('K': Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Boolean arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(true: Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Byte arg") {
        val sc     = new StringContext("", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(1.toByte: Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Short arg") {
        val sc     = new StringContext("", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(1.toShort: Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Int arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(42: Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Long arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(100L: Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Float arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(1.5f: Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Double arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(3.14: Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with BigDecimal arg") {
        val sc     = new StringContext("", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(BigDecimal("3.14"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with BigInt arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq(BigInt(42): Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with Unit arg") {
        val sc     = new StringContext("", ": value")
        val result =
          YamlInterpolatorRuntime.yamlWithContexts(sc, Seq((): Any), Seq(YamlInterpolationContext.Key))
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("key context with unexpected type throws") {
        val sc     = new StringContext("", ": value")
        val caught = try {
          YamlInterpolatorRuntime.yamlWithContexts(
            sc,
            Seq(List(1, 2, 3): Any),
            Seq(YamlInterpolationContext.Key)
          )
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(caught)
      },
      test("InString context with unexpected type throws") {
        val sc     = new StringContext("key: \"", "\"")
        val caught = try {
          YamlInterpolatorRuntime.yamlWithContexts(
            sc,
            Seq(List(1, 2, 3): Any),
            Seq(YamlInterpolationContext.InString)
          )
          false
        } catch {
          case _: IllegalArgumentException => true
          case _: Throwable                => false
        }
        assertTrue(caught)
      }
    ),
    suite("java.time types in key context")(
      test("Duration in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Duration.ofSeconds(60): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("DayOfWeek in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.DayOfWeek.MONDAY: Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("Instant in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Instant.parse("2024-01-01T00:00:00Z"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("LocalDate in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalDate.of(2024, 1, 1): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("LocalDateTime in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalDateTime.of(2024, 1, 1, 12, 0): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("LocalTime in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalTime.of(12, 30): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("Month in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Month.JANUARY: Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("MonthDay in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.MonthDay.of(1, 15): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("OffsetDateTime in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.OffsetDateTime.parse("2024-01-01T00:00:00+00:00"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("OffsetTime in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.OffsetTime.parse("12:00:00+00:00"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("Period in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Period.ofDays(30): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("Year in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Year.of(2024): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("YearMonth in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.YearMonth.of(2024, 6): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("ZoneOffset in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZoneOffset.UTC: Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("ZoneId in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZoneId.of("UTC"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("ZonedDateTime in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZonedDateTime.parse("2024-01-01T00:00:00Z[UTC]"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("Currency in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.util.Currency.getInstance("USD"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("UUID in key") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"): Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      }
    ),
    suite("java.time types in value context")(
      test("DayOfWeek in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.DayOfWeek.MONDAY: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Duration in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Duration.ofSeconds(60): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Instant in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Instant.parse("2024-01-01T00:00:00Z"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("LocalDate in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalDate.of(2024, 1, 1): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("LocalDateTime in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalDateTime.of(2024, 1, 1, 12, 0): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("LocalTime in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalTime.of(12, 30): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Month in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Month.JANUARY: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("MonthDay in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.MonthDay.of(1, 15): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("OffsetDateTime in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.OffsetDateTime.parse("2024-01-01T00:00:00+00:00"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("OffsetTime in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.OffsetTime.parse("12:00:00+00:00"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Period in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Period.ofDays(30): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Year in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Year.of(2024): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("YearMonth in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.YearMonth.of(2024, 6): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("ZoneOffset in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZoneOffset.UTC: Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("ZoneId in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZoneId.of("UTC"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("ZonedDateTime in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZonedDateTime.parse("2024-01-01T00:00:00Z[UTC]"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Currency in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.util.Currency.getInstance("USD"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("UUID in value") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"): Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      }
    ),
    suite("java.time types in InString context")(
      test("Duration in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Duration.ofSeconds(60): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("DayOfWeek in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.DayOfWeek.MONDAY: Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Instant in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Instant.parse("2024-01-01T00:00:00Z"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("LocalDate in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalDate.of(2024, 1, 1): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("LocalDateTime in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalDateTime.of(2024, 1, 1, 12, 0): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("LocalTime in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.LocalTime.of(12, 30): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Month in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Month.JANUARY: Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("MonthDay in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.MonthDay.of(1, 15): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("OffsetDateTime in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.OffsetDateTime.parse("2024-01-01T00:00:00+00:00"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("OffsetTime in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.OffsetTime.parse("12:00:00+00:00"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Period in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Period.ofDays(30): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Year in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.Year.of(2024): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("YearMonth in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.YearMonth.of(2024, 6): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("ZoneOffset in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZoneOffset.UTC: Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("ZoneId in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZoneId.of("UTC"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("ZonedDateTime in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.time.ZonedDateTime.parse("2024-01-01T00:00:00Z[UTC]"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("Currency in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.util.Currency.getInstance("USD"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("UUID in string") {
        val sc     = new StringContext("key: \"", "\"")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq(java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000"): Any),
          Seq(YamlInterpolationContext.InString)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      }
    ),
    suite("needsQuoting in interpolator")(
      test("quoting special values in key position") {
        val sc     = new StringContext("key-", ": value")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq("null": Any),
          Seq(YamlInterpolationContext.Key)
        )
        assertTrue(result != null)
      },
      test("quoting empty string in value position") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq("": Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("quoting string with control char") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq("a\u0001b": Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("quoting string with colon-space") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq("val: ue": Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      },
      test("quoting string with space-hash") {
        val sc     = new StringContext("key: ", "")
        val result = YamlInterpolatorRuntime.yamlWithContexts(
          sc,
          Seq("val #comment": Any),
          Seq(YamlInterpolationContext.Value)
        )
        assertTrue(result.isInstanceOf[Yaml.Mapping])
      }
    )
  )
}
