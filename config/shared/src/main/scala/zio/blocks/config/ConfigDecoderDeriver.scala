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

package zio.blocks.config

import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.typeid.TypeId

import scala.annotation.switch
import scala.reflect.ClassTag
import scala.util.control.NonFatal

object ConfigDecoderDeriver extends ConfigDecoderDeriver

class ConfigDecoderDeriver extends Deriver[ConfigDecoder] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[ConfigDecoder[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      new ConfigDecoder[A] {
        def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A] =
          source.get(prefix) match {
            case Some(cv) => parsePrimitive(primitiveType, cv.value, prefix, source.sourceId)
            case None     =>
              defaultValue match {
                case Some(v) => new Right(v)
                case None    => new Left(new ::(ConfigError.MissingKey(prefix, source.sourceId), Nil))
              }
          }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ConfigDecoder[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val len           = fields.length
      val fieldInfos    = new Array[ConfigFieldInfo](len)
      var offset        = 0L
      var idx           = 0
      while (idx < len) {
        val field        = fields(idx)
        val fieldReflect = field.value
        fieldInfos(idx) = new ConfigFieldInfo(
          name = field.name,
          defaultValue = getDefaultValue(fieldReflect),
          decoder = D.instance(fieldReflect.metadata).force.asInstanceOf[ConfigDecoder[Any]],
          offset = offset,
          typeTag = Reflect.publicTypeTag(fieldReflect),
          isOptional = fieldReflect.typeId.isOption
        )
        offset = RegisterOffset.add(Reflect.publicRegisterOffset(fieldReflect), offset)
        idx += 1
      }

      new ConfigDecoder[A] {
        private[this] val constructor = recordBinding.constructor

        def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A] = {
          val regs   = Registers(constructor.usedRegisters)
          var errors = List.empty[ConfigError]
          var idx    = 0
          while (idx < fieldInfos.length) {
            val fi       = fieldInfos(idx)
            val fieldKey = if (prefix.isEmpty) fi.name else s"$prefix.${fi.name}"
            fi.decoder.decode(source, fieldKey) match {
              case Right(v) =>
                setRegister(regs, fi.offset, fi.typeTag, v)
              case Left(errs) =>
                if (fi.isOptional) {
                  if (errs.forall(_.isInstanceOf[ConfigError.MissingKey])) {
                    regs.setObject(fi.offset, None)
                  } else {
                    errors = errs.toList ::: errors
                  }
                } else {
                  fi.defaultValue match {
                    case Some(v) if errs.forall(_.isInstanceOf[ConfigError.MissingKey]) =>
                      setRegister(regs, fi.offset, fi.typeTag, v)
                    case _ =>
                      errors = errs.toList ::: errors
                  }
                }
            }
            idx += 1
          }
          errors match {
            case head :: tail => new Left(new ::(head, tail))
            case Nil          => new Right(constructor.construct(regs, 0L))
          }
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ConfigDecoder[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      if (typeId.isOption) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { innerDecoder =>
          new ConfigDecoder[Option[Any]] {
            private[this] val inner = innerDecoder.asInstanceOf[ConfigDecoder[Any]]

            def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], Option[Any]] =
              inner.decode(source, prefix) match {
                case Right(v)   => new Right(Some(v))
                case Left(errs) =>
                  if (errs.forall(_.isInstanceOf[ConfigError.MissingKey])) new Right(None)
                  else new Left(errs)
              }
          }
        }
      } else {
        Lazy {
          val caseDecoders = cases.map { case_ =>
            (case_.name, D.instance(case_.value.metadata).force.asInstanceOf[ConfigDecoder[A]])
          }
          new ConfigDecoder[A] {
            private[this] val decoderMap = caseDecoders.toMap

            def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A] = {
              val typeKey = if (prefix.isEmpty) "type" else s"$prefix.type"
              source.get(typeKey) match {
                case Some(cv) =>
                  decoderMap.get(cv.value) match {
                    case Some(decoder) => decoder.decode(source, prefix)
                    case None          =>
                      new Left(
                        new ::(
                          ConfigError.InvalidValue(
                            typeKey,
                            cv.value,
                            s"one of: ${decoderMap.keys.mkString(", ")}",
                            source.sourceId
                          ),
                          Nil
                        )
                      )
                  }
                case None =>
                  new Left(new ::(ConfigError.MissingKey(typeKey, source.sourceId), Nil))
              }
            }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ConfigDecoder[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { decoder =>
        new ConfigDecoder[Col[Elem]] {
          private[this] val constructor  = seqBinding.constructor
          private[this] val elemDecoder  = decoder.asInstanceOf[ConfigDecoder[Elem]]
          private[this] val elemClassTag = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

          def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], Col[Elem]] = {
            var errors   = List.empty[ConfigError]
            val builder  = constructor.newBuilder[Elem](8)(elemClassTag)
            var idx      = 0
            var continue = true
            while (continue) {
              val elemKey   = if (prefix.isEmpty) idx.toString else s"$prefix.$idx"
              val hasKey    = source.get(elemKey).isDefined
              val hasSubKey = !source.getAll(elemKey).isEmpty
              if (hasKey || hasSubKey) {
                elemDecoder.decode(source, elemKey) match {
                  case Right(v)   => constructor.add(builder, v)
                  case Left(errs) => errors = errs.toList ::: errors
                }
                idx += 1
              } else {
                continue = false
              }
            }
            if (idx == 0 && errors.isEmpty) {
              source.get(prefix) match {
                case Some(cv) if cv.value.nonEmpty =>
                  val parts   = cv.value.split(",").map(_.trim)
                  var partIdx = 0
                  while (partIdx < parts.length) {
                    val partSource = ConfigSource.fromMap(Map(prefix -> parts(partIdx)), source.sourceId)
                    elemDecoder.decode(partSource, prefix) match {
                      case Right(v)   => constructor.add(builder, v)
                      case Left(errs) => errors = errs.toList ::: errors
                    }
                    partIdx += 1
                  }
                case _ => ()
              }
            }
            errors match {
              case head :: tail => new Left(new ::(head, tail))
              case Nil          => new Right(constructor.result(builder))
            }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ConfigDecoder[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (keyDec, valDec) =>
        new ConfigDecoder[Map[Key, Value]] {
          private[this] val constructor  = mapBinding.constructor
          private[this] val keyDecoder   = keyDec.asInstanceOf[ConfigDecoder[Key]]
          private[this] val valueDecoder = valDec.asInstanceOf[ConfigDecoder[Value]]

          def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], Map[Key, Value]] = {
            val all       = source.getAll(prefix)
            var errors    = List.empty[ConfigError]
            val dotPrefix = if (prefix.isEmpty) "" else s"$prefix."
            val subKeys   = all.keys.flatMap { k =>
              val suffix =
                if (dotPrefix.isEmpty) k
                else if (k.startsWith(dotPrefix)) k.drop(dotPrefix.length)
                else ""
              if (suffix.nonEmpty) {
                val dotIdx = suffix.indexOf('.')
                if (dotIdx < 0) Some(suffix) else Some(suffix.substring(0, dotIdx))
              } else None
            }.toSet
            val builder = constructor.newObjectBuilder[Key, Value](subKeys.size)
            subKeys.foreach { subKey =>
              val keySource = ConfigSource.fromMap(Map(prefix -> subKey), source.sourceId)
              keyDecoder.decode(keySource, prefix) match {
                case Right(k) =>
                  val valKey = if (prefix.isEmpty) subKey else s"$prefix.$subKey"
                  valueDecoder.decode(source, valKey) match {
                    case Right(v)   => constructor.addObject(builder, k, v)
                    case Left(errs) => errors = errs.toList ::: errors
                  }
                case Left(errs) => errors = errs.toList ::: errors
              }
            }
            errors match {
              case head :: tail => new Left(new ::(head, tail))
              case Nil          => new Right(constructor.resultObject(builder))
            }
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ConfigDecoder[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      new ConfigDecoder[DynamicValue] {
        def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], DynamicValue] =
          source.get(prefix) match {
            case Some(cv) => new Right(new DynamicValue.Primitive(new PrimitiveValue.String(cv.value)))
            case None     => new Right(DynamicValue.Null)
          }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[DynamicValue]]]

  override def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[ConfigDecoder[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      D.instance(wrapped.metadata).map { decoder =>
        new ConfigDecoder[A] {
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val innerDecoder = decoder.asInstanceOf[ConfigDecoder[Wrapped]]

          def decode(source: ConfigSource, prefix: String): Either[::[ConfigError], A] =
            innerDecoder.decode(source, prefix) match {
              case Right(v) =>
                try new Right(wrap(v))
                catch {
                  case err if NonFatal(err) =>
                    new Left(
                      new ::(
                        ConfigError.InvalidValue(prefix, v.toString, typeId.name, source.sourceId, Some(err)),
                        Nil
                      )
                    )
                }
              case l: Left[?, ?] => l.asInstanceOf[Either[::[ConfigError], A]]
            }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[ConfigDecoder[A]]]

  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  private def getDefaultValue[F[_, _], A](reflect: Reflect[F, A]): Option[Any] =
    reflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

  private def setRegister(regs: Registers, offset: RegisterOffset, typeTag: Int, value: Any): Unit =
    (typeTag: @switch) match {
      case 0 => regs.setObject(offset, value.asInstanceOf[AnyRef])
      case 1 => regs.setInt(offset, value.asInstanceOf[Int])
      case 2 => regs.setLong(offset, value.asInstanceOf[Long])
      case 3 => regs.setFloat(offset, value.asInstanceOf[Float])
      case 4 => regs.setDouble(offset, value.asInstanceOf[Double])
      case 5 => regs.setBoolean(offset, value.asInstanceOf[Boolean])
      case 6 => regs.setByte(offset, value.asInstanceOf[Byte])
      case 7 => regs.setChar(offset, value.asInstanceOf[Char])
      case 8 => regs.setShort(offset, value.asInstanceOf[Short])
      case _ => ()
    }

  private def parsePrimitive[A](
    primitiveType: PrimitiveType[A],
    raw: String,
    path: String,
    sourceId: String
  ): Either[::[ConfigError], A] =
    try {
      val value: Any = primitiveType match {
        case _: PrimitiveType.Unit.type => ()
        case _: PrimitiveType.Boolean   => parseBooleanValue(raw)
        case _: PrimitiveType.Byte      => java.lang.Byte.parseByte(raw)
        case _: PrimitiveType.Short     => java.lang.Short.parseShort(raw)
        case _: PrimitiveType.Int       => java.lang.Integer.parseInt(raw)
        case _: PrimitiveType.Long      => java.lang.Long.parseLong(raw)
        case _: PrimitiveType.Float     => java.lang.Float.parseFloat(raw)
        case _: PrimitiveType.Double    => java.lang.Double.parseDouble(raw)
        case _: PrimitiveType.Char      =>
          if (raw.length == 1) raw.charAt(0)
          else throw new IllegalArgumentException(s"Expected single character, got '$raw'")
        case _: PrimitiveType.String         => raw
        case _: PrimitiveType.BigInt         => BigInt(raw)
        case _: PrimitiveType.BigDecimal     => BigDecimal(raw)
        case _: PrimitiveType.UUID           => java.util.UUID.fromString(raw)
        case _: PrimitiveType.Duration       => java.time.Duration.parse(raw)
        case _: PrimitiveType.Instant        => java.time.Instant.parse(raw)
        case _: PrimitiveType.LocalDate      => java.time.LocalDate.parse(raw)
        case _: PrimitiveType.LocalDateTime  => java.time.LocalDateTime.parse(raw)
        case _: PrimitiveType.LocalTime      => java.time.LocalTime.parse(raw)
        case _: PrimitiveType.OffsetDateTime => java.time.OffsetDateTime.parse(raw)
        case _: PrimitiveType.OffsetTime     => java.time.OffsetTime.parse(raw)
        case _: PrimitiveType.ZonedDateTime  => java.time.ZonedDateTime.parse(raw)
        case _: PrimitiveType.ZoneId         => java.time.ZoneId.of(raw)
        case _: PrimitiveType.ZoneOffset     => java.time.ZoneOffset.of(raw)
        case _: PrimitiveType.Period         => java.time.Period.parse(raw)
        case _: PrimitiveType.Year           => java.time.Year.parse(raw)
        case _: PrimitiveType.YearMonth      => java.time.YearMonth.parse(raw)
        case _: PrimitiveType.Month          => java.time.Month.valueOf(raw.toUpperCase)
        case _: PrimitiveType.MonthDay       => java.time.MonthDay.parse(raw)
        case _: PrimitiveType.DayOfWeek      => java.time.DayOfWeek.valueOf(raw.toUpperCase)
        case _: PrimitiveType.Currency       => java.util.Currency.getInstance(raw)
      }
      new Right(value.asInstanceOf[A])
    } catch {
      case e if NonFatal(e) =>
        val expected = primitiveType.getClass.getSimpleName.stripSuffix("$")
        new Left(new ::(ConfigError.InvalidValue(path, raw, expected, sourceId, Some(e)), Nil))
    }

  private def parseBooleanValue(raw: String): Boolean = {
    val lower = raw.toLowerCase
    if (lower == "true" || lower == "1" || lower == "yes" || lower == "on") true
    else if (lower == "false" || lower == "0" || lower == "no" || lower == "off") false
    else throw new IllegalArgumentException(s"Cannot parse '$raw' as Boolean")
  }
}

private[config] class ConfigFieldInfo(
  val name: String,
  val defaultValue: Option[Any],
  val decoder: ConfigDecoder[Any],
  val offset: RegisterOffset,
  val typeTag: Int,
  val isOptional: Boolean
)
