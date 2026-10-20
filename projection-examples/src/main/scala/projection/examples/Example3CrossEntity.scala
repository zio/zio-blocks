/*
 * Example 3 of 13: a cross-entity projection.
 *
 * Read Example2PerEntity first. Next: Example4InsertAndDelete.
 *
 * In Example 2 every event belongs to the entity it was recorded under. Here the
 * events are recorded per order (`order-1`, `order-2`, ...), but they are viewed
 * per customer, and the customer is a field inside the event. `routedBy` tells the
 * projection to read that key from each event and send the event to the store for
 * that key, called a shard. So one customer's orders are kept together, apart from
 * everyone else's.
 *
 * Two kinds of event are involved, `OrderPlaced` and `OrderShipped`. Both carry the
 * `customerId`, so both are routed to the same shard. That is what lets
 * `OrderShipped` update a row that `OrderPlaced` created earlier: the row has the
 * ID of the order, and it is in the shard of its customer.
 *
 * A second projection, `ShopStats`, shows the other side: one global row with the
 * numbers for the whole shop (customers, orders, shipped, pending, revenue). It
 * is a global projection, so it ignores the customer and counts everything. Its
 * counters update the row whose ID the event was recorded under, and the orders
 * are recorded under their own IDs, so each event is also written to a second
 * feed, "shop", under the single ID "shop". That feed is what the stats read.
 *
 * This example runs on real SQLite files: the events are kept in
 * `.projection-examples/example3/events/orders.db`, and the projection in
 * `.projection-examples/example3/orders/`, where each customer gets their own file next to
 * the main one, for example `customer_orders_shard_alice.db`.
 *
 * It shows:
 *   - `routedBy`, which makes the projection's scope `CrossEntity`
 *   - the routing key chooses the shard, while the row keeps the ID of the order
 *   - several event types converging on the same projection
 *   - an update landing on the right row, because routing is the same for every
 *     event of a customer
 *   - an engine on SQLite files, and the shard files that appear
 *   - reading a row back by ID, which looks in the main store and then in the
 *     shards. The engine has no call that lists the rows of a shard
 *   - a global projection with several counters, some of them going up and down
 *     (`pending`), fed by three kinds of event
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example3CrossEntity"
 */

package projection.examples

