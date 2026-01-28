package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}

import scala.annotation.nowarn
import scala.reflect.macros.blackbox

object MigrationMacros {

  def selectorToOptic[S, A](c: blackbox.Context)(selector: c.Expr[S => A]): c.Expr[DynamicOptic] = {
    import c.universe._

    def extractPath(tree: Tree): List[String] = tree match {
      case Ident(_) => Nil
      case Select(qualifier, TermName(fieldName)) =>
        extractPath(qualifier) :+ fieldName
      case Apply(Select(qualifier, TermName(methodName)), _) =>
        methodName match {
          case "each" | "elements" => extractPath(qualifier) :+ "*"
          case "eachKey" | "keys" => extractPath(qualifier) :+ "**k"
          case "eachValue" | "values" => extractPath(qualifier) :+ "**v"
          case _ => c.abort(c.enclosingPosition, s"Unsupported selector method: $methodName")
        }
      case _ =>
        c.abort(c.enclosingPosition, s"Unsupported selector expression: ${showRaw(tree)}")
    }

    selector.tree match {
      case Function(_, body) =>
        val path = extractPath(body)
        val nodes = path.map { segment =>
          segment match {
            case "*" => q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"
            case "**k" => q"_root_.zio.blocks.schema.DynamicOptic.Node.MapKeys"
            case "**v" => q"_root_.zio.blocks.schema.DynamicOptic.Node.MapValues"
            case name => q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($name)"
          }
        }
        val vectorTree = q"_root_.scala.Vector(..$nodes)"
        c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic($vectorTree)")
      case _ =>
        c.abort(c.enclosingPosition, "Expected a selector function like _.field or _.nested.field")
    }
  }

  def selectorToFieldName[S, A](c: blackbox.Context)(selector: c.Expr[S => A]): c.Expr[String] = {
    import c.universe._

    selector.tree match {
      case Function(_, Select(_, TermName(fieldName))) =>
        c.Expr[String](Literal(Constant(fieldName)))
      case _ =>
        c.abort(c.enclosingPosition, "Expected a simple field selector like _.fieldName")
    }
  }

  def validateMigration[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(
    builder: c.Expr[MigrationBuilder[A, B]]
  ): c.Expr[Migration[A, B]] = {
    import c.universe._

    c.Expr[Migration[A, B]](q"$builder.buildPartial")
  }
}

object MigrationBuilderOps {
  import scala.language.experimental.macros

