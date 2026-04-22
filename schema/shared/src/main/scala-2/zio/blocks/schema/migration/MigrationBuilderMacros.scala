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

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import zio.blocks.schema.{CommonMacroOps, Schema, SchemaExpr}

private[migration] trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  def addField[F](target: B => F, default: SchemaExpr[B, F]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.addField[A, B, F]

  def dropField[F](source: A => F, defaultForReverse: SchemaExpr[A, F]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.dropField[A, B, F]

  def renameField(from: A => Any, to: B => Any): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.renameField[A, B]

  def transformField[G](from: A => Any, to: B => G, transform: SchemaExpr[A, G]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformField[A, B, G]

  def changeFieldType[F, G](source: A => F, target: B => G, converter: SchemaExpr[A, G]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.changeFieldType[A, B, F, G]

  def mandateField[G](source: A => Option[G], target: B => G, default: SchemaExpr[A, G]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.mandateField[A, B, G]

  def optionalizeField[G](source: A => G, target: B => Option[G]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.optionalizeField[A, B, G]

  def transformElements[G](at: A => Vector[G], transform: SchemaExpr[A, G]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformElements[A, B, G]

  def transformKeys[K, V](at: A => Map[K, V], transform: SchemaExpr[A, K]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformKeys[A, B, K, V]

  def transformValues[K, V](at: A => Map[K, V], transform: SchemaExpr[A, V]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.transformValues[A, B, K, V]

  def join[F](target: B => F, sources: Seq[A => Any], combiner: SchemaExpr[A, F]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.join[A, B, F]

  def split[F, G](source: A => F, targetPaths: Seq[B => Any], splitter: SchemaExpr[A, G]): MigrationBuilder[A, B] =
    macro MigrationBuilderMacros.split[A, B, F, G]

  def buildViaMacro: Migration[A, B] = macro MigrationBuilderMacros.buildImpl[A, B]

  def buildPartialViaMacro: Migration[A, B] = macro MigrationBuilderMacros.buildPartialImpl[A, B]
}

private[migration] object MigrationBuilderMacros {

  def addField[A: c.WeakTypeTag, B: c.WeakTypeTag, F: c.WeakTypeTag](
    c: whitebox.Context
  )(target: c.Expr[B => F], default: c.Expr[SchemaExpr[B, F]]): c.Tree = {
    import c.universe._
    val _ = c.weakTypeOf[A]

    val builder     = c.prefix.tree
    val targetOptic = optic[B, F](c)(target, c.Expr[Schema[B]](q"$builder.targetSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.addField($builder, $targetOptic, $default)"
  }

  def dropField[A: c.WeakTypeTag, B: c.WeakTypeTag, F: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => F], defaultForReverse: c.Expr[SchemaExpr[A, F]]): c.Tree = {
    import c.universe._
    val _ = c.weakTypeOf[B]

    val builder     = c.prefix.tree
    val sourceOptic = optic[A, F](c)(source, c.Expr[Schema[A]](q"$builder.sourceSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.dropField($builder, $sourceOptic, $defaultForReverse)"
  }

  def renameField[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: whitebox.Context
  )(from: c.Expr[A => Any], to: c.Expr[B => Any]): c.Tree = {
    import c.universe._

    val builder     = c.prefix.tree
    val sourceOptic = optic[A, Any](c)(from, c.Expr[Schema[A]](q"$builder.sourceSchema"))
    val targetOptic = optic[B, Any](c)(to, c.Expr[Schema[B]](q"$builder.targetSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.renameField($builder, $sourceOptic, $targetOptic)"
  }

  def transformField[A: c.WeakTypeTag, B: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(from: c.Expr[A => Any], to: c.Expr[B => G], transform: c.Expr[SchemaExpr[A, G]]): c.Tree = {
    import c.universe._

    val builder     = c.prefix.tree
    val sourceOptic = optic[A, Any](c)(from, c.Expr[Schema[A]](q"$builder.sourceSchema"))
    val targetOptic = optic[B, G](c)(to, c.Expr[Schema[B]](q"$builder.targetSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.transformField($builder, $sourceOptic, $targetOptic, $transform)"
  }

  def changeFieldType[A: c.WeakTypeTag, B: c.WeakTypeTag, F: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => F], target: c.Expr[B => G], converter: c.Expr[SchemaExpr[A, G]]): c.Tree = {
    import c.universe._

    val builder     = c.prefix.tree
    val sourceOptic = optic[A, F](c)(source, c.Expr[Schema[A]](q"$builder.sourceSchema"))
    val targetOptic = optic[B, G](c)(target, c.Expr[Schema[B]](q"$builder.targetSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.changeFieldType($builder, $sourceOptic, $targetOptic, $converter)"
  }

  def mandateField[A: c.WeakTypeTag, B: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => Option[G]], target: c.Expr[B => G], default: c.Expr[SchemaExpr[A, G]]): c.Tree = {
    import c.universe._

    val builder     = c.prefix.tree
    val sourceOptic = optic[A, Option[G]](c)(source, c.Expr[Schema[A]](q"$builder.sourceSchema"))
    val targetOptic = optic[B, G](c)(target, c.Expr[Schema[B]](q"$builder.targetSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.mandateField($builder, $sourceOptic, $targetOptic, $default)"
  }

  def optionalizeField[A: c.WeakTypeTag, B: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => G], target: c.Expr[B => Option[G]]): c.Tree = {
    import c.universe._

    val builder     = c.prefix.tree
    val sourceOptic = optic[A, G](c)(source, c.Expr[Schema[A]](q"$builder.sourceSchema"))
    val targetOptic = optic[B, Option[G]](c)(target, c.Expr[Schema[B]](q"$builder.targetSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.optionalizeField($builder, $sourceOptic, $targetOptic)"
  }

  def transformElements[A: c.WeakTypeTag, B: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(at: c.Expr[A => Vector[G]], transform: c.Expr[SchemaExpr[A, G]]): c.Tree = {
    import c.universe._
    val _ = c.weakTypeOf[B]

    val builder = c.prefix.tree
    val opticAt = optic[A, Vector[G]](c)(at, c.Expr[Schema[A]](q"$builder.sourceSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.transformElements($builder, $opticAt, $transform)"
  }

  def transformKeys[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](
    c: whitebox.Context
  )(at: c.Expr[A => Map[K, V]], transform: c.Expr[SchemaExpr[A, K]]): c.Tree = {
    import c.universe._
    val _ = c.weakTypeOf[B]

    val builder = c.prefix.tree
    val opticAt = optic[A, Map[K, V]](c)(at, c.Expr[Schema[A]](q"$builder.sourceSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.transformKeys($builder, $opticAt, $transform)"
  }

  def transformValues[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](
    c: whitebox.Context
  )(at: c.Expr[A => Map[K, V]], transform: c.Expr[SchemaExpr[A, V]]): c.Tree = {
    import c.universe._
    val _ = c.weakTypeOf[B]

    val builder = c.prefix.tree
    val opticAt = optic[A, Map[K, V]](c)(at, c.Expr[Schema[A]](q"$builder.sourceSchema"))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.transformValues($builder, $opticAt, $transform)"
  }

  def join[A: c.WeakTypeTag, B: c.WeakTypeTag, F: c.WeakTypeTag](
    c: whitebox.Context
  )(target: c.Expr[B => F], sources: c.Expr[Seq[A => Any]], combiner: c.Expr[SchemaExpr[A, F]]): c.Tree = {
    import c.universe._

    val builder      = c.prefix.tree
    val targetOptic  = optic[B, F](c)(target, c.Expr[Schema[B]](q"$builder.targetSchema"))
    val sourceOptics = seqSelectors(c)(sources).map(source => optic[A, Any](c)(source, c.Expr[Schema[A]](q"$builder.sourceSchema")))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.join($builder, $targetOptic, _root_.scala.Seq(..$sourceOptics), $combiner)"
  }

  def split[A: c.WeakTypeTag, B: c.WeakTypeTag, F: c.WeakTypeTag, G: c.WeakTypeTag](
    c: whitebox.Context
  )(source: c.Expr[A => F], targetPaths: c.Expr[Seq[B => Any]], splitter: c.Expr[SchemaExpr[A, G]]): c.Tree = {
    import c.universe._
    val _ = c.weakTypeOf[G]

    val builder      = c.prefix.tree
    val sourceOptic  = optic[A, F](c)(source, c.Expr[Schema[A]](q"$builder.sourceSchema"))
    val targetOptics = seqSelectors(c)(targetPaths).map(target => optic[B, Any](c)(target, c.Expr[Schema[B]](q"$builder.targetSchema")))

    q"_root_.zio.blocks.schema.migration.MigrationBuilderSupport.split($builder, $sourceOptic, _root_.scala.Seq(..$targetOptics), $splitter)"
  }

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Expr[Migration[A, B]] = {
    import c.universe._

    val builder = c.prefix.tree
    val srcTpe  = c.weakTypeOf[A]
    val tgtTpe  = c.weakTypeOf[B]

    val missing = checkCompleteness[A, B](c)(builder)
    if (missing.nonEmpty) {
      c.error(c.enclosingPosition, summaryMessage(typeNameOf(c)(srcTpe), typeNameOf(c)(tgtTpe), missing))
      missing.foreach { fieldName =>
        c.error(c.enclosingPosition, s"Missing target field: .$fieldName")
      }
    }
    //  scope: type-alignment check is .buildPartial EXCLUSIVE (never from .build).

    c.Expr[Migration[A, B]](q"""
      new _root_.zio.blocks.schema.migration.Migration(
        $builder.sourceSchema,
        $builder.targetSchema,
        new _root_.zio.blocks.schema.migration.DynamicMigration($builder.actions)
      )
    """)
  }

  def buildPartialImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Expr[Migration[A, B]] = {
    import c.universe._
    val _ = (c.weakTypeOf[A], c.weakTypeOf[B])

    val builder = c.prefix.tree
    //  literal: skip completeness, run type alignment ( exclusive).
    checkTypeAlignment[A, B](c)(builder).foreach(msg => c.error(c.enclosingPosition, msg))

    c.Expr[Migration[A, B]](q"""
      new _root_.zio.blocks.schema.migration.Migration(
        $builder.sourceSchema,
        $builder.targetSchema,
        new _root_.zio.blocks.schema.migration.DynamicMigration($builder.actions)
      )
    """)
  }

  /** Builder diagnostic-message template, byte-identical across Scala 2 and Scala 3. */
  private def summaryMessage(sourceName: String, targetName: String, missingFieldsSorted: List[String]): String =
    s"Migration from $sourceName to $targetName is incomplete. Missing target fields: ${missingFieldsSorted.mkString(", ")}."

  /** Byte-identical short type name — package-stripped, `object `-stripped. */
  private def typeNameOf(c: whitebox.Context)(tpe: c.universe.Type): String =
    tpe.typeSymbol.name.decodedName.toString

  /**
   * Tree-walker completeness diff. Walks `c.prefix.tree` syntactically and
   * extracts accounted-for target field names from each chained builder method
   * call, then compares against `targetSchema`'s top-level field set.
   *
   * Does NOT evaluate the prefix tree at macro expansion time. Instead,
   * pattern-matches the tree structure left-associatively. Schema summoning is
   * done on stable `Select(_, TermName("schema"))` trees.
   *
   * If summoning or evaluation fails, returns Nil (conservative fallback — no
   * false positives). The caller always emits a valid Migration tree regardless.
   */
  private def checkCompleteness[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: whitebox.Context
  )(builder: c.Tree): List[String] = {
    import c.universe._

    // --- Step 1: walk the prefix tree to collect accounted-for target field names ---

    def selectorFieldName(lambdaTree: Tree): Option[String] = lambdaTree match {
      case Function(_, Select(Ident(_), TermName(name)))           => Some(name)
      case Function(_, Select(_, TermName(name)))                  => Some(name)
      case Block(_, Function(_, Select(Ident(_), TermName(name)))) => Some(name)
      case Block(_, Function(_, Select(_, TermName(name))))        => Some(name)
      case _                                                       => None
    }

    def recordAccounted(method: String, args: List[Tree], acc: Set[String]): Set[String] = method match {
      case "addField" if args.nonEmpty =>
        selectorFieldName(args.head).fold(acc)(acc + _)
      case "renameField" if args.size >= 2 =>
        selectorFieldName(args(1)).fold(acc)(acc + _)
      case "transformField" if args.size >= 2 =>
        selectorFieldName(args(1)).fold(acc)(acc + _)
      case "changeFieldType" if args.size >= 2 =>
        selectorFieldName(args(1)).fold(acc)(acc + _)
      case "mandateField" if args.size >= 2 =>
        selectorFieldName(args(1)).fold(acc)(acc + _)
      case "optionalizeField" if args.size >= 2 =>
        selectorFieldName(args(1)).fold(acc)(acc + _)
      case "join" if args.nonEmpty =>
        selectorFieldName(args.head).fold(acc)(acc + _)
      case "split" if args.size >= 2 =>
        args(1) match {
          case Apply(_, selectors) =>
            selectors.foldLeft(acc)((a, s) => selectorFieldName(s).fold(a)(a + _))
          case _ => acc
        }
      case _ => acc
    }

    def collectAccounted(tree: Tree, acc: Set[String]): Set[String] = tree match {
      case Apply(TypeApply(Select(receiver, TermName(method)), _), args) =>
        collectAccounted(receiver, recordAccounted(method, args, acc))
      case Apply(Select(receiver, TermName(method)), args) =>
        collectAccounted(receiver, recordAccounted(method, args, acc))
      case Select(inner, _) => collectAccounted(inner, acc)
      case Typed(inner, _)  => collectAccounted(inner, acc)
      case _                => acc
    }

    val accounted: Set[String] = collectAccounted(builder, Set.empty)

    // --- Step 2: evaluate target/source Schema[_] via implicit summoning ---

    try {
      val schemaBTpe = appliedType(typeOf[_root_.zio.blocks.schema.Schema[_]].typeConstructor, c.weakTypeOf[B])
      val tgtSchemaTree = c.inferImplicitValue(schemaBTpe, silent = true)
      if (tgtSchemaTree.isEmpty) return Nil
      val targetSchema = c.eval(c.Expr[_root_.zio.blocks.schema.Schema[B]](c.untypecheck(tgtSchemaTree.duplicate)))

      val schemaATpe    = appliedType(typeOf[_root_.zio.blocks.schema.Schema[_]].typeConstructor, c.weakTypeOf[A])
      val srcSchemaTree = c.inferImplicitValue(schemaATpe, silent = true)

      val targetRecordOpt = targetSchema.reflect.asRecord
      if (targetRecordOpt.isEmpty) return Nil
      val targetFields = targetRecordOpt.get.fields

      val sourceByName: Map[String, _root_.zio.blocks.schema.Reflect[_root_.zio.blocks.schema.binding.Binding, _]] =
        if (srcSchemaTree.isEmpty) Map.empty
        else {
          try {
            val sourceSchema = c.eval(c.Expr[_root_.zio.blocks.schema.Schema[A]](c.untypecheck(srcSchemaTree.duplicate)))
            sourceSchema.reflect.asRecord
              .map(_.fields.iterator.map(f => (f.name, f.value)).toMap)
              .getOrElse(Map.empty[String, _root_.zio.blocks.schema.Reflect[_root_.zio.blocks.schema.binding.Binding, _]])
          } catch {
            case _: Throwable =>
              Map.empty[String, _root_.zio.blocks.schema.Reflect[_root_.zio.blocks.schema.binding.Binding, _]]
          }
        }

      targetFields
        .filterNot { f =>
          sourceByName.get(f.name).exists(_ == f.value) || accounted.contains(f.name)
        }
        .map(_.name)
        .toList
        .sorted
    } catch {
      case _: Throwable => Nil
    }
  }

  /**
   *  /  selector-path resolution check. Runs ONLY from buildPartialImpl.
   * If schema summoning or action-tree evaluation fails, returns Nil (conservative).
   */
  private def checkTypeAlignment[A: c.WeakTypeTag, B: c.WeakTypeTag](
    c: whitebox.Context
  )(builder: c.Tree): List[String] = {
    import c.universe._
    val _ = c.weakTypeOf[A]

    try {
      val schemaBTpe = appliedType(typeOf[_root_.zio.blocks.schema.Schema[_]].typeConstructor, c.weakTypeOf[B])
      val tgtSchemaTree = c.inferImplicitValue(schemaBTpe, silent = true)
      if (tgtSchemaTree.isEmpty) return Nil
      val targetSchema = c.eval(c.Expr[_root_.zio.blocks.schema.Schema[B]](c.untypecheck(tgtSchemaTree.duplicate)))

      val actionsTree = q"$builder.actions"
      val actionsValue = c.eval(
        c.Expr[_root_.zio.blocks.chunk.Chunk[_root_.zio.blocks.schema.migration.MigrationAction]](
          c.untypecheck(actionsTree)
        )
      )

      actionsValue.toVector.iterator.flatMap { action =>
        val path = action.at
        if (path != _root_.zio.blocks.schema.DynamicOptic.root && targetSchema.reflect.get(path).isEmpty)
          Iterator.single(s"Selector path ${path.toScalaString} does not resolve against target schema ${typeNameOf(c)(c.weakTypeOf[B])}")
        else
          Iterator.empty
      }.toList
    } catch {
      case _: Throwable => Nil
    }
  }

  private def optic[S: c.WeakTypeTag, A: c.WeakTypeTag](
    c: whitebox.Context
  )(path: c.Expr[S => A], schema: c.Expr[Schema[S]]): c.Tree =
    {
      val _ = (c.weakTypeOf[S], c.weakTypeOf[A])
      _root_.zio.blocks.schema.CompanionOptics.optic[S, A](c)(path)(schema)
    }

  private def seqSelectors[S: c.WeakTypeTag](
    c: whitebox.Context
  )(selectors: c.Expr[Seq[S]]): List[c.Expr[S]] = {
    import c.universe._

    selectors.tree match {
      case q"scala.this.Predef.Seq.apply[..$_](..$args)"             => args.map(arg => c.Expr[S](arg))
      case q"_root_.scala.Seq.apply[..$_](..$args)"                  => args.map(arg => c.Expr[S](arg))
      case q"_root_.scala.collection.Seq.apply[..$_](..$args)"       => args.map(arg => c.Expr[S](arg))
      case q"_root_.scala.collection.immutable.Seq.apply[..$_](..$args)" => args.map(arg => c.Expr[S](arg))
      case q"Seq(..$args)"                                           => args.map(arg => c.Expr[S](arg))
      case q"List(..$args)"                                          => args.map(arg => c.Expr[S](arg))
      case Apply(_, args) if args.nonEmpty                           => args.map(arg => c.Expr[S](arg))
      case _                                                         =>
        CommonMacroOps.fail(c)("join/split require an explicit Seq(...) of selectors")
    }
  }
}
