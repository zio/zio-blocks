package golem.data

import zio.blocks.schema.Schema

/**
 * Type class for encoding/decoding Scala types to/from Golem's structured value
 * format.
 *
 * A `GolemSchema` provides:
 *   - The [[StructuredSchema]] describing the type's structure
 *   - Encoding from Scala values to [[StructuredValue]]
 *   - Decoding from [[StructuredValue]] to Scala values
 *
 * @tparam A
 *   The Scala type this schema describes
 * @see
 *   [[StructuredSchema]] for the schema representation
 * @see
 *   [[StructuredValue]] for the value representation
 */
trait GolemSchema[A] {

  /**
   * The schema describing this type's structure.
   *
   * This is used for WIT type generation and validation.
   */
  def schema: StructuredSchema

  /**
   * Encodes a Scala value to the structured representation.
   *
   * @param value
   *   The value to encode
   * @return
   *   Right with the encoded value, or Left with an error message
   */
  def encode(value: A): Either[String, StructuredValue]

  /**
   * Decodes a structured value to a Scala type.
   *
   * @param value
   *   The structured value to decode
   * @return
   *   Right with the decoded value, or Left with an error message
   */
  def decode(value: StructuredValue): Either[String, A]
}

/**
 * Companion object providing implicit derivation and factory methods.
 */
object GolemSchema {

  implicit val unitGolemSchema: GolemSchema[Unit] =
    new GolemSchema[Unit] {
      override val schema: StructuredSchema =
        StructuredSchema.Tuple(Nil)

      override def encode(value: Unit): Either[String, StructuredValue] =
        Right(StructuredValue.Tuple(Nil))

      override def decode(value: StructuredValue): Either[String, Unit] =
        value match {
          case StructuredValue.Tuple(elements) if elements.isEmpty => Right(())
          case other                                               =>
            Left(s"Expected empty tuple for Unit payload, found: ${other.getClass.getSimpleName}")
        }
    }

  /**
   * Summons a GolemSchema instance for the given type.
   *
   * @tparam A
   *   The type to get a schema for
   * @return
   *   The GolemSchema instance
   */
  def apply[A](implicit codec: GolemSchema[A]): GolemSchema[A] = codec

  // ---------------------------------------------------------------------------
  // Convenience schemas
  //
  // Tuple schemas are provided so constructor inputs can be encoded without extra boilerplate.
  // ---------------------------------------------------------------------------

  implicit def tuple2GolemSchema[A: Schema, B: Schema]: GolemSchema[(A, B)] =
    new GolemSchema[(A, B)] {
      private val aSchema = implicitly[Schema[A]]
      private val bSchema = implicitly[Schema[B]]

      private val aDt = DataInterop.schemaToDataType(aSchema)
      private val bDt = DataInterop.schemaToDataType(bSchema)

      override val schema: StructuredSchema =
        StructuredSchema.Tuple(
          List(
            NamedElementSchema("arg0", ElementSchema.Component(aDt)),
            NamedElementSchema("arg1", ElementSchema.Component(bDt))
          )
        )

      override def encode(value: (A, B)): Either[String, StructuredValue] = {
        val (a, b) = value
        val av     = DataInterop.toData[A](a)(aSchema)
        val bv     = DataInterop.toData[B](b)(bSchema)
        Right(
          StructuredValue.Tuple(
            List(
              NamedElementValue("arg0", ElementValue.Component(av)),
              NamedElementValue("arg1", ElementValue.Component(bv))
            )
          )
        )
      }

      override def decode(value: StructuredValue): Either[String, (A, B)] =
        value match {
          case StructuredValue.Tuple(elements) =>
            def find(name: String): Either[String, DataValue] =
              elements
                .find(_.name == name)
                .toRight(s"Tuple2 payload missing field '$name'")
                .flatMap {
                  case NamedElementValue(_, ElementValue.Component(dv)) => Right(dv)
                  case other                                            =>
                    Left(
                      s"Tuple2 payload field '$name' must be component-model, found: ${other.value.getClass.getSimpleName}"
                    )
                }

            for {
              av <- find("arg0")
              bv <- find("arg1")
              a  <- DataInterop.fromData[A](av)(aSchema)
              b  <- DataInterop.fromData[B](bv)(bSchema)
            } yield (a, b)

          case StructuredValue.Multimodal(_) =>
            Left("Multimodal payload cannot be decoded as component-model value")
        }
    }