import zio.*
import zio.blocks.chunk.Chunk
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example3CrossEntity extends ZIOAppDefault {

  sealed trait OrderEvent { def customerId: String }
  object OrderEvent       {
    case class OrderPlaced(customerId: String, product: String, total: Long) extends OrderEvent
    object OrderPlaced {
      implicit val schema: Schema[OrderPlaced] = Schema.derived[OrderPlaced]
    }
    case class OrderShipped(customerId: String) extends OrderEvent
    object OrderShipped {
      implicit val schema: Schema[OrderShipped] = Schema.derived[OrderShipped]
    }
    case class CustomerRegistered(customerId: String, name: String) extends OrderEvent
    object CustomerRegistered {
      implicit val schema: Schema[CustomerRegistered] = Schema.derived[CustomerRegistered]
    }
    implicit val schema: Schema[OrderEvent] = Schema.derived[OrderEvent]
  }
  import OrderEvent.*

  private val ordersFolder: String = Sqlite.path("example3/orders")

  // Not called `Order`: the table is named after the class, and `order` is an SQL keyword.
  case class CustomerOrder(@Modifier.id id: String, customerId: String, product: String, total: Long, status: String)
  object CustomerOrder {
    implicit val schema: Schema[CustomerOrder] = Schema.derived[CustomerOrder]
    // The folder is chosen explicitly. Derived from the `id` field, it would be "ids".
    implicit val entityPath: EntityPath[CustomerOrder] = EntityPath.derived[CustomerOrder](ordersFolder)
  }

  // The key is always "shop". Its field is called `id` because a global projection has no `EntityPath`, so its
  // SQLite store looks for a column with that name.
  case class ShopStats(
    @Modifier.id id: String,
    customers: Long,
    orders: Long,
    shipped: Long,
    pending: Long,
    revenue: Long
  )
  object ShopStats {
    implicit val schema: Schema[ShopStats]         = Schema.derived[ShopStats]
    implicit val entityPath: EntityPath[ShopStats] = EntityPath.derived[ShopStats]
  }

  private def show(order: Option[CustomerOrder]): String =
    order.map(o => s"${o.customerId}, ${o.product}, ${o.total}, ${o.status}").getOrElse("not found")

  // `CrossEntity` holds the routing function, which prints as an unreadable lambda, so only the kind is printed.
  private def scopeName(scope: ProjectionScope): String = scope match {
    case ProjectionScope.PerEntity      => "PerEntity"
    case ProjectionScope.CrossEntity(_) => "CrossEntity"
    case ProjectionScope.Global         => "Global"
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 3: CrossEntity Projection (orders routed by customerId) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.globalFolder("shop_stats") *> Sqlite.folders(ordersFolder, Sqlite.path("example3/events"))

          projection = Projection[CustomerOrder]("customer_orders")
                         .from("orders")
                         .routedBy[OrderEvent](_.customerId)
                         .on[OrderPlaced]
                         .insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product, e.total, "placed"))
                         .on[OrderShipped]
                         .updateWithField("status", (_, _) => "shipped")

          // One row for the whole shop. `pending` goes up when an order is placed and down when it is shipped.
          stats = Projection
                    .global[ShopStats]("shop_stats")
                    .from("shop")
                    .routeToAll
                    .on[CustomerRegistered]
                    .aggregate(FieldUpdate.Increment("customers", 1L))
                    .on[OrderPlaced]
                    .custom { (e, _) =>
                      ProjectionAction.Update(
                        Chunk(
                          FieldUpdate.Increment("orders", 1L),
                          FieldUpdate.Increment("pending", 1L),
                          FieldUpdate.Increment("revenue", e.total)
                        )
                      )
                    }
                    .on[OrderShipped]
                    .aggregate(Chunk(FieldUpdate.Increment("shipped", 1L), FieldUpdate.Decrement("pending", 1L)))

          _ <-
            Console.printLine(s"  Scope: ${scopeName(projection.scope)}, stats scope: ${scopeName(stats.scope)}").orDie

          cache  <- TransactorCache.make()
          orders <- Sqlite.eventStore[OrderEvent](cache, Sqlite.path("example3/events/orders.db"))
          shop   <- Sqlite.eventStore[OrderEvent](cache, Sqlite.path("example3/events/shop.db"))
          engine <- ProjectionEngine.makeWithStoresVarargs(
                      cache,
                      Map[String, EventStore[?]]("orders" -> orders, "shop" -> shop)
                    )(projection, stats)
          _ <- engine.start

          // Order events are recorded under the ID of the order, and carry the customer inside. Each one is also
          // written to the "shop" feed under the single ID "shop", which is what the stats read.
          record = (orderId: String, event: OrderEvent) => orders.append(orderId, event) *> shop.append("shop", event)

          _ <- ZIO.foreachDiscard(List("alice", "bob", "charlie", "dave")) { customer =>
                 shop.append("shop", CustomerRegistered(customer, customer.capitalize))
               }
          _ <- record("order-1", OrderPlaced("alice", "keyboard", 80))
          _ <- record("order-2", OrderPlaced("alice", "monitor", 250))
          _ <- record("order-3", OrderPlaced("bob", "mouse", 25))
          _ <- record("order-1", OrderShipped("alice"))
          _ <- record("order-3", OrderShipped("bob"))
          _ <- record("order-4", OrderPlaced("charlie", "cable", 9))

          // Events are applied in order, so once order-4 exists every earlier event has been applied.
          order4 <- pollUntil(engine.query(projection, "order-4"))
          order1 <- engine.query(projection, "order-1")
          order2 <- engine.query(projection, "order-2")
          order3 <- engine.query(projection, "order-3")

          // The stats are done when all four customers, four orders and two shipments have been counted.
          totals <- pollUntil(
                      engine
                        .query(stats, "shop")
                        .map(_.filter(t => t.customers == 4 && t.orders == 4 && t.shipped == 2)),
                      5.seconds
                    )

          files <- Sqlite.dbFiles(ordersFolder)
          _     <- Console.printLine(s"  Files in $ordersFolder/: ${files.mkString(", ")}").orDie
          _     <- Console.printLine(s"  order-1: ${show(order1)}").orDie
          _     <- Console.printLine(s"  order-2: ${show(order2)}").orDie
          _     <- Console.printLine(s"  order-3: ${show(order3)}").orDie
          _     <- Console.printLine(s"  order-4: ${show(order4)}").orDie
          _     <- totals match {
                 case Some(t) =>
                   Console
                     .printLine(
                       s"  Stats: customers ${t.customers}, orders ${t.orders}, shipped ${t.shipped}, " +
                         s"pending ${t.pending}, revenue ${t.revenue}"
                     )
                     .orDie
                 case None =>
                   Console.printLine("  ⚠ Stats not yet ready (timeout)").orDie
               }
          _ <- Console.printLine("  ✓ CrossEntity: orders kept per customer in a shard, one global row of stats").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
