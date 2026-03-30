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
import zio.blocks.schema.json.{JsonCodec, NameMapper}
import zio.blocks.typeid.TypeId
import scala.annotation.switch
import scala.reflect.ClassTag
import scala.util.control.NonFatal

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
  ): Lazy[YamlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      primitiveType match {
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
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[YamlCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val isRecursive   = fields.exists(_.value.isInstanceOf[Reflect.Deferred[F, ?]])
      var fieldInfos    =
        if (isRecursive) recursiveRecordCache.get.get(typeId)
        else null
      val deriveCodecs = fieldInfos eq null
      if (deriveCodecs) {
        val len = fields.length
        fieldInfos = new Array[FieldInfo](len)
        if (isRecursive) recursiveRecordCache.get.put(typeId, fieldInfos)
        var offset = 0L
        var idx    = 0
        while (idx < len) {
          val field        = fields(idx)
          val fieldReflect = field.value
          val name         = field.name
          fieldInfos(idx) = new FieldInfo(
            name = NameMapper.KebabCase.apply(name),
            defaultValue = getDefaultValue(fieldReflect),
            codec = D.instance(fieldReflect.metadata).force.asInstanceOf[YamlCodec[Any]],
            offset = offset,
            typeTag = Reflect.typeTag(fieldReflect),
            isOptional = fieldReflect.typeId.isOption,
            span = new DynamicOptic.Node.Field(name)
          )
          offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
          idx += 1
        }
      }

      new YamlCodec[A] {
        private[this] val deconstructor = recordBinding.deconstructor
        private[this] val constructor   = recordBinding.constructor

        override def decodeValue(yaml: Yaml): A = yaml match {
          case Yaml.Mapping(entries) =>
            val childMap = new java.util.HashMap[String, Yaml](entries.length << 1) {
              entries.foreach { kv =>
                kv._1 match {
                  case s: Yaml.Scalar => put(s.value, kv._2)
                  case _              =>
                }
              }
            }
            val regs = Registers(constructor.usedRegisters)
            var idx  = 0
            while (idx < fieldInfos.length) {
              val fieldInfo  = fieldInfos(idx)
              val offset     = fieldInfo.offset
              val fieldValue = childMap.get(fieldInfo.name)
              if (fieldValue ne null) {
                val v =
                  try fieldInfo.codec.decodeValue(fieldValue)
                  catch {
                    case err if NonFatal(err) => error(fieldInfo.span, err)
                  }
                (fieldInfo.typeTag: @switch) match {
                  case 0 => regs.setObject(offset, v.asInstanceOf[AnyRef])
                  case 1 => regs.setInt(offset, v.asInstanceOf[Int])
                  case 2 => regs.setLong(offset, v.asInstanceOf[Long])
                  case 3 => regs.setFloat(offset, v.asInstanceOf[Float])
                  case 4 => regs.setDouble(offset, v.asInstanceOf[Double])
                  case 5 => regs.setBoolean(offset, v.asInstanceOf[Boolean])
                  case 6 => regs.setByte(offset, v.asInstanceOf[Byte])
                  case 7 => regs.setChar(offset, v.asInstanceOf[Char])
                  case 8 => regs.setShort(offset, v.asInstanceOf[Short])
                  case _ =>
                }
              } else {
                if (fieldInfo.isOptional) regs.setObject(offset, None)
                else {
                  fieldInfo.defaultValue match {
                    case Some(v) =>
                      (fieldInfo.typeTag: @switch) match {
                        case 0 => regs.setObject(offset, v.asInstanceOf[AnyRef])
                        case 1 => regs.setInt(offset, v.asInstanceOf[Int])
                        case 2 => regs.setLong(offset, v.asInstanceOf[Long])
                        case 3 => regs.setFloat(offset, v.asInstanceOf[Float])
                        case 4 => regs.setDouble(offset, v.asInstanceOf[Double])
                        case 5 => regs.setBoolean(offset, v.asInstanceOf[Boolean])
                        case 6 => regs.setByte(offset, v.asInstanceOf[Byte])
                        case 7 => regs.setChar(offset, v.asInstanceOf[Char])
                        case 8 => regs.setShort(offset, v.asInstanceOf[Short])
                        case _ => ()
                      }
                    case _ => error(s"Missing required field: ${fieldInfo.name}")
                  }
                }
              }
              idx += 1
            }
            constructor.construct(regs, 0L)
          case _ => error("Expected mapping for record")
        }

        override def encodeValue(x: A): Yaml = {
          val regs = Registers(deconstructor.usedRegisters)
          deconstructor.deconstruct(regs, 0, x)
          val entries = Chunk.newBuilder[(Yaml, Yaml)]
          var idx     = 0
          while (idx < fieldInfos.length) {
            val fieldInfo = fieldInfos(idx)
            val name      = fieldInfo.name
            val offset    = fieldInfo.offset
            val codec     = fieldInfo.codec
            val key       = new Yaml.Scalar(name)
            (fieldInfo.typeTag: @switch) match {
              case 0 =>
                val value = regs.getObject(offset)
                if (!fieldInfo.isOptional || (value ne None)) {
                  entries.addOne((key, codec.asInstanceOf[YamlCodec[Any]].encodeValue(value)))
                }
              case 1 =>
                val value = regs.getInt(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Int]].encodeValue(value)))
              case 2 =>
                val value = regs.getLong(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Long]].encodeValue(value)))
              case 3 =>
                val value = regs.getFloat(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Float]].encodeValue(value)))
              case 4 =>
                val value = regs.getDouble(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Double]].encodeValue(value)))
              case 5 =>
                val value = regs.getBoolean(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Boolean]].encodeValue(value)))
              case 6 =>
                val value = regs.getByte(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Byte]].encodeValue(value)))
              case 7 =>
                val value = regs.getChar(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Char]].encodeValue(value)))
              case 8 =>
                val value = regs.getShort(offset)
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Short]].encodeValue(value)))
              case _ =>
                entries.addOne((key, codec.asInstanceOf[YamlCodec[Unit]].encodeValue(())))
            }
            idx += 1
          }
          new Yaml.Mapping(entries.result())
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[YamlCodec[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      if (typeId.isOption) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { codec =>
          new YamlCodec[Option[Any]]() {
            private[this] val innerCodec = codec.asInstanceOf[YamlCodec[Any]]

            override def decodeValue(yaml: Yaml): Option[Any] =
              try {
                new Some(innerCodec.decodeValue(yaml match {
                  case Yaml.NullValue                               => return None
                  case Yaml.Scalar(v, _) if v == "null" || v == "~" => return None
                  case Yaml.Mapping(entries)                        =>
                    val firstKey = entries.headOption.map(_._1)
                    firstKey match {
                      case Some(Yaml.Scalar(k, _)) if k == "None" => return None
                      case Some(Yaml.Scalar(k, _)) if k == "Some" =>
                        entries.headOption.map(_._2) match {
                          case Some(v) => v
                          case _       => error("Expected value in Some")
                        }
                      case _ => yaml
                    }
                  case _ => yaml
                }))
              } catch {
                case err if NonFatal(err) =>
                  error(new DynamicOptic.Node.Case("Some"), new DynamicOptic.Node.Field("value"), err)
              }

            override def encodeValue(x: Option[Any]): Yaml = x match {
              case Some(value) => innerCodec.encodeValue(value)
              case _           => Yaml.NullValue
            }
          }
        }
      } else
        Lazy {
          val caseCodecs = cases.map { case_ =>
            val caseName  = case_.name
            val caseCodec = D.instance(case_.value.metadata).force.asInstanceOf[YamlCodec[A]]
            (caseName, caseCodec)
          }
          new YamlCodec[A] {
            private[this] val discriminator = binding.asInstanceOf[Binding.Variant[A]].discriminator
            private[this] val codecMap      = caseCodecs.toMap

            override def decodeValue(yaml: Yaml): A = yaml match {
              case Yaml.Mapping(entries) if entries.length == 1 =>
                val (key, value) = entries(0)
                key match {
                  case Yaml.Scalar(caseName, _) =>
                    codecMap.get(caseName) match {
                      case Some(codec) =>
                        try codec.decodeValue(value)
                        catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.Case(caseName), err)
                        }
                      case _ => error(s"Unknown variant case: $caseName")
                    }
                  case _ => error("Expected string key for variant")
                }
              case _ => error("Expected mapping with single key for variant")
            }

            override def encodeValue(x: A): Yaml = {
              val caseName = cases(discriminator.discriminate(x)).name
              val inner    = codecMap(caseName).encodeValue(x)
              Yaml.Mapping.fromStringKeys((caseName, inner))
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[YamlCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { codec =>
        new YamlCodec[Col[Elem]] {
          private[this] val deconstructor = seqBinding.deconstructor
          private[this] val constructor   = seqBinding.constructor
          private[this] val elemCodec     = codec.asInstanceOf[YamlCodec[Elem]]
          private[this] val elemClassTag  = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

          override def decodeValue(yaml: Yaml): Col[Elem] = yaml match {
            case Yaml.Sequence(elements) =>
              val builder = constructor.newBuilder[Elem](elements.length)(elemClassTag)
              val iter    = elements.iterator
              var idx     = 0
              while (iter.hasNext) {
                val elem =
                  try elemCodec.decodeValue(iter.next())
                  catch {
                    case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                  }
                idx += 1
                constructor.add(builder, elem)
              }
              constructor.result(builder)
            case _ => error("Expected sequence")
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
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[YamlCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        new YamlCodec[Map[Key, Value]] {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[YamlCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[YamlCodec[Value]]
          private[this] val keyRefl       = key.asInstanceOf[Reflect.Bound[Key]]

          override def decodeValue(yaml: Yaml): Map[Key, Value] = yaml match {
            case Yaml.Mapping(entries) =>
              val builder = constructor.newObjectBuilder[Key, Value](entries.length)
              val iter    = entries.iterator
              var idx     = 0
              while (iter.hasNext) {
                val kv  = iter.next()
                val key =
                  try keyCodec.decodeValue(kv._1)
                  catch {
                    case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                  }
                val value =
                  try valueCodec.decodeValue(kv._2)
                  catch {
                    case err if NonFatal(err) =>
                      error(new DynamicOptic.Node.AtMapKey(keyRefl.toDynamicValue(key)), err)
                  }
                idx += 1
                constructor.addObject(builder, key, value)
              }
              constructor.resultObject(builder)
            case _ => error("Expected mapping for map")
          }

          override def encodeValue(x: Map[Key, Value]): Yaml = {
            val iter    = deconstructor.deconstruct(x)
            val entries = Chunk.newBuilder[(Yaml, Yaml)]
            while (iter.hasNext) {
              val kv = iter.next()
              val k  = deconstructor.getKey(kv)
              val v  = deconstructor.getValue(kv)
              entries.addOne((keyCodec.encodeValue(k), valueCodec.encodeValue(v)))
            }
            new Yaml.Mapping(entries.result())
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[YamlCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[YamlCodec[DynamicValue]]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[YamlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      D.instance(wrapped.metadata).map { codec =>
        new YamlCodec[A]() {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec.asInstanceOf[YamlCodec[Wrapped]]

          override def decodeValue(yaml: Yaml): A =
            try wrap(wrappedCodec.decodeValue(yaml))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeValue(x: A): Yaml = wrappedCodec.encodeValue(unwrap(x))
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[YamlCodec[A]]]

  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  private[this] val recursiveRecordCache =
    new ThreadLocal[java.util.HashMap[TypeId[?], Array[FieldInfo]]] {
      override def initialValue: java.util.HashMap[TypeId[?], Array[FieldInfo]] = new java.util.HashMap
    }

  private[this] val dynamicValueCodec: YamlCodec[DynamicValue] = new YamlCodec[DynamicValue]() {
    def decodeValue(yaml: Yaml): DynamicValue = yamlToDynamicValue(yaml)

    def encodeValue(x: DynamicValue): Yaml = dynamicValueToYaml(x)
  }

  private[this] def getDefaultValue[F[_, _], A](reflect: Reflect[F, A]): Option[Any] =
    reflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

  private[this] def dynamicValueToYaml(dv: DynamicValue): Yaml = dv match {
    case v: DynamicValue.Primitive => fromPrimitiveValue(v.value)
    case v: DynamicValue.Record    =>
      new Yaml.Mapping(v.fields.map(kv => (new Yaml.Scalar(kv._1), dynamicValueToYaml(kv._2))))
    case v: DynamicValue.Variant  => Yaml.Mapping.fromStringKeys(v.caseNameValue -> dynamicValueToYaml(v.value))
    case v: DynamicValue.Sequence => new Yaml.Sequence(v.elements.map(dynamicValueToYaml))
    case v: DynamicValue.Map      =>
      val entries       = v.entries
      val allStringKeys = entries.forall {
        case (DynamicValue.Primitive(_: PrimitiveValue.String), _) => true
        case _                                                     => false
      }
      if (allStringKeys) {
        new Yaml.Mapping(entries.collect { case (DynamicValue.Primitive(k: PrimitiveValue.String), v) =>
          (new Yaml.Scalar(k.value), dynamicValueToYaml(v))
        })
      } else {
        new Yaml.Sequence(entries.map { kv =>
          Yaml.Mapping.fromStringKeys("key" -> dynamicValueToYaml(kv._1), "value" -> dynamicValueToYaml(kv._2))
        })
      }
    case _: DynamicValue.Null.type => Yaml.NullValue
  }

  private[this] def fromPrimitiveValue(pv: PrimitiveValue): Yaml = pv match {
    case v: PrimitiveValue.String => new Yaml.Scalar(v.value)
    case v: PrimitiveValue.Int    => new Yaml.Scalar(v.value.toString, tag = new Some(YamlTag.Int))
    case v: PrimitiveValue.Long   => new Yaml.Scalar(v.value.toString, tag = new Some(YamlTag.Int))
    case v: PrimitiveValue.Double =>
      new Yaml.Scalar(JsonCodec.doubleCodec.encodeToString(v.value), tag = new Some(YamlTag.Float))
    case v: PrimitiveValue.Float =>
      new Yaml.Scalar(JsonCodec.floatCodec.encodeToString(v.value), tag = new Some(YamlTag.Float))
    case v: PrimitiveValue.Boolean => new Yaml.Scalar(v.value.toString, tag = new Some(YamlTag.Bool))
    case v: PrimitiveValue.Byte    => new Yaml.Scalar(v.value.toString, tag = new Some(YamlTag.Int))
    case v: PrimitiveValue.Short   => new Yaml.Scalar(v.value.toString, tag = new Some(YamlTag.Int))
    case v: PrimitiveValue.Char    => new Yaml.Scalar(v.value.toString)
    case v: PrimitiveValue.BigInt  =>
      new Yaml.Scalar(JsonCodec.bigIntCodec.encodeToString(v.value), tag = new Some(YamlTag.Int))
    case v: PrimitiveValue.BigDecimal =>
      new Yaml.Scalar(JsonCodec.bigDecimalCodec.encodeToString(v.value), tag = new Some(YamlTag.Float))
    case _: PrimitiveValue.Unit.type => Yaml.NullValue
    case other                       => new Yaml.Scalar(other.toString)
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
    case Yaml.Sequence(elements) => new DynamicValue.Sequence(elements.map(yamlToDynamicValue))
    case Yaml.Scalar(value, _)   =>
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

private class FieldInfo(
  val name: String,
  val defaultValue: Option[?],
  val codec: YamlCodec[Any],
  val offset: RegisterOffset.RegisterOffset,
  val typeTag: Int,
  val isOptional: Boolean,
  val span: DynamicOptic.Node.Field
)
