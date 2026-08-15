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

import zio.test.*

object MaybeScala3Spec extends ZIOSpecDefault {
  private final case class Payload(value: Int)

  def spec = suite("Maybe Scala 3")(
    test("companion methods are callable at runtime") {
      val absent: Maybe[Payload]        = Maybe.Absent
      val presentOption: Maybe[Payload] = Maybe.fromOption(Some(Payload(1)))
      val absentOption: Maybe[Payload]  = Maybe.fromOption(None)

      assertTrue(
        absent.isAbsent,
        absent.toOption == None,
        presentOption.get == Payload(1),
        absentOption.isAbsent
      )
    },
    test("for-comprehension and unzip helpers mirror Option behavior") {
      val present: Maybe[Int]                = Some(2)
      val pair: Maybe[(Int, String)]         = Maybe.present((1, "one"))
      val triple: Maybe[(Int, String, Long)] = Maybe.present((1, "one", 2L))
      val computed                           = for {
        value <- present
        if value % 2 == 0
      } yield value + 1

      assertTrue(
        computed.contains(3),
        pair.unzip == (Maybe.present(1), Maybe.present("one")),
        triple.unzip3 == (Maybe.present(1), Maybe.present("one"), Maybe.present(2L))
      )
    },
    test("present(null) and fromOption(Some(null)) are present-of-absent") {
      val presentNull: Maybe[String] = Maybe.present(null: String)

      assertTrue(
        // 1. Present(null) is distinguishable from Maybe.absent
        presentNull.isPresent,
        presentNull != Maybe.absent[String],
        !presentNull.isAbsent,
        // 2. present(null) matches Present(null)
        (presentNull match { case Present(v) => v == null; case _ => false }),
        // 3. fromOption(Some(null)) is present-of-absent
        Maybe.fromOption(Some(null: String)).isPresent,
        Maybe.fromOption(Some(null: String)).get == null,
        // 4. nested present-of-absent
        (Maybe.present(Maybe.absent[Int]) match { case Present(v) => v.asInstanceOf[Maybe[Int]].isAbsent; case _ => false }),
        Maybe.present(Maybe.absent[Int]).flatten.isAbsent,
        // 5. zero-alloc flat case: raw value, no Present wrapper
        (Maybe.present(1) match { case _: Present[?] => false; case _ => true })
      )
    },
    test("pattern matching distinguishes absent, present, and present-of-absent") {
      def describe(maybe: Maybe[Int]): String = maybe match {
        case Present(v) => s"present ($v)"
        case Absent     => "absent"
      }

      val present: Maybe[Int]             = Maybe.present(1)
      val presentNull: Maybe[String]      = Maybe.present(null: String)
      val nestedAbsent: Maybe[Maybe[Int]] = Maybe.present(Maybe.absent[Int])

      assertTrue(
        // absent does not match the extractor
        (Maybe.absent[Int] match { case Present(_) => false; case _ => true }),
        // raw present(1) extracts v == 1
        describe(Maybe.present(1)) == "present (1)",
        (Maybe.present(1) match { case Present(v) => v == 1; case _ => false }),
        // present(1) is a raw value, not a Present wrapper
        (present match { case _: Present[?] => false; case _ => true }),
        // present(null) extracts v == null
        (presentNull match { case Present(v) => v == null; case _ => false }),
        // present(absent) extracts an absent inner Maybe
        (nestedAbsent match { case Present(v) => v.isAbsent; case _ => false }),
        // fold is the exhaustive, warning-free idiom
        present.fold("absent")(v => s"present ($v)") == "present (1)",
        Maybe.absent[Int].fold("absent")(v => s"present ($v)") == "absent",
        // explicit case Absent
        (Maybe.absent[Int] match { case Absent => true; case _ => false }),
        // bare Present two-case idiom extracts raw values (wildcard import style)
        (Maybe.present(1) match { case Present(v) => v; case Absent => -1 }) == 1,
        (Maybe.absent[Int] match { case Present(_) => -1; case Absent => 0 }) == 0,
        // regression: present(Present(x)) must not corrupt (extra wrap in present)
        (Maybe.present(Present(1)).get == Present(1))
      )
    }
  )
}
