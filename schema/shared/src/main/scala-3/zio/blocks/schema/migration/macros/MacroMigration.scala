package zio.blocks.schema.migration.macros

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.migration.{DynamicMigration, Migration}
import scala.quoted._

object MacroMigration {

  /**
   * Attempts to derive a Zero-Overhead Migration[A, B] at compile-time.
   * If strictly compatible (e.g., subset of fields + renames), generates direct code.
   * Otherwise, falls back to the interpreter.
   */
  inline def derive[A, B](inline dynamic: DynamicMigration): Migration[A, B] =
    ${ deriveImpl[A, B]('dynamic) }

  def deriveImpl[A: Type, B: Type](dynamic: Expr[DynamicMigration])(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect._
    import quotes.reflect.Flags

    // 1. Extract the DynamicMigration structure
    sealed trait MacroOp
    case class Rename(oldName: String, newName: String) extends MacroOp
    case class Add(name: String, value: Expr[Any]) extends MacroOp

    def extractOps(expr: Expr[DynamicMigration]): List[MacroOp] = {
      expr match {
        // Correct pattern matching for string literals in Expr
        case '{ DynamicMigration.RenameField(${ Expr(oldName) }, ${ Expr(newName) }) } =>
          List(Rename(oldName, newName))

        // Match AddClassField with variable capture
        case '{ DynamicMigration.AddClassField($nameExpr, $dvExpr) } =>
          (nameExpr.value, dvExpr) match {
            // Match DynamicValue.Primitive(pv)
            case (Some(name), '{ DynamicValue.Primitive($pv) }) =>
              pv match {
                case '{ PrimitiveValue.Int(${ v }) }     => List(Add(name, v))
                case '{ PrimitiveValue.Long(${ v }) }    => List(Add(name, v))
                case '{ PrimitiveValue.String(${ v }) }  => List(Add(name, v))
                case '{ PrimitiveValue.Boolean(${ v }) } => List(Add(name, v))
                case '{ PrimitiveValue.Double(${ v }) }  => List(Add(name, v))
                case '{ PrimitiveValue.Float(${ v }) }   => List(Add(name, v))
                case _                                   => Nil
              }
            case _ =>
              Nil
          }

        case '{ DynamicMigration.Compose($left, $right) } =>
          extractOps(left) ++ extractOps(right)
        case _ =>
          Nil
      }
    }

    val ops = extractOps(dynamic)
    val renames = ops.collect { case r: Rename => r.oldName -> r.newName }.toMap
    val adds = ops.collect { case a: Add => a.name -> a.value }.toMap

    // 2. Inspect A and B
    val tpeA = TypeRepr.of[A]
    val tpeB = TypeRepr.of[B]

    val symbolA = tpeA.typeSymbol
    val symbolB = tpeB.typeSymbol

    val isCaseClass = symbolA.isClassDef && symbolB.isClassDef && symbolA.flags.is(Flags.Case) && symbolB.flags.is(Flags.Case)

    if (!isCaseClass) {
      report.warning("Macro migration only supports Case Classes. Falling back to interpreter.")
      '{ Migration.manual(${ dynamic }) }
    } else {

      val fieldsA = symbolA.caseFields
      val fieldsB = symbolB.caseFields

      // Map A's fields by name for quick lookup
      val fieldMapA = fieldsA.map(s => s.name -> s).toMap

      // Check types for compatibility
      val isValid = fieldsB.forall { fieldB =>
        val nameB = fieldB.name
        if (adds.contains(nameB)) true
        else {
          val sourceName = renames.find(_._2 == nameB).map(_._1).getOrElse(nameB)
          fieldMapA.contains(sourceName)
        }
      }

      if (!isValid) {
        report.warning(s"Cannot derive zero-overhead migration: missing fields in source Schema. Falling back to interpreter.")
        '{ Migration.manual(${ dynamic }) }
      } else {
        // Generate the optimized migrate function body

        // Define the method type for (A, Schema[A], Schema[B]) => Either[String, B]
        val methodType = MethodType(List("a", "sA", "sB"))(
          _ => List(TypeRepr.of[A], TypeRepr.of[Schema[A]], TypeRepr.of[Schema[B]]),
          _ => TypeRepr.of[Either[String, B]]
        )

        // Create the Lambda expression
        val lambda = Lambda(
          Symbol.spliceOwner,
          methodType,
          (_, params) => {
            val aValDef = params.head
            val aTerm = Ref(aValDef.symbol)

            val args = fieldsB.map { fieldB =>
              val nameB = fieldB.name

              if (adds.contains(nameB)) {
                adds(nameB).asTerm
              } else {
                val sourceName = renames.find(_._2 == nameB).map(_._1).getOrElse(nameB)
                val fieldA = fieldMapA(sourceName)

                // Direct field selection
                Select.unique(aTerm, fieldA.name)
              }
            }

            val newB = Apply(
              Select(New(TypeTree.of[B]), symbolB.primaryConstructor),
              args
            ).asExpr.asInstanceOf[Expr[B]]

            '{ Right(${ newB }) }.asTerm
          }
        )

        val migrateFn = lambda.asExprOf[(A, Schema[A], Schema[B]) => Either[String, B]]

        // Return OptimizedMigration using the constructed lambda
        '{
          Migration.OptimizedMigration[A, B](
            ${ dynamic },
            $migrateFn
          )
        }
      }
    }
  }
}
