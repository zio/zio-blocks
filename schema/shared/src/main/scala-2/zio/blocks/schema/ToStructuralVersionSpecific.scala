package zio.blocks.schema

import scala.language.dynamics
import scala.language.experimental.macros

/**
 * Runtime representation for structural values in Scala 2.
 *
 * Uses Dynamic for field access. When you call `record.fieldName`, Scala 2
 * translates it to `record.selectDynamic("fieldName")`.
 *
 * Note: In Scala 2, we cannot use refinement types like
 * `StructuralRecord { def name: String }` because Scala 2 uses reflection (not
 * Dynamic) for refinement member access. Instead, we use the base
 * `StructuralRecord` type and rely on Dynamic for field access.
 */
final class StructuralRecord(private val values: Map[String, Any]) extends Dynamic {

  def selectDynamic(name: String): Any = values(name)

  /** Get the underlying values map (useful for Schema integration) */
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
