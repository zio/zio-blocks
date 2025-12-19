package zio.blocks.schema

import zio.Chunk
import zio.schema.TypeId

/**
 * Utilities for converting between TypeName and zio.schema.TypeId. These
 * conversions enable the migration from TypeName to TypeId while maintaining
 * backward compatibility.
 */
object TypeNameConversions {

  /**
   * Converts a TypeName to a zio.schema.TypeId.
   *
   * Type parameters are encoded in the typeName string (e.g., "List[Int]")
   * since TypeId.Nominal doesn't support type parameters directly.
   *
   * @param typeName
   *   The TypeName to convert
   * @return
   *   The corresponding TypeId
   */
  def typeNameToTypeId[A](typeName: TypeName[A]): TypeId = {
    val packageName = Chunk.fromIterable(typeName.namespace.packages)
    val objectNames = Chunk.fromIterable(typeName.namespace.values)

    // Encode type parameters in typeName if present
    val typeNameStr = if (typeName.params.isEmpty) {
      typeName.name
    } else {
      // Encode as "TypeName[Param1, Param2, ...]"
      val paramStrs = typeName.params.map(_.name)
      s"${typeName.name}[${paramStrs.mkString(", ")}]"
    }

    // TypeId.Nominal is the only constructor available in zio-schema 1.7.5
    TypeId.Nominal(packageName, objectNames, typeNameStr)
  }

  /**
   * Converts a zio.schema.TypeId to a TypeName. This is provided for backward
   * compatibility during the migration period.
   *
   * Note: Type parameters encoded in the typeName string cannot be reliably
   * parsed back, so they will be lost in this conversion. This is a limitation
   * of the conversion.
   *
   * @param typeId
   *   The TypeId to convert
   * @return
   *   The corresponding TypeName
   */
  def typeIdToTypeName[A](typeId: TypeId): TypeName[A] =
    typeId match {
      case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
        // Extract base name (try to parse type parameters, but for now just use the string)
        // Type parameters are encoded as "TypeName[Param1, Param2]" but parsing is complex
        // For now, we'll use the full string as the name and lose type parameter info
        val namespace = Namespace(packageName.toSeq, objectNames.toSeq)
        // TODO: Could attempt to parse type parameters from typeNameStr, but it's complex
        // For now, params will be empty - this is a limitation
        new TypeName(namespace, typeNameStr, Nil).asInstanceOf[TypeName[A]]

      case other =>
        // Fallback for any other TypeId variants (e.g., if Structural exists as a sealed trait case)
        val namespace = Namespace(Seq.empty, Nil)
        val name      = other.toString // fallback - try to get some representation
        new TypeName(namespace, name, Nil).asInstanceOf[TypeName[A]]
    }

  /**
   * Extension methods for TypeName to enable easy conversion to TypeId.
   */
  implicit class TypeNameOps[A](val typeName: TypeName[A]) extends AnyVal {
    def toTypeId: TypeId = typeNameToTypeId(typeName)
  }

  /**
   * Extension methods for TypeId to enable easy conversion to TypeName (for
   * backward compatibility).
   */
  implicit class TypeIdOps(val typeId: TypeId) extends AnyVal {
    def toTypeName[A]: TypeName[A] = typeIdToTypeName[A](typeId)
  }
}
