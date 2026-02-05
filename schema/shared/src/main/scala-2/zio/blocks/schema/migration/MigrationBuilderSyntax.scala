package zio.blocks.schema.migration

import zio.blocks.schema._
import scala.annotation.compileTimeOnly
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Scala 2 implicit class extension for [[MigrationBuilder]] that provides
 * type-safe selector syntax.
 *
 * These extensions allow using lambda expressions like `_.fieldName` instead of
 * constructing [[DynamicOptic]] paths manually.
 *
 * Usage:
 * {{{
 * import MigrationBuilderSyntax._
 *
 * MigrationBuilder[PersonV1, PersonV2]
 *   .addField[Int](_.age, 0)
 *   .renameField(_.firstName, _.givenName)
 *   .dropField(_.middleName)
 *   .build
 * }}}
 */
object MigrationBuilderSyntax {

  // ==================== Selector-only Syntax ====================
  //
  // These implicit classes exist solely to support the selector syntax
  // consumed by `SelectorMacros`. They intentionally fail if evaluated outside
  // of selector macros.

  implicit class ValueSelectorOps[A](private val a: A) extends AnyVal {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def when[B <: A]: B = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def wrapped[B]: B = ???
  }

  implicit class SequenceSelectorOps[C[_], A](private val c: C[A]) extends AnyVal {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def at(index: Int): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atIndices(indices: Int*): A = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def each: A = ???
  }

  implicit class MapSelectorOps[M[_, _], K, V](private val m: M[K, V]) extends AnyVal {
    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKey(key: K): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def atKeys(keys: K*): V = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachKey: K = ???

    @compileTimeOnly("Can only be used inside migration selector macros")
    def eachValue: V = ???
  }

  // ==================== Builder Syntax ====================

  implicit class MigrationBuilderOps[A, B](private val builder: MigrationBuilder[A, B]) extends AnyVal {

    /**
     * Add a field with a type-safe selector and literal default.
     */
    def addField[T](selector: B => T, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.addFieldImpl[A, B, T]

    /**
     * Add a field with a type-safe selector and expression default.
     */
    def addFieldExpr[T](selector: B => T, default: DynamicSchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.addFieldExprImpl[A, B, T]

    /**
     * Drop a field using a type-safe selector.
     */
    def dropField[T](selector: A => T): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxImpl.dropFieldImpl[A, B, T]

    /**
     * Rename a field using type-safe selectors.
     */
    def renameField[T, U](from: A => T, to: B => U): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.renameFieldImpl[A, B, T, U]

    /**
     * Transform a field using a type-safe selector.
     */
    def transformField[T](selector: A => T, transform: DynamicSchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.transformFieldImpl[A, B, T]

    /**
     * Make an optional field mandatory with type-safe selector.
     */
    def mandateField[T](selector: B => T, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.mandateFieldImpl[A, B, T]

    /**
     * Make a mandatory field optional using a type-safe selector.
     */
    def optionalizeField[T](selector: A => T): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.optionalizeFieldImpl[A, B, T]

    /**
     * Make a mandatory field optional using a type-safe selector with a reverse
     * default.
     */
    def optionalizeFieldExpr[T](
      selector: A => T,
      defaultForReverse: DynamicSchemaExpr
    ): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxImpl.optionalizeFieldExprImpl[A, B, T]

    /**
     * Transform all elements in a sequence field.
     */
    def transformElements[T](selector: A => Seq[T], transform: DynamicSchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.transformElementsImpl[A, B, T]

    /**
     * Transform map keys using type-safe selector.
     */
    def transformKeys[K, V](selector: A => Map[K, V], transform: DynamicSchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.transformKeysImpl[A, B, K, V]

    /**
     * Transform map values using type-safe selector.
     */
    def transformValues[K, V](selector: A => Map[K, V], transform: DynamicSchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderSyntaxImpl.transformValuesImpl[A, B, K, V]
  }

  /**
   * Convenient syntax for creating paths from selectors, e.g.:
   * `MigrationBuilder.paths.from[User, String](_.name)`.
   */
  implicit class PathsOps(private val paths: MigrationBuilder.paths.type) extends AnyVal {
    def from[A, B](selector: A => B): DynamicOptic = macro MigrationBuilderSyntaxImpl.fromImpl[A, B]
  }
}

class MigrationBuilderSyntaxImpl(val c: blackbox.Context) {
  import c.universe._

  private def unwrapBuilder(prefix: Tree): Tree = prefix match {
    case Apply(_, List(b))               => b
    case TypeApply(Apply(_, List(b)), _) => b
    case Apply(TypeApply(_, _), List(b)) => b
    case Apply(Select(_, _), List(b))    => b
    case other                           =>
      c.abort(c.enclosingPosition, s"Unexpected macro prefix: ${showRaw(other)}")
  }

  private def toOpticTree[A: WeakTypeTag, B: WeakTypeTag](selector: c.Expr[A => B]): Tree = {
    val aTpe = weakTypeOf[A]
    val bTpe = weakTypeOf[B]
    q"_root_.zio.blocks.schema.migration.SelectorMacros.toOptic[$aTpe, $bTpe]($selector)"
  }

  def fromImpl[A: WeakTypeTag, B: WeakTypeTag](selector: c.Expr[A => B]): c.Expr[DynamicOptic] =
    c.Expr[DynamicOptic](toOpticTree[A, B](selector))

  def addFieldImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[B => T],
    default: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[B, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    val tpe     = weakTypeOf[T]
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.addField[$tpe]($optic, $default)($schema)"
    )
  }

  def addFieldExprImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[B => T],
    default: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[B, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.addField($optic, $default)"
    )
  }

  def dropFieldImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[A => T]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.dropField($optic)"
    )
  }

  def renameFieldImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag, U: WeakTypeTag](
    from: c.Expr[A => T],
    to: c.Expr[B => U]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val fromOptic = toOpticTree[A, T](from)
    val toOptic   = toOpticTree[B, U](to)
    val builder   = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.renameField($fromOptic, $toOptic)"
    )
  }

  def transformFieldImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[A => T],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.transformField($optic, $transform)"
    )
  }

  def mandateFieldImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[B => T],
    default: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[B, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    val tpe     = weakTypeOf[T]
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.mandateField[$tpe]($optic, $default)($schema)"
    )
  }

  def optionalizeFieldImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[A => T]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.optionalizeField($optic)"
    )
  }

  def optionalizeFieldExprImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[A => T],
    defaultForReverse: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, T](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.optionalizeField($optic, $defaultForReverse)"
    )
  }

  def transformElementsImpl[A: WeakTypeTag, B: WeakTypeTag, T: WeakTypeTag](
    selector: c.Expr[A => Seq[T]],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, Seq[T]](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.transformElements($optic, $transform)"
    )
  }

  def transformKeysImpl[A: WeakTypeTag, B: WeakTypeTag, K: WeakTypeTag, V: WeakTypeTag](
    selector: c.Expr[A => Map[K, V]],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, Map[K, V]](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.transformKeys($optic, $transform)"
    )
  }

  def transformValuesImpl[A: WeakTypeTag, B: WeakTypeTag, K: WeakTypeTag, V: WeakTypeTag](
    selector: c.Expr[A => Map[K, V]],
    transform: c.Expr[DynamicSchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    val optic   = toOpticTree[A, Map[K, V]](selector)
    val builder = unwrapBuilder(c.prefix.tree)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.transformValues($optic, $transform)"
    )
  }
}
