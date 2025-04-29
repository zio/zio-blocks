package zio.blocks.schema

import scala.annotation.meta.field
import scala.annotation.StaticAnnotation

// Must be pure data
sealed trait Modifier extends StaticAnnotation

object Modifier {
  sealed trait Term extends Modifier

  @field case class deferred() extends Term

  @field case class transient() extends Term

  sealed trait Record extends Modifier

  sealed trait Variant extends Modifier

  sealed trait Dynamic extends Modifier

  sealed trait Seq extends Modifier

  sealed trait Map extends Modifier

  sealed trait Primitive extends Modifier

  /**
   * A configuration key-value pair, which can be attached to any type of
   * reflective value. The convention for keys is `<format>.<property>`. For
   * example, `protobuf.field-id` is a key that specifies the field id for a
   * protobuf format.
   */
  @field case class config(key: String, value: String)
      extends Term
      with Record
      with Variant
      with Dynamic
      with Seq
      with Map
      with Primitive
}
