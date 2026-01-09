package golem.runtime.autowire

import golem.data.GolemSchema

import scala.scalajs.js

object HostPayload {
  def schema[A](implicit codec: GolemSchema[A]): js.Dynamic =
    HostSchemaEncoder.encode(codec.schema)

  def encode[A](value: A)(implicit codec: GolemSchema[A]): Either[String, js.Dynamic] =
    for {
      structured <- codec.encode(value)
      encoded    <- HostValueEncoder.encode(codec.schema, structured)
    } yield encoded

  def decode[A](hostValue: js.Dynamic)(implicit codec: GolemSchema[A]): Either[String, A] =
    for {
      structured <- HostValueDecoder.decode(codec.schema, hostValue)
      value      <- codec.decode(structured)
    } yield value
}
