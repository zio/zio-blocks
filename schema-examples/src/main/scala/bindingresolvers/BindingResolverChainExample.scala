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