  implicit class MigrationBuilderSyntax[A, B](private val builder: MigrationBuilder[A, B]) extends AnyVal {

    def addField[T](target: B => T, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.addFieldImpl[A, B, T]

    def dropField[T](source: A => T): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.dropFieldImpl[A, B, T]

    def dropFieldWithDefault[T](source: A => T, defaultForReverse: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.dropFieldWithDefaultImpl[A, B, T]

    def renameField[T1, T2](from: A => T1, to: B => T2): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.renameFieldImpl[A, B, T1, T2]

    def transformField[T](selector: A => T, transform: SchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.transformFieldImpl[A, B, T]

    def mandateField[T](source: A => Option[T], target: B => T, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.mandateFieldImpl[A, B, T]

    def optionalizeField[T](source: A => T, target: B => Option[T]): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.optionalizeFieldImpl[A, B, T]

    def changeFieldType[T1, T2](source: A => T1, target: B => T2, converter: SchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.changeFieldTypeImpl[A, B, T1, T2]

    def transformElements[T](selector: A => Vector[T], transform: SchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.transformElementsImpl[A, B, T]

    def transformKeys[K, V](selector: A => Map[K, V], transform: SchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.transformKeysImpl[A, B, K, V]

    def transformValues[K, V](selector: A => Map[K, V], transform: SchemaExpr): MigrationBuilder[A, B] =
      macro MigrationBuilderMacros.transformValuesImpl[A, B, K, V]

    def build: Migration[A, B] =
      macro MigrationBuilderMacros.buildImpl[A, B]
  }
}

@nowarn("msg=never used")
object MigrationBuilderMacros {
  import scala.reflect.macros.blackbox

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    target: c.Expr[B => T],
    default: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(target)
    val builder = c.prefix.tree
    val tType = TypeTree(implicitly[c.WeakTypeTag[T]].tpe)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.addFieldTyped[$tType]($fieldName, $default)($schema)"
    )
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => T]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(source)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.dropField($fieldName)")
  }

  def dropFieldWithDefaultImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => T],
    defaultForReverse: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(source)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.dropFieldWithDefault($fieldName, $schema.toDynamicValue($defaultForReverse))"
    )
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T1: c.WeakTypeTag, T2: c.WeakTypeTag](c: blackbox.Context)(
    from: c.Expr[A => T1],
    to: c.Expr[B => T2]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fromName = extractFieldName(c)(from)
    val toName = extractFieldName(c)(to)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.renameField($fromName, $toName)")
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T],
    transform: c.Expr[SchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(selector)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.transformField($fieldName, $transform)")
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => Option[T]],
    target: c.Expr[B => T],
    default: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(source)
    val builder = c.prefix.tree
    val tType = TypeTree(implicitly[c.WeakTypeTag[T]].tpe)
    c.Expr[MigrationBuilder[A, B]](
      q"$builder.mandateFieldTyped[$tType]($fieldName, $default)($schema)"
    )
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => T],
    target: c.Expr[B => Option[T]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(source)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.optionalizeField($fieldName)")
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T1: c.WeakTypeTag, T2: c.WeakTypeTag](c: blackbox.Context)(
    source: c.Expr[A => T1],
    target: c.Expr[B => T2],
    converter: c.Expr[SchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val fieldName = extractFieldName(c)(source)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.changeFieldType($fieldName, $converter)")
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Vector[T]],
    transform: c.Expr[SchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val optic = extractOptic(c)(selector)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.transformElementsAt($optic, $transform)")
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Map[K, V]],
    transform: c.Expr[SchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val optic = extractOptic(c)(selector)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.transformKeysAt($optic, $transform)")
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Map[K, V]],
    transform: c.Expr[SchemaExpr]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._

    val optic = extractOptic(c)(selector)
    val builder = c.prefix.tree
    c.Expr[MigrationBuilder[A, B]](q"$builder.transformValuesAt($optic, $transform)")
  }

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[Migration[A, B]] = {
    import c.universe._

    val builder = c.prefix.tree
    // For now, just build without full validation
    // Full validation would check that all source fields are handled
    // and all target fields are populated
    val aType = TypeTree(implicitly[c.WeakTypeTag[A]].tpe)
    val bType = TypeTree(implicitly[c.WeakTypeTag[B]].tpe)
    c.Expr[Migration[A, B]](q"$builder.asInstanceOf[_root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType]].buildPartial")
  }

  private def extractFieldName[S, A](c: blackbox.Context)(selector: c.Expr[S => A]): c.Expr[String] = {
    import c.universe._

    selector.tree match {
      case Function(_, Select(_, TermName(fieldName))) =>
        c.Expr[String](Literal(Constant(fieldName)))
      case Function(_, Ident(TermName(fieldName))) if fieldName != "_" =>
        c.Expr[String](Literal(Constant(fieldName)))
      case _ =>
        c.abort(c.enclosingPosition, s"Expected a simple field selector like _.fieldName, got: ${show(selector.tree)}")
    }
  }

  private def extractOptic[S, A](c: blackbox.Context)(selector: c.Expr[S => A]): c.Expr[DynamicOptic] = {
    import c.universe._

    def extractPath(tree: Tree): List[Tree] = tree match {
      case Ident(_) => Nil
      case Select(qualifier, TermName(fieldName)) =>
        extractPath(qualifier) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"
      case Apply(Select(qualifier, TermName("each")), _) =>
        extractPath(qualifier) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Elements"
      case _ =>
        c.abort(c.enclosingPosition, s"Unsupported selector: ${show(tree)}")
    }

    selector.tree match {
      case Function(_, body) =>
        val nodes = extractPath(body)
        val vectorTree = q"_root_.scala.Vector(..$nodes)"
        c.Expr[DynamicOptic](q"_root_.zio.blocks.schema.DynamicOptic($vectorTree)")
      case _ =>
        c.abort(c.enclosingPosition, "Expected a selector function")
    }
  }
}
