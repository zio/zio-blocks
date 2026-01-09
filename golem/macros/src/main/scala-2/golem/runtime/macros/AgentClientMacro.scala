package golem.runtime.macros

import golem.data.GolemSchema
import golem.runtime.plan.AgentClientPlan
// Macro annotations live in a separate module; do not depend on them here.

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object AgentClientMacro {
  def plan[Trait]: AgentClientPlan[Trait, _] = macro AgentClientMacroImpl.planImpl[Trait]
}

object AgentClientMacroImpl {
  def planImpl[Trait: c.WeakTypeTag](c: blackbox.Context): c.Expr[AgentClientPlan[Trait, _]] = {
    import c.universe._

    val traitType   = weakTypeOf[Trait]
    val traitSymbol = traitType.typeSymbol

    if (!traitSymbol.isClass || !traitSymbol.asClass.isTrait) {
      c.abort(c.enclosingPosition, s"Agent client target must be a trait, found: ${traitSymbol.fullName}")
    }

    val (constructorType, constructorPlanExpr) = buildConstructorPlan(c)(traitType)
    val methodPlans                            = buildMethodPlans(c)(traitType)
    val traitName                              = agentTypeNameOrDefault(c)(traitSymbol)

    c.Expr[AgentClientPlan[Trait, _]](q"""
      _root_.golem.runtime.plan.AgentClientPlan[$traitType, $constructorType](
        traitClassName = ${Literal(Constant(traitSymbol.fullName))},
        traitName = $traitName,
        constructor = $constructorPlanExpr.asInstanceOf[_root_.golem.runtime.plan.ConstructorPlan[$constructorType]],
        methods = List(..$methodPlans)
      )
    """)
  }

  private def buildConstructorPlan(c: blackbox.Context)(traitType: c.universe.Type): (c.universe.Type, c.Tree) = {
    import c.universe._

    val inputType  = agentInputType(c)(traitType)
    val accessMode = if (inputType =:= typeOf[Unit]) ParamAccessMode.NoArgs else ParamAccessMode.SingleArg
    val params     = if (accessMode == ParamAccessMode.SingleArg) List((TermName("args"), inputType)) else Nil

    val schemaExpr = accessMode match {
      case ParamAccessMode.MultiArgs =>
        multiParamSchemaExpr(c)("new", params)
      case _ =>
        val golemSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, inputType)
        val schemaInstance  = c.inferImplicitValue(golemSchemaType)
        if (schemaInstance.isEmpty) {
          c.abort(c.enclosingPosition, s"Unable to summon GolemSchema for constructor input with type $inputType")
        }
        schemaInstance
    }

