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

import scala.language.implicitConversions

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
    },
    test("option-style combinators behave like Option") {
      val present: Maybe[Int] = Maybe.present(2)
      val absent: Maybe[Int]  = Maybe.absent
      var seen                = 0
      present.foreach(seen = _)
      absent.foreach(_ => seen = 99)

      assertTrue(
        seen == 2,
        present.contains(2),
        !present.contains(3),
        !absent.contains(2),
        present.exists(_ % 2 == 0),
        !absent.exists(_ => true),
        present.forall(_ % 2 == 0),
        absent.forall(_ => false),
        present.filter(_ % 2 == 0).contains(2),
        present.filter(_ % 2 != 0).isAbsent,
        present.filterNot(_ % 2 == 0).isAbsent,
        present.filterNot(_ % 2 != 0).contains(2),
        present.collect { case value if value % 2 == 0 => value + 1 }.contains(3),
        present.collect { case value if value % 2 != 0 => value + 1 }.isAbsent,
        present.orElse(Maybe.present(9)).contains(2),
        absent.orElse(Maybe.present(9)).contains(9),
        present.toList == List(2),
        absent.toList.isEmpty,
        present.toSeq == Seq(2),
        absent.toSeq.isEmpty,
        present.iterator.toList == List(2),
        absent.iterator.toList.isEmpty,
        present.toRight("missing").fold(_ => false, _ == 2),
        absent.toRight("missing") == Left("missing"),
        present.toLeft("fallback").fold(_ == 2, _ => false),
        absent.toLeft("fallback") == Right("fallback")
      )
    },
    test("flatten unwraps nested Maybe via fromOption") {
      val someValue: Maybe[Maybe[Int]] = Maybe.fromOption(Some(Maybe.fromOption(Some(42))))
      val noneValue: Maybe[Maybe[Int]] = Maybe.fromOption(Some(Maybe.absent))
      val absent: Maybe[Maybe[Int]]    = Maybe.absent

      assertTrue(
        someValue.flatten.contains(42),
        noneValue.flatten.isAbsent,
        absent.flatten.isAbsent
      )
    },
    test("zip and Option conversion helpers work") {
      val someValue: Maybe[Int] = Maybe.fromOption(Some(5))
      val noneValue: Maybe[Int] = Maybe.fromOption(None)

      assertTrue(
        Maybe.present(1).zip(Maybe.present("a")).contains((1, "a")),
        Maybe.present(1).zip(Maybe.absent[String]).isAbsent,
        someValue.contains(5),
        noneValue.isAbsent
      )
    }
  )
}
