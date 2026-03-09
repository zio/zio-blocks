package as

import zio.blocks.schema.As
import util.ShowExpr.show

// Demonstrates bidirectional schema migration using As.derived.
// Unlike Into, As supports rollback: data can round-trip V1 → V2 → V1
// because As.derived generates both directions at compile time.
object AsSchemaEvolutionExample extends App {

  case class OrderV1(id: String, total: Int)
  case class OrderV2(id: String, total: Long, discount: Option[Int])

  val migrate = As.derived[OrderV1, OrderV2]

  // Forward migration: total widens from Int to Long; discount defaults to None
  show(migrate.into(OrderV1("ord-1", 4999)))

  // Rollback: total narrows back to Int; discount is discarded (it is Option)
  show(migrate.from(OrderV2("ord-1", 4999L, Some(500))))

  // Round-trip: V1 → V2 → V1 — value within Int range returns the original
  show(migrate.into(OrderV1("ord-2", 100)).flatMap(migrate.from))

  // Overflow on rollback: total too large for Int — narrowing returns Left
  show(migrate.from(OrderV2("ord-3", Long.MaxValue, None)))
}
