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

package zio.blocks.smithy

import zio.test._

object SmithyParserBoundarySpec extends ZIOSpecDefault {
  def spec = suite("SmithyParserBoundary")(
    test("missing colon after $version returns Left, never throws") {
      val result = SmithyModel.parse("$version \"2\"\nnamespace com.example")
      result match {
        case Left(err) => assertTrue(err.message.contains("Expected ':'"))
        case _         => assertTrue(false)
      }
    },
    test("truncated version declaration returns Left, never throws") {
      val result = SmithyModel.parse("$version:")
      assertTrue(result.isLeft)
    },
    test("missing namespace returns Left, never throws") {
      val result = SmithyModel.parse("$version: \"2\"\n")
      result match {
        case Left(err) => assertTrue(err.message.contains("namespace"))
        case _         => assertTrue(false)
      }
    },
    test("unclosed shape block returns Left, never throws") {
      val input  = "$version: \"2\"\nnamespace com.example\nstructure Foo {"
      val result = SmithyModel.parse(input)
      assertTrue(result.isLeft)
    },
    test("hostile inputs never throw: parse always returns a value") {
      val hostile = List(
        "",
        "$",
        "$version",
        "$version: \"2",
        "$version: \"2\"\nnamespace",
        "$version: \"2\"\nnamespace com.example\nstructure",
        "$version: \"2\"\nnamespace com.example\nstructure Foo { @required",
        new String(Array[Char](0.toChar, 1.toChar, 2.toChar)),
        "namespace com.example",
        "{{{{",
        "$version: \"2\"\nnamespace com.example\n" + ("structure A { a: A }" * 200)
      )
      val outcomes = hostile.map(input => scala.util.Try(SmithyModel.parse(input)))
      assertTrue(
        outcomes.forall(_.isSuccess),
        outcomes.collect { case scala.util.Success(Left(err)) => err }.nonEmpty
      )
    }
  )
}
