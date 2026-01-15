package zio.blocks.schema

/**
 * A type class that converts a nominal schema to its structural equivalent.
 *
 * For example, a case class `Person(name: String, age: Int)` would be converted
 * to a structural type `{ def name: String; def age: Int }`.
 *
 * The structural type uses:
 *   - `Selectable` backing in Scala 3
 *   - `Dynamic` backing in Scala 2
 *
 * Type names are normalized with alphabetically sorted field names:
 * `{age:Int,name:String}` for the Person example.
 *
 * Recursive and mutually recursive types are rejected at compile-time with
 * helpful error messages.
 */
trait ToStructural[A] {

  /** The structural type equivalent of `A` */
  type StructuralType

  /**
   * Converts a schema of type `A` to a schema of the structural type.
   *
   * The resulting schema preserves all field metadata, validation logic, and
   * documentation from the original schema.
   */
  def apply(schema: Schema[A]): Schema[StructuralType]
}

object ToStructural extends ToStructuralCompanionVersionSpecific {

  type Aux[A, S] = ToStructural[A] { type StructuralType = S }

  /**
   * Summon an implicit ToStructural instance.
   */
  def apply[A](implicit toStructural: ToStructural[A]): ToStructural.Aux[A, toStructural.StructuralType] = toStructural

  /**
   * Helper to generate a normalized structural type name from field names and
   * types.
   *
   * Field names are sorted alphabetically. Types use their fully qualified
   * canonical representation. No whitespace in output.
   *
   * Example: `{age:Int,name:String}` for fields (name: String, age: Int)
   */
  def structuralTypeName(fields: Seq[(String, TypeName[?])]): String = {
    val sortedFields = fields.sortBy(_._1)
    val sb           = new java.lang.StringBuilder
    sb.append('{')
    var first = true
    sortedFields.foreach { case (name, typeName) =>
      if (!first) sb.append(',')
      first = false
      sb.append(name)
      sb.append(':')
      appendTypeName(sb, typeName)
    }
    sb.append('}')
    sb.toString
  }

  private def appendTypeName(sb: java.lang.StringBuilder, typeName: TypeName[?]): Unit = {
    if (typeName.namespace.packages.nonEmpty || typeName.namespace.values.nonEmpty) {
      typeName.namespace.packages.foreach { pkg =>
        sb.append(pkg)
        sb.append('.')
      }
      typeName.namespace.values.foreach { v =>
        sb.append(v)
        sb.append('.')
      }
    }
    sb.append(typeName.name)
    if (typeName.params.nonEmpty) {
      sb.append('[')
      var first = true
      typeName.params.foreach { param =>
        if (!first) sb.append(',')
        first = false
        appendTypeName(sb, param)
      }
      sb.append(']')
    }
  }
}
