package zio.blocks.schema

import scala.Selectable

/**
 * Runtime representation for structural values. Stores field values in a Map
 * and implements Selectable for field access via selectDynamic.
 *
 * When cast to a structural refinement type like
 * `Selectable { def name: String; def age: Int }`, field access like `.name`
 * will call `selectDynamic("name")`.
 */
final class StructuralRecord(private val values: Map[String, Any]) extends Selectable {
  def selectDynamic(name: String): Any = values(name)

  override def toString: String =
    values.map { case (k, v) => s"$k: $v" }.mkString("{", ", ", "}")

  override def equals(other: Any): Boolean = other match {
    case sr: StructuralRecord => values == sr.values
    case _                    => false
  }

  override def hashCode: Int = values.hashCode
}

trait ToStructuralVersionSpecific {
  transparent inline def derived[A]: ToStructural[A] = ${ DeriveToStructural.derivedImpl[A] }
}
