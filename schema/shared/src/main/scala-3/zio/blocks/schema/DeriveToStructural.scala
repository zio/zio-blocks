package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.binding.StructuralValue

object DeriveToStructural {
  def derivedImpl[A: Type](using Quotes): Expr[ToStructural[A]] = {
    import quotes.reflect._

    val tpe    = TypeRepr.of[A]
    val symbol = tpe.typeSymbol

    if (!symbol.isClassDef || !symbol.flags.is(Flags.Case)) {
      report.errorAndAbort(s"ToStructural derivation only supports case classes, found: ${tpe.show}")
    }

    // Extract fields
    val fields = symbol.caseFields.map { field =>
      val name     = field.name
      val typeRepr = tpe.memberType(field).dealias
      (name, typeRepr)
    }

    // Construct Structural Refinement Type: Selectable { val f1: T1; ... }
    // We use Selectable as the base because our runtime backing (StructuralValue) extends Selectable
    val base           = TypeRepr.of[Selectable]
    val structuralRepr = fields.foldLeft(base) { case (parent, (name, fieldTpe)) =>
      Refinement(parent, name, fieldTpe)
    }

    structuralRepr.asType match {
      case '[st] =>
        '{
          new ToStructural[A] {
            type StructuralType = st

            def toStructural(value: A): StructuralType = {
              val props = ${
                val tuples = fields.map { case (name, _) =>
                  val nameExpr   = Expr(name)
                  val fieldSym   = symbol.fieldMember(name)
                  val fieldValue = Select('value.asTerm, fieldSym).asExpr
                  '{ ($nameExpr, $fieldValue) }
                }
                '{ Map[String, Any](${ Varargs(tuples) }*) }
              }
              new StructuralValue(props).asInstanceOf[StructuralType]
            }

            def structuralSchema(implicit schema: Schema[A]): Schema[StructuralType] =
              Schema.derived[st]
          }
        }
    }
  }
}
