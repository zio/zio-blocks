package golem.config

import golem.data.{DataValue, ElementSchema}

final case class ConfigOverride(
  path: List[String],
  value: DataValue,
  valueType: ElementSchema
)

object ConfigOverride {
  def apply[A](path: List[String], value: A)(implicit gs: golem.data.GolemSchema[A]): ConfigOverride = {
    val encoded = gs.encodeElement(value) match {
      case Right(golem.data.ElementValue.Component(dv)) => dv
      case Right(_) =>
        throw new IllegalArgumentException(
          s"Expected component value for config override at ${path.mkString(".")}"
        )
      case Left(err) =>
        throw new IllegalArgumentException(
          s"Failed to encode config override at ${path.mkString(".")}: $err"
        )
    }
    new ConfigOverride(path, encoded, gs.elementSchema)
  }
}
