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

import scala.quoted._
import zio.blocks.schema.{CompanionOptics, SchemaExpr}

private[migration] trait MigrationBuilderVersionSpecific[A, B] { self: MigrationBuilder[A, B] =>

  transparent inline def addField[F](inline target: B => F, default: SchemaExpr[B, F]): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.addField[A, B, F]('self, 'target, 'default) }

  transparent inline def dropField[F](
    inline source: A => F,
    defaultForReverse: SchemaExpr[A, F]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.dropField[A, B, F]('self, 'source, 'defaultForReverse) }

  transparent inline def renameField(inline from: A => Any, inline to: B => Any): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.renameField[A, B]('self, 'from, 'to) }

  transparent inline def transformField[G](
    inline from: A => Any,
    inline to: B => G,
    transform: SchemaExpr[A, G]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformField[A, B, G]('self, 'from, 'to, 'transform) }

  transparent inline def changeFieldType[F, G](
    inline source: A => F,
    inline target: B => G,
    converter: SchemaExpr[A, G]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.changeFieldType[A, B, F, G]('self, 'source, 'target, 'converter) }

  transparent inline def mandateField[G](
    inline source: A => Option[G],
    inline target: B => G,
    default: SchemaExpr[A, G]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.mandateField[A, B, G]('self, 'source, 'target, 'default) }

  transparent inline def optionalizeField[G](
    inline source: A => G,
    inline target: B => Option[G]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.optionalizeField[A, B, G]('self, 'source, 'target) }

  transparent inline def transformElements[G](
    inline at: A => Vector[G],
    transform: SchemaExpr[A, G]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformElements[A, B, G]('self, 'at, 'transform) }

  transparent inline def transformKeys[K, V](
    inline at: A => Map[K, V],
    transform: SchemaExpr[A, K]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformKeys[A, B, K, V]('self, 'at, 'transform) }

  transparent inline def transformValues[K, V](
    inline at: A => Map[K, V],
    transform: SchemaExpr[A, V]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.transformValues[A, B, K, V]('self, 'at, 'transform) }

  transparent inline def join[F](
    inline target: B => F,
    inline sources: Seq[A => Any],
    combiner: SchemaExpr[A, F]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.join[A, B, F]('self, 'target, 'sources, 'combiner) }

  transparent inline def split[F, G](
    inline source: A => F,
    inline targetPaths: Seq[B => Any],
    splitter: SchemaExpr[A, G]
  ): MigrationBuilder[A, B] =
    ${ MigrationBuilderMacros.split[A, B, F, G]('self, 'source, 'targetPaths, 'splitter) }

  inline def buildViaMacro: Migration[A, B] =
    ${ MigrationBuilderMacros.build[A, B]('self) }

  inline def buildPartialViaMacro: Migration[A, B] =
    ${ MigrationBuilderMacros.buildPartial[A, B]('self) }
}

private[migration] object MigrationBuilderMacros {

  def addField[A: Type, B: Type, F: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => F],
    default: Expr[SchemaExpr[B, F]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val targetOptic = CompanionOptics.optic[B, F](target, '{ $builder.targetSchema })
    '{ MigrationBuilderSupport.addField($builder, $targetOptic, $default) }
  }

  def dropField[A: Type, B: Type, F: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => F],
    defaultForReverse: Expr[SchemaExpr[A, F]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = CompanionOptics.optic[A, F](source, '{ $builder.sourceSchema })
    '{ MigrationBuilderSupport.dropField($builder, $sourceOptic, $defaultForReverse) }
  }

  def renameField[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => Any]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = CompanionOptics.optic[A, Any](from, '{ $builder.sourceSchema })
    val targetOptic = CompanionOptics.optic[B, Any](to, '{ $builder.targetSchema })
    '{ MigrationBuilderSupport.renameField($builder, $sourceOptic, $targetOptic) }
  }

  def transformField[A: Type, B: Type, G: Type](
    builder: Expr[MigrationBuilder[A, B]],
    from: Expr[A => Any],
    to: Expr[B => G],
    transform: Expr[SchemaExpr[A, G]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = CompanionOptics.optic[A, Any](from, '{ $builder.sourceSchema })
    val targetOptic = CompanionOptics.optic[B, G](to, '{ $builder.targetSchema })
    '{ MigrationBuilderSupport.transformField($builder, $sourceOptic, $targetOptic, $transform) }
  }

  def changeFieldType[A: Type, B: Type, F: Type, G: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => F],
    target: Expr[B => G],
    converter: Expr[SchemaExpr[A, G]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = CompanionOptics.optic[A, F](source, '{ $builder.sourceSchema })
    val targetOptic = CompanionOptics.optic[B, G](target, '{ $builder.targetSchema })
    '{ MigrationBuilderSupport.changeFieldType($builder, $sourceOptic, $targetOptic, $converter) }
  }

  def mandateField[A: Type, B: Type, G: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => Option[G]],
    target: Expr[B => G],
    default: Expr[SchemaExpr[A, G]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = CompanionOptics.optic[A, Option[G]](source, '{ $builder.sourceSchema })
    val targetOptic = CompanionOptics.optic[B, G](target, '{ $builder.targetSchema })
    '{ MigrationBuilderSupport.mandateField($builder, $sourceOptic, $targetOptic, $default) }
  }

  def optionalizeField[A: Type, B: Type, G: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => G],
    target: Expr[B => Option[G]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic = CompanionOptics.optic[A, G](source, '{ $builder.sourceSchema })
    val targetOptic = CompanionOptics.optic[B, Option[G]](target, '{ $builder.targetSchema })
    '{ MigrationBuilderSupport.optionalizeField($builder, $sourceOptic, $targetOptic) }
  }

  def transformElements[A: Type, B: Type, G: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Vector[G]],
    transform: Expr[SchemaExpr[A, G]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = CompanionOptics.optic[A, Vector[G]](at, '{ $builder.sourceSchema })
    '{ MigrationBuilderSupport.transformElements($builder, $optic, $transform) }
  }

  def transformKeys[A: Type, B: Type, K: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Map[K, V]],
    transform: Expr[SchemaExpr[A, K]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = CompanionOptics.optic[A, Map[K, V]](at, '{ $builder.sourceSchema })
    '{ MigrationBuilderSupport.transformKeys($builder, $optic, $transform) }
  }

  def transformValues[A: Type, B: Type, K: Type, V: Type](
    builder: Expr[MigrationBuilder[A, B]],
    at: Expr[A => Map[K, V]],
    transform: Expr[SchemaExpr[A, V]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val optic = CompanionOptics.optic[A, Map[K, V]](at, '{ $builder.sourceSchema })
    '{ MigrationBuilderSupport.transformValues($builder, $optic, $transform) }
  }

  def join[A: Type, B: Type, F: Type](
    builder: Expr[MigrationBuilder[A, B]],
    target: Expr[B => F],
    sources: Expr[Seq[A => Any]],
    combiner: Expr[SchemaExpr[A, F]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val targetOptic  = CompanionOptics.optic[B, F](target, '{ $builder.targetSchema })
    val sourceOptics = Expr.ofSeq(seqElements(sources).map(source => CompanionOptics.optic[A, Any](source, '{ $builder.sourceSchema })))

    '{ MigrationBuilderSupport.join($builder, $targetOptic, $sourceOptics, $combiner) }
  }

  def split[A: Type, B: Type, F: Type, G: Type](
    builder: Expr[MigrationBuilder[A, B]],
    source: Expr[A => F],
    targetPaths: Expr[Seq[B => Any]],
    splitter: Expr[SchemaExpr[A, G]]
  )(using Quotes): Expr[MigrationBuilder[A, B]] = {
    val sourceOptic  = CompanionOptics.optic[A, F](source, '{ $builder.sourceSchema })
    val targetOptics = Expr.ofSeq(seqElements(targetPaths).map(target => CompanionOptics.optic[B, Any](target, '{ $builder.targetSchema })))

    '{ MigrationBuilderSupport.split($builder, $sourceOptic, $targetOptics, $splitter) }
  }

  def build[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect._

    val srcName = typeNameOf[A]
    val tgtName = typeNameOf[B]
    val missing = checkCompleteness[A, B](builder)

    if (missing.nonEmpty) {
      report.error(summaryMessage(srcName, tgtName, missing), Position.ofMacroExpansion)
      missing.foreach { fieldName =>
        report.error(s"Missing target field: .$fieldName", Position.ofMacroExpansion)
      }
    }
    //  scope: type-alignment is .buildPartial EXCLUSIVE. Do NOT call checkTypeAlignment here.

    '{
      new _root_.zio.blocks.schema.migration.Migration(
        $builder.sourceSchema,
        $builder.targetSchema,
        new _root_.zio.blocks.schema.migration.DynamicMigration($builder.actions)
      )
    }
  }

  def buildPartial[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): Expr[Migration[A, B]] = {
    import quotes.reflect._

    // `.buildPartial` skips completeness but still runs type alignment.
    checkTypeAlignment[A, B](builder).foreach(msg => report.error(msg, Position.ofMacroExpansion))

    '{
      new _root_.zio.blocks.schema.migration.Migration(
        $builder.sourceSchema,
        $builder.targetSchema,
        new _root_.zio.blocks.schema.migration.DynamicMigration($builder.actions)
      )
    }
  }

  /** Builder diagnostic-message template, byte-identical across Scala 2 and Scala 3. */
  private def summaryMessage(sourceName: String, targetName: String, missingFieldsSorted: List[String]): String =
    s"Migration from $sourceName to $targetName is incomplete. Missing target fields: ${missingFieldsSorted.mkString(", ")}."

  /** Byte-identical short type name helper. */
  private def typeNameOf[T: Type](using Quotes): String = {
    import quotes.reflect._
    TypeRepr.of[T].typeSymbol.name
  }

  /**
   * Quotes-reflect tree walker completeness diff. Walks `builder.asTerm` syntactically
   * to collect accounted-for target field names, then compares against summoned
   * `Schema[B]` target field set.
   *
   * CRITICAL: does NOT evaluate `builder.actions` at compile time (the Chunk is a
   * runtime Expr and Expr-eval on it silently fails). Instead, pattern-matches the
   * builder Term left-associatively via `quotes.reflect` primitives —  canonical.
   */
  private def checkCompleteness[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): List[String] = {
    import quotes.reflect._

    def selectorFieldName(arg: Term): Option[String] = arg match {
      case Lambda(_, Select(_, name))                          => Some(name)
      case Inlined(_, _, Lambda(_, Select(_, name)))           => Some(name)
      case Block(_, Lambda(_, Select(_, name)))                => Some(name)
      case Inlined(_, _, Block(_, Lambda(_, Select(_, name)))) => Some(name)
      case _                                                   => None
    }

    def collectAccounted(term: Term, acc: Set[String]): Set[String] = term match {
      case Apply(TypeApply(Select(receiver, method), _), args) =>
        collectAccounted(receiver, recordAccounted(method, args, acc))
      case Apply(Select(receiver, method), args) =>
        collectAccounted(receiver, recordAccounted(method, args, acc))
      case Inlined(_, _, inner) => collectAccounted(inner, acc)
      case Block(_, inner)      => collectAccounted(inner, acc)
      case Typed(inner, _)      => collectAccounted(inner, acc)
      case Select(inner, _)     => collectAccounted(inner, acc)
      case _                    => acc
    }

    def recordAccounted(method: String, args: List[Term], acc: Set[String]): Set[String] = method match {
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
          case Inlined(_, _, Apply(_, selectors)) =>
            selectors.foldLeft(acc)((a, s) => selectorFieldName(s).fold(a)(a + _))
          case _ => acc
        }
      case _ => acc
    }

    val accounted: Set[String] = collectAccounted(builder.asTerm, Set.empty)

    // Scala 3 has no `c.eval` equivalent: `Expr.summon[Schema[B]].value` requires a
    // `FromExpr[Schema[B]]` which does not exist for user-defined ADTs. Instead, we
    // derive target/source field inventories from the type-level shape of A and B via
    // `caseFields` (the case-class primary-constructor params). This preserves the
    // "implicit-copy same-type source field" rule by comparing TypeReprs with `=:=`.
    try {
      val tgtSym   = TypeRepr.of[B].typeSymbol
      if (!tgtSym.isClassDef || !tgtSym.flags.is(Flags.Case)) return Nil
      val tgtCaseFields = tgtSym.caseFields

      val srcSym = TypeRepr.of[A].typeSymbol
      val srcByName: Map[String, TypeRepr] =
        if (srcSym.isClassDef && srcSym.flags.is(Flags.Case))
          srcSym.caseFields.iterator.map(s => (s.name, TypeRepr.of[A].memberType(s))).toMap
        else Map.empty

      def tgtType(s: Symbol): TypeRepr = TypeRepr.of[B].memberType(s)

      tgtCaseFields
        .filterNot { f =>
          srcByName.get(f.name).exists(_ =:= tgtType(f)) || accounted.contains(f.name)
        }
        .map(_.name)
        .sorted
    } catch {
      case _: Throwable => Nil
    }
  }

  /**
   *  /  shallow selector-path resolution check. Scala 3 mirror of Scala 2
   * checkTypeAlignment. Runs ONLY from buildPartial ( exclusive scope).
   */
  private def checkTypeAlignment[A: Type, B: Type](
    builder: Expr[MigrationBuilder[A, B]]
  )(using Quotes): List[String] = {
    import quotes.reflect._

    def selectorFieldName(arg: Term): Option[String] = arg match {
      case Lambda(_, Select(_, name))                          => Some(name)
      case Inlined(_, _, Lambda(_, Select(_, name)))           => Some(name)
      case Block(_, Lambda(_, Select(_, name)))                => Some(name)
      case Inlined(_, _, Block(_, Lambda(_, Select(_, name)))) => Some(name)
      case _                                                   => None
    }

    def targetSelectors(term: Term, acc: List[String]): List[String] = term match {
      case Apply(TypeApply(Select(receiver, method), _), args) =>
        targetSelectors(receiver, recordSelector(method, args, acc))
      case Apply(Select(receiver, method), args) =>
        targetSelectors(receiver, recordSelector(method, args, acc))
      case Inlined(_, _, inner) => targetSelectors(inner, acc)
      case Block(_, inner)      => targetSelectors(inner, acc)
      case Typed(inner, _)      => targetSelectors(inner, acc)
      case Select(inner, _)     => targetSelectors(inner, acc)
      case _                    => acc
    }

    def recordSelector(method: String, args: List[Term], acc: List[String]): List[String] = method match {
      case "addField" | "join" if args.nonEmpty =>
        selectorFieldName(args.head).fold(acc)(_ :: acc)
      case "renameField" | "transformField" | "changeFieldType" | "mandateField" | "optionalizeField" if args.size >= 2 =>
        selectorFieldName(args(1)).fold(acc)(_ :: acc)
      case _ => acc
    }

    try {
      // Scala 3 path: derive target field names from the type-level shape of B.
      // No Expr eval is available, so we rely on case-class introspection.
      val tgtSym = TypeRepr.of[B].typeSymbol
      if (!tgtSym.isClassDef || !tgtSym.flags.is(Flags.Case)) return Nil
      val targetFieldNames: Set[String] = tgtSym.caseFields.iterator.map(_.name).toSet

      val selectors = targetSelectors(builder.asTerm, Nil)
      selectors.iterator
        .filterNot(targetFieldNames.contains)
        .map(n => s"Selector path .$n does not resolve against target schema ${typeNameOf[B]}")
        .toList
    } catch {
      case _: Throwable => Nil
    }
  }

  private def seqElements[S: Type](selectors: Expr[Seq[S]])(using Quotes): List[Expr[S]] = {
    import quotes.reflect._

    def loop(term: Term): List[Expr[S]] =
      term match {
        case Inlined(_, _, inner)         => loop(inner)
        case Typed(inner, _)              => loop(inner)
        case Repeated(args, _)            => args.map(_.asExprOf[S])
        case Apply(_, List(Repeated(args, _))) => args.map(_.asExprOf[S])
        case Apply(_, args) if args.nonEmpty =>
          args.toList match {
            case single :: Nil => loop(single)
            case many          => many.map(_.asExprOf[S])
          }
        case _                            =>
          report.errorAndAbort("join/split require an explicit Seq(...) of selectors")
      }

    selectors match {
      case Varargs(args) => args.toList
      case _             => loop(selectors.asTerm)
    }
  }
}
