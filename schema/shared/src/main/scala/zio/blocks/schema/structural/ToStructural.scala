package zio.blocks.schema.structural

import zio.blocks.schema.{Schema, SchemaError}

/**
 * Type class for converting a nominal type A to its structural equivalent.
 *
 * Structural types represent the "shape" of a type without its nominal
 * identity. For example, `case class Person(name: String, age: Int)` has the
 * structural type `{age:Int,name:String}` (fields sorted alphabetically).
 *
 * Usage:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * val structural = ToStructural[Person].toStructural(Person("Alice", 30))
 * // structural.typeName == "{age:Int,name:String}"
 * }}}
 *
 * @tparam A
 *   The nominal type to convert
 */
trait ToStructural[A] {

  /**
   * The normalized structural type name.
   */
  def structuralTypeName: String

  /**
   * Convert a value of type A to its structural representation.
   */
  def toStructural(value: A): StructuralValue

  /**
   * Convert a structural value back to type A.
   */
  def fromStructural(value: StructuralValue): Either[SchemaError, A]

  /**
   * Get the schema for the structural type.
   */
  def structuralSchema: Schema[StructuralValue]
}

object ToStructural extends ToStructuralLowPriority {

  def apply[A](implicit instance: ToStructural[A]): ToStructural[A] = instance

  /**
   * Create a ToStructural instance from functions.
   */
  def instance[A](
    typeName: String,
    to: A => StructuralValue,
    from: StructuralValue => Either[SchemaError, A],
    schema: Schema[StructuralValue]
  ): ToStructural[A] = new ToStructural[A] {
    def structuralTypeName: String                                     = typeName
    def toStructural(value: A): StructuralValue                        = to(value)
    def fromStructural(value: StructuralValue): Either[SchemaError, A] = from(value)
    def structuralSchema: Schema[StructuralValue]                      = schema
  }

  /**
   * Error for recursive types which cannot be converted to structural types.
   */
  final class RecursiveTypeError(typeName: String)
      extends RuntimeException(
        s"Cannot convert recursive type '$typeName' to structural type. " +
          "Structural types must have a finite, non-recursive structure."
      )

  /**
   * Error for sum types in Scala 2 (not supported).
   */
  final class SumTypeScala2Error(typeName: String)
      extends RuntimeException(
        s"Cannot convert sum type '$typeName' to structural type in Scala 2. " +
          "Sum types require union types, which are only available in Scala 3."
      )
}

/**
 * Low priority implicits for ToStructural. These provide fallback derivation
 * using Schema.
 */
trait ToStructuralLowPriority {

