package zio.blocks.schema

import zio.schema.TypeId

/**
 * Scala 3 specific TypeId to TypeName conversion.
 *
 * In Scala 3, type parameters are encoded in the name string for non-standard
 * types (e.g., "Option[Meter]" instead of params list).
 */
private[schema] object TypeIdToTypeNameImpl {

  /**
   * Converts a zio.schema.TypeId to a TypeName for Scala 3.
   *
   * Type parameters are kept encoded in the typeName string, with params = Nil
   * for most cases.
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
   * Standard collection types that may have params parsed. In Scala 3, we parse
   * params for standard Scala types with simple parameters only.
   */
  private val standardCollectionTypes = Set(
    "Option",
    "List",
    "Vector",
    "Set",
    "Map",
    "Seq",
    "ArraySeq",
    "IndexedSeq",
    "Some"
  )

  /**
   * Parses a type name string that may contain type parameters.
   *
   * In Scala 3, qualified type parameters stay encoded in the name. Only
   * simple, standard Scala type parameters are parsed into params.
   *
   * Special handling for NamedTuple which has format: NamedTuple[labels][types]
   */
  private def parseTypeNameWithParams(typeNameStr: String, namespace: Namespace): TypeName[?] = {
    val bracketIndex = typeNameStr.indexOf('[')
    if (bracketIndex == -1) {
      // No type parameters
      val (finalNamespace, finalName) =
        if (namespace.packages.isEmpty && namespace.values.isEmpty && typeNameStr.contains(".")) {
          val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(typeNameStr)
          val simpleName        = TypeNameConversions.extractSimpleTypeName(typeNameStr)
          (inferredNamespace, simpleName)
        } else {
          (namespace, typeNameStr)
        }
      new TypeName(finalNamespace, finalName, Nil)
    } else {
      val baseNameWithNamespace = typeNameStr.substring(0, bracketIndex)

      // Special case for NamedTuple with double brackets: NamedTuple[labels][types]
      // Keep the entire string as-is without parsing
      if (baseNameWithNamespace == "NamedTuple" || baseNameWithNamespace.endsWith(".NamedTuple")) {
        val (baseNamespace, baseName) = if (baseNameWithNamespace.contains(".")) {
          val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(baseNameWithNamespace)
          val simpleName        = TypeNameConversions.extractSimpleTypeName(baseNameWithNamespace)
          (inferredNamespace, simpleName)
        } else {
          (namespace, baseNameWithNamespace)
        }
        // Return the entire typeNameStr as name, no params parsing
        new TypeName(baseNamespace, typeNameStr, Nil)
      } else {
        val paramsStr = typeNameStr.substring(bracketIndex + 1, typeNameStr.length - 1)

        // Extract namespace and simple name
        val (baseNamespace, baseName) = if (baseNameWithNamespace.contains(".")) {
          val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(baseNameWithNamespace)
          val simpleName        = TypeNameConversions.extractSimpleTypeName(baseNameWithNamespace)
          (inferredNamespace, simpleName)
        } else {
          (namespace, baseNameWithNamespace)
        }

        // In Scala 3: Check if params are qualified (contain dots)
        val parsedParams       = TypeNameConversions.parseTypeParams(paramsStr)
        val hasQualifiedParams = parsedParams.exists(_.contains("."))

        // For standard collections with simple params, parse them into list
        // For anything with qualified params (e.g., Option[zio.blocks.schema.Meter]),
        // keep params encoded in name
        if (standardCollectionTypes.contains(baseName) && !hasQualifiedParams) {
          val params = parsedParams.map { paramStr =>
            val trimmed = paramStr.trim
            if (trimmed.indexOf('[') == -1) {
              val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(trimmed)
              val simpleName        = TypeNameConversions.extractSimpleTypeName(trimmed)
              new TypeName(inferredNamespace, simpleName, Nil)
            } else {
              val paramBaseName     = trimmed.substring(0, trimmed.indexOf('['))
              val inferredNamespace = TypeNameConversions.inferNamespaceForTypeName(paramBaseName)
              parseTypeNameWithParams(trimmed, inferredNamespace)
            }
          }
          new TypeName(baseNamespace, baseName, params)
        } else {
          // For Option with qualified params, or any other type, keep params in name
          // Extract simple names from qualified names for the final type name string
          if (hasQualifiedParams) {
            val simpleParamNames = parsedParams.map { paramStr =>
              TypeNameConversions.extractSimpleTypeName(paramStr.trim)
            }
            new TypeName(baseNamespace, s"$baseName[${simpleParamNames.mkString(", ")}]", Nil)
          } else {
            // Keep as-is
            new TypeName(baseNamespace, typeNameStr, Nil)
          }
        }
      }
    }
  }
}
