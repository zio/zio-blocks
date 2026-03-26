package golem.runtime

/**
 * Validation rules for HTTP mount and endpoint configurations.
 *
 * These follow the same semantics as the TS and Rust Golem SDKs:
 *   - Mount variables bind to constructor parameters
 *   - Endpoint variables bind to method parameters
 *   - Various type safety checks
 */
object HttpValidation {

  /**
   * Validates an HTTP endpoint at the trait level (method params are known).
   */
  def validateEndpointVars(
    agentName: String,
    methodName: String,
    endpoint: HttpEndpointDetails,
    methodParamNames: Set[String],
    hasMount: Boolean
  ): Either[String, Unit] =
    if (!hasMount)
      Left(
        s"Agent method '$methodName' of '$agentName' defines HTTP endpoints " +
          s"but the agent is not mounted over HTTP. Please specify mount in @agentDefinition."
      )
    else
      for {
        _ <- validatePathVars(methodName, endpoint.pathSuffix, methodParamNames)
        _ <- validateHeaderVars(methodName, endpoint.headerVars, methodParamNames)
        _ <- validateQueryVars(methodName, endpoint.queryVars, methodParamNames)
      } yield ()

  private def validatePathVars(
    methodName: String,
    segments: List[PathSegment],
    methodParamNames: Set[String]
  ): Either[String, Unit] = {
    val missing = segments.collect {
      case PathSegment.PathVariable(v) if !methodParamNames.contains(v)          => v
      case PathSegment.RemainingPathVariable(v) if !methodParamNames.contains(v) => v
    }
    missing.headOption match {
      case Some(varName) =>
        Left(s"HTTP endpoint path variable '$varName' in method '$methodName' is not defined in method parameters.")
      case None => Right(())
    }
  }

  private def validateHeaderVars(
    methodName: String,
    headerVars: List[HeaderVariable],
    methodParamNames: Set[String]
  ): Either[String, Unit] =
    headerVars.find(hv => !methodParamNames.contains(hv.variableName)) match {
      case Some(hv) =>
        Left(
          s"HTTP endpoint header variable '${hv.variableName}' in method '$methodName' is not defined in method parameters."
        )
      case None => Right(())
    }

  private def validateQueryVars(
    methodName: String,
    queryVars: List[QueryVariable],
    methodParamNames: Set[String]
  ): Either[String, Unit] =
    queryVars.find(qv => !methodParamNames.contains(qv.variableName)) match {
      case Some(qv) =>
        Left(
          s"HTTP endpoint query variable '${qv.variableName}' in method '$methodName' is not defined in method parameters."
        )
      case None => Right(())
    }

  /**
   * Validates that the mount path has no catch-all (remaining path) variables.
   */
  def validateNoCatchAllInMount(
    agentName: String,
    mount: HttpMountDetails
  ): Either[String, Unit] =
    mount.pathPrefix.collectFirst { case PathSegment.RemainingPathVariable(name) =>
      name
    } match {
      case Some(name) =>
        Left(s"HTTP mount for agent '$agentName' cannot contain catch-all path variable '{*$name}'")
      case None =>
        Right(())
    }

  /** Validates mount path variables exist in constructor param names. */
  def validateMountVarsExistInConstructor(
    mount: HttpMountDetails,
    constructorParamNames: Set[String]
  ): Either[String, Unit] = {
    val missing = mount.pathPrefix.zipWithIndex.collect {
      case (PathSegment.PathVariable(varName), idx) if !constructorParamNames.contains(varName) =>
        (varName, idx)
    }
    missing.headOption match {
      case Some((varName, idx)) =>
        Left(
          s"HTTP mount path variable '$varName' (in path segment $idx) is not defined in the agent constructor."
        )
      case None => Right(())
    }
  }

  /** Validates all constructor params are satisfied by mount path variables. */
  def validateConstructorVarsSatisfied(
    mount: HttpMountDetails,
    constructorParamNames: Set[String]
  ): Either[String, Unit] = {
    val providedVars = mount.pathPrefix.collect { case PathSegment.PathVariable(name) =>
      name
    }.toSet

    constructorParamNames.find(param => !providedVars.contains(param)) match {
      case Some(param) =>
        Left(s"Agent constructor variable '$param' is not provided by the HTTP mount path.")
      case None =>
        Right(())
    }
  }

  /**
   * Runs all mount-level validations (called from implementation-level macro).
   */
  def validateHttpMount(
    agentName: String,
    mount: HttpMountDetails,
    constructorParamNames: Set[String]
  ): Either[String, Unit] =
    for {
      _ <- validateNoCatchAllInMount(agentName, mount)
      _ <- validateMountVarsExistInConstructor(mount, constructorParamNames)
      _ <- validateConstructorVarsSatisfied(mount, constructorParamNames)
    } yield ()

  /**
   * Validates the HTTP mount against constructor parameter names extracted from
   * the agent's constructor schema. Called from generated code in the
   * `@agentImplementation` macro.
   *
   * @throws IllegalArgumentException
   *   if validation fails
   */
  def validateHttpMountFromMetadata(metadata: AgentMetadata): Unit =
    metadata.httpMount.foreach { mount =>
      val constructorParamNames = extractConstructorParamNames(metadata.constructor)
      validateHttpMount(metadata.name, mount, constructorParamNames) match {
        case Left(err) => throw new IllegalArgumentException(err)
        case Right(()) => ()
      }
    }

  /**
   * Extracts constructor parameter names from the agent's constructor schema.
   *
   * The parameter names depend on how the agent's `class Id` is
   * defined:
   *
   *   - '''Single parameter''' (e.g. `class Id(val value: String)`):
   *     produces one parameter named `"value"`. The mount path must use
   *     `{value}` to refer to it.
   *   - '''Multiple parameters''' (e.g.
   *     `class Id(val arg0: String, val arg1: Int)`): produces
   *     parameters named `"arg0"`, `"arg1"`, etc. The mount path must use
   *     `{arg0}`, `{arg1}`, etc.
   *   - '''No id''': produces no parameters. Mount paths must not
   *     contain variables.
   */
  private def extractConstructorParamNames(schema: golem.data.StructuredSchema): Set[String] =
    schema match {
      case golem.data.StructuredSchema.Tuple(elements) =>
        elements.map(_.name).toSet
      case _ => Set.empty
    }
}
