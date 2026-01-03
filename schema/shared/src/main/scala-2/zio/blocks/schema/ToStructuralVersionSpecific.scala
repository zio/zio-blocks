package zio.blocks.schema

import scala.language.dynamics
import scala.language.experimental.macros

//Runtime representation for structural values in Scala 2.
final class StructuralRecord(private val values: Map[String, Any]) extends Dynamic {

  def selectDynamic(name: String): Any = values(name)

  // Get the underlying values map (useful for Schema integration)
  def toMap: Map[String, Any] = values

  override def toString: String =
    values.map { case (k, v) => s"$k: $v" }.mkString("{", ", ", "}")

  override def equals(other: Any): Boolean = other match {
    case sr: StructuralRecord => values == sr.values
    case _                    => false
  }

  override def hashCode: Int = values.hashCode
}

object StructuralRecord {
  def apply(values: Map[String, Any]): StructuralRecord = new StructuralRecord(values)

  def apply(values: (String, Any)*): StructuralRecord = new StructuralRecord(values.toMap)
}

trait ToStructuralVersionSpecific {
  def derived[A]: ToStructural[A] = macro DeriveToStructural.derivedImpl[A]
}
