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

package zio.blocks.scope

import zio.test._
import zio.blocks.chunk.Chunk

object FinalizationSpec extends ZIOSpecDefault {

  def spec = suite("Finalization")(
    test("empty has no errors") {
      val fin = Finalization.empty
      assertTrue(
        fin.errors.isEmpty,
        fin.isEmpty,
        !fin.nonEmpty
      )
    },
    test("orThrow on empty does nothing") {
      Finalization.empty.orThrow()
      assertCompletes
    },
    test("orThrow with single error throws it with no suppressed") {
      val error  = new RuntimeException("boom")
      val fin    = Finalization(Chunk(error))
      val caught = try {
        fin.orThrow()
        null: Throwable
      } catch {
        case t: Throwable => t
      }
      assertTrue(
        caught eq error,
        caught.getSuppressed.isEmpty
      )
    },
    test("orThrow with multiple errors throws first with rest as suppressed") {
      val e1     = new RuntimeException("first")
      val e2     = new RuntimeException("second")
      val e3     = new RuntimeException("third")
      val fin    = Finalization(Chunk(e1, e2, e3))
      val caught = try {
        fin.orThrow()
        null: Throwable
      } catch {
        case t: Throwable => t
      }
      assertTrue(
        caught eq e1,
        caught.getSuppressed.length == 2,
        caught.getSuppressed()(0) eq e2,
        caught.getSuppressed()(1) eq e3
      )
    },
    test("suppress on empty returns initial unchanged with no suppressed") {
      val initial = new RuntimeException("primary")
      val result  = Finalization.empty.suppress(initial)
      assertTrue(
        result eq initial,
        result.getSuppressed.isEmpty
      )
    },
    test("suppress with single error adds it as suppressed to initial") {
      val initial = new RuntimeException("primary")
      val error   = new RuntimeException("fin1")
      val fin     = Finalization(Chunk(error))
      val result  = fin.suppress(initial)
      assertTrue(
        result eq initial,
        result.getSuppressed.length == 1,
        result.getSuppressed()(0) eq error
      )
    },
    test("suppress with multiple errors adds all as suppressed to initial") {
      val initial = new RuntimeException("primary")
      val e1      = new RuntimeException("fin1")
      val e2      = new RuntimeException("fin2")
      val e3      = new RuntimeException("fin3")
      val fin     = Finalization(Chunk(e1, e2, e3))
      val result  = fin.suppress(initial)
      assertTrue(
        result eq initial,
        result.getSuppressed.length == 3,
        result.getSuppressed()(0) eq e1,
        result.getSuppressed()(1) eq e2,
        result.getSuppressed()(2) eq e3
      )
    },
    test("suppress returns the same reference") {
      val initial = new RuntimeException("primary")
      val fin     = Finalization(Chunk(new RuntimeException("err")))
      val result  = fin.suppress(initial)
      assertTrue(result eq initial)
    }
  )
}
