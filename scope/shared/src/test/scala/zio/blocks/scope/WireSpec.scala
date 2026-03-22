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
import zio.blocks.context.Context

object WireSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  class Database(@scala.annotation.unused config: Config) extends AutoCloseable {
    var closed        = false
    def close(): Unit = closed = true
  }

  def spec = suite("Wire")(
    test("Wire(...) creates shared wire") {
      val wire = Wire(Config(true))
      assertTrue(wire.isInstanceOf[Wire.Shared[?, ?]])
    },
    test("Wire(...) construction works") {
      val wire  = Wire(Config(true))
      val debug = Scope.global.scoped { scope =>
        wire.make(scope, Context.empty).debug
      }
      assertTrue(debug)
    },
    test("Wire.Shared constructs value") {
      val wire: Wire.Shared[Any, Config] = Wire.Shared.fromFunction[Any, Config] { (_, _) =>
        Config(debug = true)
      }
      val debug = Scope.global.scoped { scope =>
        wire.make(scope, Context.empty).debug
      }
      assertTrue(debug)
    },
    test("Wire.Unique constructs value") {
      val wire: Wire.Unique[Any, Config] = Wire.Unique.fromFunction[Any, Config] { (_, _) =>
        Config(debug = false)
      }
      val debug = Scope.global.scoped { scope =>
        wire.make(scope, Context.empty).debug
      }
      assertTrue(!debug)
    },
    test("Wire.isShared and isUnique") {
      val sharedWire = Wire(Config(true))
      val uniqueWire = sharedWire.unique
      assertTrue(sharedWire.isShared, !sharedWire.isUnique)
      assertTrue(!uniqueWire.isShared, uniqueWire.isUnique)
    },
    test("Wire.shared and unique conversions") {
      val sharedWire   = Wire(Config(true))
      val uniqueWire   = sharedWire.unique
      val backToShared = uniqueWire.shared
      assertTrue(sharedWire.isShared, uniqueWire.isUnique, backToShared.isShared)
    },
    test("Wire.Unique.unique returns self") {
      val sharedWire = Wire(Config(true))
      val uniqueWire = sharedWire.unique
      val sameWire   = uniqueWire.unique
      assertTrue(uniqueWire eq sameWire)
    },
    test("Wire.Shared.shared returns self") {
      val sharedWire = Wire(Config(true))
      val sameWire   = sharedWire.shared
      assertTrue(sharedWire eq sameWire)
    },
    suite("toResource")(
      test("Wire with no deps uses toResource(Context.empty)") {
        val wire     = Wire(Config(true))
        val resource = wire.toResource(Context.empty)
        val debug    = Scope.global.scoped { scope =>
          resource.make(scope).debug
        }
        assertTrue(debug)
      },
      test("Wire.Unique.toResource with Context creates unique resource") {
        var counter = 0
        val wire    = Wire.Unique.fromFunction[Config, Int] { (_, _) =>
          counter += 1
          counter
        }
        val deps     = Context[Config](Config(true))
        val resource = wire.toResource(deps)
        val (a, b)   = Scope.global.scoped { scope =>
          val a = resource.make(scope)
          val b = resource.make(scope)
          (a, b)
        }
        assertTrue(a == 1, b == 2)
      },
      test("Wire(value) auto-finalizes AutoCloseable") {
        var closed = false
        class Closeable extends AutoCloseable {
          def close(): Unit = closed = true
        }
        val closeable    = new Closeable
        val wire         = Wire(closeable)
        val closedBefore = Scope.global.scoped { scope =>
          wire.make(scope, Context.empty)
          closed
        }
        assertTrue(!closedBefore, closed)
      }
    )
  )
}