  implicit def tuple3GolemSchema[A: Schema, B: Schema, C: Schema]: GolemSchema[(A, B, C)] =
    // See tuple2GolemSchema above for rationale.
    new GolemSchema[(A, B, C)] {
      private val aSchema = implicitly[Schema[A]]
      private val bSchema = implicitly[Schema[B]]
      private val cSchema = implicitly[Schema[C]]

      private val aDt = DataInterop.schemaToDataType(aSchema)
      private val bDt = DataInterop.schemaToDataType(bSchema)
      private val cDt = DataInterop.schemaToDataType(cSchema)

      override val schema: StructuredSchema =
        StructuredSchema.Tuple(
          List(
            NamedElementSchema("arg0", ElementSchema.Component(aDt)),
            NamedElementSchema("arg1", ElementSchema.Component(bDt)),
            NamedElementSchema("arg2", ElementSchema.Component(cDt))
          )
        )

      override def encode(value: (A, B, C)): Either[String, StructuredValue] = {
        val (a, b, c) = value
        val av        = DataInterop.toData[A](a)(aSchema)
        val bv        = DataInterop.toData[B](b)(bSchema)
        val cv        = DataInterop.toData[C](c)(cSchema)
        Right(
          StructuredValue.Tuple(
            List(
              NamedElementValue("arg0", ElementValue.Component(av)),
              NamedElementValue("arg1", ElementValue.Component(bv)),
              NamedElementValue("arg2", ElementValue.Component(cv))
            )
          )
        )
      }

      override def decode(value: StructuredValue): Either[String, (A, B, C)] =
        value match {
          case StructuredValue.Tuple(elements) =>
            def find(name: String): Either[String, DataValue] =
              elements
                .find(_.name == name)
                .toRight(s"Tuple3 payload missing field '$name'")
                .flatMap {
                  case NamedElementValue(_, ElementValue.Component(dv)) => Right(dv)
                  case other                                            =>
                    Left(
                      s"Tuple3 payload field '$name' must be component-model, found: ${other.value.getClass.getSimpleName}"
                    )
                }

            for {
              av <- find("arg0")
              bv <- find("arg1")
              cv <- find("arg2")
              a  <- DataInterop.fromData[A](av)(aSchema)
              b  <- DataInterop.fromData[B](bv)(bSchema)
              c  <- DataInterop.fromData[C](cv)(cSchema)
            } yield (a, b, c)

          case StructuredValue.Multimodal(_) =>
            Left("Multimodal payload cannot be decoded as component-model value")
        }
    }

  /**
   * Derives a GolemSchema from ZIO Blocks Schema.
   *
   * This is the primary derivation path - any type with a
   * `zio.blocks.schema.Schema` automatically gets a `GolemSchema` via this
   * implicit.
   */
  implicit def fromBlocksSchema[A](implicit baseSchema: Schema[A]): GolemSchema[A] = new GolemSchema[A] {
    private val dataType = DataInterop.schemaToDataType(baseSchema)

    override val schema: StructuredSchema =
      StructuredSchema.single(ElementSchema.Component(dataType))

    override def encode(value: A): Either[String, StructuredValue] = {
      val dataValue = DataInterop.toData[A](value)(baseSchema)
      Right(StructuredValue.single(ElementValue.Component(dataValue)))
    }

    override def decode(value: StructuredValue): Either[String, A] =
      value match {
        case StructuredValue.Tuple(elements) =>
          elements.headOption match {
            case Some(NamedElementValue(_, ElementValue.Component(dataValue))) =>
              DataInterop.fromData[A](dataValue)(baseSchema)
            case Some(other) =>
              Left(s"Expected component-model value, found: ${other.value.getClass.getSimpleName}")
            case None =>
              Left("Tuple payload missing component value")
          }
        case StructuredValue.Multimodal(_) =>
          Left("Multimodal payload cannot be decoded as component-model value")
      }
  }
}
