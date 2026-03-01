package zio.blocks.schema.migration.macros

import scala.quoted.*
import zio.blocks.schema.migration.{Migration, MigrationBuilder, MigrationState, MigrationAction, SchemaExpr}
import zio.blocks.schema.DynamicOptic

object MigrationMacros {

  // =================================================================================
  // 1. OPERATION IMPLEMENTATIONS
  // =================================================================================

  def renameFieldImpl[A: Type, B: Type, S <: MigrationState.State: Type, T1: Type, T2: Type](
    builder: Expr[MigrationBuilder[A, B, S]],
    from: Expr[A => T1],
    to: Expr[B => T2]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    val fromName   = extractFieldName(from)
    val toName     = extractFieldName(to)
    val toNameExpr = Expr(toName)

    (ConstantType(StringConstant(fromName)).asType, ConstantType(StringConstant(toName)).asType) match {
      case ('[f], '[t]) =>
        val fromOptic = deriveOptic[A, T1](from, fromName) // [FIXED] explicit types
        '{
          $builder.withState[MigrationState.RenameField[f & String, t & String, S]](
            MigrationAction.Rename($fromOptic, $toNameExpr)
          )
        }
    }
  }

  def addFieldImpl[A: Type, B: Type, S <: MigrationState.State: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, S]],
    target: Expr[B => T],
    default: Expr[SchemaExpr[?]]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    val fieldName = extractFieldName(target)
    val fieldType = cleanType(TypeRepr.of[T])

    (ConstantType(StringConstant(fieldName)).asType, ConstantType(StringConstant(fieldType)).asType) match {
      case ('[n], '[tpe]) =>
        val targetOptic = deriveOptic[B, T](target, fieldName) // [FIXED] explicit types
        '{
          $builder.withState[MigrationState.AddField[n & String, tpe & String, S]](
            MigrationAction.AddField($targetOptic, $default)
          )
        }
    }
  }

  def dropFieldImpl[A: Type, B: Type, S <: MigrationState.State: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, S]],
    source: Expr[A => T],
    default: Expr[SchemaExpr[?]]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    val fieldName = extractFieldName(source)

    ConstantType(StringConstant(fieldName)).asType match {
      case '[n] =>
        val sourceOptic = deriveOptic[A, T](source, fieldName) // [FIXED] explicit types
        '{
          $builder.withState[MigrationState.DropField[n & String, S]](
            MigrationAction.DropField($sourceOptic, $default)
          )
        }
    }
  }

  def transformFieldImpl[A: Type, B: Type, S <: MigrationState.State: Type, T1: Type, T2: Type](
    builder: Expr[MigrationBuilder[A, B, S]],
    from: Expr[A => T1],
    transform: Expr[SchemaExpr[?]]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    val fieldName = extractFieldName(from)
    val fromT     = cleanType(TypeRepr.of[T1])
    val toT       = cleanType(TypeRepr.of[T2])

    (
      ConstantType(StringConstant(fieldName)).asType,
      ConstantType(StringConstant(fromT)).asType,
      ConstantType(StringConstant(toT)).asType
    ) match {
      case ('[n], '[ft], '[tt]) =>
        val optic = deriveOptic[A, T1](from, fieldName) // [FIXED] explicit types
        '{
          $builder.withState[MigrationState.ChangeType[n & String, ft & String, tt & String, S]](
            MigrationAction.TransformValue($optic, $transform)
          )
        }
    }
  }

  def mandateFieldImpl[A: Type, B: Type, S <: MigrationState.State: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, S]],
    source: Expr[A => Option[T]],
    target: Expr[B => T],
    default: Expr[SchemaExpr[?]]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    val sourceName = extractFieldName(source)
    val targetName = extractFieldName(target)

    if (sourceName != targetName)
      report.errorAndAbort(s"mandateField requires same field name ($sourceName vs $targetName).")

    ConstantType(StringConstant(sourceName)).asType match {
      case '[n] =>
        val optic = deriveOptic[A, Option[T]](source, sourceName) // [FIXED] explicit types
        '{
          $builder.withState[MigrationState.MandateField[n & String, T, S]](
            MigrationAction.Mandate($optic, $default)
          )
        }
    }
  }

  def optionalizeFieldImpl[A: Type, B: Type, S <: MigrationState.State: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B, S]],
    source: Expr[A => T],
    target: Expr[B => Option[T]]
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*
    val sourceName = extractFieldName(source)
    val targetName = extractFieldName(target)

    if (sourceName != targetName)
      report.errorAndAbort(s"optionalizeField requires same field name ($sourceName vs $targetName).")

    ConstantType(StringConstant(sourceName)).asType match {
      case '[n] =>
        val optic = deriveOptic[A, T](source, sourceName) // [FIXED] explicit types
        '{
          $builder.withState[MigrationState.OptionalizeField[n & String, T, S]](
            MigrationAction.Optionalize($optic)
          )
        }
    }
  }

  // =================================================================================
  // 2. VALIDATION ENGINE
  // =================================================================================

  def verifyMigration[A: Type, B: Type, S <: MigrationState.State: Type](
    builder: Expr[MigrationBuilder[A, B, S]]
  )(using q: Quotes): Expr[Migration[A, B]] = {
    import q.reflect.*
    val sourceSchema     = extractSchema(TypeRepr.of[A])
    val targetSchema     = extractSchema(TypeRepr.of[B])
    val calculatedSchema = simulateMigration[S](sourceSchema)

    if (calculatedSchema != targetSchema) {
      val missingKeys    = targetSchema.keySet -- calculatedSchema.keySet
      val extraKeys      = calculatedSchema.keySet -- targetSchema.keySet
      val typeMismatches = targetSchema.collect {
        case (name, expectedType) if calculatedSchema.get(name).exists(_ != expectedType) =>
          name -> (calculatedSchema(name), expectedType)
      }
      if (missingKeys.nonEmpty || extraKeys.nonEmpty || typeMismatches.nonEmpty) {
        val mismatchMsg = typeMismatches.map { case (name, (got, expected)) =>
          s"  - Field '$name': Expected '$expected', but got '$got'."
        }.mkString("\n")
        report.errorAndAbort(
          s"""
             | STATIC MIGRATION VALIDATION FAILED!
             |Errors:
             |${if (missingKeys.nonEmpty) s"  - Missing Fields: ${missingKeys.mkString(", ")}" else ""}
             |${if (extraKeys.nonEmpty) s"  - Unexpected Fields: ${extraKeys.mkString(", ")}" else ""}
             |${if (typeMismatches.nonEmpty) s"  - TYPE MISMATCHES:\n$mismatchMsg" else ""}
             |""".stripMargin
        )
      }
    }
    '{ $builder.buildPartial }.asExprOf[Migration[A, B]]
  }

  private def simulateMigration[S: Type](currentSchema: Map[String, String])(using q: Quotes): Map[String, String] = {
    import q.reflect.*
    Type.of[S] match {
      case '[MigrationState.Empty]                       => currentSchema
      case '[MigrationState.RenameField[from, to, prev]] =>
        val schemaBefore = simulateMigration[prev](currentSchema)
        val fromName     = extractStringFromType[from]; val toName = extractStringFromType[to]
        if (!schemaBefore.contains(fromName)) report.errorAndAbort(s"Invalid Rename: '$fromName' not found.")
        val typeSig = schemaBefore(fromName)
        (schemaBefore - fromName) + (toName -> typeSig)
      case '[MigrationState.DropField[from, prev]] =>
        val schemaBefore = simulateMigration[prev](currentSchema)
        val fromName     = extractStringFromType[from]
        if (!schemaBefore.contains(fromName)) report.errorAndAbort(s"Invalid Drop: '$fromName' not found.")
        schemaBefore - fromName
      case '[MigrationState.AddField[name, tpe, prev]] =>
        val schemaBefore = simulateMigration[prev](currentSchema)
        val fieldName    = extractStringFromType[name]; val typeSig = extractStringFromType[tpe]
        if (schemaBefore.contains(fieldName)) report.errorAndAbort(s"Invalid Add: '$fieldName' exists.")
        schemaBefore + (fieldName -> typeSig)
      case '[MigrationState.ChangeType[name, _, toT, prev]] =>
        val schemaBefore = simulateMigration[prev](currentSchema)
        val fieldName    = extractStringFromType[name]; val newType = extractStringFromType[toT]
        if (!schemaBefore.contains(fieldName)) report.errorAndAbort(s" Invalid ChangeType: '$fieldName' not found.")
        schemaBefore + (fieldName -> newType)
      case '[MigrationState.MandateField[name, _, prev]] =>
        val schemaBefore = simulateMigration[prev](currentSchema); val fieldName = extractStringFromType[name]
        if (!schemaBefore.contains(fieldName)) report.errorAndAbort(s"Invalid Mandate: '$fieldName' not found.")
        schemaBefore
      case '[MigrationState.OptionalizeField[name, _, prev]] =>
        val schemaBefore = simulateMigration[prev](currentSchema); val fieldName = extractStringFromType[name]
        if (!schemaBefore.contains(fieldName)) report.errorAndAbort(s"Invalid Optionalize: '$fieldName' not found.")
        schemaBefore
      case '[MigrationState.TransformCase[_, _, prev]] => simulateMigration[prev](currentSchema)
      case _                                           => currentSchema
    }
  }

  // =================================================================================
  // 3. UTILITIES & CUSTOM DERIVATION
  // =================================================================================

  // [CRITICAL FIX] Signature updated to require Function type [S => T]
  // This solves the "Found Expr[T], Required Expr[Nothing => Any]" error.
  private def deriveOptic[S: Type, T: Type](expr: Expr[S => T], fieldName: String)(using
    q: Quotes
  ): Expr[DynamicOptic] = {
    import q.reflect.*

    // Check if the expression contains 'asInstanceOf'
    val isCast = expr.asTerm match {
      case Inlined(_, _, body) =>
        body match {
          case TypeApply(Select(_, "asInstanceOf"), _)                => true
          case Block(List(), TypeApply(Select(_, "asInstanceOf"), _)) => true
          case _                                                      => false
        }
      case _ => false
    }

    if (isCast) {
      // Manual construction: DynamicOptic(Vector(Node.Field(fieldName)))
      '{ DynamicOptic(Vector(DynamicOptic.Node.Field(${ Expr(fieldName) }))) }
    } else {
      // Fallback to AccessorMacros for standard fields
      '{ AccessorMacros.derive($expr).optic }
    }
  }

  private def cleanType(using q: Quotes)(tpe: q.reflect.TypeRepr): String = {
    import q.reflect.*
    tpe match {
      case ByNameType(inner)     => cleanType(inner)
      case MethodType(_, _, res) => cleanType(res)
      case other                 =>
        val s = other.dealias.widen.show
        s match {
          case "java.lang.String" | "scala.Predef.String" => "String"
          case "scala.Int"                                => "Int"
          case "scala.Boolean"                            => "Boolean"
          case "scala.Long"                               => "Long"
          case "scala.Double"                             => "Double"
          case _                                          => s
        }
    }
  }

  private def extractSchema(using q: Quotes)(tpe: q.reflect.TypeRepr): Map[String, String] = {
    import q.reflect.*
    tpe.dealias match {
      case Refinement(parent, name, info) => extractSchema(parent) + (name -> cleanType(info))
      case _                              =>
        val symbol = tpe.classSymbol
        symbol match {
          case Some(cls) if cls.flags.is(Flags.Case) =>
            cls.caseFields.map(field => field.name -> cleanType(tpe.memberType(field))).toMap
          case _ => Map.empty
        }
    }
  }

  private def extractStringFromType[T: Type](using q: Quotes): String = {
    import q.reflect.*
    def loop(t: TypeRepr): Option[String] = t.dealias match {
      case ConstantType(StringConstant(s)) => Some(s)
      case AndType(left, right)            => loop(left).orElse(loop(right))
      case _                               => None
    }
    loop(TypeRepr.of[T]).getOrElse {
      val s = TypeRepr.of[T].show
      if (s.contains("&")) s.split("&").head.trim.replace("\"", "") else s
    }
  }

  private def extractFieldName(using q: Quotes)(expr: Expr[Any]): String = {
    import q.reflect.*
    def dive(term: Term): String = term match {
      case Inlined(_, _, body)                                                    => dive(body)
      case Block(List(), expr)                                                    => dive(expr)
      case Lambda(_, body)                                                        => dive(body)
      case TypeApply(Select(_, "asInstanceOf"), List(tpe))                        => cleanType(tpe.tpe)
      case Apply(Select(_, "selectDynamic"), List(Literal(StringConstant(name)))) => name
      case Select(_, name)                                                        => name
      case _                                                                      => "unknown"
    }
    val result = dive(expr.asTerm)
    if (result == "unknown") {
      val s = expr.show
      if (s.contains("asInstanceOf[")) s.split("asInstanceOf\\[").last.split("\\]").head.trim
      else if (s.contains("selectDynamic")) s.split("selectDynamic").last.split("\"").lift(1).getOrElse("unknown")
      else if (s.contains(".")) s.split("\\.").last.trim
      else "unknown"
    } else result
  }
}
