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

import zio.test._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.MILLISECONDS
import scala.concurrent.duration.SECONDS
import scala.concurrent.duration.MINUTES
import scala.concurrent.duration.HOURS
import scala.concurrent.duration.DAYS

object FlagReaderSpec extends ZIOSpecDefault {
  import Flag.Reader

  def spec = suite("FlagReaderSpec")(
    suite("Int reader")(
      test("parse valid integer") {
        val result = Reader.intReader.parse("port", "8080")
        assertTrue(result == Right(8080))
      },
      test("parse negative integer") {
        val result = Reader.intReader.parse("count", "-42")
        assertTrue(result == Right(-42))
      },
      test("parse zero") {
        val result = Reader.intReader.parse("value", "0")
        assertTrue(result == Right(0))
      },
      test("fail on non-numeric") {
        val result = Reader.intReader.parse("port", "abc")
        assertTrue(result.isLeft)
      },
      test("fail on decimal") {
        val result = Reader.intReader.parse("count", "3.14")
        assertTrue(result.isLeft)
      }
    ),
    suite("Long reader")(
      test("parse valid long") {
        val result = Reader.longReader.parse("timestamp", "1234567890")
        assertTrue(result == Right(1234567890L))
      },
      test("parse large long") {
        val result = Reader.longReader.parse("bignum", "9223372036854775807")
        assertTrue(result == Right(9223372036854775807L))
      },
      test("fail on non-numeric") {
        val result = Reader.longReader.parse("timestamp", "not-a-number")
        assertTrue(result.isLeft)
      }
    ),
    suite("Double reader")(
      test("parse valid double") {
        val result = Reader.doubleReader.parse("ratio", "3.14")
        assertTrue(result == Right(3.14))
      },
      test("parse integer as double") {
        val result = Reader.doubleReader.parse("value", "42")
        assertTrue(result == Right(42.0))
      },
      test("parse scientific notation") {
        val result = Reader.doubleReader.parse("exponent", "1.5e-10")
        assertTrue(result == Right(1.5e-10))
      },
      test("fail on non-numeric") {
        val result = Reader.doubleReader.parse("ratio", "pi")
        assertTrue(result.isLeft)
      }
    ),
    suite("Boolean reader")(
      test("parse 'true'") {
        val result = Reader.booleanReader.parse("enabled", "true")
        assertTrue(result == Right(true))
      },
      test("parse 'false'") {
        val result = Reader.booleanReader.parse("enabled", "false")
        assertTrue(result == Right(false))
      },
      test("parse 'TRUE' (case-insensitive)") {
        val result = Reader.booleanReader.parse("enabled", "TRUE")
        assertTrue(result == Right(true))
      },
      test("parse 'False' (case-insensitive)") {
        val result = Reader.booleanReader.parse("enabled", "False")
        assertTrue(result == Right(false))
      },
      test("parse '1' as true") {
        val result = Reader.booleanReader.parse("enabled", "1")
        assertTrue(result == Right(true))
      },
      test("parse '0' as false") {
        val result = Reader.booleanReader.parse("enabled", "0")
        assertTrue(result == Right(false))
      },
      test("fail on invalid value") {
        val result = Reader.booleanReader.parse("enabled", "maybe")
        assertTrue(result.isLeft)
      }
    ),
    suite("String reader")(
      test("parse any string") {
        val result = Reader.stringReader.parse("name", "hello world")
        assertTrue(result == Right("hello world"))
      },
      test("parse empty string") {
        val result = Reader.stringReader.parse("name", "")
        assertTrue(result == Right(""))
      },
      test("parse string with special characters") {
        val result = Reader.stringReader.parse("path", "/usr/local/bin")
        assertTrue(result == Right("/usr/local/bin"))
      }
    ),
    suite("Seq reader")(
      test("parse comma-separated integers") {
        val result = Reader.seqReader[Int].parse("ports", "8080,8081,8082")
        assertTrue(result == Right(Seq(8080, 8081, 8082)))
      },
      test("parse single value") {
        val result = Reader.seqReader[Int].parse("ports", "8080")
        assertTrue(result == Right(Seq(8080)))
      },
      test("parse with whitespace around commas") {
        val result = Reader.seqReader[Int].parse("ports", "8080 , 8081 , 8082")
        assertTrue(result == Right(Seq(8080, 8081, 8082)))
      },
      test("parse empty string as empty seq") {
        val result = Reader.seqReader[Int].parse("ports", "")
        assertTrue(result == Right(Seq.empty))
      },
      test("fail on invalid element") {
        val result = Reader.seqReader[Int].parse("ports", "8080,abc,8082")
        assertTrue(result.isLeft)
      },
      test("parse comma-separated strings") {
        val result = Reader.seqReader[String].parse("names", "alice,bob,charlie")
        assertTrue(result == Right(Seq("alice", "bob", "charlie")))
      },
      test("parse comma-separated booleans") {
        val result = Reader.seqReader[Boolean].parse("flags", "true,false,1,0")
        assertTrue(result == Right(Seq(true, false, true, false)))
      }
    ),
    suite("Duration reader")(
      test("parse milliseconds (ms)") {
        val result = Reader.durationReader.parse("timeout", "100ms")
        assertTrue(result == Right(new FiniteDuration(100, MILLISECONDS)))
      },
      test("parse seconds (s)") {
        val result = Reader.durationReader.parse("timeout", "10s")
        assertTrue(result == Right(new FiniteDuration(10, SECONDS)))
      },
      test("parse minutes (m)") {
        val result = Reader.durationReader.parse("timeout", "5m")
        assertTrue(result == Right(new FiniteDuration(5, MINUTES)))
      },
      test("parse hours (h)") {
        val result = Reader.durationReader.parse("timeout", "2h")
        assertTrue(result == Right(new FiniteDuration(2, HOURS)))
      },
      test("parse days (d)") {
        val result = Reader.durationReader.parse("timeout", "1d")
        assertTrue(result == Right(new FiniteDuration(1, DAYS)))
      },
      test("parse seconds (sec)") {
        val result = Reader.durationReader.parse("timeout", "30sec")
        assertTrue(result == Right(new FiniteDuration(30, SECONDS)))
      },
      test("parse minutes (min)") {
        val result = Reader.durationReader.parse("timeout", "15min")
        assertTrue(result == Right(new FiniteDuration(15, MINUTES)))
      },
      test("parse hours (hour)") {
        val result = Reader.durationReader.parse("timeout", "3hour")
        assertTrue(result == Right(new FiniteDuration(3, HOURS)))
      },
      test("parse days (day)") {
        val result = Reader.durationReader.parse("timeout", "7day")
        assertTrue(result == Right(new FiniteDuration(7, DAYS)))
      },
      test("parse case-insensitive units") {
        val result = Reader.durationReader.parse("timeout", "10S")
        assertTrue(result == Right(new FiniteDuration(10, SECONDS)))
      },
      test("fail on invalid format") {
        val result = Reader.durationReader.parse("timeout", "10")
        assertTrue(result.isLeft)
      },
      test("fail on unknown unit") {
        val result = Reader.durationReader.parse("timeout", "10x")
        assertTrue(result.isLeft)
      },
      test("fail on non-numeric value") {
        val result = Reader.durationReader.parse("timeout", "abcs")
        assertTrue(result.isLeft)
      }
    ),
    suite("Error messages")(
      test("Int reader includes flag name and type") {
        val result = Reader.intReader.parse("myPort", "invalid")
        assertTrue(result match {
          case Left(ConfigError.InvalidValue(path, value, expectedType, source, _)) =>
            path == "myPort" && value == "invalid" && expectedType == "Int" && source == "flag"
          case _ => false
        })
      },
      test("Duration reader includes helpful type hint") {
        val result = Reader.durationReader.parse("timeout", "invalid")
        assertTrue(result match {
          case Left(ConfigError.InvalidValue(_, _, expectedType, _, _)) =>
            expectedType.contains("Duration") && expectedType.contains("10s")
          case _ => false
        })
      }
    ),
    suite("Scalar marker")(
      test("Int reader is Scalar") {
        assertTrue(Reader.intReader.isInstanceOf[Reader.Scalar[_]])
      },
      test("String reader is Scalar") {
        assertTrue(Reader.stringReader.isInstanceOf[Reader.Scalar[_]])
      },
      test("Duration reader is Scalar") {
        assertTrue(Reader.durationReader.isInstanceOf[Reader.Scalar[_]])
      }
    )
  )
}
