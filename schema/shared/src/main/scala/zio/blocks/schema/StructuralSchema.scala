package zio.blocks.schema

import scala.quoted._
import zio.Chunk

/**
 * A type class to provide a Schema for a structural type
 */
trait StructuralSchema[T] {
  def schema: Schema[T]
}

object StructuralSchema {
  implicit inline def structural[T]: StructuralSchema[T] =
    ${ StructuralSchemaMacro.materialize[T] }
}

object StructuralSchemaMacro {
  def materialize[T: Type](using Quotes): Expr[StructuralSchema[T]] = {
    import quotes.reflect._

    val tpe = TypeRepr.of[T]
    val sym = tpe.typeSymbol

    if (sym.flags.is(Flags.Sealed) && sym.flags.is(Flags.Abstract)) {
      // This is a sealed trait or abstract class, likely an enum
      val cases = sym.children.filter(_.isClassDef).map(_.typeRef)

      val caseSchemas = cases.map { caseTpe =>
        caseTpe.asType match {
          case '[c] =>
            val caseSchema = Expr.summon[Schema[c]].getOrElse {
              report.errorAndAbort(s"Cannot find Schema for enum case ${caseTpe.show}")
            }
            '{
              Schema.Case[T, c](
                name = ${Expr(caseTpe.typeSymbol.name)},
                schema = ${caseSchema},
                get = (t: T) => t.asInstanceOf[c],
                set = (c: c) => c.asInstanceOf[T]
              )
            }
        }
      }
      '{
        new StructuralSchema[T] {
          def schema: Schema[T] = Schema.Enum(zio.Chunk.fromIterable(${Expr.ofSeq(caseSchemas)})).asInstanceOf[Schema[T]]
        }
      }
    } else {
      // Assume it's a record/struct type
      val fields = sym.memberFields.collect { case s: Symbol if s.isValDef => s }

      val schemaFields = fields.map { fieldSymbol =>
        val fieldName = fieldSymbol.name
        val fieldType = tpe.memberType(fieldSymbol)

        fieldType.asType match {
          case '[f] =>
            val fieldSchema = Expr.summon[Schema[f]].getOrElse {
              report.errorAndAbort(s"Cannot find Schema for field '$fieldName' of type ${fieldType.show}")
            }
            val term = Ref(fieldSymbol).asExprOf[Any]
            '{
              Schema.Field[T, f](
                name = ${Expr(fieldName)},
                schema = ${fieldSchema},
                get = (obj: T) => ${term.asExprOf[f]},
                set = (obj: T, value: f) => obj
              )
            }
        }
      }

      val recordSchema =
        '{Schema.Record(zio.Chunk.fromIterable(${Expr.ofSeq(schemaFields)})).asInstanceOf[Schema[T]]}

      '{
        new StructuralSchema[T] {
          def schema: Schema[T] = ${recordSchema}
        }
      }
    }
  }
}