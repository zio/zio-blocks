package golem.runtime.macros

import golem.data.GolemSchema
import golem.runtime.plan.AgentImplementationPlan

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

object AgentImplementationMacro {
  def plan[Trait](build: => Trait): AgentImplementationPlan[Trait, Unit] = macro
    AgentImplementationMacroImpl.planImpl[Trait]

  def planWithCtor[Trait <: AnyRef { type AgentInput }, Ctor](
    build: Ctor => Trait
  ): AgentImplementationPlan[Trait, Ctor] = macro
    AgentImplementationMacroImpl.planWithCtorImpl[Trait, Ctor]
}

object AgentImplementationMacroImpl {
  def planImpl[Trait: c.WeakTypeTag](c: blackbox.Context)(
    build: c.Expr[Trait]
  ): c.Expr[AgentImplementationPlan[Trait, Unit]] = {
    import c.universe._

    val traitType   = weakTypeOf[Trait]
    val traitSymbol = traitType.typeSymbol

    if (!traitSymbol.isClass || !traitSymbol.asClass.isTrait) {
      c.abort(c.enclosingPosition, s"@agentImplementation target must be a trait, found: ${traitSymbol.fullName}")
    }

    val metadataExpr = q"_root_.golem.runtime.macros.AgentDefinitionMacro.generate[$traitType]"
    val methodsExpr  = buildMethodPlansExpr(c)(traitType, metadataExpr)

    val ctorSchemaExpr =
      c.inferImplicitValue(appliedType(typeOf[GolemSchema[_]].typeConstructor, typeOf[Unit]))

    c.Expr[AgentImplementationPlan[Trait, Unit]](q"""
      val metadata = $metadataExpr
      _root_.golem.runtime.plan.AgentImplementationPlan[$traitType, _root_.scala.Unit](
        metadata = metadata,
        constructorSchema = $ctorSchemaExpr,
        buildInstance = (_: _root_.scala.Unit) => $build,
        methods = $methodsExpr
      )
    """)
  }

  def planWithCtorImpl[Trait: c.WeakTypeTag, Ctor: c.WeakTypeTag](c: blackbox.Context)(
    build: c.Expr[Any]
  ): c.Expr[AgentImplementationPlan[Trait, Ctor]] = {
    import c.universe._

    val traitType   = weakTypeOf[Trait]
    val traitSymbol = traitType.typeSymbol

    if (!traitSymbol.isClass || !traitSymbol.asClass.isTrait) {
      c.abort(c.enclosingPosition, s"@agentImplementation target must be a trait, found: ${traitSymbol.fullName}")
    }

    val ctorType: Type = {
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

    val gotCtor = weakTypeOf[Ctor].dealias
    if (!(gotCtor =:= ctorType)) {
      c.abort(
        c.enclosingPosition,
        s"Constructor function must have input type matching `type AgentInput = $ctorType` on ${traitSymbol.fullName} (found: $gotCtor)"
      )
    }

    val metadataExpr = q"_root_.golem.runtime.macros.AgentDefinitionMacro.generate[$traitType]"
    val methodsExpr  = buildMethodPlansExpr(c)(traitType, metadataExpr)

    val ctorSchemaTpe  = appliedType(typeOf[GolemSchema[_]].typeConstructor, ctorType)
    val ctorSchemaExpr = c.inferImplicitValue(ctorSchemaTpe)
    if (ctorSchemaExpr.isEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Unable to summon GolemSchema for constructor type $ctorType on ${traitSymbol.fullName}"
      )
    }

    c.Expr[AgentImplementationPlan[Trait, Ctor]](
      q"""
      val metadata = $metadataExpr
      _root_.golem.runtime.plan.AgentImplementationPlan[$traitType, $ctorType](
        metadata = metadata,
        constructorSchema = $ctorSchemaExpr,
        buildInstance = ($build).asInstanceOf[$ctorType => $traitType],
        methods = $methodsExpr
      ).asInstanceOf[_root_.golem.runtime.plan.AgentImplementationPlan[$traitType, ${weakTypeOf[Ctor]}]]
      """
    )
  }

  private def buildMethodPlansExpr(c: blackbox.Context)(
    traitType: c.universe.Type,
    metadataExpr: c.Tree
  ): c.Tree = {
    import c.universe._

    val methods = traitType.decls.collect {
      case method: MethodSymbol if method.isAbstract && method.isMethod && method.name.toString != "new" =>
        method
    }.toList

    val planExprs = methods.map { method =>
      val methodName         = method.name.toString
      val methodMetadataExpr =
        q"""
        $metadataExpr.methods.find(_.name == $methodName).getOrElse {
          throw new IllegalStateException("Method metadata missing for " + $methodName)
        }
      """

      val params = method.paramLists.flatten.collect {
        case param if param.isTerm => (param.name.toTermName, param.typeSignature)
      }

      val (isAsync, payloadType) = methodReturnInfo(c)(method)
      val accessMode             = paramAccessMode(params)
      val inputType              = inputTypeFor(c)(accessMode, params)

      buildMethodPlan(c)(traitType, method, methodMetadataExpr, params, accessMode, inputType, payloadType, isAsync)
    }

    q"List(..$planExprs)"
  }

