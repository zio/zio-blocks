package bindingresolvers

import zio.blocks.schema._
import zio.blocks.schema.binding._
import util.ShowExpr.show

// Demonstrates the schema registry pattern: schemas are persisted as DynamicSchema
// (pure structural metadata — no Scala functions, fully serializable) and rehydrated
// on service startup by rebinding them against local domain types using BindingResolver.
//
// In production, the registry Map would be populated by loading schemas from a
// database or a remote schema registry service (e.g., Confluent Schema Registry).
// DynamicSchema carries only field names, type names, and annotations, so it can
// be stored as JSON or bytes without capturing any Scala closures.

// ── Domain model ──────────────────────────────────────────────────────────────────

object BindingResolverSchemaRegistryExample extends App {

  case class Customer(id: Long, name: String, email: String)
  object Customer {
    implicit val schema: Schema[Customer] = Schema.derived[Customer]
  }

  case class Invoice(customerId: Long, amount: Double, paid: Boolean)
  object Invoice {
    implicit val schema: Schema[Invoice] = Schema.derived[Invoice]
  }

  // ── Schema registry (simulated) ───────────────────────────────────────────────

  // Normally loaded from storage on startup. DynamicSchema#toDynamicSchema strips
  // all Scala functions, leaving only serializable structural metadata.
  val registry: Map[String, DynamicSchema] = Map(
    "customer" -> Schema[Customer].toDynamicSchema,
    "invoice"  -> Schema[Invoice].toDynamicSchema
  )

  // ── Resolver ──────────────────────────────────────────────────────────────────

  // One BindingResolver covers all domain types this service knows about.
  // BindingResolver.defaults handles primitives, java.time types, and standard collections.
  val resolver: BindingResolver =
    BindingResolver.empty
      .bind(Binding.of[Customer])
      .bind(Binding.of[Invoice])
      ++ BindingResolver.defaults

  // ── Rebind on startup ─────────────────────────────────────────────────────────

  // DynamicSchema#rebind walks the unbound Reflect tree and attaches Binding
  // instances from the resolver to each node, producing a fully operational Schema.
  val customerSchema: Schema[Customer] = registry("customer").rebind[Customer](resolver)
  val invoiceSchema: Schema[Invoice]   = registry("invoice").rebind[Invoice](resolver)

  // ── Round-trip: encode then decode through the rebound schemas ─────────────────

  val alice = Customer(1L, "Alice", "alice@example.com")
  // Encodes Customer to a DynamicValue tree, then decodes it back.
  val aliceDecoded =
    customerSchema.reflect.fromDynamicValue(customerSchema.reflect.toDynamicValue(alice))
  show(aliceDecoded)

  val inv = Invoice(1L, 250.00, paid = false)
  // Encodes Invoice to a DynamicValue tree, then decodes it back.
  val invDecoded =
    invoiceSchema.reflect.fromDynamicValue(invoiceSchema.reflect.toDynamicValue(inv))
  show(invDecoded)
}
