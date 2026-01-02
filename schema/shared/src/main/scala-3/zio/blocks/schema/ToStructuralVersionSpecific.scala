package zio.blocks.schema

import scala.Selectable

// Runtime representation for structural values.
//StructuralRecord does NOT extend Dynamic to preserve proper type inference
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
