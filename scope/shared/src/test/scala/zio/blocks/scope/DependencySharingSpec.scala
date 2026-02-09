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

  def spec = suite("Dependency sharing")(
    suite("Diamond dependency pattern")(
      test("shared Database is constructed once when both UserService and OrderService depend on it") {
        resetCounters()

        // All wires are shared
        val dbWire    = Wire.shared[Database]
        val userWire  = Wire.shared[UserService]
        val orderWire = Wire.shared[OrderService]

        // Whole graph wireup via Resource.from
        val resource = Resource.from[App](dbWire, userWire, orderWire)

        val (scope, close) = Scope.createTestableScope()
        resource.make(scope)
        close()

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

        val (scope, close) = Scope.createTestableScope()
        resource.make(scope)
        close()

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

        val (scope, close) = Scope.createTestableScope()
        val db1            = dbWire1.toResource(Context.empty).make(scope)
        val db2            = dbWire2.toResource(Context.empty).make(scope)
        close()

        // Two databases - different Wire instances = different Resources
        assertTrue(
          dbCounter.get() == 2,
          db1.id != db2.id
        )
      }
    ),
    suite("Resource.shared memoization")(
      test("Resource.shared memoizes across makes within same Resource instance") {
        resetCounters()

        val dbWire   = Wire.shared[Database]
        val resource = dbWire.toResource(Context.empty)

        val (scope, close) = Scope.createTestableScope()
        val db1            = resource.make(scope)
        val db2            = resource.make(scope)
        close()

        assertTrue(
          dbCounter.get() == 1,
          db1.eq(db2)
        )
      }
    )
  ) @@ sequential
}
