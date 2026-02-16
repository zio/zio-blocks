package scope.examples

import zio.blocks.scope._
import java.util.concurrent.atomic.AtomicInteger

/**
 * Demonstrates `Wire.shared` vs `Wire.unique` and diamond dependency patterns.
 *
 * Two services (ProductService, OrderService) share one Logger instance
 * (diamond pattern), but each gets its own unique Cache instance. This shows
 * how shared wires provide singleton behavior while unique wires create fresh
 * instances per injection site.
 *
 * Key concepts:
 *   - `Wire.shared[T]`: Single instance shared across all dependents (memoized)
 *   - `Wire.unique[T]`: Fresh instance created for each dependent
 *   - Diamond dependency: Multiple services depend on the same shared resource
 *   - Reference counting: Shared resources track usage and clean up when last
 *     user closes
 */
object CachingSharedLoggerExample {

  /** Tracks instantiation counts for demonstration purposes. */
  val loggerInstances = new AtomicInteger(0)
  val cacheInstances  = new AtomicInteger(0)

  /**
   * A shared logger that tracks instantiations and provides logging methods.
   * Implements AutoCloseable for proper resource cleanup.
   */
  class Logger extends AutoCloseable {
    val instanceId: Int = loggerInstances.incrementAndGet()
    println(s"  [Logger#$instanceId] Created")

    def info(msg: String): Unit  = println(s"  [Logger#$instanceId] INFO: $msg")
    def debug(msg: String): Unit = println(s"  [Logger#$instanceId] DEBUG: $msg")
    def close(): Unit            = println(s"  [Logger#$instanceId] Closed")
  }

  /**
   * A unique cache per service. Each service gets its own isolated cache
   * instance. Implements AutoCloseable for proper resource cleanup. Note: No
   * constructor params so it can be auto-wired with Wire.unique.
   */
  class Cache extends AutoCloseable {
    val instanceId: Int                    = cacheInstances.incrementAndGet()
    private var store: Map[String, String] = Map.empty
    println(s"  [Cache#$instanceId] Created")

    def get(key: String): Option[String]      = store.get(key)
    def put(key: String, value: String): Unit = store = store.updated(key, value)
    def close(): Unit                         = println(s"  [Cache#$instanceId] Closed")
  }

  /** Product service with its own cache but sharing the logger. */
  class ProductService(val logger: Logger, val cache: Cache) {
    println(s"  [ProductService] Created with Logger#${logger.instanceId} and Cache#${cache.instanceId}")

    def findProduct(id: String): String =
      cache.get(id) match {
        case Some(product) =>
          logger.debug(s"Cache hit for product $id")
          product
        case None =>
          logger.info(s"Loading product $id from database")
          val product = s"Product-$id"
          cache.put(id, product)
          product
      }
  }

  /**
   * Order service with its own cache but sharing the same logger as
   * ProductService.
   */
  class OrderService(val logger: Logger, val cache: Cache) {
    println(s"  [OrderService] Created with Logger#${logger.instanceId} and Cache#${cache.instanceId}")

    def createOrder(productId: String): String = {
      val orderId = s"ORD-${System.currentTimeMillis() % 10000}"
      cache.put(orderId, productId)
      logger.info(s"Created order $orderId for product $productId")
      orderId
    }
  }

  /** Top-level application combining both services. */
  class CachingApp(val productService: ProductService, val orderService: OrderService) extends AutoCloseable {
    def run(): Unit = {
      productService.logger.info("=== Application Started ===")
      val product = productService.findProduct("P001")
      orderService.createOrder(product)
      productService.findProduct("P001") // cache hit
    }
    def close(): Unit = println("  [CachingApp] Closed")
  }

  @main def runCachingExample(): Unit = {
    println("\n╔════════════════════════════════════════════════════════════════╗")
    println("║  Wire.shared vs Wire.unique - Diamond Dependency Example       ║")
    println("╚════════════════════════════════════════════════════════════════╝\n")

    println("Creating wires...")
    println("  - Logger: Wire.shared (singleton across all services)")
    println("  - Cache:  Wire.unique (fresh instance per service)\n")

    println("─── Resource Acquisition ───")
    Scope.global.scoped { scope =>
      import scope._
      val app: $[CachingApp] = allocate(
        Resource.from[CachingApp](
          Wire.shared[Logger],
          Wire.unique[Cache]
        )
      )

      println("\n─── Verification ───")
      println(s"  Logger instances created: ${loggerInstances.get()} (expected: 1)")
      println(s"  Cache instances created:  ${cacheInstances.get()} (expected: 2)")
      (scope $ app) { a =>
        println(s"  ProductService.logger eq OrderService.logger: ${a.productService.logger eq a.orderService.logger}")
        println(s"  ProductService.cache  eq OrderService.cache:  ${a.productService.cache eq a.orderService.cache}")

        println("\n─── Running Application ───")
        a.run()
      }

      println("\n─── Scope Closing (LIFO cleanup) ───")
    }

    println("\n─── Summary ───")
    println(s"  Final Logger count: ${loggerInstances.get()} (shared = 1 instance)")
    println(s"  Final Cache count:  ${cacheInstances.get()} (unique = 2 instances)")
    println("\nDiamond pattern verified: both services received the same Logger instance.")
  }
}
