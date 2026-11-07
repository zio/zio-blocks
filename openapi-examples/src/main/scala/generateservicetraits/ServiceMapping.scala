package generateservicetraits

import zio.blocks.docs.Doc
import zio.blocks.openapi._
import zio.blocks.codegen.ir._

/**
 * Generate Scala Service Traits from OpenAPI Paths: the mapping.
 *
 * Maps every operation of every path to one abstract method of a service
 * trait, its path parameters and request body to parameters, its 2xx response
 * to the success type, and its other responses to a sealed error type that
 * belongs to that operation.
 */
object ServiceMapping {
  val refPrefix = "#/components/schemas/"

  final case class Endpoint(method: String, path: String, operationId: String, operation: Operation, item: PathItem)

  def docOf(text: Option[Doc]): Option[String] = text.map(_.toString.trim)

  def endpoints(api: OpenAPI): List[Endpoint] =
    api.paths.toList.flatMap(_.paths.toList).flatMap { case (path, item) =>
      List(
        "get"    -> item.get,
        "put"    -> item.put,
        "post"   -> item.post,
        "delete" -> item.delete,
        "patch"  -> item.patch
      ).collect { case (method, Some(op)) =>
        val id = op.operationId.getOrElse(
          throw new IllegalArgumentException(s"$method $path has no operationId")
        )
        Endpoint(method, path, id, op, item)
      }
    }

  /** The component name a `$ref` schema points at. */
  def componentName(schema: ReferenceOr[SchemaObject]): String = schema match {
    case ReferenceOr.Ref(reference) => reference.$ref.stripPrefix(refPrefix)
    case ReferenceOr.Value(_)       => throw new IllegalArgumentException("inline schemas are not supported: name the schema in components")
  }

  /** The JSON media type's schema of a content map, if it has one. */
  def jsonSchema(content: Iterable[(String, MediaType)]): Option[ReferenceOr[SchemaObject]] =
    content.collectFirst { case ("application/json", mediaType) => mediaType.schema }.flatten

  def pathParams(e: Endpoint): List[MethodParam] =
    (e.item.parameters.toList ++ e.operation.parameters.toList).collect {
      case ReferenceOr.Value(p) if p.in == ParameterLocation.Path => MethodParam(p.name, TypeRef.String)
    }

  def bodyParam(e: Endpoint): List[MethodParam] =
    e.operation.requestBody.toList.collect {
      case ReferenceOr.Value(body) =>
        jsonSchema(body.content.toList).map(s => MethodParam("body", TypeRef(componentName(s))))
    }.flatten

  def successType(e: Endpoint): TypeRef =
    e.operation.responses.responses.toList.collectFirst {
      case (code, ReferenceOr.Value(response)) if code.startsWith("2") =>
        jsonSchema(response.content.toList).map(s => TypeRef(componentName(s))).getOrElse(TypeRef.Unit)
    }.getOrElse(TypeRef.Unit)

  /** The error type of one operation, or None when it has no error responses. */
  def errorType(e: Endpoint): Option[SealedTrait] = {
    val name = e.operationId.take(1).toUpperCase + e.operationId.drop(1) + "Error"
    val cases = e.operation.responses.responses.toList.collect {
      case (code, ReferenceOr.Value(response)) if !code.startsWith("2") =>
        val fields = jsonSchema(response.content.toList)
          .map(s => List(Field("body", TypeRef(componentName(s)))))
          .getOrElse(Nil)
        SealedTraitCase.CaseClassCase(
          CaseClass(s"Status$code", fields, derives = List("Schema"), doc = Some(response.description.toString.trim))
        )
    }
    if (cases.isEmpty) None
    else Some(SealedTrait(name, cases = cases, doc = Some(s"Responses of ${e.operationId} other than success.")))
  }

  def method(e: Endpoint): Method = {
    val params    = List(ParamList(pathParams(e) ++ bodyParam(e)))
    val success   = successType(e)
    val errorName = errorType(e).map(_.name)
    val returnType = errorName match {
      case Some(name) => TypeRef.of("Either", TypeRef(name), success)
      case None       => success
    }
    Method(e.operationId, params = params.filter(_.params.nonEmpty), returnType = returnType, doc = docOf(e.operation.summary))
  }

  def serviceTrait(api: OpenAPI, packageName: String): ScalaFile = {
    val eps    = endpoints(api)
    val name   = api.info.title.filter(_.isLetterOrDigit) + "Service"
    val errors = eps.flatMap(errorType)
    val service = Trait(
      name,
      members = eps.map(e => ObjectMember.DefMember(method(e))),
      doc = Some(s"Operations of the ${api.info.title} API, one method per operation.")
    )
    ScalaFile(
      PackageDecl(packageName),
      imports = List(Import.WildcardImport("zio.blocks.schema")),
      types = errors :+ service
    )
  }
}
