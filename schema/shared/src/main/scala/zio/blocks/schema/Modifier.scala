package zio.blocks.schema

import scala.annotation.meta.field
import scala.annotation.StaticAnnotation

// Must be pure data
sealed trait Modifier extends StaticAnnotation

object Modifier {
  sealed trait Term extends Modifier

  @field case class transient() extends Term

  @field case class rename(name: String) extends Term

  @field case class alias(name: String) extends Term

  sealed trait Reflect extends Modifier

  /**
   * A configuration key-value pair, which can be attached to any type of
   * reflective value. The convention for keys is `<format>.<property>`. For
   * example, `protobuf.field-id` is a key that specifies the field id for a
   * protobuf format.
   */
  @field case class config(key: String, value: String) extends Term with Reflect
}
