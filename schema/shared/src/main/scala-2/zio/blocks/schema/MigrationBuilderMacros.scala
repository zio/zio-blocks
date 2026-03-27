package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait MigrationBuilderMacros[A, B] {
  import scala.language.implicitConversions
  implicit final def toMigrationBuilderSyntax(self: MigrationBuilder[A, B]): MigrationBuilderSyntax[A, B] =
    new MigrationBuilderSyntax(self)
}

final class MigrationBuilderSyntax[A, B](val self: MigrationBuilder[A, B]) extends AnyVal {
  def addField[T](path: B => T, default: SchemaExpr[_, _]): MigrationBuilder[A, B] = macro MigrationBuilderMacroImpls.addFieldImpl[A, B, T]
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

object MigrationBuilderMacroImpls {
  def extractOptic(c: blackbox.Context)(path: c.Tree): c.Tree = {
    import c.universe._

    def extractOpticNodes(t: c.Tree): List[c.Tree] = t match {
      case Function(_, body) => extractOpticNodes(body)
      case Select(parent, TermName(fieldName)) => extractOpticNodes(parent) :+ q"_root_.zio.blocks.schema.DynamicOptic.Node.Field($fieldName)"
      case Ident(_) => Nil
      case _ => c.abort(c.enclosingPosition, s"Unsupported path element: ${showRaw(t)}")
    }

    val nodes = extractOpticNodes(path)
    q"_root_.zio.blocks.schema.DynamicOptic(Vector(..$nodes))"
  }

  def addFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, default: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.addFieldCore(${extractOptic(c)(path)}, $default)"
  }

  def dropFieldImpl[A, B, T](c: blackbox.Context)(oldPath: c.Tree, defaultForReverse: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.dropFieldCore(${extractOptic(c)(oldPath)}, $defaultForReverse)"
  }

  def renameFieldImpl[A, B, T](c: blackbox.Context)(oldPath: c.Tree, newPath: c.Tree): c.Tree = {
    import c.universe._
    
    val fromNodeExpr = extractOptic(c)(oldPath)
    val toNodeExpr = extractOptic(c)(newPath)

    q"""
      val toOptic = $toNodeExpr
      val toField = toOptic.nodes.last match {
        case _root_.zio.blocks.schema.DynamicOptic.Node.Field(name) => name
        case _ => throw new IllegalArgumentException("renameField target must end in a Field node")
      }
      ${c.prefix}.self.renameFieldCore($fromNodeExpr, toField)
    """
  }

  def transformFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.transformFieldCore(${extractOptic(c)(path)}, $transform)"
  }

  def mandateFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, default: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.mandateFieldCore(${extractOptic(c)(path)}, $default)"
  }

  def optionalizeFieldImpl[A, B, T](c: blackbox.Context)(path: c.Tree, defaultForReverse: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.optionalizeFieldCore(${extractOptic(c)(path)}, $defaultForReverse)"
  }

  def changeFieldTypeImpl[A, B, T](c: blackbox.Context)(path: c.Tree, converter: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.changeFieldTypeCore(${extractOptic(c)(path)}, $converter)"
  }

  def transformCaseImpl[A, B, SumA, CaseA: c.WeakTypeTag, SumB, CaseB](c: blackbox.Context)(caseMigration: c.Tree)(schemaCaseA: c.Tree, schemaCaseB: c.Tree): c.Tree = {
    import c.universe._
    val caseName = weakTypeOf[CaseA].typeSymbol.name.decodedName.toString
    q"""
      val builtMigration = $caseMigration(_root_.zio.blocks.schema.MigrationBuilder.make($schemaCaseA, $schemaCaseB))
      ${c.prefix}.self.transformCaseCore($caseName, builtMigration)
    """
  }

  def transformElementsImpl[A, B, T](c: blackbox.Context)(at: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.transformElementsCore(${extractOptic(c)(at)}, $transform)"
  }

  def transformKeysImpl[A, B, K, V](c: blackbox.Context)(at: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.transformKeysCore(${extractOptic(c)(at)}, $transform)"
  }

  def transformValuesImpl[A, B, K, V](c: blackbox.Context)(at: c.Tree, transform: c.Tree): c.Tree = {
    import c.universe._
    q"${c.prefix}.self.transformValuesCore(${extractOptic(c)(at)}, $transform)"
  }

  def buildImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Tree = {
    import c.universe._
    
    val sourceTpe = weakTypeOf[A]
    val targetTpe = weakTypeOf[B]

    val sourceFields = sourceTpe.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString }.toSet
    val targetFields = targetTpe.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m.name.decodedName.toString }.toSet

    val missingFields = targetFields -- sourceFields

    if (missingFields.nonEmpty) {
      q"""
        val builder = ${c.prefix}.self
        val requiredAdds = Set(..$missingFields)
        val addedFields = builder.actions.collect {
          case _root_.zio.blocks.schema.MigrationAction.AddField(optic, _) => 
            optic.nodes.last match {
              case _root_.zio.blocks.schema.DynamicOptic.Node.Field(name) => name
              case _ => ""
            }
          case _root_.zio.blocks.schema.MigrationAction.Rename(_, _, to) => to
        }.toSet
        
        val stillMissing = requiredAdds -- addedFields
        
        if (stillMissing.nonEmpty) {
          throw new IllegalArgumentException("Field(s) [" + stillMissing.mkString(", ") + "] in target schema are missing from source and have no default value provided.")
        }
        
        builder.buildPartial
      """
    } else {
      q"${c.prefix}.self.buildPartial"
    }
  }
}
