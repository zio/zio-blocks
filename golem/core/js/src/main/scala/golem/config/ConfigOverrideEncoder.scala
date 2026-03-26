package golem.config

import golem.data.ElementSchema
import golem.host.js.{JsTypedAgentConfigValue, JsValueAndType}
import golem.runtime.autowire.{WitTypeBuilder, WitValueBuilder}

import scala.scalajs.js

private[golem] object ConfigOverrideEncoder {
  def encode(overrides: List[ConfigOverride]): js.Array[JsTypedAgentConfigValue] = {
    val result = new js.Array[JsTypedAgentConfigValue]()
    overrides.foreach { co =>
      co.valueType match {
        case ElementSchema.Component(dataType) =>
          val witValue = WitValueBuilder.build(dataType, co.value) match {
            case Right(v)  => v
            case Left(err) =>
              throw new IllegalArgumentException(
                s"Failed to encode config override at ${co.path.mkString(".")}: $err"
              )
          }
          val witType = WitTypeBuilder.build(dataType)
          result.push(JsTypedAgentConfigValue(js.Array(co.path: _*), JsValueAndType(witValue, witType)))
        case other =>
          throw new IllegalArgumentException(
            s"Config overrides only support component types, found: $other at ${co.path.mkString(".")}"
          )
      }
    }
    result
  }
}
