package zio.blocks.scope

import zio.{Scope => _}
import zio.blocks.context.Context
import zio.test._
import zio.test.TestAspect._

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests that shared dependencies are constructed exactly once across the entire
 * dependency graph during a "whole graph wireup" operation (Resource.from,
 * Wire#toResource, etc.).
 *
 * The key invariant: if a wire is shared, its output is computed once and
 * shared across all dependents in the graph, regardless of how many times it
 * appears.
 */
object DependencySharingSpec extends ZIOSpecDefault {

  // Counters - reset before each test
  val dbCounter    = new AtomicInteger(0)
  val userCounter  = new AtomicInteger(0)
  val orderCounter = new AtomicInteger(0)

  def resetCounters(): Unit = {
    dbCounter.set(0)
    userCounter.set(0)
    orderCounter.set(0)
  }

  // Test fixtures
  class Database extends AutoCloseable {
    val id            = dbCounter.incrementAndGet()
    var closed        = false
    def close(): Unit = closed = true
  }

  class UserService(@annotation.unused db: Database) {
    val id = userCounter.incrementAndGet()
  }

  class OrderService(@annotation.unused db: Database) {
    val id = orderCounter.incrementAndGet()
  }

  // Diamond: App depends on UserService and OrderService, both depend on Database
  class App(
    @annotation.unused userService: UserService,
    @annotation.unused orderService: OrderService
  )

  // Additional counters for non-leaf node test
  val leafCounter = new AtomicInteger(0)
  val midCounter  = new AtomicInteger(0)

  class Leaf {
    val id = leafCounter.incrementAndGet()
  }

  class Mid(@annotation.unused leaf: Leaf) {
    val id = midCounter.incrementAndGet()
  }

  class Top1(@annotation.unused mid: Mid)
  class Top2(@annotation.unused mid: Mid)
  class DiamondApp(@annotation.unused t1: Top1, @annotation.unused t2: Top2)

  // For subtype canonicalization test
  trait Service
  val liveServiceCounter = new AtomicInteger(0)

  class LiveService extends Service {
    val id = liveServiceCounter.incrementAndGet()
  }

  class NeedsService(@annotation.unused s: Service)
  class NeedsLive(@annotation.unused l: LiveService)
  class SubtypeApp(@annotation.unused a: NeedsService, @annotation.unused b: NeedsLive)

  def spec = suite("Dependency sharing")(
    suite("Diamond dependency pattern")(
      test("shared non-leaf Mid is constructed once in diamond pattern") {
        leafCounter.set(0)
        midCounter.set(0)

        // Mid is a non-leaf shared node (has Leaf dependency)
        // Both Top1 and Top2 depend on Mid
        // Mid should be constructed once and shared
        val resource = Resource.from[DiamondApp]()

        Scope.global.scoped { scope =>
          val _ = resource.make(scope)
        }

        assertTrue(
          leafCounter.get() == 1,
          midCounter.get() == 1
        )
      },
      test("shared Database is constructed once when both UserService and OrderService depend on it") {
        resetCounters()

        // All wires are shared
        val dbWire    = Wire.shared[Database]
        val userWire  = Wire.shared[UserService]
        val orderWire = Wire.shared[OrderService]

        // Whole graph wireup via Resource.from
        val resource = Resource.from[App](dbWire, userWire, orderWire)

        Scope.global.scoped { scope =>
          val _ = resource.make(scope)
        }

        // Database should be constructed exactly once, shared between UserService and OrderService
        assertTrue(
          dbCounter.get() == 1,
          userCounter.get() == 1,
          orderCounter.get() == 1
        )
      },
      test("shared Database via Wire#toResource is constructed once") {
        resetCounters()

        val dbWire    = Wire.shared[Database]
        val userWire  = Wire.shared[UserService]
        val orderWire = Wire.shared[OrderService]

        // Partially wire: App wire depends on userWire and orderWire
        // Both userWire and orderWire depend on dbWire
        // Whole graph wireup via toResource should share Database
        val appWire  = Wire.shared[App]
        val resource = Resource.from[App](appWire, dbWire, userWire, orderWire)

        Scope.global.scoped { scope =>
          val _ = resource.make(scope)
        }

        assertTrue(
          dbCounter.get() == 1,
          userCounter.get() == 1,
          orderCounter.get() == 1
        )
      }
    ),
    suite("Separate wireups create separate instances")(
      test("different Wire.shared[Database] wires create separate instances") {
        resetCounters()

        // Two separate Database wires - even though both are "shared",
        // they are different Wire instances, so each creates its own Database
        val dbWire1 = Wire.shared[Database]
        val dbWire2 = Wire.shared[Database]

        val (id1, id2) = Scope.global.scoped { scope =>
          val db1 = dbWire1.toResource(Context.empty).make(scope)
          val db2 = dbWire2.toResource(Context.empty).make(scope)
          (db1.id, db2.id)
        }

        // Two databases - different Wire instances = different Resources
        assertTrue(
          dbCounter.get() == 2,
          id1 != id2
        )
      }
    ),
    suite("Resource.shared memoization")(
      test("Resource.shared memoizes across makes within same Resource instance") {
        resetCounters()

        val dbWire   = Wire.shared[Database]
        val resource = dbWire.toResource(Context.empty)

        val same = Scope.global.scoped { scope =>
          val db1 = resource.make(scope)
          val db2 = resource.make(scope)
          db1.eq(db2)
        }

        assertTrue(
          dbCounter.get() == 1,
          same
        )
      }
    ),
    suite("Subtype canonicalization")(
      test("same wire satisfies both supertype and subtype dependencies - single instance") {
        liveServiceCounter.set(0)

        // LiveService wire should satisfy both Service and LiveService dependencies
        val resource = Resource.from[SubtypeApp](
          Wire.shared[LiveService]
        )

        Scope.global.scoped { scope =>
          val _ = resource.make(scope)
        }

        // Should create only one LiveService instance
        assertTrue(liveServiceCounter.get() == 1)
      }
    )
  ) @@ sequential
}
