/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.yaml

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.typeid.{Owner, TypeId}

import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import scala.reflect.ClassTag

/**
 * A [[zio.blocks.schema.codec.BinaryFormat]] for `application/yaml` using
 * [[YamlCodecDeriver]].
 */
object YamlFormat extends BinaryFormat("application/yaml", YamlCodecDeriver)

/** Default instance of [[YamlCodecDeriver]]. */
object YamlCodecDeriver extends YamlCodecDeriver

/**
 * A [[zio.blocks.schema.derive.Deriver]] that produces [[YamlCodec]] instances
 * for schema-described types, converting between Scala values and YAML
 * representations.
 */
class YamlCodecDeriver extends Deriver[YamlCodec] {
  import YamlCodec._

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[YamlCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[A]] = Lazy {
    deriveCodec(
      new Reflect.Record(
        fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
        typeId,
        binding,
        doc,
        modifiers
      )
    )
  }

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[A]] = Lazy {
    deriveCodec(
      new Reflect.Variant(
        cases.asInstanceOf[IndexedSeq[Term[Binding, A, ? <: A]]],
        typeId,
        binding,
        doc,
        modifiers
      )
    )
  }

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[C[A]]] = Lazy {
    deriveCodec(
      new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeId, binding, doc, modifiers)
    )
  }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[M[K, V]]] = Lazy {
    deriveCodec(
      new Reflect.Map(
        key.asInstanceOf[Reflect[Binding, K]],
        value.asInstanceOf[Reflect[Binding, V]],
        typeId,
        binding,
        doc,
        modifiers
      )
    )
  }

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeId.of[DynamicValue], doc, modifiers)))

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[A]] = Lazy {
    deriveCodec(
      new Reflect.Wrapper(
        wrapped.asInstanceOf[Reflect[Binding, B]],
        typeId,
        binding,
        doc,
        modifiers
      )
    )
  }

  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  private def toKebabCase(name: String): String = {
    val sb = new StringBuilder
    var i  = 0
    while (i < name.length) {
      val c = name.charAt(i)
      if (c.isUpper) {
        if (i > 0) sb.append('-')
        sb.append(c.toLower)
      } else sb.append(c)
      i += 1
    }
    sb.toString
  }

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): YamlCodec[A] = {
    if (reflect.isPrimitive) {
      val primitive = reflect.asPrimitive.get
      if (primitive.primitiveBinding.isInstanceOf[Binding[?, ?]]) {
        primitive.primitiveType match {
          case _: PrimitiveType.Unit.type      => unitCodec
          case _: PrimitiveType.Boolean        => booleanCodec
          case _: PrimitiveType.Byte           => byteCodec
          case _: PrimitiveType.Short          => shortCodec
          case _: PrimitiveType.Int            => intCodec
          case _: PrimitiveType.Long           => longCodec
          case _: PrimitiveType.Float          => floatCodec
          case _: PrimitiveType.Double         => doubleCodec
          case _: PrimitiveType.Char           => charCodec
          case _: PrimitiveType.String         => stringCodec
          case _: PrimitiveType.BigInt         => bigIntCodec
          case _: PrimitiveType.BigDecimal     => bigDecimalCodec
          case _: PrimitiveType.DayOfWeek      => dayOfWeekCodec
          case _: PrimitiveType.Duration       => durationCodec
          case _: PrimitiveType.Instant        => instantCodec
          case _: PrimitiveType.LocalDate      => localDateCodec
          case _: PrimitiveType.LocalDateTime  => localDateTimeCodec
          case _: PrimitiveType.LocalTime      => localTimeCodec
          case _: PrimitiveType.Month          => monthCodec
          case _: PrimitiveType.MonthDay       => monthDayCodec
          case _: PrimitiveType.OffsetDateTime => offsetDateTimeCodec
          case _: PrimitiveType.OffsetTime     => offsetTimeCodec
          case _: PrimitiveType.Period         => periodCodec
          case _: PrimitiveType.Year           => yearCodec
          case _: PrimitiveType.YearMonth      => yearMonthCodec
          case _: PrimitiveType.ZoneId         => zoneIdCodec
          case _: PrimitiveType.ZoneOffset     => zoneOffsetCodec
          case _: PrimitiveType.ZonedDateTime  => zonedDateTimeCodec
          case _: PrimitiveType.Currency       => currencyCodec
          case _: PrimitiveType.UUID           => uuidCodec
        }
      } else primitive.primitiveBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isVariant) {
      val variant = reflect.asVariant.get
      if (variant.variantBinding.isInstanceOf[Binding[?, ?]]) {
        option(variant) match {
          case Some(value) =>
            val innerCodec = deriveCodec(value).asInstanceOf[YamlCodec[Any]]
            new YamlCodec[Option[Any]]() {
              override def decodeValue(yaml: Yaml): Either[YamlError, Option[Any]] = yaml match {
                case Yaml.NullValue                               => Right(None)
                case Yaml.Scalar(v, _) if v == "null" || v == "~" => Right(None)
                case Yaml.Mapping(entries)                        =>
                  val firstKey = entries.headOption.map(_._1)
                  firstKey match {
                    case Some(Yaml.Scalar(k, _)) if k == "None" => Right(None)
                    case Some(Yaml.Scalar(k, _)) if k == "Some" =>
                      entries.headOption.map(_._2) match {
                        case Some(v) => innerCodec.decodeValue(v).map(Some(_))
                        case None    => Left(YamlError.parseError("Expected value in Some", 0, 0))
                      }
                    case _ => innerCodec.decodeValue(yaml).map(Some(_))
                  }
                case other =>
                  innerCodec.decodeValue(other).map(Some(_))
              }

              override def encodeValue(x: Option[Any]): Yaml = x match {
                case None        => Yaml.NullValue
                case Some(value) => innerCodec.encodeValue(value)
              }
            }
          case _ =>
            val discr      = variant.variantBinding.asInstanceOf[Binding.Variant[A]].discriminator
            val cases      = variant.cases
            val caseCodecs = cases.map { case_ =>
              val caseName  = case_.name
              val caseCodec = deriveCodec(case_.value).asInstanceOf[YamlCodec[A]]
              (caseName, caseCodec)
            }

            new YamlCodec[A]() {
              private[this] val discriminator = discr
              private[this] val codecMap      = caseCodecs.toMap

              override def decodeValue(yaml: Yaml): Either[YamlError, A] = yaml match {
                case Yaml.Mapping(entries) if entries.length == 1 =>
                  val (key, value) = entries(0)
                  key match {
                    case Yaml.Scalar(caseName, _) =>
                      codecMap.get(caseName) match {
                        case Some(codec) => codec.decodeValue(value)
                        case None        => Left(YamlError.parseError(s"Unknown variant case: $caseName", 0, 0))
                      }
                    case _ => Left(YamlError.parseError("Expected string key for variant", 0, 0))
                  }
                case _ =>
                  Left(YamlError.parseError("Expected mapping with single key for variant", 0, 0))
              }

              override def encodeValue(x: A): Yaml = {
                val caseIdx   = discriminator.discriminate(x)
                val caseName  = cases(caseIdx).name
                val caseCodec = codecMap(caseName)
                val inner     = caseCodec.encodeValue(x)
                Yaml.Mapping.fromStringKeys(caseName -> inner)
              }
            }
        }
      } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isSequence) {
      val sequence = reflect.asSequenceUnknown.get.sequence
      if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
        val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
        val elemCodec    = deriveCodec(sequence.element).asInstanceOf[YamlCodec[Elem]]
        val elemClassTag = sequence.elemClassTag.asInstanceOf[ClassTag[Elem]]

        new YamlCodec[Col[Elem]]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          override def decodeValue(yaml: Yaml): Either[YamlError, Col[Elem]] = yaml match {
            case Yaml.Sequence(elements) =>
              val builder          = constructor.newBuilder[Elem](elements.length)(elemClassTag)
              var error: YamlError = null
              val iter             = elements.iterator
              while (iter.hasNext && error == null) {
                elemCodec.decodeValue(iter.next()) match {
                  case Right(v)  => constructor.add(builder, v)
                  case Left(err) => error = err
                }
              }
              if (error != null) Left(error)
              else Right(constructor.result(builder))
            case _ =>
              Left(YamlError.parseError("Expected sequence", 0, 0))
          }

          override def encodeValue(x: Col[Elem]): Yaml = {
            val iter     = deconstructor.deconstruct(x)
            val children = Chunk.newBuilder[Yaml]
            while (iter.hasNext) {
              children += elemCodec.encodeValue(iter.next())
            }
            Yaml.Sequence(children.result())
          }

        }
      } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map
      if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
        val binding  = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
        val keyCodec = deriveCodec(map.key).asInstanceOf[YamlCodec[Key]]
        val valCodec = deriveCodec(map.value).asInstanceOf[YamlCodec[Value]]

        new YamlCodec[Map[Key, Value]]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          override def decodeValue(yaml: Yaml): Either[YamlError, Map[Key, Value]] = yaml match {
            case Yaml.Mapping(entries) =>
              val builder          = constructor.newObjectBuilder[Key, Value](entries.length)
              var error: YamlError = null
              val iter             = entries.iterator
              while (iter.hasNext && error == null) {
                val (k, v) = iter.next()
                (keyCodec.decodeValue(k), valCodec.decodeValue(v)) match {
                  case (Right(key), Right(value)) => constructor.addObject(builder, key, value)
                  case (Left(err), _)             => error = err
                  case (_, Left(err))             => error = err
                }
              }
              if (error != null) Left(error)
              else Right(constructor.resultObject(builder))
            case _ =>
              Left(YamlError.parseError("Expected mapping for map", 0, 0))
          }

          override def encodeValue(x: Map[Key, Value]): Yaml = {
            val iter    = deconstructor.deconstruct(x)
            val entries = Chunk.newBuilder[(Yaml, Yaml)]
            while (iter.hasNext) {
              val kv = iter.next()
              val k  = deconstructor.getKey(kv)
              val v  = deconstructor.getValue(kv)
              entries += ((keyCodec.encodeValue(k), valCodec.encodeValue(v)))
            }
            Yaml.Mapping(entries.result())
          }
        }
      } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isRecord) {
      val record = reflect.asRecord.get
      if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        val binding = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields  = record.fields

        val fieldInfos: IndexedSeq[(String, YamlCodec[Any], Boolean, RegisterOffset.RegisterOffset)] = {
          var offset = RegisterOffset.Zero
          fields.map { field =>
            val fieldName   = toKebabCase(field.name)
            val fieldCodec  = deriveCodec(field.value).asInstanceOf[YamlCodec[Any]]
            val isOptional  = isOptionalField(field.value)
            val fieldOffset = offset
            offset = RegisterOffset.add(offset, fieldCodec.valueOffset)
            (fieldName, fieldCodec, isOptional, fieldOffset)
          }
        }

        new YamlCodec[A]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          override def decodeValue(yaml: Yaml): Either[YamlError, A] = yaml match {
            case Yaml.Mapping(entries) =>
              val childMap = scala.collection.mutable.Map.empty[String, Yaml]
              val iter     = entries.iterator
              while (iter.hasNext) {
                val (k, v) = iter.next()
                k match {
                  case Yaml.Scalar(key, _) => childMap(key) = v
                  case _                   => ()
                }
              }

              val regs             = Registers(constructor.usedRegisters)
              var error: YamlError = null
              var idx              = 0
              while (idx < fieldInfos.length && error == null) {
                val (fieldName, codec, isOptional, fieldOffset) = fieldInfos(idx)
                childMap.get(fieldName) match {
                  case Some(fieldValue) =>
                    codec.decodeValue(fieldValue) match {
                      case Right(v) =>
                        (codec.valueType: @switch) match {
                          case 0 => regs.setObject(fieldOffset, v.asInstanceOf[AnyRef])
                          case 1 => regs.setInt(fieldOffset, v.asInstanceOf[Int])
                          case 2 => regs.setLong(fieldOffset, v.asInstanceOf[Long])
                          case 3 => regs.setFloat(fieldOffset, v.asInstanceOf[Float])
                          case 4 => regs.setDouble(fieldOffset, v.asInstanceOf[Double])
                          case 5 => regs.setBoolean(fieldOffset, v.asInstanceOf[Boolean])
                          case 6 => regs.setByte(fieldOffset, v.asInstanceOf[Byte])
                          case 7 => regs.setChar(fieldOffset, v.asInstanceOf[Char])
                          case 8 => regs.setShort(fieldOffset, v.asInstanceOf[Short])
                          case _ => ()
                        }
                      case Left(err) => error = err
                    }
                  case None =>
                    if (isOptional) {
                      regs.setObject(fieldOffset, None)
                    } else {
                      val defaultVal = getDefaultValue(fields(idx).value)
                      defaultVal match {
                        case Some(v) =>
                          (codec.valueType: @switch) match {
                            case 0 => regs.setObject(fieldOffset, v.asInstanceOf[AnyRef])
                            case 1 => regs.setInt(fieldOffset, v.asInstanceOf[Int])
                            case 2 => regs.setLong(fieldOffset, v.asInstanceOf[Long])
                            case 3 => regs.setFloat(fieldOffset, v.asInstanceOf[Float])
                            case 4 => regs.setDouble(fieldOffset, v.asInstanceOf[Double])
                            case 5 => regs.setBoolean(fieldOffset, v.asInstanceOf[Boolean])
                            case 6 => regs.setByte(fieldOffset, v.asInstanceOf[Byte])
                            case 7 => regs.setChar(fieldOffset, v.asInstanceOf[Char])
                            case 8 => regs.setShort(fieldOffset, v.asInstanceOf[Short])
                            case _ => ()
                          }
                        case None => error = YamlError.parseError(s"Missing required field: $fieldName", 0, 0)
                      }
                    }
                }
                idx += 1
              }
              if (error != null) Left(error)
              else Right(constructor.construct(regs, 0))
            case _ =>
              Left(YamlError.parseError("Expected mapping for record", 0, 0))
          }

          override def encodeValue(x: A): Yaml = {
            val regs = Registers(deconstructor.usedRegisters)
            deconstructor.deconstruct(regs, 0, x)
            val entries = Chunk.newBuilder[(Yaml, Yaml)]
            var idx     = 0
            while (idx < fieldInfos.length) {
              val (fieldName, codec, isOptional, fieldOffset) = fieldInfos(idx)
              (codec.valueType: @switch) match {
                case 0 =>
                  val value = regs.getObject(fieldOffset)
                  if (isOptional && value == None) {
                    // skip None
                  } else if (isOptional && value.isInstanceOf[Some[?]]) {
                    val inner      = value.asInstanceOf[Some[Any]].get
                    val innerCodec = getInnerCodec(codec)
                    entries += ((Yaml.Scalar(fieldName), innerCodec.encodeValue(inner)))
                  } else {
                    entries += ((Yaml.Scalar(fieldName), codec.encodeValue(value)))
                  }
                case 1 =>
                  val value = regs.getInt(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Int]].encodeValue(value)))
                case 2 =>
                  val value = regs.getLong(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Long]].encodeValue(value)))
                case 3 =>
                  val value = regs.getFloat(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Float]].encodeValue(value)))
                case 4 =>
                  val value = regs.getDouble(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Double]].encodeValue(value)))
                case 5 =>
                  val value = regs.getBoolean(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Boolean]].encodeValue(value)))
                case 6 =>
                  val value = regs.getByte(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Byte]].encodeValue(value)))
                case 7 =>
                  val value = regs.getChar(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Char]].encodeValue(value)))
                case 8 =>
                  val value = regs.getShort(fieldOffset)
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Short]].encodeValue(value)))
                case _ =>
                  entries += ((Yaml.Scalar(fieldName), codec.asInstanceOf[YamlCodec[Unit]].encodeValue(())))
              }
              idx += 1
            }
            Yaml.Mapping(entries.result())
          }
        }
      } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isWrapper) {
      val wrapper = reflect.asWrapperUnknown.get.wrapper
      if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
        val binding      = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
        val wrappedCodec = deriveCodec(wrapper.wrapped).asInstanceOf[YamlCodec[Wrapped]]

        new YamlCodec[A]() {
          private[this] val unwrap = binding.unwrap
          private[this] val wrap   = binding.wrap

          override def decodeValue(yaml: Yaml): Either[YamlError, A] =
            wrappedCodec.decodeValue(yaml).map(wrap)

          override def encodeValue(x: A): Yaml =
            wrappedCodec.encodeValue(unwrap(x))
        }
      } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else {
      val dynamic = reflect.asDynamic.get
      if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
      else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    }
  }.asInstanceOf[YamlCodec[A]]

  private[this] def option[F[_, _], A](variant: Reflect.Variant[F, A]): Option[Reflect[F, ?]] = {
    val typeId = variant.typeId
    val cases  = variant.cases
    if (
      typeId.owner == Owner.fromPackagePath("scala") && typeId.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    ) cases(1).value.asRecord.map(_.fields(0).value)
    else None
  }

  private[this] def isOptionalField[F[_, _], A](reflect: Reflect[F, A]): Boolean =
    reflect.isVariant && {
      val variant = reflect.asVariant.get
      val typeId  = reflect.typeId
      val cases   = variant.cases
      typeId.owner == Owner.fromPackagePath("scala") && typeId.name == "Option" &&
      cases.length == 2 && cases(1).name == "Some"
    }

  private[this] def getDefaultValue[F[_, _], A](reflect: Reflect[F, A]): Option[Any] =
    reflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

  private[this] def getInnerCodec(optionCodec: YamlCodec[Any]): YamlCodec[Any] = {
    val codec = optionCodec.asInstanceOf[YamlCodec[Option[Any]]]
    new YamlCodec[Any]() {
      override def decodeValue(yaml: Yaml): Either[YamlError, Any] =
        codec.decodeValue(yaml).map(_.getOrElse(null))

      override def encodeValue(x: Any): Yaml =
        codec.encodeValue(Some(x)) match {
          case other => other
        }
    }
  }

  private def decodeScalar[A](yaml: Yaml)(parse: String => A): Either[YamlError, A] = yaml match {
    case Yaml.Scalar(v, _) =>
      try Right(parse(v.trim))
      catch {
        case e: Exception => Left(YamlError.parseError(s"Invalid value: ${e.getMessage}", 0, 0))
      }
    case _ => Left(YamlError.parseError("Expected scalar value", 0, 0))
  }

  private val dayOfWeekCodec: YamlCodec[DayOfWeek] = new YamlCodec[DayOfWeek]() {
    def decodeValue(yaml: Yaml): Either[YamlError, DayOfWeek] =
      decodeScalar(yaml)(s => DayOfWeek.valueOf(s.toUpperCase))
    def encodeValue(x: DayOfWeek): Yaml = Yaml.Scalar(x.toString)
  }

  private val durationCodec: YamlCodec[Duration] = new YamlCodec[Duration]() {
    def decodeValue(yaml: Yaml): Either[YamlError, Duration] = decodeScalar(yaml)(Duration.parse)
    def encodeValue(x: Duration): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val instantCodec: YamlCodec[Instant] = new YamlCodec[Instant]() {
    def decodeValue(yaml: Yaml): Either[YamlError, Instant] = decodeScalar(yaml)(Instant.parse)
    def encodeValue(x: Instant): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val localDateCodec: YamlCodec[LocalDate] = new YamlCodec[LocalDate]() {
    def decodeValue(yaml: Yaml): Either[YamlError, LocalDate] = decodeScalar(yaml)(LocalDate.parse)
    def encodeValue(x: LocalDate): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val localDateTimeCodec: YamlCodec[LocalDateTime] = new YamlCodec[LocalDateTime]() {
    def decodeValue(yaml: Yaml): Either[YamlError, LocalDateTime] = decodeScalar(yaml)(LocalDateTime.parse)
    def encodeValue(x: LocalDateTime): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val localTimeCodec: YamlCodec[LocalTime] = new YamlCodec[LocalTime]() {
    def decodeValue(yaml: Yaml): Either[YamlError, LocalTime] = decodeScalar(yaml)(LocalTime.parse)
    def encodeValue(x: LocalTime): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val monthCodec: YamlCodec[Month] = new YamlCodec[Month]() {
    def decodeValue(yaml: Yaml): Either[YamlError, Month] = decodeScalar(yaml)(s => Month.valueOf(s.toUpperCase))
    def encodeValue(x: Month): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val monthDayCodec: YamlCodec[MonthDay] = new YamlCodec[MonthDay]() {
    def decodeValue(yaml: Yaml): Either[YamlError, MonthDay] = decodeScalar(yaml)(MonthDay.parse)
    def encodeValue(x: MonthDay): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val offsetDateTimeCodec: YamlCodec[OffsetDateTime] = new YamlCodec[OffsetDateTime]() {
    def decodeValue(yaml: Yaml): Either[YamlError, OffsetDateTime] = decodeScalar(yaml)(OffsetDateTime.parse)
    def encodeValue(x: OffsetDateTime): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val offsetTimeCodec: YamlCodec[OffsetTime] = new YamlCodec[OffsetTime]() {
    def decodeValue(yaml: Yaml): Either[YamlError, OffsetTime] = decodeScalar(yaml)(OffsetTime.parse)
    def encodeValue(x: OffsetTime): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val periodCodec: YamlCodec[Period] = new YamlCodec[Period]() {
    def decodeValue(yaml: Yaml): Either[YamlError, Period] = decodeScalar(yaml)(Period.parse)
    def encodeValue(x: Period): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val yearCodec: YamlCodec[Year] = new YamlCodec[Year]() {
    def decodeValue(yaml: Yaml): Either[YamlError, Year] = decodeScalar(yaml)(Year.parse)
    def encodeValue(x: Year): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val yearMonthCodec: YamlCodec[YearMonth] = new YamlCodec[YearMonth]() {
    def decodeValue(yaml: Yaml): Either[YamlError, YearMonth] = decodeScalar(yaml)(YearMonth.parse)
    def encodeValue(x: YearMonth): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val zoneIdCodec: YamlCodec[ZoneId] = new YamlCodec[ZoneId]() {
    def decodeValue(yaml: Yaml): Either[YamlError, ZoneId] = decodeScalar(yaml)(ZoneId.of)
    def encodeValue(x: ZoneId): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val zoneOffsetCodec: YamlCodec[ZoneOffset] = new YamlCodec[ZoneOffset]() {
    def decodeValue(yaml: Yaml): Either[YamlError, ZoneOffset] = decodeScalar(yaml)(ZoneOffset.of)
    def encodeValue(x: ZoneOffset): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val zonedDateTimeCodec: YamlCodec[ZonedDateTime] = new YamlCodec[ZonedDateTime]() {
    def decodeValue(yaml: Yaml): Either[YamlError, ZonedDateTime] = decodeScalar(yaml)(ZonedDateTime.parse)
    def encodeValue(x: ZonedDateTime): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val currencyCodec: YamlCodec[Currency] = new YamlCodec[Currency]() {
    def decodeValue(yaml: Yaml): Either[YamlError, Currency] = decodeScalar(yaml)(Currency.getInstance)
    def encodeValue(x: Currency): Yaml                       = Yaml.Scalar(x.getCurrencyCode)
  }

  private val uuidCodec: YamlCodec[UUID] = new YamlCodec[UUID]() {
    def decodeValue(yaml: Yaml): Either[YamlError, UUID] = decodeScalar(yaml)(UUID.fromString)
    def encodeValue(x: UUID): Yaml                       = Yaml.Scalar(x.toString)
  }

  private val dynamicValueCodec: YamlCodec[DynamicValue] = new YamlCodec[DynamicValue]() {
    def decodeValue(yaml: Yaml): Either[YamlError, DynamicValue] = Right(yamlToDynamicValue(yaml))

    def encodeValue(x: DynamicValue): Yaml = dynamicValueToYaml(x)
  }

  private def dynamicValueToYaml(dv: DynamicValue): Yaml = dv match {
    case v: DynamicValue.Primitive => fromPrimitiveValue(v.value)
    case v: DynamicValue.Record    =>
      Yaml.Mapping(v.fields.map(kv => (Yaml.Scalar(kv._1): Yaml, dynamicValueToYaml(kv._2))))
    case v: DynamicValue.Variant =>
      Yaml.Mapping.fromStringKeys(v.caseNameValue -> dynamicValueToYaml(v.value))
    case v: DynamicValue.Sequence =>
      Yaml.Sequence(v.elements.map(dynamicValueToYaml))
    case v: DynamicValue.Map =>
      val entries       = v.entries
      val allStringKeys = entries.forall {
        case (DynamicValue.Primitive(_: PrimitiveValue.String), _) => true
        case _                                                     => false
      }
      if (allStringKeys) {
        Yaml.Mapping(entries.collect { case (DynamicValue.Primitive(k: PrimitiveValue.String), v) =>
          (Yaml.Scalar(k.value): Yaml, dynamicValueToYaml(v))
        })
      } else {
        Yaml.Sequence(entries.map { kv =>
          Yaml.Mapping.fromStringKeys("key" -> dynamicValueToYaml(kv._1), "value" -> dynamicValueToYaml(kv._2))
        })
      }
    case _: DynamicValue.Null.type => Yaml.NullValue
  }

  private def fromPrimitiveValue(pv: PrimitiveValue): Yaml = pv match {
    case v: PrimitiveValue.String     => Yaml.Scalar(v.value)
    case v: PrimitiveValue.Int        => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Int))
    case v: PrimitiveValue.Long       => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Int))
    case v: PrimitiveValue.Double     => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Float))
    case v: PrimitiveValue.Float      => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Float))
    case v: PrimitiveValue.Boolean    => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Bool))
    case v: PrimitiveValue.Byte       => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Int))
    case v: PrimitiveValue.Short      => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Int))
    case v: PrimitiveValue.Char       => Yaml.Scalar(v.value.toString)
    case v: PrimitiveValue.BigInt     => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Int))
    case v: PrimitiveValue.BigDecimal => Yaml.Scalar(v.value.toString, tag = Some(YamlTag.Float))
    case _: PrimitiveValue.Unit.type  => Yaml.NullValue
    case other                        => Yaml.Scalar(other.toString)
  }

  private def yamlToDynamicValue(yaml: Yaml): DynamicValue = yaml match {
    case Yaml.Mapping(entries) =>
      new DynamicValue.Record(entries.map { case (k, v) =>
        val key = k match {
          case Yaml.Scalar(s, _) => s
          case other             => other.print
        }
        (key, yamlToDynamicValue(v))
      })
    case Yaml.Sequence(elements) =>
      new DynamicValue.Sequence(elements.map(yamlToDynamicValue))
    case Yaml.Scalar(value, _) =>
      value match {
        case "true" | "True" | "TRUE"    => new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
        case "false" | "False" | "FALSE" => new DynamicValue.Primitive(new PrimitiveValue.Boolean(false))
        case _                           =>
          try {
            val bd        = BigDecimal(value)
            val longValue = bd.bigDecimal.longValue
            if (bd == BigDecimal(longValue)) {
              val intValue = longValue.toInt
              if (longValue == intValue) new DynamicValue.Primitive(new PrimitiveValue.Int(intValue))
              else new DynamicValue.Primitive(new PrimitiveValue.Long(longValue))
            } else new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(bd))
          } catch {
            case _: NumberFormatException => new DynamicValue.Primitive(new PrimitiveValue.String(value))
          }
      }
    case _: Yaml.NullValue.type => DynamicValue.Null
  }
}
