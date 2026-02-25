package into

import zio.blocks.schema.Into
import util.ShowExpr.show

// Demonstrates automatic collection reshaping.
// Into composes through List, Vector, Set, Map, and Option,
// applying element-level type coercion at each step.
object IntoCollectionsExample extends App {

  // List → Vector with Int → Long element widening
  show(Into[List[Int], Vector[Long]].into(List(1, 2, 3)))

  // List → Set removes duplicates
  show(Into[List[Int], Set[Long]].into(List(1, 2, 2, 3)))

  // Map with key and value coercion
  show(Into[Map[String, Int], Map[String, Long]].into(Map("a" -> 1, "b" -> 2)))

  // Option element coercion — Some and None both handled
  show(Into[Option[Int], Option[Long]].into(Some(42)))
  show(Into[Option[Int], Option[Long]].into(None))

  // Case class with a collection field — the field conversion is derived automatically
  case class OrderV1(id: String, quantities: List[Int])
  case class OrderV2(id: String, quantities: Vector[Long])

  show(Into.derived[OrderV1, OrderV2].into(OrderV1("order-1", List(5, 10, 3))))
}
