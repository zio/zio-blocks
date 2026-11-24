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

package zio.http.htmx

import _root_.zio.test.Assertion.{isLeft, isRight}
import _root_.zio.test._

object HtmxCompileTimeSpec extends ZIOSpecDefault {
  private val fromStringError: String =
    "None of the overloaded alternatives of method from in class HxTrigger with types\n" +
      " (target: zio.http.htmx.HxTarget): zio.http.htmx.HxTrigger\n" +
      " (selector: zio.blocks.html.CssSelector): zio.http.htmx.HxTrigger\n" +
      "match arguments ((\"#source\" : String))"

  private val targetStringError390: String =
    "Found:    (\".save\" : String)\n" +
      "Required: zio.blocks.html.CssSelector"

  private val targetStringError337: String =
    "Found:    (\".save\" : String)\n" +
      "Required: zio.blocks.html.CssSelector\n" +
      "\n" +
      "The following import might make progress towards fixing the problem:\n" +
      "\n" +
      "  import izumi.reflect.dottyreflection.ReflectionUtil.\n" +
      "  reflectiveUncheckedNonOverloadedSelectable\n" +
      "\n"

  private val rootStringError390: String =
    "Found:    (\"main\" : String)\n" +
      "Required: zio.blocks.html.CssSelector"

  private val rootStringError337: String =
    "Found:    (\"main\" : String)\n" +
      "Required: zio.blocks.html.CssSelector\n" +
      "\n" +
      "The following import might make progress towards fixing the problem:\n" +
      "\n" +
      "  import izumi.reflect.dottyreflection.ReflectionUtil.\n" +
      "  reflectiveUncheckedNonOverloadedSelectable\n" +
      "\n"

  def spec = suite("HtmxCompileTime")(
    test("hxTrigger from rejects raw String") {
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.from("#source")
        """)
      )(Assertion.equalTo(Left(fromStringError)))
    },
    test("hxTrigger target rejects raw String") {
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.target(".save")
        """).map(result => result == Left(targetStringError390) || result == Left(targetStringError337))
      )(Assertion.isTrue)
    },
    test("hxTrigger root rejects raw String") {
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.root("main")
        """).map(result => result == Left(rootStringError390) || result == Left(rootStringError337))
      )(Assertion.isTrue)
    },
    test("hxTrigger from/target/root accept typed and raw forms") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html.CssSelector
          import zio.http.htmx._

          HxTrigger.click.from(CssSelector.id("source"))
        """)
      )(isRight) &&
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.fromRaw("#source")
        """)
      )(isRight) &&
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html.CssSelector
          import zio.http.htmx._

          HxTrigger.click.target(CssSelector.`class`("save"))
        """)
      )(isRight) &&
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.targetRaw(".save")
        """)
      )(isRight) &&
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html.CssSelector
          import zio.http.htmx._

          HxTrigger.click.root(CssSelector.element("main"))
        """)
      )(isRight) &&
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.rootRaw("main")
        """)
      )(isRight)
    },
    test("hxTrigger from accepts HxTarget") {
      assertZIO(
        typeCheck("""
          import zio.http.htmx._

          HxTrigger.click.from(HxTarget.closest("form"))
        """)
      )(isRight)
    },
    test("hxSwap rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxSwap := "outerHTML")
        """)
      )(isLeft)
    },
    test("hxTarget rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxTarget := "#result")
        """)
      )(isLeft)
    },
    test("hxSelect rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxSelect := "#result")
        """)
      )(isLeft)
    },
    test("hxVals rejects raw Json strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxVals := "{}")
        """)
      )(isLeft)
    },
    test("hxHeaders rejects raw Json strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxHeaders := "{}")
        """)
      )(isLeft)
    },
    test("hxSync rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxSync := "closest form:abort")
        """)
      )(isLeft)
    },
    test("hxTrigger rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxTrigger := "click")
        """)
      )(isLeft)
    },
    test("hxParams rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          div(hxParams := "page,sort")
        """)
      )(isLeft)
    },
    test("hxEncoding rejects raw strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          form(hxEncoding := "multipart/form-data")
        """)
      )(isLeft)
    },
    test("request URL attributes still accept strings") {
      assertZIO(
        typeCheck("""
          import _root_.zio.blocks.html._
          import zio.http.htmx._

          form(hxPost := "/search")
        """)
      )(Assertion.isRight)
    }
  )
}
