package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import scala.util.control.NoStackTrace

object Serde {
  private[json] sealed trait SerdeError extends Exception with NoStackTrace
  private[json] final case class UnsupportedPrimitiveValue(primitiveValue: PrimitiveValue) extends SerdeError {
    override def getMessage: String =
      s"Unsupported primitive kind ${primitiveValue.getClass.getSimpleName} of value $primitiveValue"
  }

  final def fromJson(json: String): DynamicValue = ???
}
