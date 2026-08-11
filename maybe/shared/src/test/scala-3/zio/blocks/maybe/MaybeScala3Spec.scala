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
        (Maybe.present(Maybe.absent[Int]) match { case Present(v) => v == null; case _ => false }),
        Maybe.present(Maybe.absent[Int]).flatten.isAbsent,
        // 5. zero-alloc flat case: raw value, no Present wrapper
        (Maybe.present(1) match { case _: Present[?] => false; case _ => true })
      )
    }
  )
}
