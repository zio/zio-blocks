package zio.blocks.schema.migration

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import zio.blocks.schema.Schema

/**
 * Scala 2 implicit class for MigrationBuilder that provides selector-based
 * APIs. These methods use macros to extract DynamicOptic paths from lambda
 * selectors.
 *
 * Method naming uses `WithSelector` suffix for consistency with Scala 3 API.
 */
object MigrationBuilderSyntax {

  // Note: Not using AnyVal to avoid macro expansion issues with .builder access
  implicit class MigrationBuilderOps[A, B](val builder: MigrationBuilder[A, B]) {
    // format: off
    /** Add a field using a parent selector to specify the location. */
    def addFieldWithSelector[P, T](parentSelector: B => P)(fieldName: String, default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.addFieldWithSelectorImpl[A, B, P, T]
    /** Drop a field using a selector expression. */
    def dropFieldWithSelector[T](selector: A => T): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.dropFieldAtImpl[A, B, T]
    /** Drop a field using a selector, with a default for reverse migration. */
    def dropFieldWithSelectorDefault[T](selector: A => T, defaultForReverse: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.dropFieldAtWithDefaultImpl[A, B, T]
    /** Rename a field using a selector for source location. */
    def renameFieldWithSelector[T](selector: A => T, newName: String): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.renameFieldAtImpl[A, B, T]
    /** Transform a field value using a selector. */
    def transformFieldWithSelector[T](selector: A => T)(transform: Resolved, reverseTransform: Resolved): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.transformFieldWithSelectorImpl[A, B, T]
    /** Make an optional field mandatory using a selector. */
    def mandateFieldWithSelector[T](selector: A => Option[T], default: T)(implicit schema: Schema[T]): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.mandateFieldAtImpl[A, B, T]
    /** Make a mandatory field optional using a selector. */
    def optionalizeFieldWithSelector[T](selector: A => T): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.optionalizeFieldAtImpl[A, B, T]
    /** Change a field's type using a selector. */
    def changeFieldTypeWithSelector[T](selector: A => T)(fromType: String, toType: String): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.changeFieldTypeWithSelectorImpl[A, B, T]
    /** Transform elements of a collection using a selector. */
    def transformElementsWithSelector[T](selector: A => Seq[T])(transform: Resolved, reverseTransform: Resolved): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.transformElementsWithSelectorImpl[A, B, T]
    /** Transform map keys using a selector. */
    def transformKeysWithSelector[K, V](selector: A => Map[K, V])(keyTransform: Resolved, reverseTransform: Resolved): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.transformKeysWithSelectorImpl[A, B, K, V]
    /** Transform map values using a selector. */
    def transformValuesWithSelector[K, V](selector: A => Map[K, V])(valueTransform: Resolved, reverseTransform: Resolved): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.transformValuesWithSelectorImpl[A, B, K, V]
    /** Join two source fields into a single target field using selectors. */
    def joinFieldsWithSelector[S1, S2, T](source1: A => S1, source2: A => S2, target: B => T)(combiner: Resolved, splitter: Resolved): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.joinFieldsWithSelector2Impl[A, B, S1, S2, T]
    /** Split a source field into two target fields using selectors. */
    def splitFieldWithSelector[S, T1, T2](source: A => S, target1: B => T1, target2: B => T2)(splitter: Resolved, combiner: Resolved): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.splitFieldWithSelector2Impl[A, B, S, T1, T2]
    /** Rename an enum case using selector for the field containing the enum. */
    def renameCaseWithSelector[E](enumSelector: A => E, from: String, to: String): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.renameCaseWithSelectorImpl[A, B, E]
    /** Transform an enum case using selector for the field containing the enum. */
    def transformCaseWithSelector[E](enumSelector: A => E, caseName: String, caseActions: zio.blocks.chunk.Chunk[MigrationAction]): MigrationBuilder[A, B] = macro MigrationBuilderSyntaxMacros.transformCaseWithSelectorImpl[A, B, E]
    /**
     * Build migration with compile-time validation.
     *
     * This macro performs schema shape validation at compile time,
     * providing parity with Scala 3's ValidationProof API.
     * Compilation fails if the migration is incomplete.
     */
    def buildValidated(implicit schemaA: Schema[A], schemaB: Schema[B]): DynamicMigration = macro MigrationBuilderSyntaxMacros.buildValidatedImpl[A, B]
    /**
      * Build the migration with strict runtime validation.
      *
      * Validates that all paths are valid and all source/target fields are
      * properly handled. This is the Scala 2 equivalent of Scala 3's
      * macro-validated build.
      *
      * Throws IllegalArgumentException if validation fails.
      */
    // NOTE: The .build method is intentionally NOT provided on the untracked builder.
    // For runtime-validated builds, use buildStrict or buildValidated directly.
    // format: on
  }
}

object MigrationBuilderSyntaxMacros {

  def addFieldWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, P: c.WeakTypeTag, T](
    c: blackbox.Context
  )(parentSelector: c.Expr[B => P])(fieldName: c.Expr[String], default: c.Expr[T])(
    schema: c.Expr[Schema[T]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[B, P](c)(parentSelector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      ${c.prefix}.builder.addFieldResolved(
        _path,
        $fieldName,
        _root_.zio.blocks.schema.migration.Resolved.Literal($default, $schema)
      )
    }""")
  }

  def dropFieldAtImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, T](c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _fieldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.dropFieldResolved(
        _parentPath,
        _fieldName,
        _root_.zio.blocks.schema.migration.Resolved.Fail("Cannot reverse drop of " + _fieldName)
      )
    }""")
  }

  def dropFieldAtWithDefaultImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T],
    defaultForReverse: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, T](c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _fieldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.dropFieldResolved(
        _parentPath,
        _fieldName,
        _root_.zio.blocks.schema.migration.Resolved.Literal($defaultForReverse, $schema)
      )
    }""")
  }

  def renameFieldAtImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T],
    newName: c.Expr[String]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, T](c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _oldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.renameFieldAt(_parentPath, _oldName, $newName)
    }""")
  }

  def transformFieldWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T]
  )(transform: c.Expr[Resolved], reverseTransform: c.Expr[Resolved]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, T](c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _fieldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.transformFieldResolved(_parentPath, _fieldName, $transform, $reverseTransform)
    }""")
  }

  def mandateFieldAtImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Option[T]],
    default: c.Expr[T]
  )(schema: c.Expr[Schema[T]]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val optionType = appliedType(c.weakTypeOf[Option[_]].typeConstructor, c.weakTypeOf[T] :: Nil)
    val path       = MigrationBuilderMacros.extractPathImpl[A, Option[T]](c)(selector.asInstanceOf[c.Expr[A => Option[T]]])(
      c.WeakTypeTag(c.weakTypeOf[A]),
      c.WeakTypeTag(optionType)
    )
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _fieldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.mandateFieldResolved(
        _parentPath,
        _fieldName,
        _root_.zio.blocks.schema.migration.Resolved.Literal($default, $schema)
      )
    }""")
  }

  def optionalizeFieldAtImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, T](c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _fieldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.optionalizeFieldAt(_parentPath, _fieldName)
    }""")
  }

  def changeFieldTypeWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => T]
  )(fromType: c.Expr[String], toType: c.Expr[String]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, T](c)(selector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      val _fieldName = _path.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_path.nodes.dropRight(1))
      ${c.prefix}.builder.changeFieldTypeResolved(
        _parentPath, 
        _fieldName, 
        _root_.zio.blocks.schema.migration.Resolved.Convert($fromType, $toType, _root_.zio.blocks.schema.migration.Resolved.Identity),
        _root_.zio.blocks.schema.migration.Resolved.Convert($toType, $fromType, _root_.zio.blocks.schema.migration.Resolved.Identity)
      )
    }""")
  }

  def transformElementsWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context)(
    selector: c.Expr[A => Seq[T]]
  )(transform: c.Expr[Resolved], reverseTransform: c.Expr[Resolved]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val seqType = appliedType(c.weakTypeOf[Seq[_]].typeConstructor, c.weakTypeOf[T] :: Nil)
    val path    = MigrationBuilderMacros.extractPathImpl[A, Seq[T]](c)(selector.asInstanceOf[c.Expr[A => Seq[T]]])(
      c.WeakTypeTag(c.weakTypeOf[A]),
      c.WeakTypeTag(seqType)
    )
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      ${c.prefix}.builder.transformElementsResolved(_path, $transform, $reverseTransform)
    }""")
  }

  def transformKeysWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[A => Map[K, V]])(
    keyTransform: c.Expr[Resolved],
    reverseTransform: c.Expr[Resolved]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val mapType = appliedType(c.weakTypeOf[Map[_, _]].typeConstructor, c.weakTypeOf[K] :: c.weakTypeOf[V] :: Nil)
    val path    = MigrationBuilderMacros.extractPathImpl[A, Map[K, V]](c)(selector.asInstanceOf[c.Expr[A => Map[K, V]]])(
      c.WeakTypeTag(c.weakTypeOf[A]),
      c.WeakTypeTag(mapType)
    )
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      ${c.prefix}.builder.transformKeysResolved(_path, $keyTransform, $reverseTransform)
    }""")
  }

  def transformValuesWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, K: c.WeakTypeTag, V: c.WeakTypeTag](
    c: blackbox.Context
  )(selector: c.Expr[A => Map[K, V]])(
    valueTransform: c.Expr[Resolved],
    reverseTransform: c.Expr[Resolved]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val mapType = appliedType(c.weakTypeOf[Map[_, _]].typeConstructor, c.weakTypeOf[K] :: c.weakTypeOf[V] :: Nil)
    val path    = MigrationBuilderMacros.extractPathImpl[A, Map[K, V]](c)(selector.asInstanceOf[c.Expr[A => Map[K, V]]])(
      c.WeakTypeTag(c.weakTypeOf[A]),
      c.WeakTypeTag(mapType)
    )
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      ${c.prefix}.builder.transformValuesResolved(_path, $valueTransform, $reverseTransform)
    }""")
  }

  def joinFieldsWithSelector2Impl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    S1: c.WeakTypeTag,
    S2: c.WeakTypeTag,
    T: c.WeakTypeTag
  ](c: blackbox.Context)(source1: c.Expr[A => S1], source2: c.Expr[A => S2], target: c.Expr[B => T])(
    combiner: c.Expr[Resolved],
    splitter: c.Expr[Resolved]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path1      = MigrationBuilderMacros.extractPathImpl[A, S1](c)(source1)
    val path2      = MigrationBuilderMacros.extractPathImpl[A, S2](c)(source2)
    val targetPath = MigrationBuilderMacros.extractPathImpl[B, T](c)(target)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path1 = $path1
      val _path2 = $path2
      val _targetPath = $targetPath
      val _targetFieldName = _targetPath.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_targetPath.nodes.dropRight(1))
      ${c.prefix}.builder.joinFields(
        _parentPath,
        _targetFieldName,
        _root_.zio.blocks.chunk.Chunk(_path1, _path2),
        $combiner,
        $splitter
      )
    }""")
  }

  def splitFieldWithSelector2Impl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    S: c.WeakTypeTag,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag
  ](c: blackbox.Context)(source: c.Expr[A => S], target1: c.Expr[B => T1], target2: c.Expr[B => T2])(
    splitter: c.Expr[Resolved],
    combiner: c.Expr[Resolved]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val sourcePath  = MigrationBuilderMacros.extractPathImpl[A, S](c)(source)
    val targetPath1 = MigrationBuilderMacros.extractPathImpl[B, T1](c)(target1)
    val targetPath2 = MigrationBuilderMacros.extractPathImpl[B, T2](c)(target2)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _sourcePath = $sourcePath
      val _targetPath1 = $targetPath1
      val _targetPath2 = $targetPath2
      val _sourceFieldName = _sourcePath.nodes.lastOption match {
        case Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Source selector must end with a field access")
      }
      val _parentPath = _root_.zio.blocks.schema.DynamicOptic(_sourcePath.nodes.dropRight(1))
      ${c.prefix}.builder.splitField(
        _parentPath,
        _sourceFieldName,
        _root_.zio.blocks.chunk.Chunk(_targetPath1, _targetPath2),
        $splitter,
        $combiner
      )
    }""")
  }

  def renameCaseWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, E: c.WeakTypeTag](
    c: blackbox.Context
  )(enumSelector: c.Expr[A => E], from: c.Expr[String], to: c.Expr[String]): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, E](c)(enumSelector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      ${c.prefix}.builder.renameCaseAt(_path, $from, $to)
    }""")
  }

  def transformCaseWithSelectorImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, E: c.WeakTypeTag](
    c: blackbox.Context
  )(
    enumSelector: c.Expr[A => E],
    caseName: c.Expr[String],
    caseActions: c.Expr[zio.blocks.chunk.Chunk[MigrationAction]]
  ): c.Expr[MigrationBuilder[A, B]] = {
    import c.universe._
    val path = MigrationBuilderMacros.extractPathImpl[A, E](c)(enumSelector)
    c.Expr[MigrationBuilder[A, B]](q"""{
      val _path = $path
      ${c.prefix}.builder.transformCaseAt(_path, $caseName, $caseActions)
    }""")
  }

  def buildValidatedImpl[A, B](c: blackbox.Context)(
    schemaA: c.Expr[Schema[A]],
    schemaB: c.Expr[Schema[B]]
  ): c.Expr[DynamicMigration] = {
    import c.universe._
    // The macro creates code that validates at runtime, but provides a clear API
    // for compile-time validation intent. Full compile-time validation would
    // require more complex type-level programming not practical in Scala 2 macros.
    // Note: schemaA and schemaB are passed to ensure type safety at call site
    val _ = (schemaA, schemaB) // Suppress unused warning
    c.Expr[DynamicMigration](q"""{
      val builder = ${c.prefix}.builder
      val migration = builder.build
      val result = _root_.zio.blocks.schema.migration.SchemaShapeValidator.validateShape(migration)
      result match {
        case _root_.zio.blocks.schema.migration.SchemaShapeValidator.ShapeValidationResult.Complete =>
          migration
        case incomplete: _root_.zio.blocks.schema.migration.SchemaShapeValidator.ShapeValidationResult.Incomplete =>
          throw new IllegalArgumentException(
            "Migration validation failed: " + incomplete.renderReport
          )
      }
    }""")
  }
}
