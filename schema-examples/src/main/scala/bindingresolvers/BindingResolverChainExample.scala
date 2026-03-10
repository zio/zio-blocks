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

package bindingresolvers

import zio.blocks.schema._
import zio.blocks.schema.binding._
import util.ShowExpr.show

// Demonstrates left-biased precedence when combining resolvers with ++.
object BindingResolverChainExample extends App {

  case class Order(id: Long, item: String, quantity: Int)
  object Order {
    implicit val schema: Schema[Order] = Schema.derived[Order]
  }

  case class LineItem(orderId: Long, sku: String, price: Double)
  object LineItem {
    implicit val schema: Schema[LineItem] = Schema.derived[LineItem]
  }

  // Each resolver covers different types; ++ forms a resolution chain.
  val orderResolver: BindingResolver.Registry =
    BindingResolver.empty.bind(Binding.of[Order])

  val lineItemResolver: BindingResolver.Registry =
    BindingResolver.empty.bind(Binding.of[LineItem])

  val combined: BindingResolver = orderResolver ++ lineItemResolver ++ BindingResolver.defaults

  show(combined.resolveRecord[Order].isDefined)
  show(combined.resolveRecord[LineItem].isDefined)
  show(combined.resolvePrimitive[Long].isDefined)   // from defaults
  show(combined.resolvePrimitive[String].isDefined) // from defaults
}

// Demonstrates schema rebinding: convert DynamicSchema back to a typed Schema.
object BindingResolverRebindExample extends App {

  case class Product(sku: String, price: Double, tags: List[String])
  object Product {
    implicit val schema: Schema[Product] = Schema.derived[Product]
  }

  val dynamic: DynamicSchema = Schema[Product].toDynamicSchema

  val resolver: BindingResolver =
    BindingResolver.empty.bind(Binding.of[Product]) ++ BindingResolver.defaults

  val rebound: Schema[Product] = dynamic.rebind[Product](resolver)

  val product = Product("ABC-123", 29.99, List("sale", "electronics"))

  // Verify the rebound schema can round-trip values through DynamicValue
  val dv      = rebound.reflect.toDynamicValue(product)
  val decoded = rebound.reflect.fromDynamicValue(dv)

  show(decoded)
}
