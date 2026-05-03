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

package zio.blocks.maybe

import zio.test._

object MaybeSpec extends ZIOSpecDefault {
  private final case class Payload(value: Int)
  private final case class Label(value: String)

  def spec = suite("Maybe")(
    test("present value exposes all present operations") {
      val value: Maybe[Payload] = Maybe.present(Payload(1))
      assertTrue(
        value.isPresent,
        value.isDefined,
        !value.isAbsent,
        !value.isEmpty,
        value.get == Payload(1),
        value.getOrElse(Payload(2)) == Payload(1),
        value.toOption.nonEmpty,
        value.toOption.get == Payload(1)
      )
    },
    test("absent value exposes all absent operations") {
      val value: Maybe[Payload] = Maybe.absent
      assertTrue(
        value.isAbsent,
        value.isEmpty,
        !value.isPresent,
        !value.isDefined,
        value.toOption == None,
        value.getOrElse(Payload(2)) == Payload(2)
      )
    },
    test("absent get throws") {
      val value: Maybe[Payload] = Maybe.absent
      val thrown                = scala.util.Try(value.get).failed.toOption
      assertTrue(
        thrown.exists(_.isInstanceOf[NoSuchElementException]),
        thrown.map(_.getMessage).contains("Maybe.absent.get")
      )
    },
    test("constructors preserve semantics") {
      val present: Maybe[Payload] = Maybe.fromOption(Some(Payload(42)))
      val absent: Maybe[Payload]  = Maybe.fromOption(None)
      val missing: Maybe[Payload] = Maybe.absent
      assertTrue(
        present.get == Payload(42),
        present.isPresent,
        absent.isAbsent,
        missing.isAbsent
      )
    },
    test("map and flatMap preserve absent/present semantics") {
      val present: Maybe[Payload] = Maybe.present(Payload(1))
      val absent: Maybe[Payload]  = Maybe.absent
      assertTrue(
        present.map(payload => Label((payload.value + 1).toString)).get == Label("2"),
        present.map(payload => Label((payload.value + 1).toString)).isPresent,
        present.flatMap(payload => Maybe.present(Label((payload.value + 2).toString))).get == Label("3"),
        present.flatMap(_ => Maybe.absent[Label]).isAbsent,
        absent.map(payload => Label((payload.value + 1).toString)).isAbsent,
        absent.flatMap(payload => Maybe.present(Label((payload.value + 2).toString))).isAbsent
      )
    }
  )
}