  private def buildMethodPlan(c: blackbox.Context)(
    traitType: c.universe.Type,
    method: c.universe.MethodSymbol,
    methodMetadataExpr: c.Tree,
    params: List[(c.universe.TermName, c.universe.Type)],
    accessMode: ParamAccessMode,
    inputType: c.universe.Type,
    outputType: c.universe.Type,
    isAsync: Boolean
  ): c.Tree = {
    import c.universe._

    val methodName = method.name.toString

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

    val handlerExpr = buildHandler(c)(traitType, method, params, accessMode, inputType, outputType, isAsync)

    if (isAsync) {
      q"""
        _root_.golem.runtime.plan.AsyncMethodPlan[$traitType, $inputType, $outputType](
          metadata = $methodMetadataExpr,
          inputSchema = $inputSchemaExpr,
          outputSchema = $outputSchemaInstance,
          handler = $handlerExpr
        )
      """
    } else {
      q"""
        _root_.golem.runtime.plan.SyncMethodPlan[$traitType, $inputType, $outputType](
          metadata = $methodMetadataExpr,
          inputSchema = $inputSchemaExpr,
          outputSchema = $outputSchemaInstance,
          handler = $handlerExpr
        )
      """
    }
  }

  private def buildHandler(c: blackbox.Context)(
    traitType: c.universe.Type,
    method: c.universe.MethodSymbol,
    params: List[(c.universe.TermName, c.universe.Type)],
    accessMode: ParamAccessMode,
    inputType: c.universe.Type,
    outputType: c.universe.Type,
    isAsync: Boolean
  ): c.Tree = {
    import c.universe._

    val instanceName   = TermName("instance")
    val inputName      = TermName("input")
    val methodCallName = method.name

    val callExpr = accessMode match {
      case ParamAccessMode.NoArgs =>
        q"$instanceName.$methodCallName()"
      case ParamAccessMode.SingleArg =>
        q"$instanceName.$methodCallName($inputName)"
      case ParamAccessMode.MultiArgs =>
        val expectedCount = params.length
        val argExprs      = params.zipWithIndex.map { case ((paramName, paramType), idx) =>
          q"$inputName($idx).asInstanceOf[$paramType]"
        }
        q"""
          if ($inputName.length != $expectedCount)
            throw new IllegalArgumentException(
              "Parameter count mismatch when invoking method '" + ${method.name.toString} + "'. Expected " + $expectedCount + "."
            )
          $instanceName.$methodCallName(..$argExprs)
        """
    }

    if (isAsync) {
      q"($instanceName: $traitType, $inputName: $inputType) => $callExpr"
    } else {
      q"($instanceName: $traitType, $inputName: $inputType) => $callExpr"
    }
  }

  private def multiParamSchemaExpr(c: blackbox.Context)(
    methodName: String,
    params: List[(c.universe.TermName, c.universe.Type)]
  ): c.Tree = {
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
      new _root_.golem.data.GolemSchema[List[Any]] {
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

        override def encode(value: List[Any]): Either[String, _root_.golem.data.StructuredValue] = {
          val values = value.toVector
          if (values.length != params.length)
            Left("Parameter count mismatch for method '" + $methodName + "'. Expected " + $expectedCount + ", found " + values.length)
          else {
            val builder = List.newBuilder[_root_.golem.data.NamedElementValue]
            var idx = 0
            var error: Option[String] = None
            while (idx < params.length && error.isEmpty) {
              val (paramName, codec) = params(idx)
              codec.encode(values(idx)) match {
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

        override def decode(value: _root_.golem.data.StructuredValue): Either[String, List[Any]] =
          value match {
            case _root_.golem.data.StructuredValue.Tuple(elements) =>
              if (elements.length != params.length)
                Left("Structured element count mismatch for method '" + $methodName + "'. Expected " + $expectedCount + ", found " + elements.length)
              else {
                val builder = List.newBuilder[Any]
                var idx = 0
                var error: Option[String] = None
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
                        builder += decoded
                    }
                  }
                  idx += 1
                }
                error.fold[Either[String, List[Any]]](Right(builder.result()))(Left(_))
              }
            case other =>
              Left("Structured value mismatch for method '" + $methodName + "'. Expected tuple payload, found: " + other)
          }
      }
    """
  }

  private def methodReturnInfo(c: blackbox.Context)(
    method: c.universe.MethodSymbol
  ): (Boolean, c.universe.Type) = {
    import c.universe._

    val returnType   = method.returnType
    val futureSymbol = typeOf[scala.concurrent.Future[_]].typeSymbol

    returnType match {
      case TypeRef(_, sym, args) if sym == futureSymbol && args.nonEmpty =>
        (true, args.head)
      case _ =>
        (false, returnType)
    }
  }

  private def paramAccessMode(params: List[(_, _)]): ParamAccessMode = params match {
    case Nil      => ParamAccessMode.NoArgs
    case _ :: Nil => ParamAccessMode.SingleArg
    case _        => ParamAccessMode.MultiArgs
  }

  private def inputTypeFor(c: blackbox.Context)(
    accessMode: ParamAccessMode,
    params: List[(c.universe.TermName, c.universe.Type)]
  ): c.universe.Type = {
    import c.universe._
    accessMode match {
      case ParamAccessMode.NoArgs    => typeOf[Unit]
      case ParamAccessMode.SingleArg => params.head._2
      case ParamAccessMode.MultiArgs => typeOf[List[Any]]
    }
  }

  private sealed trait ParamAccessMode

  private object ParamAccessMode {
    case object NoArgs extends ParamAccessMode

    case object SingleArg extends ParamAccessMode

    case object MultiArgs extends ParamAccessMode
  }
}
