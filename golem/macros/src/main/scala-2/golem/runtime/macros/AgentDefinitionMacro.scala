package golem.runtime.macros

import golem.data.GolemSchema
import golem.runtime.AgentMetadata

import scala.reflect.macros.blackbox

object AgentDefinitionMacro {
  def generate[T]: AgentMetadata = macro AgentDefinitionMacroImpl.impl[T]
}

object AgentDefinitionMacroImpl {
  private val schemaHint: String =
    "\nHint: GolemSchema is derived from zio.blocks.schema.Schema.\n" +
      "Define or import an implicit Schema[T] for your type.\n" +
      "Scala 3: `final case class T(...) derives zio.blocks.schema.Schema` (or `given Schema[T] = Schema.derived`).\n" +
      "Scala 2: `implicit val schema: zio.blocks.schema.Schema[T] = zio.blocks.schema.Schema.derived`.\n"
  def impl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[AgentMetadata] = {
    import c.universe._

    val tpe        = weakTypeOf[T]
    val typeSymbol = tpe.typeSymbol

    if (!typeSymbol.isClass || !typeSymbol.asClass.isTrait) {
      c.abort(c.enclosingPosition, s"@agent target must be a trait, found: ${typeSymbol.fullName}")
    }

    val agentDefinitionType                           = typeOf[golem.runtime.annotations.agentDefinition]
    def defaultTypeNameFromTrait(sym: Symbol): String =
      sym.name.decodedName.toString

    val rawTypeName: String =
      typeSymbol.annotations.collectFirst {
        case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
          ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
      }
        .getOrElse("")

    val agentTypeName: String = {
      val trimmed  = rawTypeName.trim
      val resolved =
        if (trimmed.nonEmpty) trimmed
        else {
          val hasAnn = typeSymbol.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
          if (!hasAnn)
            c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${typeSymbol.fullName}")
          defaultTypeNameFromTrait(typeSymbol)
        }
      validateTypeName(resolved)
    }

    val descriptionType = typeOf[golem.runtime.annotations.description]
    val promptType      = typeOf[golem.runtime.annotations.prompt]

    val traitDescription = annotationString(c)(typeSymbol, descriptionType)
    val traitMode        =
      agentDefinitionModeWireValueExpr(c)(typeSymbol, agentDefinitionType)

    val methods = tpe.decls.collect {
      case method: MethodSymbol if method.isAbstract && method.isMethod && method.name.toString != "new" =>
        methodMetadata(c)(method, descriptionType, promptType)
    }.toList

    val constructorSchema = inferConstructorSchema(c)(tpe)

    val typeName      = agentTypeName
    val traitDescExpr = optionalStringExpr(c)(traitDescription)
    val traitModeExpr = optionalTreeExpr(c)(traitMode)

    c.Expr[AgentMetadata](q"""
      _root_.golem.runtime.AgentMetadata(
        name = $typeName,
        description = $traitDescExpr,
        mode = $traitModeExpr,
        methods = List(..$methods),
        constructor = $constructorSchema
      )
    """)
  }

  private def validateTypeName(value: String): String =
    value

  private def methodMetadata(c: blackbox.Context)(
    method: c.universe.MethodSymbol,
    descriptionType: c.universe.Type,
    promptType: c.universe.Type
  ): c.Tree = {
    import c.universe._

    val methodName   = method.name.toString
    val descExpr     = optionalStringExpr(c)(annotationString(c)(method, descriptionType))
    val promptExpr   = optionalStringExpr(c)(annotationString(c)(method, promptType))
    val inputSchema  = methodInputSchema(c)(method)
    val outputSchema = methodOutputSchema(c)(method)

    q"""
      _root_.golem.runtime.MethodMetadata(
        name = $methodName,
        description = $descExpr,
        prompt = $promptExpr,
        mode = _root_.scala.None,
        input = $inputSchema,
        output = $outputSchema
      )
    """
  }

  private def methodInputSchema(c: blackbox.Context)(method: c.universe.MethodSymbol): c.Tree = {
    import c.universe._

    val params = method.paramLists.flatten.collect {
      case param if param.isTerm => (param.name.toString, param.typeSignature)
    }

    if (params.isEmpty) {
      q"_root_.golem.data.StructuredSchema.Tuple(Nil)"
    } else if (params.length == 1) {
      val (_, paramType) = params.head
      structuredSchemaExpr(c)(paramType)
    } else {
      val elements = params.map { case (name, tpe) =>
        val schemaExpr = elementSchemaExpr(c)(name, tpe)
        q"_root_.golem.data.NamedElementSchema($name, $schemaExpr)"
      }
      q"_root_.golem.data.StructuredSchema.Tuple(List(..$elements))"
    }
  }