    val planExpr = q"_root_.golem.runtime.plan.ConstructorPlan[$inputType]($schemaExpr)"
    (inputType, planExpr)
  }

  private def agentTypeNameOrDefault(c: blackbox.Context)(symbol: c.universe.Symbol): String = {
    import c.universe._
    def defaultTypeNameFromTrait(sym: Symbol): String =
      kebabCase(sym.name.decodedName.toString)

    def extractTypeName(args: List[Tree]): Option[String] =
      // Keep this simple and resilient across Scala 2 minor versions:
      // `@agentDefinition(typeName = "...")` always contains exactly one String literal (the type name).
      args.collectFirst { case Literal(Constant(s: String)) => s }

    val annOpt =
      symbol.annotations.collectFirst {
        case ann
            if ann.tree.tpe != null && ann.tree.tpe.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
          ann
      }

    annOpt match {
      case None =>
        c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${symbol.fullName}")
      case Some(ann) =>
        extractTypeName(ann.tree.children.tail) match {
          case Some(s) if s.trim.nonEmpty => s
          case _                          => defaultTypeNameFromTrait(symbol)
        }
    }
  }

  private def agentInputType(c: blackbox.Context)(traitType: c.universe.Type): c.universe.Type = {
    import c.universe._
    val member = traitType.member(TypeName("AgentInput"))
    if (member == NoSymbol) typeOf[Unit]
    else {
      val sig = member.typeSignatureIn(traitType)
      sig match {
        case TypeBounds(_, hi) => hi.dealias
        case other             => other.dealias
      }
    }
  }

  private def buildMethodPlans(c: blackbox.Context)(
    traitType: c.universe.Type
  ): List[c.Tree] = {
    import c.universe._

    traitType.decls.collect {
      case method: MethodSymbol if method.isAbstract && method.isMethod && method.name.toString != "new" =>
        buildMethodPlan(c)(traitType, method)
    }.toList
  }

  private def buildMethodPlan(
    c: blackbox.Context
  )(traitType: c.universe.Type, method: c.universe.MethodSymbol): c.Tree = {
    import c.universe._

    val methodName    = method.name.toString
    val agentTypeName = agentTypeNameOrDefault(c)(traitType.typeSymbol)
    val functionName  = s"$agentTypeName.{${kebabCase(methodName)}}"
    val metadataExpr  = methodMetadata(c)(method)

    val params                       = extractParameters(c)(method)
    val accessMode                   = paramAccessMode(params)
    val inputType                    = inputTypeFor(c)(accessMode, params)
    val (invocationKind, outputType) = methodInvocationInfo(c)(method)

    val invocationExpr = invocationKind match {
      case InvocationKind.Awaitable     => q"_root_.golem.runtime.plan.ClientInvocation.Awaitable"
      case InvocationKind.FireAndForget => q"_root_.golem.runtime.plan.ClientInvocation.FireAndForget"
    }

    val inputSchemaExpr = accessMode match {
      case ParamAccessMode.MultiArgs =>
        multiParamSchemaExpr(c)(methodName, params)
      case _ =>
        val golemSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, inputType)
        val schemaInstance  = c.inferImplicitValue(golemSchemaType)
        if (schemaInstance.isEmpty) {
          c.abort(
            c.enclosingPosition,
            s"Unable to summon GolemSchema for input of method $methodName with type $inputType"
          )
        }
        schemaInstance
    }

    val golemOutputSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, outputType)
    val outputSchemaInstance  = c.inferImplicitValue(golemOutputSchemaType)
    if (outputSchemaInstance.isEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Unable to summon GolemSchema for output of method $methodName with type $outputType"
      )
    }

    q"""
      _root_.golem.runtime.plan.ClientMethodPlan[$traitType, $inputType, $outputType](
        metadata = $metadataExpr,
        functionName = $functionName,
        inputSchema = $inputSchemaExpr,
        outputSchema = $outputSchemaInstance,
        invocation = $invocationExpr
      )
    """
  }

  private def methodMetadata(c: blackbox.Context)(method: c.universe.MethodSymbol): c.Tree = {
    import c.universe._

    val methodName   = method.name.toString
    val inputSchema  = methodInputSchema(c)(method)
    val outputSchema = methodOutputSchema(c)(method)

    q"""
      _root_.golem.runtime.MethodMetadata(
        name = $methodName,
        description = None,
        prompt = None,
        mode = None,
        input = $inputSchema,
        output = $outputSchema
      )
    """
  }

  private def methodInputSchema(c: blackbox.Context)(method: c.universe.MethodSymbol): c.Tree = {
    import c.universe._

    val params = extractParameters(c)(method)

    if (params.isEmpty) {
      q"_root_.golem.data.StructuredSchema.Tuple(Nil)"
    } else if (params.length == 1) {
      val (_, paramType) = params.head
      structuredSchemaExpr(c)(paramType)
    } else {
      val elements = params.map { case (name, tpe) =>
        val schemaExpr = elementSchemaExpr(c)(name.toString, tpe)
        q"_root_.golem.data.NamedElementSchema(${name.toString}, $schemaExpr)"
      }
      q"_root_.golem.data.StructuredSchema.Tuple(List(..$elements))"
    }
  }

  private def elementSchemaExpr(c: blackbox.Context)(paramName: String, tpe: c.universe.Type): c.Tree = {
    import c.universe._

    val golemSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, tpe)
    val schemaInstance  = c.inferImplicitValue(golemSchemaType)

    if (schemaInstance.isEmpty) {
      c.abort(c.enclosingPosition, s"No implicit GolemSchema available for type $tpe")
    }

    q"""
      $schemaInstance.schema match {
        case _root_.golem.data.StructuredSchema.Tuple(elements) if elements.length == 1 =>
          elements.head.schema
        case _root_.golem.data.StructuredSchema.Tuple(_) =>
          throw new IllegalArgumentException("Parameter " + $paramName + " expands to multiple elements; wrap it in a case class")
        case _root_.golem.data.StructuredSchema.Multimodal(_) =>
          throw new IllegalArgumentException("Parameter " + $paramName + " is multimodal; use a single multimodal wrapper parameter")
      }
    """
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
      c.abort(c.enclosingPosition, s"No implicit GolemSchema available for type $tpe")
    }

    q"$schemaInstance.schema"
  }

  private def unwrapAsyncType(c: blackbox.Context)(tpe: c.universe.Type): c.universe.Type = {
    import c.universe._

    val futureSymbol = typeOf[scala.concurrent.Future[_]].typeSymbol

    tpe match {
      case TypeRef(_, sym, args) if sym == futureSymbol && args.nonEmpty =>
        args.head
      case _ =>
        tpe
    }
  }

  private def extractParameters(c: blackbox.Context)(
    method: c.universe.MethodSymbol
  ): List[(c.universe.TermName, c.universe.Type)] =
    method.paramLists.flatten.collect {
      case param if param.isTerm => (param.name.toTermName, param.typeSignature)
    }

  private def paramAccessMode(params: List[(_, _)]): ParamAccessMode = params match {
    case Nil      => ParamAccessMode.NoArgs
    case _ :: Nil => ParamAccessMode.SingleArg
    case _        => ParamAccessMode.MultiArgs
  }

  private def inputTypeFor(
    c: blackbox.Context
  )(accessMode: ParamAccessMode, params: List[(c.universe.TermName, c.universe.Type)]): c.universe.Type = {
    import c.universe._
    accessMode match {
      case ParamAccessMode.NoArgs    => typeOf[Unit]
      case ParamAccessMode.SingleArg => params.head._2
      case ParamAccessMode.MultiArgs => typeOf[Vector[Any]]
    }
  }

  private def methodInvocationInfo(
    c: blackbox.Context
  )(method: c.universe.MethodSymbol): (InvocationKind, c.universe.Type) = {
    import c.universe._

    val returnType   = method.returnType
    val futureSymbol = typeOf[scala.concurrent.Future[_]].typeSymbol

    returnType match {
      case TypeRef(_, sym, args) if sym == futureSymbol && args.nonEmpty =>
        (InvocationKind.Awaitable, args.head)
      case _ if returnType =:= typeOf[Unit] =>
        (InvocationKind.FireAndForget, typeOf[Unit])
      case _ =>
        c.abort(
          c.enclosingPosition,
          s"Agent client method ${method.name} must return scala.concurrent.Future[...] or Unit, found: $returnType"
        )
    }
  }

  private def multiParamSchemaExpr(
    c: blackbox.Context
  )(methodName: String, params: List[(c.universe.TermName, c.universe.Type)]): c.Tree = {
    import c.universe._

    val expectedCount = params.length

    val paramEntries = params.map { case (name, tpe) =>
      val nameStr         = name.toString
      val golemSchemaType = appliedType(typeOf[GolemSchema[_]].typeConstructor, tpe)
      val schemaInstance  = c.inferImplicitValue(golemSchemaType)
      if (schemaInstance.isEmpty) {
        c.abort(
          c.enclosingPosition,
          s"Unable to summon GolemSchema for parameter '$nameStr' of method $methodName with type $tpe"
        )
      }
      q"($nameStr, $schemaInstance.asInstanceOf[_root_.golem.data.GolemSchema[Any]])"
    }

    q"""
      new _root_.golem.data.GolemSchema[Vector[Any]] {
        private val params = Array[(String, _root_.golem.data.GolemSchema[Any])](..$paramEntries)

        override val schema: _root_.golem.data.StructuredSchema = {
          val builder = List.newBuilder[_root_.golem.data.NamedElementSchema]
          var idx = 0
          while (idx < params.length) {
            val (paramName, codec) = params(idx)
            codec.schema match {
              case _root_.golem.data.StructuredSchema.Tuple(elements) if elements.length == 1 =>
                builder += _root_.golem.data.NamedElementSchema(paramName, elements.head.schema)
              case other =>
                throw new IllegalArgumentException(
                  "Parameter '" + paramName + "' in method '" + $methodName + "' must encode to a single element, found: " + other
                )
            }
            idx += 1
          }
          _root_.golem.data.StructuredSchema.Tuple(builder.result())
        }

        override def encode(value: Vector[Any]): Either[String, _root_.golem.data.StructuredValue] = {
          if (value.length != params.length)
            Left("Parameter count mismatch for method '" + $methodName + "'. Expected " + $expectedCount + ", found " + value.length)
          else {
            val builder = List.newBuilder[_root_.golem.data.NamedElementValue]
            var idx = 0
            var error: Option[String] = None
            while (idx < params.length && error.isEmpty) {
              val (paramName, codec) = params(idx)
              codec.encode(value(idx)) match {
                case Left(err) =>
                  error = Some("Failed to encode parameter '" + paramName + "' in method '" + $methodName + "': " + err)
                case Right(_root_.golem.data.StructuredValue.Tuple(elements)) =>
                  elements match {
                    case _root_.golem.data.NamedElementValue(_, elementValue) :: Nil =>
                      builder += _root_.golem.data.NamedElementValue(paramName, elementValue)
                    case _ =>
                      error = Some("Parameter '" + paramName + "' in method '" + $methodName + "' must encode to a single element value")
                  }
                case Right(other) =>
                  error = Some("Parameter '" + paramName + "' in method '" + $methodName + "' produced unexpected structured value: " + other)
              }
              idx += 1
            }
            error.fold[Either[String, _root_.golem.data.StructuredValue]](
              Right(_root_.golem.data.StructuredValue.Tuple(builder.result()))
            )(Left(_))
          }
        }

        override def decode(value: _root_.golem.data.StructuredValue): Either[String, Vector[Any]] =
          value match {
            case _root_.golem.data.StructuredValue.Tuple(elements) =>
              if (elements.length != params.length)
                Left("Structured element count mismatch for method '" + $methodName + "'. Expected " + $expectedCount + ", found " + elements.length)
              else {
                var idx = 0
                var error: Option[String] = None
                val buffer = Vector.newBuilder[Any]

                while (idx < params.length && error.isEmpty) {
                  val (paramName, codec) = params(idx)
                  val element = elements(idx)
                  if (element.name != paramName)
                    error = Some("Structured element name mismatch for method '" + $methodName + "'. Expected '" + paramName + "', found '" + element.name + "'")
                  else {
                    codec.decode(_root_.golem.data.StructuredValue.single(element.value)) match {
                      case Left(err) =>
                        error = Some("Failed to decode parameter '" + paramName + "' in method '" + $methodName + "': " + err)
                      case Right(decoded) =>
                        buffer += decoded
                    }
                  }
                  idx += 1
                }

                error.fold[Either[String, Vector[Any]]](Right(buffer.result()))(Left(_))
              }
            case other =>
              Left("Structured value mismatch for method '" + $methodName + "'. Expected tuple payload, found: " + other)
          }
      }
    """
  }

  private def kebabCase(name: String): String = {
    val builder = new StringBuilder
    name.zipWithIndex.foreach { case (ch, idx) =>
      if (ch.isUpper) {
        if (idx != 0) builder.append('-')
        builder.append(ch.toLower)
      } else {
        builder.append(ch)
      }
    }
    builder.toString
  }

  private sealed trait ParamAccessMode

  private sealed trait InvocationKind

  private object ParamAccessMode {
    case object NoArgs extends ParamAccessMode

    case object SingleArg extends ParamAccessMode

    case object MultiArgs extends ParamAccessMode
  }

  private object InvocationKind {
    case object Awaitable extends InvocationKind

    case object FireAndForget extends InvocationKind
  }
}
