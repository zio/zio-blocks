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

package zio.blocks.schema

/**
 * Mix this trait into a companion object to build optics for `S` from a
 * selector lambda: `$(_.title)`, or the same with a name, `optic(_.title)`. A
 * macro reads the lambda at compile time and builds the optic from the implicit
 * `Schema[S]`. The kind of optic follows from the path: a [[Lens]] when every
 * step is a field, a [[Prism]] when every step is `when`, a [[Traversal]] when
 * any step is `each`, `eachKey`, `eachValue`, `atIndices`, `atKeys` or
 * `searchFor`, and an [[Optional]] otherwise.
 *
 * A path is a chain of these steps:
 *
 *   - `.field`: a field of a record;
 *   - `.when[C]`: the case `C` of a variant;
 *   - `.at(i)`, `.atIndices(i, j)`, `.each`: elements of a sequence;
 *   - `.atKey(k)`, `.atKeys(k1, k2)`, `.eachKey`, `.eachValue`: keys and values
 *     of a map;
 *   - `.wrapped[B]`: the value of type `B` inside a wrapper;
 *   - `.searchFor[T]`: every value at or below this point that decodes as a `T`
 *     (needs an implicit `Schema[T]`; `modify` skips map keys).
 *
 * On Scala 3, a path can also select a tuple element by index, as in `$(_(0))`,
 * and `when` also takes a case of a union type. The step methods compile only
 * inside `$` and `optic`, and any other expression in the lambda is a compile
 * error.
 *
 * The macro checks the path against the types, and the schema is checked when
 * the optic is built: if the schema at a step has another shape than the step
 * needs, for example a `.field` step where the schema is a wrapper built with
 * `Schema#transform`, building the optic throws a `RuntimeException` such as
 * `Expected a record`.
 *
 * {{{
 * sealed trait Format
 * case class Paperback(pages: Int) extends Format
 * case class Ebook(sizeMb: Double) extends Format
 *
 * case class Book(title: String, tags: List[String], format: Format)
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val title: Lens[Book, String]     = $(_.title)
 *   val pages: Optional[Book, Int]    = $(_.format.when[Paperback].pages)
 *   val tags: Traversal[Book, String] = optic(_.tags.each)
 * }
 *
 * val zionomicon = Book("Zionomicon", List("zio", "scala"), Paperback(870))
 * Book.pages.getOption(zionomicon)   // Some(870)
 * Book.pages.toString              // Optional(_.format.when[Paperback].pages)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
trait CompanionOptics[S] {
  import scala.annotation.compileTimeOnly
  import scala.language.experimental.macros

  /**
   * Scala 2 only: adds the `when`, `wrapped` and `searchFor` steps to any value
   * inside a `$` or `optic` lambda. On Scala 3 they are extension methods.
   */
  implicit class ValueExtension[A](a: A) {

    /**
     * In a path, focuses on the case `B` of a variant. Compiles only inside `$`
     * and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def when[B <: A]: B = ???

    /**
     * In a path, focuses on the value of type `B` inside a wrapper, such as a
     * schema built with `Schema#transform`. Compiles only inside `$` and
     * `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def wrapped[B]: B = ???

    /**
     * In a path, focuses on every value at or below this point that decodes as
     * a `B`, found depth first. It needs an implicit `Schema[B]`. Map keys are
     * read by `fold` and `check` but skipped by `modify` (see
     * `docs/dev/issues.md`). Compiles only inside `$` and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def searchFor[B]: B = ???
  }

  /**
   * Scala 2 only: adds the `at`, `atIndices` and `each` steps to a sequence
   * inside a `$` or `optic` lambda. On Scala 3 they are extension methods.
   */
  implicit class SequenceExtension[C[_], A](c: C[A]) {

    /**
     * In a path, focuses on the element at `index` of a sequence. Compiles only
     * inside `$` and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def at(index: Int): A = ???

    /**
     * In a path, focuses on the elements at `indices` of a sequence. Compiles
     * only inside `$` and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def atIndices(indices: Int*): A = ???

    /**
     * In a path, focuses on every element of a sequence. Compiles only inside
     * `$` and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def each: A = ???
  }

  /**
   * Scala 2 only: adds the `atKey`, `atKeys`, `eachKey` and `eachValue` steps
   * to a map inside a `$` or `optic` lambda. On Scala 3 they are extension
   * methods.
   */
  implicit class MapExtension[M[_, _], K, V](m: M[K, V]) {

    /**
     * In a path, focuses on the value at `key` in a map. Compiles only inside
     * `$` and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def atKey(key: K): V = ???

    /**
     * In a path, focuses on the values at `keys` in a map. Compiles only inside
     * `$` and `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def atKeys(keys: K*): V = ???

    /**
     * In a path, focuses on every key of a map. Compiles only inside `$` and
     * `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachKey: K = ???

    /**
     * In a path, focuses on every value of a map. Compiles only inside `$` and
     * `optic`.
     */
    @compileTimeOnly("Can only be used inside `$(_)` and `optic(_)` macros")
    def eachValue: V = ???
  }

  /**
   * Builds the optic that `path` describes (see [[CompanionOptics]]). The
   * result has the exact optic type, such as `Lens[S, String]`. `$` and `optic`
   * are the same macro.
   */
  def $[A](path: S => A)(implicit schema: Schema[S]): Any = macro CompanionOptics.optic[S, A]

  /**
   * Builds the optic that `path` describes (see [[CompanionOptics]]). The
   * result has the exact optic type, such as `Lens[S, String]`. `$` and `optic`
   * are the same macro.
   */
  def optic[A](path: S => A)(implicit schema: Schema[S]): Any = macro CompanionOptics.optic[S, A]
}

/**
 * Macro implementation of `CompanionOptics#$` and `CompanionOptics#optic`.
 */
private object CompanionOptics {
  import scala.reflect.macros.whitebox
  import scala.reflect.NameTransformer
  import zio.blocks.schema.CommonMacroOps

  /**
   * Macro implementation of `CompanionOptics#$` and `CompanionOptics#optic`.
   */
  def optic[S, A](c: whitebox.Context)(path: c.Expr[S => A])(schema: c.Expr[Schema[S]]): c.Tree = {
    import c.universe._

    def fail(msg: String): Nothing = CommonMacroOps.fail(c)(msg)

    def directSubTypes(tpe: Type): List[Type] = CommonMacroOps.directSubTypes(c)(tpe)

    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $pathBody" => pathBody
      case _                    => fail(s"Expected a lambda expression, got '$tree'")
    }

    def toOptic(tree: c.Tree): c.Tree = tree match {
      case q"$_[..$_]($parent).each" =>
        val parentTpe  = parent.tpe.widen.dealias
        val elementTpe = tree.tpe.widen.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.seqValues(x.sequence)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.seqValues(x.sequence)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]])"""
        }
      case q"$_[..$_]($parent).eachKey" =>
        val parentTpe = parent.tpe.widen.dealias
        val keyTpe    = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapKeys(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $keyTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapKeys(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $keyTpe]])"""
        }
      case q"$_[..$_]($parent).eachValue" =>
        val parentTpe = parent.tpe.widen.dealias
        val valueTpe  = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapValues(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.mapValues(x.map)
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]])"""
        }
      case q"$_[..$_]($parent).when[$caseTree]" =>
        val parentTpe = parent.tpe.widen.dealias
        val caseTpe   = caseTree.tpe.dealias
        val caseIdx   = directSubTypes(parentTpe).indexWhere(_ =:= caseTpe, 0)
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asVariant.flatMap(_.prismByIndex[$caseTpe]($caseIdx))
                .getOrElse(sys.error("Expected a variant"))"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asVariant.flatMap(_.prismByIndex[$caseTpe]($caseIdx))
                .getOrElse(sys.error("Expected a variant")))"""
        }
      case q"$_[..$_]($parent).wrapped[$wrappedTree]" =>
        val parentTpe  = parent.tpe.widen.dealias
        val wrappedTpe = wrappedTree.tpe.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asWrapperUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, $parentTpe, $wrappedTpe]])
              }
              .getOrElse(sys.error("Expected a wrapper"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $wrappedTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asWrapperUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.wrapped(x.wrapper.asInstanceOf[Reflect.Wrapper[Binding, $parentTpe, $wrappedTpe]])
              }
              .getOrElse(sys.error("Expected a wrapper"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $wrappedTpe]])"""
        }
      case q"$_[..$_]($parent).at(..$args)" if args.size == 1 && args.head.tpe.widen.dealias <:< definitions.IntTpe =>
        val parentTpe  = parent.tpe.widen.dealias
        val elementTpe = tree.tpe.widen.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.at(x.sequence, ${args.head})
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $elementTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.at(x.sequence, ${args.head})
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $elementTpe]])"""
        }
      case q"$_[..$_]($parent).atKey(..$args)" if args.size == 1 =>
        val parentTpe = parent.tpe.widen.dealias
        val valueTpe  = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.atKey(x.map, ${args.head}.asInstanceOf[x.KeyType])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $valueTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Optional.atKey(x.map, ${args.head}.asInstanceOf[x.KeyType])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Optional[$parentTpe, $valueTpe]])"""
        }
      case q"$_[..$_]($parent).atIndices(..$args)"
          if args.nonEmpty && args.forall(_.tpe.widen.dealias <:< definitions.IntTpe) =>
        val parentTpe  = parent.tpe.widen.dealias
        val elementTpe = tree.tpe.widen.dealias
        val optic      = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atIndices(x.sequence, $args)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asSequenceUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atIndices(x.sequence, $args)
              }
              .getOrElse(sys.error("Expected a sequence"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $elementTpe]])"""
        }
      case q"$_[..$_]($parent).atKeys(..$args)" if args.nonEmpty =>
        val parentTpe = parent.tpe.widen.dealias
        val valueTpe  = tree.tpe.widen.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atKeys(x.map, $args.asInstanceOf[Seq[x.KeyType]])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asMapUnknown.map { x =>
                _root_.zio.blocks.schema.Traversal.atKeys(x.map, $args.asInstanceOf[Seq[x.KeyType]])
              }
              .getOrElse(sys.error("Expected a map"))
              .asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $valueTpe]])"""
        }
      case q"$_[..$_]($parent).searchFor[$searchTree]" =>
        val parentTpe = parent.tpe.widen.dealias
        val searchTpe = searchTree.tpe.dealias
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""_root_.zio.blocks.schema.SearchTraversal(
                $schema.reflect.asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[$parentTpe]],
                _root_.scala.Predef.implicitly[_root_.zio.blocks.schema.Schema[$searchTpe]].reflect
              ).asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $searchTpe]]"""
        } else {
          q"""val optic = $optic
              optic.apply(
                _root_.zio.blocks.schema.SearchTraversal(
                  optic.focus.asInstanceOf[_root_.zio.blocks.schema.Reflect.Bound[$parentTpe]],
                  _root_.scala.Predef.implicitly[_root_.zio.blocks.schema.Schema[$searchTpe]].reflect
                ).asInstanceOf[_root_.zio.blocks.schema.Traversal[$parentTpe, $searchTpe]]
              )"""
        }
      case q"$parent.$child" =>
        val childTpe  = tree.tpe.widen.dealias
        val fieldName = NameTransformer.decode(child.toString)
        val optic     = toOptic(parent)
        if (optic.isEmpty) {
          q"""$schema.reflect.asRecord.flatMap(_.lensByName[$childTpe]($fieldName))
                .getOrElse(sys.error("Expected a record"))"""
        } else {
          q"""val optic = $optic
              optic.apply(optic.focus.asRecord.flatMap(_.lensByName[$childTpe]($fieldName))
                .getOrElse(sys.error("Expected a record")))"""
        }
      case _: Ident =>
        q""
      case tree =>
        fail(
          s"Expected path elements: .<field>, .when[<T>], .at(<index>), .atIndices(<indices>), .atKey(<key>), .atKeys(<keys>), .each, .eachKey, .eachValue, .wrapped[<T>], or .searchFor[<T>], got '$tree'."
        )
    }

    val optic = toOptic(toPathBody(path.tree))
    // c.info(c.enclosingPosition, s"Generated optic:\n${showCode(optic)}", force = true)
    optic
  }
}
