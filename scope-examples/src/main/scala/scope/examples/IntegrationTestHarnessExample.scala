package scope.examples

import zio.blocks.scope._

/**
 * Demonstrates combining DI-wired services with manually-managed test fixtures.
 *
 * This example shows a realistic integration test setup where:
 *   - Application services are wired via [[Resource.from]] (DI approach)
 *   - Test fixtures are managed with [[Resource.acquireRelease]] (manual
 *     approach)
 *   - Both resource types are properly cleaned up in LIFO order
 */
object IntegrationTestHarnessExample {

  // --- Test Infrastructure ---

  /** Configuration for the test environment. */
  case class TestConfig(dbUrl: String, serverPort: Int)

  /** Test database with lifecycle hooks for setup/teardown and data seeding. */
  class TestDatabase(val config: TestConfig) extends AutoCloseable {
    private var data = Map.empty[String, Any]

    def setup(): Unit                         = println(s"  [DB] Initialized at ${config.dbUrl}")
    def teardown(): Unit                      = { data = Map.empty; println("  [DB] Data cleared") }
    def seed(newData: Map[String, Any]): Unit = {
      data = newData; println(s"  [DB] Seeded with ${newData.size} entries")
    }
    def query(key: String): Option[Any] = data.get(key)
    def close(): Unit                   = println("  [DB] Connection closed")
  }

  /** Test HTTP server that can be started and stopped. */
  class TestServer(val config: TestConfig) extends AutoCloseable {
    val baseUrl: String = s"http://localhost:${config.serverPort}"

    def start(): Unit = println(s"  [Server] Started at $baseUrl")
    def stop(): Unit  = println("  [Server] Stopped")
    def close(): Unit = println("  [Server] Resources released")
  }

  /** Aggregates test fixtures for convenient access during tests. */
  case class TestFixture(db: TestDatabase, server: TestServer)

  // --- Application Under Test ---

  /** The application being tested; requires a database connection. */
  class AppUnderTest(val db: TestDatabase) extends AutoCloseable {
    def handleRequest(req: String): String = db.query(req).map(_.toString).getOrElse("Not found")
    def close(): Unit                      = println("  [App] Shutdown complete")
  }

  // --- Resource Definitions ---

  /**
   * Creates a manually-managed test fixture using [[Resource.acquireRelease]].
   *
   * This approach gives explicit control over setup and teardown phases, which
   * is typical for test fixtures that need initialization beyond construction.
   */
  def testFixtureResource(config: TestConfig): Resource[TestFixture] =
    Resource.acquireRelease {
      println("  [Fixture] Acquiring test fixture...")
      val db     = new TestDatabase(config)
      val server = new TestServer(config)
      db.setup()
      server.start()
      TestFixture(db, server)
    } { fixture =>
      println("  [Fixture] Releasing test fixture...")
      fixture.server.stop()
      fixture.db.teardown()
      fixture.db.close()
      fixture.server.close()
    }

  /**
   * Runs the integration test harness example.
   *
   * Demonstrates:
   *   1. Manual fixture via [[Resource.acquireRelease]] for test infrastructure
   *   2. DI-wired application via [[Resource.from]] consuming the fixture
   *   3. Proper cleanup ordering: app closes before fixtures
   */
  def run(): Unit = {
    println("=== Integration Test Harness Example ===\n")

    val config = TestConfig("jdbc:h2:mem:test", 8080)

    // Combine manual fixtures with DI-wired application
    val testHarnessResource: Resource[(TestFixture, AppUnderTest)] =
      testFixtureResource(config).flatMap { fixture =>
        // Seed test data
        fixture.db.seed(Map("user:1" -> "Alice", "user:2" -> "Bob"))

        // Wire the app using DI, injecting the fixture's database
        val appWire     = Wire.shared[AppUnderTest]
        val dbWire      = Wire(fixture.db)
        val appResource = Resource.from[AppUnderTest](appWire, dbWire)

        appResource.map(app => (fixture, app))
      }

    // Run in a scoped block - all resources cleaned up on exit
    Scope.global.scoped { scope =>
      import scope._
      println("Allocating resources...")
      val harness: $[(TestFixture, AppUnderTest)] = allocate(testHarnessResource)
      println()

      // Run test scenarios - access the tuple via use
      println("Running test scenarios:")
      scope.use(harness) { h =>
        println(s"  GET user:1 -> ${h._2.handleRequest("user:1")}")
        println(s"  GET user:2 -> ${h._2.handleRequest("user:2")}")
        println(s"  GET user:3 -> ${h._2.handleRequest("user:3")}")
        println(s"  Server URL: ${h._1.server.baseUrl}")
      }
      println()

      println("Scope closing, releasing resources in LIFO order...")
    }

    println("\n=== Example Complete ===")
  }
}

@main def runIntegrationTestHarness(): Unit = IntegrationTestHarnessExample.run()
