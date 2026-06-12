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

package async

import zio.blocks.async._

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
 * A single-file tour of [[zio.blocks.async.Async]]: constructors, direct-style
 * `Async.async` / `.await`, combinators, the `promise` callback bridge, custom
 * [[Pollable]] leaves, cancellable `start` / [[Async.Running]], and JVM `Future` interop.
 *
 * Run with:
 *
 * {{{
 *   sbt "++3.8.3; async-examples/run"
 * }}}
 */
object AsyncShowcaseExample extends App {

  // ---------------------------------------------------------------------------
  // Domain model (in-memory "services" for the demo)
  // ---------------------------------------------------------------------------

  final case class User(id: Int, name: String, tier: String)
  final case class LineItem(sku: String, qty: Int)
  final case class Order(id: Int, userId: Int, items: List[LineItem])
  final case class Stock(sku: String, onHand: Int)
  final case class Shipment(orderId: Int, lines: List[(String, Int)], carrier: String)
  final case class FulfillmentReport(shipment: Shipment, audit: List[String])

  private val users = Map(
    42 -> User(42, "Ada", "gold"),
    7  -> User(7, "Grace", "silver")
  )

  private val orders = Map(
    9001 -> Order(9001, 42, List(LineItem("widget", 2), LineItem("gadget", 1))),
    9002 -> Order(9002, 7, List(LineItem("widget", 99)))
  )

  private val warehouse = Map(
    "widget" -> Stock("widget", 50),
    "gadget" -> Stock("gadget", 12)
  )

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def section(n: Int, title: String): Unit = {
    println()
    println("=" * 72)
    println(f"$n%2d. $title")
    println("=" * 72)
  }

  private def show[A](label: String)(fa: Async[A]): A = {
    val value = fa.block
    println(s"  $label => $value")
    value
  }

  // ---------------------------------------------------------------------------
  // 1 — Ready path: a succeeded Async *is* its value (no effect wrapper)
  // ---------------------------------------------------------------------------

  section(1, "Ready path — map / flatMap on values with no suspension")
  show("doubled")(
    Async.succeed(21).map(_ * 2)
  )
  show("chained")(
    Async.succeed("order-").flatMap(prefix => Async.succeed(prefix + "9001"))
  )

  // ---------------------------------------------------------------------------
  // 2 — Direct style: straight-line code over async steps
  // ---------------------------------------------------------------------------

  section(2, "Direct style — Async.async { ... .await ... }")

  def fetchUser(id: Int): Async[User] =
    users.get(id) match {
      case Some(u) => Async.succeed(u)
      case None    => Async.fail(new NoSuchElementException(s"user $id"))
    }

  def fetchOrder(id: Int): Async[Order] =
    orders.get(id) match {
      case Some(o) => Async.succeed(o)
      case None    => Async.fail(new NoSuchElementException(s"order $id"))
    }

  val sequentialSummary: String = Async.async {
    val user  = fetchUser(42).await
    val order = fetchOrder(9001).await
    s"${user.name}'s order ${order.id} has ${order.items.size} line(s)"
  }.block

  println(s"  sequentialSummary => $sequentialSummary")

  // ---------------------------------------------------------------------------
  // 3 — Parallel composition with zip / zipWith
  // ---------------------------------------------------------------------------

  section(3, "Parallel zip — inventory checks run only after the order is known")

  def stockFor(sku: String): Async[Stock] =
    warehouse.get(sku) match {
      case Some(s) => Async.succeed(s)
      case None    => Async.fail(new NoSuchElementException(s"sku $sku"))
    }

  def availability(order: Order): Async[List[(String, Int, Int)]] = Async.async {
    order.items.map { item =>
      val stock = stockFor(item.sku).await
      (item.sku, item.qty, stock.onHand)
    }
  }

  val order9001 = fetchOrder(9001)
  val zipped: (Order, List[(String, Int, Int)]) =
    order9001.zipWith(order9001.flatMap(availability))((o, av) => (o, av)).block

  println(s"  order + availability => ${zipped._1.id} -> ${zipped._2}")

  // ---------------------------------------------------------------------------
  // 4 — Error handling: catchAll, orElse, either
  // ---------------------------------------------------------------------------

  section(4, "Error handling — recover, fall back, or reify")

  show("recovered missing user")(
    fetchUser(999).catchAll(_ => Async.succeed(User(0, "guest", "bronze")))
  )

  show("orElse to default order")(
    fetchOrder(404).orElse(Async.succeed(Order(0, 0, Nil)))
  )

  show("either for missing stock")(
    stockFor("phaser").either.map {
      case Right(s) => s"in stock: ${s.onHand}"
      case Left(t)  => s"missing: ${t.getMessage}"
    }
  )

  // ---------------------------------------------------------------------------
  // 5 — Callback bridge: Async.promise
  // ---------------------------------------------------------------------------

  section(5, "Callback bridge — Async.promise wraps legacy APIs")

  /**
   * Simulates a callback-style HTTP client (think Node.js or old Java SDKs).
   * Hand the [[Completer]] to the callback thread — see `AsyncBlockingSpec` for
   * off-thread completion tests.
   */
  def legacyHttpGet(path: String, completer: Completer[String]): Unit = {
    val t = new Thread(
      () => {
        Thread.sleep(30)
        if (path.startsWith("/users/")) completer.succeed(s"""{"id":42,"name":"Ada"}""")
        else completer.fail(new RuntimeException(s"404 $path"))
      },
      "legacy-http"
    )
    t.setDaemon(true)
    t.start()
  }