  /**
   * Derive a ToStructural instance from a Schema. This is a fallback that uses
   * DynamicValue conversion.
   */
  implicit def fromSchema[A](implicit schema: Schema[A]): ToStructural[A] =
    new ToStructural[A] {
      private lazy val cachedTypeName: String = extractTypeName(schema)

      def structuralTypeName: String = cachedTypeName

      def toStructural(value: A): StructuralValue = {
        val dv = schema.toDynamicValue(value)
        dynamicToStructural(dv, structuralTypeName)
      }

      def fromStructural(value: StructuralValue): Either[SchemaError, A] =
        schema.fromDynamicValue(value.toDynamicValue)

      def structuralSchema: Schema[StructuralValue] =
        // StructuralValue doesn't have its own schema - we use DynamicValue as the underlying representation.
        // This is a workaround that creates a schema treating StructuralValue as a wrapper around DynamicValue.
        StructuralValueSchema.schema

      private def extractTypeName(s: Schema[_]): String =
        extractReflectTypeName(s.reflect)

      private def extractReflectTypeName(r: Any): String = {
        import zio.blocks.schema.Reflect
        r match {
          case r: Reflect.Record[_, _] =>
            val fields = r.fields.map { field =>
              (field.name, extractReflectTypeName(field.value))
            }
            StructuralValue.productTypeName(fields)
          case r: Reflect.Variant[_, _] =>
            val cases = r.cases.map(_.name)
            StructuralValue.sumTypeName(cases)
          case r: Reflect.Primitive[_, _] =>
            r.primitiveType.typeName.name
          case r: Reflect.Sequence[_, _, _] =>
            s"Seq[${extractReflectTypeName(r.element)}]"
          case r: Reflect.Map[_, _, _, _] =>
            s"Map[${extractReflectTypeName(r.key)},${extractReflectTypeName(r.value)}]"
          case r: Reflect.Wrapper[_, _, _] =>
            extractReflectTypeName(r.wrapped)
          case _: Reflect.Dynamic[_] =>
            "Dynamic"
          case d: Reflect.Deferred[_, _] =>
            // Force evaluation for deferred types (recursive)
            throw new ToStructural.RecursiveTypeError(d.typeName.name)
          case _ =>
            "Unknown"
        }
      }

      private def dynamicToStructural(dv: zio.blocks.schema.DynamicValue, typeName: String): StructuralValue = {
        import zio.blocks.schema.DynamicValue
        dv match {
          case DynamicValue.Record(fields) =>
            val fieldValues = fields.map { case (name, value) =>
              (name, dynamicToAny(value), dynamicTypeName(value))
            }
            StructuralValue.Product(fieldValues, typeName)
          case DynamicValue.Variant(caseName, payload) =>
            StructuralValue.Sum(caseName, dynamicToStructural(payload, caseName), typeName)
          case _ =>
            // For primitives, wrap in a single-field record
            StructuralValue.Product(Vector(("value", dynamicToAny(dv), dynamicTypeName(dv))), typeName)
        }
      }

      private def dynamicToAny(dv: zio.blocks.schema.DynamicValue): Any = {
        import zio.blocks.schema.{DynamicValue, PrimitiveValue}
        dv match {
          case DynamicValue.Primitive(pv) =>
            pv match {
              case PrimitiveValue.String(v)  => v
              case PrimitiveValue.Int(v)     => v
              case PrimitiveValue.Long(v)    => v
              case PrimitiveValue.Double(v)  => v
              case PrimitiveValue.Float(v)   => v
              case PrimitiveValue.Boolean(v) => v
              case PrimitiveValue.Byte(v)    => v
              case PrimitiveValue.Short(v)   => v
              case PrimitiveValue.Char(v)    => v
              case PrimitiveValue.Unit       => ()
              case _                         => pv.toString
            }
          case DynamicValue.Record(fields) =>
            fields.map { case (k, v) => (k, dynamicToAny(v)) }.toMap
          case DynamicValue.Sequence(elements) =>
            elements.map(dynamicToAny).toList
          case DynamicValue.Variant(caseName, payload) =>
            (caseName, dynamicToAny(payload))
          case DynamicValue.Map(entries) =>
            entries.map { case (k, v) => (dynamicToAny(k), dynamicToAny(v)) }.toMap
        }
      }

      private def dynamicTypeName(dv: zio.blocks.schema.DynamicValue): String = {
        import zio.blocks.schema.{DynamicValue, PrimitiveValue}
        dv match {
          case DynamicValue.Primitive(pv) =>
            pv match {
              case _: PrimitiveValue.String  => "String"
              case _: PrimitiveValue.Int     => "Int"
              case _: PrimitiveValue.Long    => "Long"
              case _: PrimitiveValue.Double  => "Double"
              case _: PrimitiveValue.Float   => "Float"
              case _: PrimitiveValue.Boolean => "Boolean"
              case _: PrimitiveValue.Byte    => "Byte"
              case _: PrimitiveValue.Short   => "Short"
              case _: PrimitiveValue.Char    => "Char"
              case PrimitiveValue.Unit       => "Unit"
              case _                         => "Any"
            }
          case DynamicValue.Record(_)     => "Record"
          case DynamicValue.Sequence(_)   => "Seq"
          case DynamicValue.Variant(_, _) => "Variant"
          case DynamicValue.Map(_)        => "Map"
        }
      }
    }
}

