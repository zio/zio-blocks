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

/*
 * Example 2 of 5: a cross-entity projection.
 *
 * Read Example1PerEntity first. Next: Example3GlobalAggregate.
 *
 * In Example 1 every event belongs to the entity it was recorded under. Here the
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
 * This example runs on real SQLite files: the events are kept in
 * `.projection-examples/example2/events/orders.db`, and the projection in
 * `.projection-examples/example2/orders/`, where each customer gets their own file next to
 * the main one, for example `ordersByCustomer_shard_alice.db`.
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
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example2CrossEntity"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example2CrossEntity extends ZIOAppDefault {

  sealed trait OrderEvent { def customerId: String }
  object OrderEvent {
    case class OrderPlaced(customerId: String, product: String, total: Long) extends OrderEvent
    object OrderPlaced {
      implicit val schema: Schema[OrderPlaced] = Schema.derived[OrderPlaced]
    }
    case class OrderShipped(customerId: String) extends OrderEvent
    object OrderShipped {
      implicit val schema: Schema[OrderShipped] = Schema.derived[OrderShipped]
    }
    implicit val schema: Schema[OrderEvent] = Schema.derived[OrderEvent]
  }
  import OrderEvent.*

  private val ordersFolder: String = Sqlite.path("example2/orders")

  // Not called `Order`: the table is named after the class, and `order` is an SQL keyword.
  case class CustomerOrder(@Modifier.id id: String, customerId: String, product: String, total: Long, status: String)
  object CustomerOrder {
    implicit val schema: Schema[CustomerOrder]         = Schema.derived[CustomerOrder]
    // The folder is chosen explicitly. Derived from the `id` field, it would be "ids".
    implicit val entityPath: EntityPath[CustomerOrder] = EntityPath.derived[CustomerOrder](ordersFolder)
  }

  private def show(order: Option[CustomerOrder]): String =
    order.map(o => s"${o.customerId}, ${o.product}, ${o.total}, ${o.status}").getOrElse("not found")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 2: CrossEntity Projection (orders routed by customerId) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.folders(ordersFolder, Sqlite.path("example2/events"))

          projection = Projection[CustomerOrder]("customer_orders")
                         .from("orders")
                         .routedBy[OrderEvent](_.customerId)
                         .on[OrderPlaced]
                         .insert((e, ctx) => CustomerOrder(ctx.entityId, e.customerId, e.product, e.total, "placed"))
                         .on[OrderShipped]
                         .updateWithField("status", (_, _) => "shipped")

          _ <- Console.printLine(s"  Scope: ${projection.scope}").orDie

          cache <- TransactorCache.make()
          orders <- Sqlite.eventStore[OrderEvent](cache, Sqlite.path("example2/events/orders.db"))
          engine <- ProjectionEngine.makeWithStoresVarargs(
                      cache,
                      Map[String, EventStore[?]]("orders" -> orders)
                    )(projection)
          _ <- engine.start

          // Events are recorded under the ID of the order, and carry the customer inside.
          _ <- orders.append("order-1", OrderPlaced("alice", "keyboard", 80))
          _ <- orders.append("order-2", OrderPlaced("alice", "monitor", 250))
          _ <- orders.append("order-3", OrderPlaced("bob", "mouse", 25))
          _ <- orders.append("order-1", OrderShipped("alice"))
          _ <- orders.append("order-3", OrderShipped("bob"))
          _ <- orders.append("order-4", OrderPlaced("charlie", "cable", 9))

          // Events are applied in order, so once order-4 exists every earlier event has been applied.
          order4 <- pollUntil(engine.query(projection, "order-4"))
          order1 <- engine.query(projection, "order-1")
          order2 <- engine.query(projection, "order-2")
          order3 <- engine.query(projection, "order-3")

          files <- Sqlite.dbFiles(ordersFolder)
          _     <- Console.printLine(s"  Files in $ordersFolder/: ${files.mkString(", ")}").orDie
          _     <- Console.printLine(s"  order-1: ${show(order1)}").orDie
          _     <- Console.printLine(s"  order-2: ${show(order2)}").orDie
          _     <- Console.printLine(s"  order-3: ${show(order3)}").orDie
          _     <- Console.printLine(s"  order-4: ${show(order4)}").orDie
          _ <- Console.printLine("  ✓ CrossEntity: orders recorded per order, kept per customer in a shard").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