  // Eager completion demonstrates `succeed`/`fail` sugar; wire `legacyHttpGet` for real callbacks.
  val fromCallback: String =
    Async.promise[String](succeed(s"""{"id":42,"name":"Ada"}""")).block

  println(s"  promise (sync) => $fromCallback")

  // ---------------------------------------------------------------------------
  // 6 — Custom Pollable: a hand-rolled asynchronous leaf
  // ---------------------------------------------------------------------------

  section(6, "Custom Pollable — suspend until an external event fires")

  /** Pollable that becomes ready after `delay` simulated scheduler ticks. */
  final class Delayed[A](value: A, private var ticks: Int) extends Pollable[A] {
    def poll(onComplete: Runnable): Async[A] =
      if (ticks <= 0) Async.succeed(value)
      else {
        ticks -= 1
        onComplete.run()
        this
      }
  }

  val delayedCarrier: String =
    Async.succeed("TRK-").flatMap(prefix => new Delayed(prefix + "Z42", ticks = 2)).block

  println(s"  delayed tracking id => $delayedCarrier")

  // ---------------------------------------------------------------------------
  // 7 — collectAll + for-comprehension with .await
  // ---------------------------------------------------------------------------

  section(7, "collectAll and for-comprehensions with .await")

  val batchedUsers: List[User] = Async
    .collectAll(List(fetchUser(42), fetchUser(7)))
    .block

  println(s"  batchedUsers => ${batchedUsers.map(_.name).mkString(", ")}")

  val pairSums: List[Int] = Async.async {
    for {
      i <- List(1, 2, 3)
      j <- List(10, 20)
    } yield Async.succeed(i + j).await
  }.block

  println(s"  for-comprehension sums => $pairSums")

  // ---------------------------------------------------------------------------
  // 8 — tap / ensuring: effects around a successful outcome
  // ---------------------------------------------------------------------------

  section(8, "tap and ensuring — audit without changing the result")

  val auditLog = scala.collection.mutable.ListBuffer.empty[String]

  def fulfill(orderId: Int): Async[Shipment] = Async.async {
    val order = fetchOrder(orderId).await
    val lines = order.items.map { item =>
      val stock = stockFor(item.sku).await
      if (stock.onHand < item.qty)
        throw new IllegalStateException(s"short ${item.sku}: need ${item.qty}, have ${stock.onHand}")
      (item.sku, item.qty)
    }
    Shipment(orderId, lines, carrier = "zio-blocks-express")
  }

  val report: FulfillmentReport = fulfill(9001)
    .tap { shipment =>
      auditLog += s"packed ${shipment.lines.size} line(s)"
      Async.succeed(())
    }
    .ensuring {
      auditLog += "finalizer: notify warehouse"
      Async.succeed(())
    }
    .map(shipment => FulfillmentReport(shipment, auditLog.toList))
    .block

  println(s"  fulfillment => ${report.shipment}")
  println(s"  audit trail => ${report.audit.mkString(" | ")}")

  // ---------------------------------------------------------------------------
  // 9 — start + cancel (non-blocking edge runner)
  // ---------------------------------------------------------------------------

  section(9, "start — compositional observation and cancellation")

  val callbackFired = new AtomicBoolean(false)

  val running: Async.Running[Nothing] =
    Async.start(Async.never.tap((_: Nothing) => { callbackFired.set(true); Async.succeed(()) }))

  running.cancel()
  Thread.sleep(50)
  println(s"  callback suppressed after cancel => ${!callbackFired.get()}")

  var asyncOut: Either[Throwable, Int] = Right(-1)
  Async.start(Async.succeed(99).map(_ + 1).either.tap(res => { asyncOut = res; Async.succeed(()) }))
  Thread.sleep(20)
  println(s"  start result => ${asyncOut.toTry}")

  // ---------------------------------------------------------------------------
  // 10 — Future interop (JVM)
  // ---------------------------------------------------------------------------

  section(10, "Future interop — round-trip without blocking the Async encoding")

  val fromFuture: Int =
    AsyncInterop
      .fromFuture(Future {
        Thread.sleep(20)
        42
      })
      .block

  val toFuture: Int =
    Await.result(AsyncInterop.toFuture(Async.succeed(fromFuture).map(_ + 1)), 2.seconds)

  println(s"  fromFuture => $fromFuture, toFuture => $toFuture")

  // ---------------------------------------------------------------------------
  // 11 — End-to-end: one direct-style program tying it together
  // ---------------------------------------------------------------------------

  section(11, "End-to-end — direct-style fulfillment with recovery")

  def fulfillOrGuest(orderId: Int): Async[String] = Async.async {
    val order =
      fetchOrder(orderId)
        .catchAll(_ => fetchOrder(9001))
        .await

    val user =
      fetchUser(order.userId)
        .catchAll(_ => Async.succeed(User(0, "guest", "bronze")))
        .await

    val shipment = fulfill(order.id).await
    s"shipped ${shipment.orderId} for ${user.name} via ${shipment.carrier}"
  }

  println(s"  pipeline => ${fulfillOrGuest(9001).block}")

  // ---------------------------------------------------------------------------
  // 12 — attempt: bridge throw-based code
  // ---------------------------------------------------------------------------

  section(12, "attempt — capture thrown exceptions as failures")

  val parsed: Async[Int] = Async.attempt("not-a-number".toInt)
  println(s"  attempt parse => ${parsed.either.block}")

  println()
  println("Done. See async-examples/src/main/scala/async/AsyncShowcaseExample.scala")
}
