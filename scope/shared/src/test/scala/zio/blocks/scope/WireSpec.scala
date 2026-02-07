package zio.blocks.scope

import zio.test._
import zio.blocks.context.Context
import zio.blocks.scope.internal.Finalizers

object WireSpec extends ZIOSpecDefault {

  case class Config(debug: Boolean)

  def spec = suite("Wire")(
    test("Wire(...) creates shared wire") {
      val wire = Wire(Config(true))
      assertTrue(wire.isInstanceOf[Wire.Shared[_, _]])
    },
    test("Wire(...) construction works") {
      val wire       = Wire(Config(true))
      val parent     = Scope.global
      val finalizers = new Finalizers
      val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val config     = wire.make(scope)
      assertTrue(config.debug)
    },
    test("Wire.Shared constructs value") {
      val wire: Wire.Shared[Any, Config] = Wire.Shared.fromFunction[Any, Config] { _ =>
        Config(debug = true)
      }
      val parent     = Scope.global
      val finalizers = new Finalizers
      val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val config     = wire.make(scope)
      assertTrue(config.debug)
    },
    test("Wire.Unique constructs value") {
      val wire: Wire.Unique[Any, Config] = Wire.Unique.fromFunction[Any, Config] { _ =>
        Config(debug = false)
      }
      val parent     = Scope.global
      val finalizers = new Finalizers
      val scope      = Scope.makeCloseable[Any, Scope.Global](parent, Context.empty, finalizers)
      val config     = wire.make(scope)
      assertTrue(!config.debug)
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
    }
  )
}
