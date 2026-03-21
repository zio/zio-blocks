package golem.runtime.autowire

import golem.data.GolemSchema
import golem.host.js._

object HostPayload {
  def schema[A](implicit codec: GolemSchema[A]): JsDataSchema =
    HostSchemaEncoder.encode(codec.schema)

  def encode[A](value: A)(implicit codec: GolemSchema[A]): Either[String, JsDataValue] =
    for {
      structured <- codec.encode(value)
      encoded    <- HostValueEncoder.encode(codec.schema, structured)
    } yield encoded

  def decode[A](hostValue: JsDataValue)(implicit codec: GolemSchema[A]): Either[String, A] =
    for {
      structured <- HostValueDecoder.decode(codec.schema, hostValue)
      value      <- codec.decode(structured)
    } yield value
}
