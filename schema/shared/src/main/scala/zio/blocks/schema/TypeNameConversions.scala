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
   * Type parameters encoded in the typeName string (e.g., "Option[String]") are
   * parsed and converted to TypeName params when possible.
   *
   * @param typeId
   *   The TypeId to convert
   * @return
   *   The corresponding TypeName
   */
  def typeIdToTypeName[A](typeId: TypeId): TypeName[A] =
    typeId match {
      case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
        val namespace = Namespace(packageName.toSeq, objectNames.toSeq)
        parseTypeNameWithParams(typeNameStr, namespace).asInstanceOf[TypeName[A]]

      case other =>
        // Fallback for any other TypeId variants (e.g., if Structural exists as a sealed trait case)
        val namespace = Namespace(Seq.empty, Nil)
        val name      = other.toString // fallback - try to get some representation
        new TypeName(namespace, name, Nil).asInstanceOf[TypeName[A]]
    }

  /**
   * Infers the namespace for a type name based on common patterns.
   * For standard Scala types (Int, String, Long, etc.), returns scala namespace.
   * For other types, returns empty namespace.
   */
  private def inferNamespaceForTypeName(typeName: String): Namespace = {
    // Standard Scala primitive and common types
    val scalaTypes = Set(
      "Int", "Long", "Short", "Byte", "Char", "Float", "Double", "Boolean", "Unit",
      "String", "Any", "AnyRef", "AnyVal", "Nothing", "Null",
      "Option", "Some", "None", "Either", "Left", "Right",
      "List", "Vector", "Set", "Map", "Seq", "Array", "ArraySeq", "IndexedSeq",
      "Tuple1", "Tuple2", "Tuple3", "Tuple4", "Tuple5", "Tuple6", "Tuple7", "Tuple8",
      "Tuple9", "Tuple10", "Tuple11", "Tuple12", "Tuple13", "Tuple14", "Tuple15",
      "Tuple16", "Tuple17", "Tuple18", "Tuple19", "Tuple20", "Tuple21", "Tuple22"
    )
    if (scalaTypes.contains(typeName)) {
      Namespace(Seq("scala"), Nil)
    } else {
      Namespace(Seq.empty, Nil)
    }
  }

  /**
   * Parses a type name string that may contain type parameters.
   * Handles cases like:
   * - "Int" -> TypeName("Int", Nil)
   * - "Option[String]" -> TypeName("Option", List(TypeName("String", Nil)))
   * - "List[Option[Int]]" -> TypeName("List", List(TypeName("Option[Int]", Nil))) (nested params not fully parsed)
   * - "Map[String, Int]" -> TypeName("Map", List(TypeName("String", Nil), TypeName("Int", Nil)))
   */
  private def parseTypeNameWithParams(typeNameStr: String, namespace: Namespace): TypeName[?] = {
    val bracketIndex = typeNameStr.indexOf('[')
    if (bracketIndex == -1) {
      // No type parameters
      new TypeName(namespace, typeNameStr, Nil)
    } else {
      val baseName = typeNameStr.substring(0, bracketIndex)
      val paramsStr = typeNameStr.substring(bracketIndex + 1, typeNameStr.length - 1) // Remove '[' and ']'
      
      // Parse comma-separated type parameters
      // Try to infer namespace for parameters based on type name
      val params = parseTypeParams(paramsStr).map { paramStr =>
        // For each parameter, try to parse it recursively
        // For simple cases like "String", "Int", create a simple TypeName with inferred namespace
        // For complex cases like "Option[Int]", we create a TypeName with the full string as name
        if (paramStr.indexOf('[') == -1) {
          // Simple type name without parameters - try to infer namespace
          val paramName = paramStr.trim
          val inferredNamespace = inferNamespaceForTypeName(paramName)
          new TypeName(inferredNamespace, paramName, Nil)
        } else {
          // Complex type with parameters - parse recursively with empty namespace
          // (we can't infer namespace for complex types)
          parseTypeNameWithParams(paramStr.trim, Namespace(Seq.empty, Nil))
        }
      }
      
      new TypeName(namespace, baseName, params)
    }
  }

  /**
   * Parses comma-separated type parameters, handling nested brackets.
   * Example: "String, Int" -> List("String", "Int")
   * Example: "String, Option[Int]" -> List("String", "Option[Int]")
   * Example: "List[Option[Int]], String" -> List("List[Option[Int]]", "String")
   */
  private def parseTypeParams(paramsStr: String): List[String] = {
    if (paramsStr.trim.isEmpty) {
      Nil
    } else {
      var result = List.empty[String]
      val current = new StringBuilder
      var bracketDepth = 0
      var i = 0
      
      while (i < paramsStr.length) {
        val char = paramsStr(i)
        char match {
          case '[' =>
            bracketDepth += 1
            current.append(char)
          case ']' =>
            bracketDepth -= 1
            current.append(char)
          case ',' if bracketDepth == 0 =>
            // Found a top-level comma - this is a parameter separator
            val param = current.toString.trim
            if (param.nonEmpty) {
              result = param :: result
            }
            current.clear()
          case _ =>
            current.append(char)
        }
        i += 1
      }
      
      // Add the last parameter
      val lastParam = current.toString.trim
      if (lastParam.nonEmpty) {
        result = lastParam :: result
      }
      
      result.reverse
    }
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
