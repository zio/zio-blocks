package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Structural type analyzer for schema migrations.
 *
 * This object provides macros to analyze the structural composition of types at
 * compile time, enabling the migration builder to validate that all fields are
 * properly handled during schema evolution.
 */
object StructuralAnalyzer {

  /**
   * Represents the analyzed structure of a schema type at compile time.
   */
  case class TypeStructure(
    typeName: String,
    fields: Map[String, FieldStructure],
    isStructural: Boolean,
    isEnum: Boolean,
    cases: Map[String, TypeStructure]
  ) {
    def fieldNames: Set[String] = fields.keySet
    def caseNames: Set[String]  = cases.keySet

    def render: String = {
      val fieldLines = fields.map { case (name, structure) =>
        s"  $name: ${structure.typeName}${if (structure.isOptional) "?" else ""}"
      }
      val caseLines = cases.map { case (name, structure) =>
        s"  case $name: ${structure.typeName}"
      }

      val allLines = fieldLines ++ caseLines
      s"$typeName {\n${allLines.mkString("\n")}\n}"
    }
  }

  case class FieldStructure(
    name: String,
    typeName: String,
    isOptional: Boolean,
    isPrimitive: Boolean,
    nested: Option[TypeStructure]
  )

  /**
   * Analyze the structure of a type at compile time.
   */
  inline def analyze[A]: TypeStructure =
    ${ analyzeImpl[A] }

  private def analyzeImpl[A: Type](using Quotes): Expr[TypeStructure] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]
    analyzeType(tpe)
  }

  private def analyzeType(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[TypeStructure] = {
    import quotes.reflect.*

    val typeName = tpe.show

    tpe.dealias match {
      case ref: Refinement =>
        val fields = analyzeRefinementFields(ref)
        '{
          TypeStructure(
            ${ Expr(typeName) },
            $fields,
            isStructural = true,
            isEnum = false,
            Map.empty
          )
        }

      case tpe if tpe.typeSymbol.flags.is(Flags.Case) =>
        val fields = analyzeCaseClassFields(tpe)
        '{
          TypeStructure(
            ${ Expr(typeName) },
            $fields,
            isStructural = false,
            isEnum = false,
            Map.empty
          )
        }

      case tpe if tpe.typeSymbol.flags.is(Flags.Sealed) =>
        val cases = analyzeSealedCases(tpe)
        '{
          TypeStructure(
            ${ Expr(typeName) },
            Map.empty,
            isStructural = false,
            isEnum = true,
            $cases
          )
        }

      case _ =>
        '{
          TypeStructure(
            ${ Expr(typeName) },
            Map.empty,
            isStructural = false,
            isEnum = false,
            Map.empty
          )
        }
    }
  }

  private def analyzeRefinementFields(using
    Quotes
  )(
    refinement: quotes.reflect.Refinement
  ): Expr[Map[String, FieldStructure]] = {
    import quotes.reflect.*

    def loop(
      tpe: TypeRepr,
      acc: List[Expr[(String, FieldStructure)]]
    ): List[Expr[(String, FieldStructure)]] = tpe match {
      case Refinement(parent, name, info) if name != "Tag" =>
        val fieldTypeName = info match {
          case ByNameType(resultType)       => resultType.show
          case MethodType(_, _, resultType) => resultType.show
          case other                        => other.show
        }
        val isPrimitive = isPrimitiveName(fieldTypeName)
        val isOptional  = fieldTypeName.contains("Option")

        val entry = '{
          ${ Expr(name) } -> FieldStructure(
            ${ Expr(name) },
            ${ Expr(fieldTypeName) },
            ${ Expr(isOptional) },
            ${ Expr(isPrimitive) },
            None
          )
        }
        loop(parent, entry :: acc)

      case Refinement(parent, _, _) =>
        loop(parent, acc)

      case _ => acc
    }

    val entries     = loop(refinement, Nil)
    val entriesExpr = Expr.ofList(entries)
    '{ $entriesExpr.toMap }
  }

  private def analyzeCaseClassFields(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[Map[String, FieldStructure]] = {
    val caseFields = tpe.typeSymbol.caseFields
    val entries    = caseFields.map { field =>
      val fieldName     = field.name
      val fieldType     = tpe.memberType(field)
      val fieldTypeName = fieldType.show
      val isPrimitive   = isPrimitiveRepr(fieldType)
      val isOptional    = fieldType.typeSymbol.fullName.contains("Option")

      '{
        ${ Expr(fieldName) } -> FieldStructure(
          ${ Expr(fieldName) },
          ${ Expr(fieldTypeName) },
          ${ Expr(isOptional) },
          ${ Expr(isPrimitive) },
          None
        )
      }
    }

    val entriesExpr = Expr.ofList(entries)
    '{ $entriesExpr.toMap }
  }

  private def analyzeSealedCases(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[Map[String, TypeStructure]] = {
    val children = tpe.typeSymbol.children
    val entries  = children.map { child =>
      val caseName      = child.name
      val caseType      = child.typeRef
      val caseStructure = analyzeType(caseType)
      '{ ${ Expr(caseName) } -> $caseStructure }
    }

    val entriesExpr = Expr.ofList(entries)
    '{ $entriesExpr.toMap }
  }

  private def isPrimitiveRepr(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean =
    isPrimitiveName(tpe.typeSymbol.fullName)

  private def isPrimitiveName(name: String): Boolean = {
    val primitiveNames = Set(
      "scala.Boolean",
      "scala.Byte",
      "scala.Short",
      "scala.Int",
      "scala.Long",
      "scala.Float",
      "scala.Double",
      "scala.Char",
      "java.lang.String",
      "scala.Predef.String",
      "String",
      "Int",
      "Long",
      "Boolean",
      "Double",
      "Float",
      "Byte",
      "Short",
      "Char",
      "BigInt",
      "BigDecimal",
      "java.util.UUID",
      "java.time.Instant",
      "java.time.LocalDate"
    )
    primitiveNames.exists(name.contains)
  }

  /**
   * Compare two type structures and identify differences.
   */
  def compare(
    source: TypeStructure,
    target: TypeStructure
  ): StructuralDiff = {
    val addedFields   = target.fieldNames.diff(source.fieldNames)
    val removedFields = source.fieldNames.diff(target.fieldNames)
    val commonFields  = source.fieldNames.intersect(target.fieldNames)

    val changedFields = commonFields.filter { name =>
      source.fields.get(name).map(_.typeName) != target.fields.get(name).map(_.typeName)
    }

    StructuralDiff(
      addedFields = addedFields,
      removedFields = removedFields,
      changedFields = changedFields,
      source = source,
      target = target
    )
  }

  case class StructuralDiff(
    addedFields: Set[String],
    removedFields: Set[String],
    changedFields: Set[String],
    source: TypeStructure,
    target: TypeStructure
  ) {
    def requiresMigration: Boolean =
      addedFields.nonEmpty || removedFields.nonEmpty || changedFields.nonEmpty

    def summary: String = {
      val parts = List(
        if (addedFields.nonEmpty) Some(s"Added: ${addedFields.mkString(", ")}") else None,
        if (removedFields.nonEmpty) Some(s"Removed: ${removedFields.mkString(", ")}") else None,
        if (changedFields.nonEmpty) Some(s"Changed: ${changedFields.mkString(", ")}") else None
      ).flatten

      if (parts.isEmpty) "No changes detected"
      else parts.mkString("; ")
    }
  }
}
