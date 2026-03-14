/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Compile-time validation for [[MigrationBuilder.build]]. Reflects on Type[A] and Type[B],
 * extracts structural or case class fields, symbolically executes the [[MigrationAction]] vector
 * from the builder's AST, and verifies that the migrated shape aligns with the target schema.
 */
object MigrationMacros {

  def buildImpl[A: Type, B: Type](builder: Expr[MigrationBuilder[A, B]])(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect.*

    // 1. Extract structural or case class fields
    def extractShape(tpe: TypeRepr): Set[String] = tpe.dealias match {
      case Refinement(parent, name, _) =>
        extractShape(parent) + name
      case AndType(left, right) =>
        extractShape(left) ++ extractShape(right)
      case t if t.classSymbol.isDefined && t.classSymbol.get.flags.is(Flags.Case) =>
        t.classSymbol.get.caseFields.map(_.name).toSet
      case _ =>
        Set.empty
    }

    val sourceFields = extractShape(TypeRepr.of[A])
    val targetFields = extractShape(TypeRepr.of[B])

    // Aggressively extract the first string literal or field name (from Select) in the AST subtree
    def extractStringLiteral(tree: Tree): Option[String] = tree match {
      case Literal(constant) if constant.value.isInstanceOf[String] =>
        Some(constant.value.asInstanceOf[String])
      case Apply(_, args) => args.flatMap(extractStringLiteral).headOption
      case Inlined(_, _, body) => extractStringLiteral(body)
      case Block(stats, expr) =>
        stats.flatMap {
          case d: DefDef => d.rhs.toList.flatMap(extractStringLiteral)
          case _         => Nil
        }.headOption.orElse(extractStringLiteral(expr))
      case Typed(expr, _) => extractStringLiteral(expr)
      case s @ Select(qual, _) => extractStringLiteral(qual).orElse(Some(s.name))
      case _ => None
    }

    sealed trait Op
    case class Add(field: String) extends Op
    case class Drop(field: String) extends Op
    case class Ren(from: String, to: String) extends Op

    var operations = List.empty[Op]

    // Resolve Idents to their definition RHS (inlined locals from renameField/addField).
    // Uses this.traverseTree (not super) for body/expr in Block/Inlined so that Ident nodes
    // are visited directly and can follow their symbol to a ValDef definition.
    val valDefs = scala.collection.mutable.Map[Symbol, Tree]()
    def registerValDef(v: ValDef): Unit = v.rhs.foreach(rhs => { valDefs(v.symbol) = rhs })
    object collectValDefs extends TreeTraverser {
      override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match {
        case v: ValDef =>
          registerValDef(v)
          v.rhs.foreach(traverseTree(_)(owner))
          super.traverseTree(tree)(owner)
        case Block(stats, expr) =>
          stats.foreach {
            case v: ValDef => registerValDef(v); v.rhs.foreach(traverseTree(_)(owner))
            case s        => traverseTree(s)(owner)
          }
          traverseTree(expr)(owner)
        case Inlined(_, bindings, body) =>
          bindings.foreach {
            case v: ValDef => registerValDef(v); v.rhs.foreach(traverseTree(_)(owner))
            case s        => traverseTree(s)(owner)
          }
          traverseTree(body)(owner)
        case i: Ident =>
          try {
            i.symbol.tree match {
              case v: ValDef => registerValDef(v); v.rhs.foreach(traverseTree(_)(owner))
              case _         =>
            }
          } catch { case _: Throwable => }
          super.traverseTree(tree)(owner)
        case _ => super.traverseTree(tree)(owner)
      }
    }
    collectValDefs.traverseTree(builder.asTerm)(Symbol.spliceOwner)

    // Resolve Idents through the valDefs map before falling back to raw literal extraction.
    // The Apply case recurses with extractField (not extractStringLiteral) so that Ident arguments
    // to wrapper calls like DynamicOptic.terminalName(...) are also resolved.
    def extractField(tree: Tree): Option[String] = tree match {
      case i: Ident =>
        valDefs.get(i.symbol).flatMap(extractField).orElse(extractStringLiteral(tree))
      case Apply(_, args) =>
        args.flatMap(extractField).headOption
      case Inlined(_, _, body) =>
        extractField(body)
      case Block(stats, expr) =>
        stats.flatMap {
          case d: DefDef => d.rhs.toList.flatMap(extractField)
          case _         => Nil
        }.headOption.orElse(extractField(expr))
      case Typed(expr, _) =>
        extractField(expr)
      case _ =>
        extractStringLiteral(tree)
    }

    val traverser = new TreeTraverser {
      override def traverseTree(tree: Tree)(owner: Symbol): Unit = tree match {
        case applyTree @ Apply(fun, args) =>
          val inner   = fun match { case TypeApply(i, _) => i case _ => fun }
          val symName = inner match { case s: Select => s.name case _ => inner.symbol.name }
          val funStr  = fun.show
          val tpeName = try applyTree.tpe.typeSymbol.name catch { case _: Throwable => "" }

          val isAdd    = symName == "AddField" || symName == "addField" || funStr.contains("AddField") || tpeName == "AddField"
          val isDrop   = symName == "DropField" || symName == "dropField" || funStr.contains("DropField") || tpeName == "DropField"
          val isRename = symName == "Rename" || symName == "renameField" || funStr.contains("Rename") || tpeName == "Rename"

          if (isAdd && args.nonEmpty) {
            extractField(args.head).foreach(f => operations = operations :+ Add(f))
          } else if (isDrop && args.nonEmpty) {
            extractField(args.head).foreach(f => operations = operations :+ Drop(f))
          } else if (isRename && args.length >= 2) {
            val fromName = extractField(args(0))
            val toName   = extractField(args(1))
            (fromName, toName) match {
              case (Some(f), Some(t)) if f != t => operations = operations :+ Ren(f, t)
              case _                             =>
            }
          } else if (args.length >= 2 && (funStr.contains("ename") || funStr.contains("Rename")) && !isAdd && !isDrop) {
            val fromName = extractField(args(0))
            val toName   = extractField(args(1))
            (fromName, toName) match {
              case (Some(f), Some(t)) if f != t => operations = operations :+ Ren(f, t)
              case _                             =>
            }
          }
          super.traverseTree(fun)(owner)
          args.foreach(a => traverseTree(a)(owner))
        case Block(stats, expr) =>
          stats.foreach(s => traverseTree(s)(owner))
          traverseTree(expr)(owner)
        case Inlined(_, _, body) =>
          traverseTree(body)(owner)
        case i: Ident =>
          valDefs.get(i.symbol).foreach(rhs => traverseTree(rhs)(owner))
          try {
            i.symbol.tree match {
              case v: ValDef => v.rhs.foreach(traverseTree(_)(owner))
              case _        =>
            }
          } catch { case _: Throwable => }
          super.traverseTree(tree)(owner)
        case _ => super.traverseTree(tree)(owner)
      }
    }

    val term = builder.asTerm
    // If compiler bound the chain to a temp (e.g. .build on a val), traverse the definition's RHS
    term match {
      case i: Ident =>
        try {
          i.symbol.tree match {
            case v: ValDef => v.rhs.foreach(traverser.traverseTree(_)(Symbol.spliceOwner))
            case _         =>
          }
        } catch { case _: Throwable => }
      case _ =>
    }
    traverser.traverseTree(term)(Symbol.spliceOwner)

    // 4. Symbolic execution
    val finalShape = operations.foldLeft(sourceFields) { (shape, op) =>
      op match {
        case Add(f)       => shape + f
        case Drop(f)      => shape - f
        case Ren(f, t)    => (shape - f) + t
      }
    }

    // 5. Build-time constraint enforcement
    val missingFields = targetFields.diff(finalShape)
    if (missingFields.nonEmpty) {
      report.errorAndAbort(
        s"Compile-time validation failed. Target schema requires fields that are missing from the migration: ${missingFields.mkString(", ")}"
      )
    }

    val leftoverFields = finalShape.diff(targetFields)
    if (leftoverFields.nonEmpty) {
      report.errorAndAbort(
        s"Compile-time validation failed. Migration leaves fields unmapped that are not in the target schema: ${leftoverFields.mkString(", ")}. Use .dropField() to explicitly remove them."
      )
    }

    '{ $builder.buildPartial }
  }
}
