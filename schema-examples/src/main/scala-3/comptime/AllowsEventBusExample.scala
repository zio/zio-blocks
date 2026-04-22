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

package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Primitive, Record, Sequence, `|`}
import Allows.{Optional => AOptional}
import util.ShowExpr.show

// ---------------------------------------------------------------------------
// Event bus / message broker example using Allows[A, S]
//
// Published events are typically sealed traits of flat record cases. Sealed
// traits are automatically unwrapped by the Allows macro — each case is
// checked individually against the grammar. No Variant node is needed.
//
// This example also shows nested sealed traits (auto-unwrap is recursive).
// ---------------------------------------------------------------------------

// Domain events — a sealed trait hierarchy
sealed trait AccountEvent
case class AccountOpened(accountId: String, owner: String, initialBalance: BigDecimal) extends AccountEvent
case class FundsDeposited(accountId: String, amount: BigDecimal)                       extends AccountEvent
case class FundsWithdrawn(accountId: String, amount: BigDecimal)                       extends AccountEvent
case class AccountClosed(accountId: String, reason: Option[String])                    extends AccountEvent
object AccountEvent { implicit val schema: Schema[AccountEvent] = Schema.derived }

// Nested sealed trait — InventoryEvent has a sub-hierarchy
sealed trait InventoryEvent
case class ItemAdded(sku: String, quantity: Int)   extends InventoryEvent
case class ItemRemoved(sku: String, quantity: Int) extends InventoryEvent

sealed trait InventoryAlert                      extends InventoryEvent
case class LowStock(sku: String, remaining: Int) extends InventoryAlert
case class OutOfStock(sku: String)               extends InventoryAlert

object InventoryEvent { implicit val schema: Schema[InventoryEvent] = Schema.derived }

// Event with sequence fields (e.g. tags or batch items)
sealed trait BatchEvent
case class BatchImport(batchId: String, itemIds: List[String]) extends BatchEvent
case class BatchComplete(batchId: String, count: Int)          extends BatchEvent
object BatchEvent { implicit val schema: Schema[BatchEvent] = Schema.derived }

object EventBus {

  type EventShape = Primitive | AOptional[Primitive]

  /**
   * Publish a domain event. All cases of the sealed trait must be flat records.
   */
  def publish[A](event: A)(implicit schema: Schema[A], ev: Allows[A, Record[EventShape]]): String = {
    val dv                  = schema.toDynamicValue(event)
    val (typeName, payload) = dv match {
      case DynamicValue.Variant(name, inner) => (name, inner.toJson.toString)
      case _                                 => (schema.reflect.typeId.name, dv.toJson.toString)
    }
    s"PUBLISH topic=${schema.reflect.typeId.name} type=$typeName payload=$payload"
  }

  /**
   * Publish events that may contain sequence fields (e.g. batch operations).
   */
  def publishBatch[A](event: A)(implicit
    schema: Schema[A],
    ev: Allows[A, Record[Primitive | Sequence[Primitive]]]
  ): String = {
    val dv                  = schema.toDynamicValue(event)
    val (typeName, payload) = dv match {
      case DynamicValue.Variant(name, inner) => (name, inner.toJson.toString)
      case _                                 => (schema.reflect.typeId.name, dv.toJson.toString)
    }
    s"PUBLISH topic=${schema.reflect.typeId.name} type=$typeName payload=$payload"
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsEventBusExample extends App {

  // Flat sealed trait — all cases are records of primitives/optionals
  show(EventBus.publish[AccountEvent](AccountOpened("acc-001", "Alice", BigDecimal("1000.00"))))
  show(EventBus.publish[AccountEvent](FundsDeposited("acc-001", BigDecimal("500.00"))))
  show(EventBus.publish[AccountEvent](AccountClosed("acc-001", Some("customer request"))))

  // Nested sealed trait — auto-unwrap is recursive
  // InventoryAlert extends InventoryEvent, both are unwrapped
  show(EventBus.publish[InventoryEvent](ItemAdded("SKU-100", 50)))
  show(EventBus.publish[InventoryEvent](LowStock("SKU-100", 3)))
  show(EventBus.publish[InventoryEvent](OutOfStock("SKU-100")))

  // Events with sequence fields use a wider grammar
  show(EventBus.publishBatch[BatchEvent](BatchImport("batch-42", List("item-1", "item-2", "item-3"))))
  show(EventBus.publishBatch[BatchEvent](BatchComplete("batch-42", 3)))
}
