package golem.config

import scala.reflect.macros.blackbox

object ConfigSchemaDerived {
  def derived[T]: ConfigSchema[T] = macro ConfigSchemaDerivedMacro.derivedImpl[T]
}

object ConfigSchemaDerivedMacro {
  def derivedImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ConfigSchema[T]] = {
    import c.universe._

    val tpe       = weakTypeOf[T]
    val tpeSymbol = tpe.typeSymbol

    if (!tpeSymbol.isClass || !tpeSymbol.asClass.isCaseClass)
      c.abort(c.enclosingPosition, s"ConfigSchemaDerived.derived requires a case class, found: ${tpeSymbol.fullName}")

    val primaryCtor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"No primary constructor found for ${tpeSymbol.fullName}"))

    val fields = primaryCtor.paramLists.flatten

    val fieldDescriptions = fields.map { param =>
      val fieldName = param.name.decodedName.toString
      val fieldType = param.typeSignatureIn(tpe)

      val configSchemaType = appliedType(typeOf[ConfigSchema[_]].typeConstructor, fieldType)
      val configSchemaInst = c.inferImplicitValue(configSchemaType, silent = true)

      if (configSchemaInst != EmptyTree) {
        q"$configSchemaInst.describe(path :+ $fieldName)"
      } else {
        val golemSchemaType = appliedType(typeOf[golem.data.GolemSchema[_]].typeConstructor, fieldType)
        val golemSchemaInst = c.inferImplicitValue(golemSchemaType, silent = true)

        if (golemSchemaInst != EmptyTree) {
          q"_root_.scala.List(_root_.golem.config.AgentConfigDeclaration(_root_.golem.config.AgentConfigSource.Local, path :+ $fieldName, $golemSchemaInst.elementSchema))"
        } else {
          c.abort(c.enclosingPosition,
            s"No ConfigSchema or GolemSchema found for field '$fieldName' of type ${fieldType} in ${tpeSymbol.fullName}")
        }
      }
    }

    val orderedBody = fields.size match {
      case 0 => q"_root_.scala.Nil"
      case _ =>
        fieldDescriptions.reduceLeft((a, b) => q"$a ::: $b")
    }

    c.Expr[ConfigSchema[T]](
      q"""
      new _root_.golem.config.ConfigSchema[$tpe] {
        def describe(path: _root_.scala.List[_root_.java.lang.String]): _root_.scala.List[_root_.golem.config.AgentConfigDeclaration] =
          $orderedBody
      }
      """
    )
  }
}
