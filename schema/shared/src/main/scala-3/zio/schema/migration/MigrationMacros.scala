package zio.schema.migration

import scala.quoted._
import zio.blocks.schema.{DynamicOptic, SchemaExpr}

object MigrationMacros {

  def addFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => Any],
    default: Expr[SchemaExpr[A, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {

    val opticVal = extractPath(target)
    '{ $builder.withAction(MigrationAction.AddField($opticVal, $default)) }
  }

  def dropFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Any],
    defaultForReverse: Expr[SchemaExpr[B, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val opticVal = extractPath(source)
    '{ $builder.withAction(MigrationAction.DropField($opticVal, $defaultForReverse)) }
  }

  def renameFieldImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val fromOptic = extractPath(from)
    val toName    = extractLeafName(to)
    '{ $builder.withAction(MigrationAction.Rename($fromOptic, $toName)) }
  }

  def transformValueImpl[A: Type, B: Type, S: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => S],
    expr: Expr[SchemaExpr[S, T]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.TransformValue($at, $expr.asInstanceOf[SchemaExpr[Any, Any]])) }
  }

  def mandateImpl[A: Type, B: Type, S: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => Option[S]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{
      $builder.withAction(
        MigrationAction.Mandate($at, zio.blocks.schema.SchemaExpr.Literal((), zio.blocks.schema.Schema.unit))
      )
    }
  }

  def optionalizeImpl[A: Type, B: Type, S: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => S]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.Optionalize($at)) }
  }

  def transformElementsImpl[A: Type, B: Type, S: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => Seq[S]],
    migration: Expr[Migration[S, T]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.TransformElements($at, $migration.dynamicMigration)) }
  }

  def transformKeysImpl[A: Type, B: Type, K: Type, V: Type, K2: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => Map[K, V]],
    migration: Expr[Migration[K, K2]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.TransformKeys($at, $migration.dynamicMigration)) }
  }

  def transformValuesImpl[A: Type, B: Type, K: Type, V: Type, V2: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => Map[K, V]],
    migration: Expr[Migration[V, V2]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.TransformValues($at, $migration.dynamicMigration)) }
  }

  def transformCaseImpl[A: Type, B: Type, S: Type, T: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => S],
    migration: Expr[Migration[S, T]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.TransformCase($at, $migration.dynamicMigration)) }
  }

  def renameCaseImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => Any],
    from: Expr[String],
    to: Expr[String]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.RenameCase($at, $from, $to)) }
  }

  def changeTypeImpl[A: Type, B: Type, S: Type](
    builder: Expr[MigrationBuilder[A, B]],
    path: Expr[A => S],
    converter: Expr[SchemaExpr[S, ?]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val at = extractPath(path)
    '{ $builder.withAction(MigrationAction.ChangeType($at, $converter.asInstanceOf[SchemaExpr[Any, Any]])) }
  }

  def joinImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[B => Any],
    combiner: Expr[SchemaExpr[?, ?]],
    sources: Expr[Seq[A => Any]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val atOptic      = extractPath(at)
    val sourceOptics = extractPaths(sources)
    '{
      $builder.withAction(MigrationAction.Join($atOptic, $sourceOptics, $combiner.asInstanceOf[SchemaExpr[Any, Any]]))
    }
  }

  def splitImpl[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Any],
    splitter: Expr[SchemaExpr[?, ?]],
    targets: Expr[Seq[B => Any]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val atOptic      = extractPath(at)
    val targetOptics = extractPaths(targets)
    '{
      $builder.withAction(MigrationAction.Split($atOptic, $targetOptics, $splitter.asInstanceOf[SchemaExpr[Any, Any]]))
    }
  }

  private def extractPaths[T: Type, R: Type](funcs: Expr[Seq[T => R]])(using Quotes): Expr[Vector[DynamicOptic]] = {
    import quotes.reflect._
    funcs match {
      case Varargs(args) =>
        val optics = args.map(extractPath(_))
        Expr.ofSeq(optics).asExprOf[Vector[DynamicOptic]]
      case _ => report.errorAndAbort("Expected varargs of selector functions")
    }
  }

  // Helper to extract DynamicOptic from lambda
  private def extractPath[T: Type, R: Type](func: Expr[T => R])(using Quotes): Expr[DynamicOptic] = {
    import quotes.reflect._
    import zio.blocks.schema.DynamicOptic.Node

    // Expecting lambda: (x: T) => x.field.subfield...
    // func.asTerm

    // Simple recursive extractor
    def loop(term: Term, acc: List[Expr[Node]]): List[Expr[Node]] =
      term match {
        case Inlined(_, _, body) => loop(body, acc)
        case Block(_, expr)      => loop(expr, acc)
        case Typed(expr, _)      => loop(expr, acc)

        // Handle "each" property access: x.items.each
        case Select(qual, "each") =>
          loop(qual, '{ Node.Elements } :: acc)

        // Select(qual, name) -> x.name
        case Select(qual, name) =>
          val node = '{ Node.Field(${ Expr(name) }) }
          loop(qual, node :: acc)

        // General Apply handling
        case Apply(rawFun, args) =>
          def strip(t: Term): Term = t match {
            case Inlined(_, _, body) => strip(body)
            case Block(_, expr)      => strip(expr)
            case Typed(expr, _)      => strip(expr)
            case other               => other
          }
          val fun = strip(rawFun)

          val (name, methodQual) = fun match {
            case TypeApply(inner, _) =>
              val core = strip(inner)
              core match {
                case Select(q, n) => (n, Some(q))
                case Ident(n)     => (n, None)
                case _            => ("", None)
              }
            case Select(q, n) => (n, Some(q))
            case Ident(n)     => (n, None)
            case _            => ("", None)
          }

          if (name == "each") {
            args match {
              case List(qual) => loop(qual, '{ Node.Elements } :: acc)
              case Nil        =>
                methodQual match {
                  case Some(q) => loop(q, '{ Node.Elements } :: acc)
                  case None    => report.errorAndAbort("Unsupported 'each' usage")
                }
              case _ => report.errorAndAbort("'each' expects 0 or 1 arguments")
            }
          } else if (name == "when") {
            fun match {
              case TypeApply(_, List(tpe)) =>
                val typeName = Expr(tpe.tpe.typeSymbol.name)
                methodQual match {
                  case Some(q) => loop(q, '{ Node.Case($typeName) } :: acc)
                  case None    =>
                    // Extension method fallback? when[T](qual)
                    args match {
                      case List(q) => loop(q, '{ Node.Case($typeName) } :: acc)
                      case _       => report.errorAndAbort("Unsupported 'when' usage")
                    }
                }
              case _ => report.errorAndAbort("'when' requires type param")
            }
          } else {
            // Attempt to unwrap implicit conversions / wrappers
            args match {
              case List(arg) => loop(arg, acc)
              case _         => report.errorAndAbort(s"Unsupported method call '$name' (raw: ${rawFun.show})")
            }
          }

        case Ident(_) => acc // Base of lambda parameter

        // Case: (x: A) => x.field
        // The lambda structure:
        // Lambda(params, body)
        case _ =>
          report.errorAndAbort(s"Unsupported selector path: ${term.show}")
      }

    func.asTerm match {
      case Inlined(_, _, Lambda(_, body)) =>
        // body is the expression.
        val nodes      = loop(body, Nil)
        val vectorExpr = Expr.ofSeq(nodes)
        '{ DynamicOptic(Vector($vectorExpr: _*)) }
      case Lambda(_, body) =>
        val nodes      = loop(body, Nil)
        val vectorExpr = Expr.ofSeq(nodes)
        '{ DynamicOptic(Vector($vectorExpr: _*)) }
      case _ =>
        report.errorAndAbort("Selector must be a lambda, e.g., _.field")
    }
  }

  private def extractLeafName[T: Type, R: Type](func: Expr[T => R])(using Quotes): Expr[String] = {
    import quotes.reflect._
    func.asTerm match {
      case Inlined(_, _, Lambda(_, body)) => getLeaf(body)
      case Lambda(_, body)                => getLeaf(body)
      case _                              => report.errorAndAbort("Selector must be a lambda")
    }
  }

  private def getLeaf(using Quotes)(term: quotes.reflect.Term): Expr[String] = {
    import quotes.reflect._
    term match {
      case Inlined(_, _, body) => getLeaf(body)
      case Select(_, name)     => Expr(name)
      case _                   => report.errorAndAbort("Selector must end in a field selection")
    }
  }
}