  private def methodOutputSchema(c: blackbox.Context)(method: c.universe.MethodSymbol): c.Tree = {
    val outputType = unwrapAsyncType(c)(method.returnType)
    structuredSchemaExpr(c)(outputType)
  }

  private def structuredSchemaExpr(c: blackbox.Context)(tpe: c.universe.Type): c.Tree = {
    import c.universe._

    val golemSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, tpe)
    val schemaInstance  = c.inferImplicitValue(golemSchemaType)

    if (schemaInstance.isEmpty) {
      c.abort(c.enclosingPosition, s"No implicit GolemSchema available for type $tpe.$schemaHint")
    }

    q"$schemaInstance.schema"
  }

  private def elementSchemaExpr(c: blackbox.Context)(paramName: String, tpe: c.universe.Type): c.Tree = {
    import c.universe._

    val golemSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, tpe)
    val schemaInstance  = c.inferImplicitValue(golemSchemaType)

    if (schemaInstance.isEmpty) {
      c.abort(c.enclosingPosition, s"No implicit GolemSchema available for type $tpe.$schemaHint")
    }

    q"""
      $schemaInstance.schema match {
        case _root_.golem.data.StructuredSchema.Tuple(elements) if elements.length == 1 =>
          elements.head.schema
        case _root_.golem.data.StructuredSchema.Tuple(_) =>
          throw new IllegalArgumentException("Parameter " + $paramName + " expands to multiple elements; wrap it in a case class")
        case _root_.golem.data.StructuredSchema.Multimodal(_) =>
          throw new IllegalArgumentException("Parameter " + $paramName + " is multimodal; use a dedicated multimodal wrapper")
      }
    """
  }

  private def inferConstructorSchema(c: blackbox.Context)(tpe: c.universe.Type): c.Tree = {
    import c.universe._

    val baseSymOpt = tpe.baseClasses.find(_.fullName == "golem.BaseAgent")
    val baseArgs   = baseSymOpt.toList.flatMap(sym => tpe.baseType(sym).typeArgs)
    val inputTpe   = baseArgs.headOption.getOrElse(typeOf[Unit]).dealias
    if (inputTpe =:= typeOf[Unit]) q"_root_.golem.data.StructuredSchema.Tuple(Nil)"
    else structuredSchemaExpr(c)(inputTpe)
  }

  private def annotationString(
    c: blackbox.Context
  )(symbol: c.universe.Symbol, annType: c.universe.Type): Option[String] = {
    import c.universe._

    symbol.annotations.collectFirst {
      case ann if ann.tree.tpe =:= annType =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(value: String)) =>
          value
        }
    }.flatten
  }

  private def agentDefinitionModeWireValueExpr(
    c: blackbox.Context
  )(symbol: c.universe.Symbol, annType: c.universe.Type): Option[c.Tree] = {
    import c.universe._
    symbol.annotations.collectFirst {
      case ann if ann.tree.tpe =:= annType =>
        // agentDefinition(typeName: String = "", mode: DurabilityMode = null)
        ann.tree.children.tail.drop(1).headOption.map {
          case Literal(Constant(value: String)) =>
            // (Legacy) allow stringly-typed values.
            val v = value.trim.toLowerCase
            if (v == "durable") EmptyTree else Literal(Constant(v))
          case Literal(Constant(null)) =>
            EmptyTree
          case Select(_, TermName("Durable")) =>
            // Treat default Durable as unset (omit defaults in metadata)
            EmptyTree
          case Ident(TermName("Durable")) =>
            EmptyTree
          case other =>
            q"$other.wireValue()"
        }
    }.flatten.filter(_ != EmptyTree)
  }

  private def optionalStringExpr(c: blackbox.Context)(value: Option[String]): c.Tree = {
    import c.universe._
    value match {
      case Some(v) => q"Some($v)"
      case None    => q"None"
    }
  }

  private def optionalTreeExpr(c: blackbox.Context)(value: Option[c.Tree]): c.Tree = {
    import c.universe._
    value match {
      case Some(v) => q"Some($v)"
      case None    => q"None"
    }
  }

  private def unwrapAsyncType(c: blackbox.Context)(tpe: c.universe.Type): c.universe.Type = {
    import c.universe._

    val futureSymbol = typeOf[scala.concurrent.Future[_]].typeSymbol

    tpe match {
      case TypeRef(_, sym, args) if sym == futureSymbol && args.nonEmpty =>
        args.head
      case TypeRef(_, sym, args) if sym.fullName == "scala.scalajs.js.Promise" && args.nonEmpty =>
        args.head
      case _ =>
        tpe
    }
  }
}
