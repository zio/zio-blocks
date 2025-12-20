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
   * The behavior differs between Scala 2.13 and Scala 3:
   * - Scala 2.13: Always parses params into list for standard collections
   * - Scala 3: Keeps qualified params encoded in name string
   *
   * @param typeId
   *   The TypeId to convert
   * @return
   *   The corresponding TypeName
   */
  def typeIdToTypeName[A](typeId: TypeId): TypeName[A] =
    TypeIdToTypeNameImpl.typeIdToTypeName[A](typeId)

  /**
   * Infers the namespace for a type name based on common patterns.
   * For standard Scala types (Int, String, Long, etc.), returns scala namespace.
   * For fully qualified names (e.g., "zio.blocks.schema.ZIOPreludeSupportSpec.Meter"),
   * extracts the namespace from the qualified name.
   * For other types, returns empty namespace.
   *
   * This is exposed as package-private for use by version-specific implementations.
   */
  private[schema] def inferNamespaceForTypeName(typeName: String): Namespace = {
    // Check if this is a fully qualified name (contains dots)
    if (typeName.contains(".")) {
      // Extract namespace from qualified name
      // Format: "package1.package2.objectName.typeName" or "package1.package2.typeName"
      val parts = typeName.split('.').toList
      if (parts.length >= 2) {
        // Last part is the type name, everything before is namespace
        val namespaceParts = parts.init
        
        // Try to determine if last part before type name is an object or package
        // This is heuristic - we assume if it starts with uppercase, it's an object
        val (packages, objects) = if (namespaceParts.nonEmpty && namespaceParts.last.head.isUpper) {
          // Last part is likely an object
          (namespaceParts.init, List(namespaceParts.last))
        } else {
          // All parts are packages
          (namespaceParts, Nil)
        }
        
        Namespace(packages, objects)
      } else {
        Namespace(Seq.empty, Nil)
      }
    } else {
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
  }
  
  /**
   * Extracts the simple type name from a potentially qualified name.
   * Example: "zio.blocks.schema.ZIOPreludeSupportSpec.Meter" -> "Meter"
   * Example: "Int" -> "Int"
   *
   * This is exposed as package-private for use by version-specific implementations.
   */
  private[schema] def extractSimpleTypeName(qualifiedName: String): String = {
    if (qualifiedName.contains(".")) {
      qualifiedName.split('.').last
    } else {
      qualifiedName
    }
  }



  /**
   * Parses comma-separated type parameters, handling nested brackets.
   * Example: "String, Int" -> List("String", "Int")
   * Example: "String, Option[Int]" -> List("String", "Option[Int]")
   * Example: "List[Option[Int]], String" -> List("List[Option[Int]]", "String")
   *
   * This is exposed as package-private for use by version-specific implementations.
   */
  private[schema] def parseTypeParams(paramsStr: String): List[String] = {
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
