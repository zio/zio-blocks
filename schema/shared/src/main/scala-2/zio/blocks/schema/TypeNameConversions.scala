package zio.blocks.schema

import zio.schema.TypeId

/**
 * Scala 2.13 specific TypeId to TypeName conversion.
 *
 * In Scala 2.13, type parameters are ALWAYS parsed into the params list for
 * standard collection types (Option, List, Map, Set, Vector, etc.).
 */
private[schema] object TypeIdToTypeNameImpl {

  /**
   * Converts a zio.schema.TypeId to a TypeName for Scala 2.13.
   *
   * For standard collection types (Option, List, Map, etc.), type parameters
   * are parsed and placed in the params list.
   */
  def typeIdToTypeName[A](typeId: TypeId): TypeName[A] =
    typeId match {
      case TypeId.Nominal(packageName, objectNames, typeNameStr) =>
        val namespace = Namespace(packageName.toSeq, objectNames.toSeq)
        parseTypeNameWithParams(typeNameStr, namespace).asInstanceOf[TypeName[A]]

      case other =>
        // Fallback for any other TypeId variants
        val namespace = Namespace(Seq.empty, Nil)
        val name      = other.toString
        new TypeName(namespace, name, Nil).asInstanceOf[TypeName[A]]
    }

  /**
   * Types that should have parameters in params list (not encoded in name).
   * This matches the behavior of the old typeName() function in Scala 2.13.
   */
  private val typesWithParamsInList = Set(
    "Option", "List", "Map", "Set", "Vector", "ArraySeq", "IndexedSeq", "Seq", "Some"
  )

  /**
   * Parses a type name string that may contain type parameters.
   *
   * In Scala 2.13, for standard collection types, parameters are ALWAYS
   * parsed into the params list, even if they contain qualified names.
   */
  private def parseTypeNameWithParams(typeNameStr: String, namespace: Namespace): TypeName[?] = {
    val bracketIndex = typeNameStr.indexOf('[')
    if (bracketIndex == -1) {
      // No type parameters
      val (finalNamespace, finalName) = if (namespace.packages.isEmpty && namespace.values.isEmpty && typeNameStr.contains(".")) {
        val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(typeNameStr)
        val simpleName = TypeNameConversions.extractSimpleTypeName(typeNameStr)
        (inferredNamespace, simpleName)
      } else {
        (namespace, typeNameStr)
      }
      new TypeName(finalNamespace, finalName, Nil)
    } else {
      val baseNameWithNamespace = typeNameStr.substring(0, bracketIndex)
      val paramsStr = typeNameStr.substring(bracketIndex + 1, typeNameStr.length - 1)
      
      // Extract namespace and simple name from baseName if it's qualified
      val (baseNamespace, baseName) = if (baseNameWithNamespace.contains(".")) {
        val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(baseNameWithNamespace)
        val simpleName = TypeNameConversions.extractSimpleTypeName(baseNameWithNamespace)
        (inferredNamespace, simpleName)
      } else {
        (namespace, baseNameWithNamespace)
      }
      
      // In Scala 2.13: ALWAYS parse params into list for standard collection types
      if (typesWithParamsInList.contains(baseName)) {
        val params = TypeNameConversions.parseTypeParams(paramsStr).map { paramStr =>
          val trimmed = paramStr.trim
          if (trimmed.indexOf('[') == -1) {
            // Simple type name - infer namespace
            val paramName = trimmed
            val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(paramName)
            val simpleName = TypeNameConversions.extractSimpleTypeName(paramName)
            new TypeName(inferredNamespace, simpleName, Nil)
          } else {
            // Complex type - parse recursively
            val paramBaseName = trimmed.substring(0, trimmed.indexOf('['))
            val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(paramBaseName)
            parseTypeNameWithParams(trimmed, inferredNamespace)
          }
        }
        new TypeName(baseNamespace, baseName, params)
      } else {
        // For other types (Tuple, Either, user-defined), keep parameters encoded in name
        new TypeName(baseNamespace, typeNameStr, Nil)
      }
    }
  }
}
