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

package zio.blocks.telemetry

import zio.test._

object ContextStorageSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("ContextStorage")(
    suite("factory")(
      test("create returns a ContextStorage") {
        val storage = ContextStorage.create("initial")
        assertTrue(storage != null)
      },
      test("implementationName is ScopedValue") {
        assertTrue(ContextStorage.implementationName == "ScopedValue")
      }
    ),
    suite("get")(
      test("returns initial value") {
        val storage = ContextStorage.create("initial")
        assertTrue(storage.get() == "initial")
      },
      test("returns initial value for numeric type") {
        val storage = ContextStorage.create(42)
        assertTrue(storage.get() == 42)
      }
    ),
    suite("scoped")(
      test("get returns scoped value inside block") {
        val storage = ContextStorage.create("initial")
        val inside  = storage.scoped("scoped") {
          storage.get()
        }
        assertTrue(inside == "scoped")
      },
      test("get returns previous value after scoped block") {
        val storage = ContextStorage.create("initial")
        storage.scoped("scoped") {
          ()
        }
        assertTrue(storage.get() == "initial")
      },
      test("scoped restores after exception") {
        val storage = ContextStorage.create("initial")
        try {
          storage.scoped("scoped") {
            throw new RuntimeException("boom")
          }
        } catch {
          case _: RuntimeException => ()
        }
        assertTrue(storage.get() == "initial")
      },
      test("nested scoped blocks restore correctly - 3 levels deep") {
        val storage      = ContextStorage.create("L0")
        val r0Before     = storage.get()
        val (r1, r2, r3) = storage.scoped("L1") {
          val v1       = storage.get()
          val (v2, v3) = storage.scoped("L2") {
            val vv2 = storage.get()
            val vv3 = storage.scoped("L3") {
              storage.get()
            }
            assertTrue(storage.get() == "L2")
            (vv2, vv3)
          }
          assertTrue(storage.get() == "L1")
          (v1, v2, v3)
        }
        assertTrue(
          r0Before == "L0" &&
            r1 == "L1" &&
            r2 == "L2" &&
            r3 == "L3" &&
            storage.get() == "L0"
        )
      },
      test("scoped returns the block result") {
        val storage = ContextStorage.create(0)
        val result  = storage.scoped(42) {
          storage.get() * 2
        }
        assertTrue(result == 84)
      }
    ),
    suite("isolation")(
      test("implementationName reports correctly") {
        assertTrue(ContextStorage.implementationName == "ScopedValue")
      },
      test("scoped on one storage does not affect another") {
        val s1     = ContextStorage.create("a")
        val s2     = ContextStorage.create("b")
        val result = s1.scoped("x") {
          s1.get() -> s2.get()
        }
        assertTrue(result == ("x" -> "b"))
      }
    )
  )
}
