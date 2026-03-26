package zio.blocks.schema

import scala.language.experimental.macros

trait MigrationBuilderMacros[A, B] { self: MigrationBuilder[A, B] =>
  def addField[T](targetPath: B => T, default: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.addFieldImpl[A, B, T]
  def dropField[T](oldPath: A => T, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.dropFieldImpl[A, B, T]
  def renameField[T](oldPath: A => T, newPath: B => T): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.renameFieldImpl[A, B, T]
  def transformField[T](path: B => T, transform: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.transformFieldImpl[A, B, T]
  def mandateField[T](path: B => T, default: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.mandateFieldImpl[A, B, T]
  def optionalizeField[T](path: B => T, defaultForReverse: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.optionalizeFieldImpl[A, B, T]
  def changeFieldType[T](path: B => T, converter: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.changeFieldTypeImpl[A, B, T]

  def renameCase[SumA, SumB](from: String, to: String): MigrationBuilder[A, B] = self.renameCaseCore(from, to)

  def transformCase[SumA, CaseA, SumB, CaseB](
    caseMigration: MigrationBuilder[CaseA, CaseB] => MigrationBuilder[CaseA, CaseB]
  )(implicit schemaCaseA: Schema[CaseA], schemaCaseB: Schema[CaseB]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.transformCaseImpl[A, B, SumA, CaseA, SumB, CaseB]

  def transformElements[T](at: A => Iterable[T], transform: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.transformElementsImpl[A, B, T]
  def transformKeys[K, V](at: A => Map[K, V], transform: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.transformKeysImpl[A, B, K, V]
  def transformValues[K, V](at: A => Map[K, V], transform: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.transformValuesImpl[A, B, K, V]

  def build: Migration[A, B] = macro MigrationBuilderMacroImpls.buildImpl[A, B]
}

private[schema] object MigrationBuilderMacroImpls {
  import scala.reflect.macros.blackbox

  def extractOpticNodes(c: blackbox.Context)(path: c.Tree): List[c.Tree] = {
    import c.universe._
    def toPathBody(tree: c.Tree): c.Tree = tree match {
      case q"($_) => $body" => body
      case Block(_, expr) => toPathBody(expr)
      case _ => c.abort(c.enclosingPosition, s"Expected a lambda, got $tree")
    }

    def collectNodes(tree: c.Tree): List[c.Tree] = tree match {
      case q"$parent.$name" => collectNodes(parent) :+ tree
      case Ident(_) => Nil
      case _ => c.abort(c.enclosingPosition, s"Unsupported path element: $tree")
    }
    
    collectNodes(toPathBody(path))
  }

  def extractOptic(c: blackbox.Context)(path: c.Tree): c.Tree = {
    import c.universe._
    val nodes = extractOpticNodes(c)(path)
    val nodeExprs = nodes.map {
      case q"$_.$name" => q"_root_.zio.blocks.schema.DynamicOptic.Node.Field(${name.decodedName.toString})"
      case _ => c.abort(c.enclosingPosition, "unsupported")
    }
    q"_root_.zio.blocks.schema.DynamicOptic(Vector(..$nodeExprs))"
  }

  def extractParentOptic(c: blackbox.Context)(path: c.Tree): c.Tree = {
    import c.universe._
    val nodes = extractOpticNodes(c)(path)
    if (nodes.isEmpty) c.abort(c.enclosingPosition, "Path empty")
    val parentNodes = nodes.init
    val nodeExprs = parentNodes.map {
      case q"$_.$name" => q"_root_.zio.blocks.schema.DynamicOptic.Node.Field(${name.decodedName.toString})"
      case _ => c.abort(c.enclosingPosition, "unsupported")
    }
    q"_root_.zio.blocks.schema.DynamicOptic(Vector(..$nodeExprs))"
  }

  def extractLastNodeName(c: blackbox.Context)(path: c.Tree): c.Tree = {
    import c.universe._
    val nodes = extractOpticNodes(c)(path)
    if (nodes.isEmpty) c.abort(c.enclosingPosition, "Path empty")
    nodes.last match {
      case q"$_.$name" => q"${name.decodedName.toString}"
      case _ => c.abort(c.enclosingPosition, "Expected a field select")
    }
  }

  def addFieldImpl[A, B, T](c: blackbox.Context)(targetPath: c.Tree, default: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.addFieldCore(${extractOptic(c)(targetPath)}, $default)"
  }

  def dropFieldImpl[A, B, T](c: blackbox.Context)(oldPath: c.Tree, defaultForReverse: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.dropFieldCore(${extractOptic(c)(oldPath)}, $defaultForReverse)"
  }

  def renameFieldImpl[A, B, T](c: blackbox.Context)(oldPath: c.Tree, newPath: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.renameFieldCore(${extractParentOptic(c)(oldPath)}, ${extractLastNodeName(c)(oldPath)}, ${extractLastNodeName(c)(newPath)})"
  }

  def transformFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.transformFieldCore(${extractOptic(c)(path)}, $transform)"
  }

  def mandateFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, default: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.mandateFieldCore(${extractOptic(c)(path)}, $default)"
  }

  def optionalizeFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, defaultForReverse: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.optionalizeFieldCore(${extractOptic(c)(path)}, $defaultForReverse)"
  }

  def changeFieldTypeImpl[A, B, T](c: blackbox.Context)(path: c.Tree, converter: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.changeFieldTypeCore(${extractOptic(c)(path)}, $converter)"
  }

  def transformCaseImpl[A, B, SumA, CaseA: c.WeakTypeTag, SumB, CaseB](c: blackbox.Context)(caseMigration: c.Tree)(schemaCaseA: c.Tree, schemaCaseB: c.Tree): c.Tree = {
    import c.universe._
    val caseName = weakTypeOf[CaseA].typeSymbol.name.decodedName.toString
    q"""
      val builtMigration = $caseMigration(_root_.zio.blocks.schema.MigrationBuilder.make($schemaCaseA, $schemaCaseB))
      ${c.prefix}.transformCaseCore($caseName, builtMigration)
    """
  }

  def transformElementsImpl[A, B, T](c: blackbox.Context)(at: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.transformElementsCore(${extractOptic(c)(at)}, $transform)"
  }

  def transformKeysImpl[A, B, K, V](c: blackbox.Context)(at: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.transformKeysCore(${extractOptic(c)(at)}, $transform)"
  }

  def transformValuesImpl[A, B, K, V](c: blackbox.Context)(at: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.transformValuesCore(${extractOptic(c)(at)}, $transform)"
  }

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._
    
    val targetType = weakTypeOf[B]
    val sourceType = weakTypeOf[A]
    
    val targetFields = targetType.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString
    }.toSet
    val sourceFields = sourceType.decls.collect {
      case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString
    }.toSet

    def extractProvidedFields(tree: c.Tree): Set[String] = tree match {
      case q"$qual.addField[..$_]($targetPath, $_)" =>
        val nodes = extractOpticNodes(c)(targetPath)
        val name = nodes.lastOption.collect { case q"$_.$n" => n.decodedName.toString }.toSet
        extractProvidedFields(qual) ++ name
      case q"$qual.renameField[..$_]($_, $newPath)" =>
        val nodes = extractOpticNodes(c)(newPath)
        val name = nodes.lastOption.collect { case q"$_.$n" => n.decodedName.toString }.toSet
        extractProvidedFields(qual) ++ name
      case q"$qual.transformField[..$_]($targetPath, $_)" =>
        val nodes = extractOpticNodes(c)(targetPath)
        val name = nodes.lastOption.collect { case q"$_.$n" => n.decodedName.toString }.toSet
        extractProvidedFields(qual) ++ name
      case q"$qual.$method[..$_](...$args)" =>
        extractProvidedFields(qual)
      case q"$qual.$method" =>
        extractProvidedFields(qual)
      case _ => Set.empty
    }

    val handledFields = extractProvidedFields(c.prefix.tree)
    val uncoveredFields = targetFields -- sourceFields -- handledFields

    if (uncoveredFields.nonEmpty) {
      c.abort(c.enclosingPosition, s"Field(s) [${uncoveredFields.mkString(", ")}] in target schema are missing from source and have no default value provided.")
    }

    q"${c.prefix}.buildPartial"
  }
}
