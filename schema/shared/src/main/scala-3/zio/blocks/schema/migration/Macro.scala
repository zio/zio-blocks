package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.CommonMacroOps._

import scala.annotation.tailrec
import scala.quoted._

object Macro {
  inline def toPath[A, B](f: A => B): DynamicOptic = ${ toPathImpl[A, B]('f) }

  private def toPathImpl[A: Type, B: Type](f: Expr[A => B])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._

    @tailrec
    def toPathBody(term: Term): Term = term match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Lambda(_, body)                                 => body
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _                                               => fail(s"Expected a lambda expression, got '${term.show}'")
    }

    def extractPath(term: Term): List[String] = {
      @tailrec
      def loop(t: Term, acc: List[String]): List[String] = t match {
        case Select(qual, name) => loop(qual, name :: acc)
        case Ident("_") => acc
        case _ => fail(s"Unsupported path: ${t.show}")
      }
      loop(term, Nil).reverse
    }

    def pathToOptic(path: List[String]): Expr[DynamicOptic] = {
      path.foldLeft('{DynamicOptic.root}) { (optic, field) =>
        '{$optic.field(${Expr(field)})}
      }
    }

    val pathBody = toPathBody(f.asTerm)
    val path = extractPath(pathBody)
    pathToOptic(path)
  }

  def validateMigration[A, B](builder: MigrationBuilder[A, B]): Either[MigrationError, Migration[A, B]] = {
    // For now, we'll do basic validation - check that the builder has valid schemas
    // More sophisticated validation would check that the paths in actions exist in the schemas
    try {
      Right(Migration(
        DynamicMigration(builder.actions),
        builder.sourceSchema,
        builder.targetSchema
      ))
    } catch {
      case e: Exception => Left(MigrationError.IncompatibleSchemas(e.getMessage))
    }
  }
}
