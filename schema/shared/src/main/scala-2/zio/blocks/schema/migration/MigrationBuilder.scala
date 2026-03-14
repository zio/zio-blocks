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

import zio.blocks.schema.Schema
import scala.language.experimental.macros

final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) {

  def addField[T](target: B => T, default: DynamicSchemaExpr): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.addFieldImpl[A, B, T]

  def dropField[T](
    source: A => T,
    defaultForReverse: DynamicSchemaExpr = DynamicSchemaExpr.DefaultValue
  ): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropFieldImpl[A, B, T]

  def renameField[T](from: A => T, to: B => T): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameFieldImpl[A, B, T]

  def transformValue[T](at: A => T, transform: DynamicSchemaExpr): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformValueImpl[A, B, T]

  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  def build: Migration[A, B] = macro MigrationBuilderMacros.buildImpl[A, B]
}

object MigrationBuilderMacros {
  import scala.reflect.macros.blackbox

  def buildImpl[A, B](c: blackbox.Context)(implicit evA: c.WeakTypeTag[A], evB: c.WeakTypeTag[B]): c.Expr[Migration[A, B]] = {
    import c.universe._

    def extractShape(tpe: Type): Set[String] = {
      val members = tpe.decls.collect {
        case m: MethodSymbol if m.isVal || m.isGetter || m.isAbstract => m.name.decodedName.toString.trim
      }.toSet
      if (tpe.typeSymbol.isClass && tpe.typeSymbol.asClass.isCaseClass) {
        tpe.decls.collect {
          case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString.trim
        }.toSet
      } else members
    }

    val sourceFields = extractShape(c.weakTypeOf[A])
    val targetFields = extractShape(c.weakTypeOf[B])

    def extractFieldName(tree: Tree): Option[String] = tree match {
      case Apply(_, List(Literal(Constant(name: String)), _)) => Some(name)
      case Apply(_, args)                                    => args.flatMap(extractFieldName).headOption
      case Select(qual, _)                                   => extractFieldName(qual)
      case Block(_, expr)                                    => extractFieldName(expr)
      case Typed(expr, _)                                    => extractFieldName(expr)
      case _                                                 => None
    }

    sealed trait Op
    case class Add(field: String)              extends Op
    case class Drop(field: String)              extends Op
    case class Ren(from: String, to: String)   extends Op

    var operations = List.empty[Op]

    val traverser = new Traverser {
      override def traverse(tree: Tree): Unit = tree match {
        case Apply(fun, args) if fun.toString.contains("AddField") =>
          args.headOption.flatMap(extractFieldName).foreach(f => operations = operations :+ Add(f))
          super.traverse(tree)
        case Apply(fun, args) if fun.toString.contains("DropField") =>
          args.headOption.flatMap(extractFieldName).foreach(f => operations = operations :+ Drop(f))
          super.traverse(tree)
        case Apply(fun, args) if fun.toString.contains("Rename") && args.size >= 2 =>
          val fromName = extractFieldName(args.head)
          val toName = args(1) match {
            case Apply(_, List(Literal(Constant(name: String)))) => Some(name)
            case Literal(Constant(name: String))                 => Some(name)
            case _                                             => extractFieldName(args(1))
          }
          (fromName, toName) match {
            case (Some(f), Some(t)) => operations = operations :+ Ren(f, t)
            case _                  =>
          }
          super.traverse(tree)
        case _ => super.traverse(tree)
      }
    }

    traverser.traverse(c.prefix.tree)

    val finalShape = operations.foldLeft(sourceFields) { (shape, op) =>
      op match {
        case Add(f)    => shape + f
        case Drop(f)   => shape - f
        case Ren(f, t) => (shape - f) + t
      }
    }

    val missingFields = targetFields.diff(finalShape)
    if (missingFields.nonEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Compile-time validation failed. Target schema requires missing fields: ${missingFields.mkString(", ")}"
      )
    }

    val leftoverFields = finalShape.diff(targetFields)
    if (leftoverFields.nonEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Compile-time validation failed. Migration leaves fields unmapped: ${leftoverFields.mkString(", ")}. Use .dropField()."
      )
    }

    c.Expr[Migration[A, B]](q"${c.prefix}.buildPartial")
  }

  def addFieldImpl[A, B, T](c: blackbox.Context)(
    target: c.Expr[B => T],
    default: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacro.extractPathImpl(c)(target)
    c.Expr[MigrationBuilder[A, B]](
      q"new zio.blocks.schema.migration.MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ zio.blocks.schema.migration.MigrationAction.AddField($optic, $default))"
    )
  }

  def dropFieldImpl[A, B, T](c: blackbox.Context)(
    source: c.Expr[A => T],
    defaultForReverse: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacro.extractPathImpl(c)(source)
    c.Expr[MigrationBuilder[A, B]](
      q"new zio.blocks.schema.migration.MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ zio.blocks.schema.migration.MigrationAction.DropField($optic, $defaultForReverse))"
    )
  }

  def renameFieldImpl[A, B, T](c: blackbox.Context)(
    from: c.Expr[A => T],
    to: c.Expr[B => T]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val fromOptic = SelectorMacro.extractPathImpl(c)(from)
    val toOptic   = SelectorMacro.extractPathImpl(c)(to)
    c.Expr[MigrationBuilder[A, B]](
      q"new zio.blocks.schema.migration.MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ zio.blocks.schema.migration.MigrationAction.Rename($fromOptic, zio.blocks.schema.migration.DynamicOptic.terminalName($toOptic)))"
    )
  }

  def transformValueImpl[A, B, T](c: blackbox.Context)(
    at: c.Expr[A => T],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optic = SelectorMacro.extractPathImpl(c)(at)
    c.Expr[MigrationBuilder[A, B]](
      q"new zio.blocks.schema.migration.MigrationBuilder(${c.prefix}.sourceSchema, ${c.prefix}.targetSchema, ${c.prefix}.actions :+ zio.blocks.schema.migration.MigrationAction.TransformValue($optic, $transform))"
    )
  }
}
