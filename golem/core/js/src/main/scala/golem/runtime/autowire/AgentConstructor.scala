package golem.runtime.autowire

import golem.Principal
import golem.data._
import golem.host.js._
import golem.runtime.util.FutureInterop

import scala.concurrent.Future
import scala.scalajs.js

trait AgentConstructor[Instance] {
  def info: ConstructorMetadata

  def schema: JsDataSchema

  def initialize(payload: JsDataValue, principal: Principal): js.Promise[Instance]
}

object AgentConstructor {
  def asyncJs[A, Instance](ctorInfo: ConstructorMetadata)(build: (A, Principal) => js.Promise[Instance])(implicit
    codec: GolemSchema[A]
  ): AgentConstructor[Instance] =
    async[A, Instance](ctorInfo)((a, principal) => FutureInterop.fromPromise(build(a, principal)))

  def noArgs[Instance](description: String, prompt: Option[String] = None)(build: Principal => Instance)(implicit
    codec: GolemSchema[Unit]
  ): AgentConstructor[Instance] =
    sync[Unit, Instance](ConstructorMetadata(name = None, description = description, promptHint = prompt))((_, principal) => build(principal))

  def sync[A, Instance](ctorInfo: ConstructorMetadata)(build: (A, Principal) => Instance)(implicit
    codec: GolemSchema[A]
  ): AgentConstructor[Instance] =
    async[A, Instance](ctorInfo)((a, principal) => Future.successful(build(a, principal)))

  def async[A, Instance](
    ctorInfo: ConstructorMetadata
  )(build: (A, Principal) => Future[Instance])(implicit codec: GolemSchema[A]): AgentConstructor[Instance] = {
    val flattened = flattenConstructorCodec(codec)
    new AgentConstructor[Instance] {
      override val info: ConstructorMetadata = ctorInfo
      override val schema: JsDataSchema      = HostSchemaEncoder.encode(flattened.schema)

      override def initialize(payload: JsDataValue, principal: Principal): js.Promise[Instance] =
        flattened
          .decode(payload)
          .fold(
            err => js.Promise.reject(err.asInstanceOf[Any]).asInstanceOf[js.Promise[Instance]],
            value => FutureInterop.toPromise(build(value, principal))
          )
    }
  }

  private case class FlattenedCodec[A](
    schema: StructuredSchema,
    decode: JsDataValue => Either[String, A]
  )

  private def flattenConstructorCodec[A](codec: GolemSchema[A]): FlattenedCodec[A] =
    codec.elementSchema match {
      case ElementSchema.Component(DataType.StructType(fields)) if fields.nonEmpty =>
        val flatSchema = StructuredSchema.Tuple(
          fields.map(f => NamedElementSchema(f.name, ElementSchema.Component(f.dataType)))
        )
        FlattenedCodec(
          schema = flatSchema,
          decode = (payload: JsDataValue) => {
            HostValueDecoder.decode(flatSchema, payload).flatMap {
              case StructuredValue.Tuple(elements) =>
                val fieldMap = elements.map { named =>
                  named.value match {
                    case ElementValue.Component(dv) => Right(named.name -> dv)
                    case other                      => Left(s"Expected component-model value for field '${named.name}', found: ${other.getClass.getSimpleName}")
                  }
                }
                val errors = fieldMap.collect { case Left(e) => e }
                if (errors.nonEmpty) Left(errors.mkString("; "))
                else {
                  val structValue = DataValue.StructValue(fieldMap.collect { case Right(kv) => kv }.toMap)
                  codec.decodeElement(ElementValue.Component(structValue))
                }
              case other => Left(s"Expected tuple payload for flattened constructor, found: ${other.getClass.getSimpleName}")
            }
          }
        )
      case _ =>
        FlattenedCodec(
          schema = codec.schema,
          decode = (payload: JsDataValue) => {
            HostValueDecoder.decode(codec.schema, payload).flatMap(codec.decode)
          }
        )
    }
}
