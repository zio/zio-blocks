package golem.runtime.macros

import scala.reflect.macros.blackbox

/**
 * Scala 2 macro for generating typed RPC config field accessors.
 *
 * Mirrors the Scala 3 `RpcConfigFieldsDerived` behavior:
 *  - Inspects case class fields via reflection
 *  - Secret fields are omitted
 *  - Nested configs (types with implicit `ConfigBuilder`) get recursive sub-fields
 *  - Leaf local fields (types with implicit `GolemSchema`) become `RpcConfig.Field`
 *
 * Usage:
 * {{{
 * import golem.runtime.macros.RpcConfigFieldsMacro
 *
 * object MyConfig {
 *   val rpcFields: RpcFields[MyConfig] = RpcConfigFieldsMacro.fields[MyConfig]
 * }
 * }}}
 */
object RpcConfigFieldsMacro {
  def fields[T]: _root_.golem.config.RpcFields[T] = macro RpcConfigFieldsMacroImpl.fieldsImpl[T]
}

object RpcConfigFieldsMacroImpl {

  def fieldsImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[_root_.golem.config.RpcFields[T]] = {
    import c.universe._

    val tpe = weakTypeOf[T]

    if (!tpe.typeSymbol.isClass || !tpe.typeSymbol.asClass.isCaseClass)
      c.abort(c.enclosingPosition, s"RpcConfigFieldsMacro.fields requires a case class, found: $tpe")

    val tree = buildFieldsTree(c)(tpe, tpe, q"_root_.scala.Nil")
    c.Expr[_root_.golem.config.RpcFields[T]](tree)
  }

  private def buildFieldsTree(c: blackbox.Context)(
    rootType: c.universe.Type,
    currentType: c.universe.Type,
    prefixExpr: c.Tree
  ): c.Tree = {
    import c.universe._

    val secretTypeConstructor = typeOf[_root_.golem.config.Secret[_]].typeConstructor
    val configBuilderTC       = typeOf[_root_.golem.config.ConfigBuilder[_]].typeConstructor
    val golemSchemaTC          = typeOf[_root_.golem.data.GolemSchema[_]].typeConstructor

    val fields = currentType.decls.sorted.collect {
      case m: MethodSymbol if m.isCaseAccessor => m
    }

    val builderName = TermName(c.freshName("builder"))

    val addStatements = fields.flatMap { field =>
      val fieldName    = field.name.decodedName.toString
      val fieldType    = field.returnType.dealias
      val fieldNameLit = Literal(Constant(fieldName))

      // Check if it's a Secret[_]
      val isSecret = fieldType.typeConstructor =:= secretTypeConstructor

      if (isSecret) {
        // Skip secret fields entirely
        None
      } else {
        // Check if it's a nested config (has ConfigBuilder)
        val configBuilderType = appliedType(configBuilderTC, fieldType)
        val hasConfigBuilder  = c.inferImplicitValue(configBuilderType, silent = true).nonEmpty

        if (hasConfigBuilder) {
          // Nested config: recurse
          val pathExpr     = q"$prefixExpr :+ $fieldNameLit"
          val nestedTree   = buildFieldsTree(c)(rootType, fieldType, pathExpr)
          Some(q"$builderName.addNested($fieldNameLit, $nestedTree)")
        } else {
          // Check if it has GolemSchema (leaf field)
          val golemSchemaType = appliedType(golemSchemaTC, fieldType)
          val gsInstance      = c.inferImplicitValue(golemSchemaType, silent = true)

          if (gsInstance.nonEmpty) {
            val pathExpr = q"$prefixExpr :+ $fieldNameLit"
            Some(q"$builderName.addLeaf[$fieldType]($fieldNameLit, $pathExpr, $gsInstance)")
          } else {
            // No GolemSchema — skip
            None
          }
        }
      }
    }

    q"""
      {
        val $builderName = _root_.golem.config.RpcFields.builder[$rootType]
        ..$addStatements
        $builderName.build
      }
    """
  }
}