/**
 * Provides a Schema instance for StructuralValue.
 */
object StructuralValueSchema {
  import zio.blocks.schema.{DynamicValue, Namespace, PrimitiveValue, Reflect, TypeName}
  import zio.blocks.schema.binding.Binding

  /**
   * Schema for StructuralValue using DynamicValue as the underlying
   * representation.
   */
  implicit lazy val schema: Schema[StructuralValue] = {
    // Create a wrapper reflect that converts between StructuralValue and DynamicValue
    val wrappedReflect = Reflect.dynamic[Binding]
    val wrapperBinding = new Binding.Wrapper[StructuralValue, DynamicValue](
      (dv: DynamicValue) => Right(dynamicToStructural(dv)),
      (sv: StructuralValue) => sv.toDynamicValue
    )

    new Schema(
      new Reflect.Wrapper[Binding, StructuralValue, DynamicValue](
        wrappedReflect,
        TypeName(Namespace(Seq("zio", "blocks", "schema", "structural")), "StructuralValue"),
        None,
        wrapperBinding
      )
    )
  }

  private def dynamicToStructural(dv: DynamicValue): StructuralValue =
    dv match {
      case DynamicValue.Record(fields) =>
        val fieldValues = fields.map { case (name, value) =>
          (name, dynamicToAny(value), dynamicTypeName(value))
        }
        val typeName = StructuralValue.productTypeName(fieldValues.map(f => (f._1, f._3)))
        StructuralValue.Product(fieldValues, typeName)
      case DynamicValue.Variant(caseName, payload) =>
        val innerSv = dynamicToStructural(payload)
        StructuralValue.Sum(caseName, innerSv, caseName)
      case _ =>
        val typeName = dynamicTypeName(dv)
        StructuralValue.Product(Vector(("value", dynamicToAny(dv), typeName)), s"{value:$typeName}")
    }

  private def dynamicToAny(dv: DynamicValue): Any =
    dv match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case PrimitiveValue.String(v)  => v
          case PrimitiveValue.Int(v)     => v
          case PrimitiveValue.Long(v)    => v
          case PrimitiveValue.Double(v)  => v
          case PrimitiveValue.Float(v)   => v
          case PrimitiveValue.Boolean(v) => v
          case PrimitiveValue.Byte(v)    => v
          case PrimitiveValue.Short(v)   => v
          case PrimitiveValue.Char(v)    => v
          case PrimitiveValue.Unit       => ()
          case _                         => pv.toString
        }
      case DynamicValue.Record(fields) =>
        fields.map { case (k, v) => (k, dynamicToAny(v)) }.toMap
      case DynamicValue.Sequence(elements) =>
        elements.map(dynamicToAny).toList
      case DynamicValue.Variant(caseName, payload) =>
        (caseName, dynamicToAny(payload))
      case DynamicValue.Map(entries) =>
        entries.map { case (k, v) => (dynamicToAny(k), dynamicToAny(v)) }.toMap
    }

  private def dynamicTypeName(dv: DynamicValue): String =
    dv match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case _: PrimitiveValue.String  => "String"
          case _: PrimitiveValue.Int     => "Int"
          case _: PrimitiveValue.Long    => "Long"
          case _: PrimitiveValue.Double  => "Double"
          case _: PrimitiveValue.Float   => "Float"
          case _: PrimitiveValue.Boolean => "Boolean"
          case _: PrimitiveValue.Byte    => "Byte"
          case _: PrimitiveValue.Short   => "Short"
          case _: PrimitiveValue.Char    => "Char"
          case PrimitiveValue.Unit       => "Unit"
          case _                         => "Any"
        }
      case DynamicValue.Record(_)     => "Record"
      case DynamicValue.Sequence(_)   => "Seq"
      case DynamicValue.Variant(_, _) => "Variant"
      case DynamicValue.Map(_)        => "Map"
    }
}
