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

import _root_.zio.test.Assertion.isLeft
import _root_.zio.test._

object HtmxCompileTimeSpec extends ZIOSpecDefault {
  def spec = suite("HtmxCompileTime")(
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
