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

package golem.wasi

import org.scalatest.funsuite.AnyFunSuite

class ConfigCompileSpec extends AnyFunSuite {
  import Config._

  private val errors: List[ConfigError] = List(
    ConfigError.Upstream("upstream error"),
    ConfigError.Io("io error")
  )

  private def describeError(e: ConfigError): String = e match {
    case ConfigError.Upstream(msg) => s"upstream($msg)"
    case ConfigError.Io(msg)       => s"io($msg)"
  }

  test("ConfigError exhaustive match") {
    errors.foreach(e => assert(describeError(e).nonEmpty))
  }

  test("ConfigError field access") {
    assert(errors.head.asInstanceOf[ConfigError.Upstream].message == "upstream error")
    assert(errors(1).asInstanceOf[ConfigError.Io].message == "io error")
  }

  test("Either result type usage") {
    val result: Either[ConfigError, Option[String]]         = Right(Some("value"))
    val allResult: Either[ConfigError, Map[String, String]] = Right(Map("k" -> "v"))

    result match {
      case Right(Some(v))                  => assert(v == "value")
      case Right(None)                     => fail("expected Some")
      case Left(ConfigError.Upstream(msg)) => fail(msg)
      case Left(ConfigError.Io(msg))       => fail(msg)
    }

    allResult match {
      case Right(m) => assert(m.size == 1)
      case Left(_)  => fail("expected Right")
    }
  }
}
