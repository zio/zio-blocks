package zio.blocks.schema.structural

import zio.blocks.schema.{DynamicValue, SchemaError}

/**
 * A runtime representation of a structural type value. Structural types are
 * defined by their shape (fields and types) rather than by their nominal
 * identity.
 *
 * For products: represents a record with named fields For sums (Scala 3 only):
 * represents a union of variants
 */
sealed trait StructuralValue {

  /**
   * The normalized type name for this structural value. Format:
   * `{field1:Type1,field2:Type2}` with fields sorted alphabetically.
   */
  def typeName: String

  /**
   * Convert to DynamicValue for schema operations.
   */
  def toDynamicValue: DynamicValue

  /**
   * Access a field by name (for products).
   */
  def selectDynamic(name: String): Either[SchemaError, Any]
}

object StructuralValue {

  /**
   * A structural product value (case class-like).
   */
  final case class Product(
    fields: Vector[(String, Any, String)], // (name, value, typeName)
    override val typeName: String
  ) extends StructuralValue {

    def toDynamicValue: DynamicValue = {
      val dvFields = fields.map { case (name, value, _) =>
        // Convert value to DynamicValue using runtime reflection
        // This is a simplified version - full impl would use Schema
        name -> valueToDynamic(value)
      }
      DynamicValue.Record(dvFields)
    }

    def selectDynamic(name: String): Either[SchemaError, Any] =
      fields.find(_._1 == name) match {
        case Some((_, value, _)) => Right(value)
        case None                => Left(SchemaError.missingField(Nil, name))
      }

    private def valueToDynamic(value: Any): DynamicValue = value match {
      case s: String           => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(s))
      case i: Int              => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Int(i))
      case l: Long             => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Long(l))
      case d: Double           => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Double(d))
      case f: Float            => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Float(f))
      case b: Boolean          => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Boolean(b))
      case b: Byte             => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Byte(b))
      case s: Short            => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Short(s))
      case c: Char             => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Char(c))
      case ()                  => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.Unit)
      case sv: StructuralValue => sv.toDynamicValue
      case dv: DynamicValue    => dv
      case other               => DynamicValue.Primitive(zio.blocks.schema.PrimitiveValue.String(other.toString))
    }
  }

  /**
   * A structural sum value (Scala 3 only - union type).
   */
  final case class Sum(
    caseName: String,
    value: StructuralValue,
    override val typeName: String
  ) extends StructuralValue {

    def toDynamicValue: DynamicValue =
      DynamicValue.Variant(caseName, value.toDynamicValue)

    def selectDynamic(name: String): Either[SchemaError, Any] =
      if (name == caseName) Right(value)
      else Left(SchemaError.expectationMismatch(Nil, s"Expected case '$name' but got '$caseName'"))
  }

  /**
   * Generate a normalized type name for a product type. Fields are sorted
   * alphabetically for deterministic naming.
   */
  def productTypeName(fields: Seq[(String, String)]): String = {
    val sorted = fields.sortBy(_._1)
    sorted.map { case (name, tpe) => s"$name:$tpe" }.mkString("{", ",", "}")
  }

  /**
   * Generate a normalized type name for a sum type (Scala 3 only).
   */
  def sumTypeName(cases: Seq[String]): String =
    cases.sorted.mkString(" | ")

  /**
   * Create a product structural value.
   */
  def product(fields: (String, Any, String)*): Product = {
    val fieldVec = fields.toVector
    val typeName = productTypeName(fieldVec.map(f => (f._1, f._3)))
    Product(fieldVec, typeName)
  }
}
