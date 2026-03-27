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

package zio.blocks.schema.xml

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.schema.json.{Json, JsonCodec}
import zio.blocks.typeid.TypeId
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import scala.reflect.ClassTag

object XmlFormat extends BinaryFormat("application/xml", XmlCodecDeriver)

object XmlCodecDeriver extends XmlCodecDeriver

class XmlCodecDeriver extends Deriver[XmlCodec] {
  import XmlCodec._

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[XmlCodec[A]] = {
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
  }.asInstanceOf[Lazy[XmlCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val typeName      = typeId.name
      val namespaceOpt  = xmlNamespace.getNamespace(modifiers)
      // (fieldName, codec, isOptional, offset, xmlAttrName: Option[String])
      // xmlAttrName = Some(name) means encode as attribute with that name, None means encode as child element
      var offset     = 0L
      val fieldInfos = fields.map { field =>
        val fieldReflect = field.value
        val fieldInfo    = new FieldInfo(
          name = field.name,
          codec = D.instance(fieldReflect.metadata).force.asInstanceOf[XmlCodec[Any]],
          offset = offset,
          typeTag = Reflect.typeTag(fieldReflect),
          isOptional = isOptionalField(fieldReflect),
          attrName = getXmlAttributeName(field)
        )
        offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
        fieldInfo
      }
      new XmlCodec[A]() {
        private[this] val deconstructor = recordBinding.deconstructor
        private[this] val constructor   = recordBinding.constructor
        private[this] val recordName    = typeName
        private[this] val nsInfo        = namespaceOpt

        override def decodeValue(xml: Xml): Either[XmlError, A] = xml match {
          case e: Xml.Element =>
            val childMap = new java.util.HashMap[String, Xml.Element](e.children.length << 1) {
              e.children.foreach {
                case elem: Xml.Element => put(elem.name.localName, elem)
                case _                 =>
              }
            }
            val attrMap = new java.util.HashMap[String, String](e.attributes.length << 1) {
              e.attributes.foreach(nv => put(nv._1.localName, nv._2))
            }
            val regs = Registers(constructor.usedRegisters)
            val len  = fieldInfos.length
            var idx  = 0
            while (idx < len) {
              val fieldInfo = fieldInfos(idx)
              val offset    = fieldInfo.offset
              fieldInfo.attrName match {
                case Some(attrName) =>
                  val attrValue = attrMap.get(attrName)
                  if (attrValue ne null) {
                    parseAttributeValue(attrValue, fieldInfo.codec, regs, offset, fieldInfo.typeTag) match {
                      case Some(err) => return new Left(err)
                      case _         =>
                    }
                  } else {
                    if (fieldInfo.isOptional) regs.setObject(offset, None)
                    else return new Left(XmlError.parseError(s"Missing required attribute: $attrName", 0, 0))
                  }
                case _ =>
                  val fieldElem = childMap.get(fieldInfo.name)
                  if (fieldElem ne null) {
                    val hasElementChildren = fieldElem.children.exists(_.isInstanceOf[Xml.Element])
                    val xmlToDecode        =
                      if (hasElementChildren) fieldElem
                      else new Xml.Element(XmlName("value"), Chunk.empty, fieldElem.children)
                    fieldInfo.codec.decodeValue(xmlToDecode) match {
                      case Right(v) =>
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
                      case l => return l.asInstanceOf[Either[XmlError, A]]
                    }
                  } else {
                    if (fieldInfo.isOptional) regs.setObject(offset, None)
                    else {
                      getDefaultValue(fields(idx).value) match {
                        case Some(dv) =>
                          (fieldInfo.typeTag: @switch) match {
                            case 0 => regs.setObject(offset, dv.asInstanceOf[AnyRef])
                            case 1 => regs.setInt(offset, dv.asInstanceOf[Int])
                            case 2 => regs.setLong(offset, dv.asInstanceOf[Long])
                            case 3 => regs.setFloat(offset, dv.asInstanceOf[Float])
                            case 4 => regs.setDouble(offset, dv.asInstanceOf[Double])
                            case 5 => regs.setBoolean(offset, dv.asInstanceOf[Boolean])
                            case 6 => regs.setByte(offset, dv.asInstanceOf[Byte])
                            case 7 => regs.setChar(offset, dv.asInstanceOf[Char])
                            case 8 => regs.setShort(offset, dv.asInstanceOf[Short])
                            case _ => ()
                          }
                        case _ =>
                          return new Left(XmlError.parseError(s"Missing required field: ${fieldInfo.name}", 0, 0))
                      }
                    }
                  }
              }
              idx += 1
            }
            new Right(constructor.construct(regs, 0))
          case _ => new Left(XmlError.parseError("Expected element for record", 0, 0))
        }

        override def encodeValue(x: A): Xml = {
          val regs = Registers(deconstructor.usedRegisters)
          deconstructor.deconstruct(regs, 0, x)
          val attributes = ChunkBuilder.make[(XmlName, String)]()
          val children   = ChunkBuilder.make[Xml]()
          val len        = fieldInfos.length
          var idx        = 0
          while (idx < len) {
            val fieldInfo = fieldInfos(idx)
            val offset    = fieldInfo.offset
            fieldInfo.attrName match {
              case Some(attrName) =>
                val xmlName = XmlName(attrName)
                (fieldInfo.typeTag: @switch) match {
                  case 0 =>
                    val value = regs.getObject(offset)
                    if (fieldInfo.isOptional) {
                      if (value ne None) attributes.addOne((xmlName, value.asInstanceOf[Some[Any]].get.toString))
                    } else attributes.addOne((xmlName, value.toString))
                  case 1 => attributes.addOne((xmlName, regs.getInt(offset).toString))
                  case 2 => attributes.addOne((xmlName, regs.getLong(offset).toString))
                  case 3 => attributes.addOne((xmlName, regs.getFloat(offset).toString))
                  case 4 => attributes.addOne((xmlName, regs.getDouble(offset).toString))
                  case 5 => attributes.addOne((xmlName, regs.getBoolean(offset).toString))
                  case 6 => attributes.addOne((xmlName, regs.getByte(offset).toString))
                  case 7 => attributes.addOne((xmlName, regs.getChar(offset).toString))
                  case 8 => attributes.addOne((xmlName, regs.getShort(offset).toString))
                  case _ => ()
                }
              case None =>
                val name  = fieldInfo.name
                val codec = fieldInfo.codec
                (fieldInfo.typeTag: @switch) match {
                  case 0 =>
                    val value = regs.getObject(offset)
                    if (fieldInfo.isOptional) {
                      if (value ne None) {
                        val encodedValue = codec.encodeValue(value) match {
                          case e: Xml.Element =>
                            if (e.children.isEmpty) xmlElement("value")
                            else e.children.head
                          case _ => xmlElement("value")
                        }
                        children.addOne(xmlFieldElement(name, encodedValue))
                      }
                    } else children.addOne(xmlFieldElement(name, codec.encodeValue(value)))
                  case 1 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Int]].encodeValue(regs.getInt(offset)))
                    )
                  case 2 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Long]].encodeValue(regs.getLong(offset)))
                    )
                  case 3 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Float]].encodeValue(regs.getFloat(offset)))
                    )
                  case 4 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Double]].encodeValue(regs.getDouble(offset)))
                    )
                  case 5 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Boolean]].encodeValue(regs.getBoolean(offset)))
                    )
                  case 6 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Byte]].encodeValue(regs.getByte(offset)))
                    )
                  case 7 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Char]].encodeValue(regs.getChar(offset)))
                    )
                  case 8 =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Short]].encodeValue(regs.getShort(offset)))
                    )
                  case _ =>
                    children.addOne(
                      xmlFieldElement(name, codec.asInstanceOf[XmlCodec[Unit]].encodeValue(()))
                    )
                }
            }
            idx += 1
          }
          val chld = children.result()
          val attr = attributes.result()
          nsInfo match {
            case Some((uri, prefix)) =>
              if (prefix.nonEmpty) {
                new Xml.Element(
                  XmlName(recordName, new Some(prefix), None),
                  (XmlName(prefix, new Some("xmlns"), None), uri) +: attr,
                  chld
                )
              } else new Xml.Element(XmlName(recordName), (XmlName("xmlns"), uri) +: attr, chld)
            case None => new Xml.Element(XmlName(recordName), attr, chld)
          }
        }
      }
    }
    else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[XmlCodec[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      if (typeId.isOption) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { codec =>
          new XmlCodec[Option[Any]]() {
            private[this] val innerCodec = codec.asInstanceOf[XmlCodec[Any]]

            override def decodeValue(xml: Xml): Either[XmlError, Option[Any]] = xml match {
              case e: Xml.Element if e.name.localName == "None" && e.children.isEmpty => new Right(None)
              case e: Xml.Element if e.name.localName == "Some"                       =>
                e.children.headOption match {
                  case Some(child) =>
                    innerCodec.decodeValue(child) match {
                      case Right(v) => new Right(new Some(v))
                      case l        => l.asInstanceOf[Either[XmlError, Option[Any]]]
                    }
                  case _ => Left(XmlError.parseError("Expected child element in Some", 0, 0))
                }
              case other =>
                innerCodec.decodeValue(other) match {
                  case Right(v) => new Right(new Some(v))
                  case l        => l.asInstanceOf[Either[XmlError, Option[Any]]]
                }
            }

            override def encodeValue(x: Option[Any]): Xml = x match {
              case Some(value) => xmlElement("Some", innerCodec.encodeValue(value))
              case _           => xmlElement("None")
            }
          }
        }
      } else
        Lazy {
          val caseCodecs = cases.map { case_ =>
            val caseName  = case_.name
            val caseCodec = D.instance(case_.value.metadata).force.asInstanceOf[XmlCodec[A]]
            (caseName, caseCodec)
          }
          new XmlCodec[A]() {
            private[this] val discriminator = binding.asInstanceOf[Binding.Variant[A]].discriminator
            private[this] val codecMap      = caseCodecs.toMap

            override def decodeValue(xml: Xml): Either[XmlError, A] = xml match {
              case Xml.Element(name, _, children) =>
                val caseName = name.localName
                codecMap.get(caseName) match {
                  case Some(codec) =>
                    children.headOption match {
                      case Some(e: Xml.Element) if e.name.localName == caseName =>
                        // Old double-wrapped format: <Dog><Dog>...</Dog></Dog>
                        codec.decodeValue(e)
                      case Some(_) =>
                        // New format: <Dog><name>...</name></Dog>
                        // Decode the element itself, not its children
                        codec.decodeValue(xml)
                      case _ =>
                        // No children, decode as empty case
                        codec.decodeValue(xmlElement(caseName))
                    }
                  case _ => new Left(XmlError.parseError(s"Unknown variant case: $caseName", 0, 0))
                }
              case _ => new Left(XmlError.parseError("Expected element for variant", 0, 0))
            }

            override def encodeValue(x: A): Xml = {
              val caseName = cases(discriminator.discriminate(x)).name
              val inner    = codecMap(caseName).encodeValue(x)
              inner match {
                case e: Xml.Element if e.name.localName == caseName => // Already wrapped with correct name
                  e
                case _ => // Wrap with case name
                  xmlElement(caseName, inner)
              }
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[XmlCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { codec =>
        new XmlCodec[Col[Elem]]() {
          private[this] val deconstructor = seqBinding.deconstructor
          private[this] val constructor   = seqBinding.constructor
          private[this] val elemCodec     = codec.asInstanceOf[XmlCodec[Elem]]
          private[this] val elemClassTag  = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

          override def decodeValue(xml: Xml): Either[XmlError, Col[Elem]] = xml match {
            case Xml.Element(_, _, children) =>
              val builder = constructor.newBuilder[Elem](children.length)(elemClassTag)
              val iter    = children.iterator
              while (iter.hasNext) {
                iter.next() match {
                  case e: Xml.Element =>
                    val wrappedForDecode = Xml.Element(XmlName("value"), Chunk.empty, e.children)
                    elemCodec.decodeValue(wrappedForDecode) match {
                      case Right(v) => constructor.add(builder, v)
                      case l        => return l.asInstanceOf[Either[XmlError, Col[Elem]]]
                    }
                  case _ => ()
                }
              }
              new Right(constructor.result(builder))
            case _ => new Left(XmlError.parseError("Expected element for sequence", 0, 0))
          }

          override def encodeValue(x: Col[Elem]): Xml = {
            val iter     = deconstructor.deconstruct(x)
            val children = ChunkBuilder.make[Xml]()
            while (iter.hasNext) children.addOne(xmlFieldElement("item", elemCodec.encodeValue(iter.next())))
            xmlElementWithChildren("items", children.result())
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[XmlCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        new XmlCodec[Map[Key, Value]]() {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[XmlCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[XmlCodec[Value]]

          override def decodeValue(xml: Xml): Either[XmlError, Map[Key, Value]] = xml match {
            case Xml.Element(_, _, children) =>
              val builder = constructor.newObjectBuilder[Key, Value](children.length)
              val iter    = children.iterator
              while (iter.hasNext) {
                iter.next() match {
                  case Xml.Element(name, _, entryChildren) if name.localName == "entry" =>
                    var keyXml: Xml   = null
                    var valueXml: Xml = null
                    entryChildren.foreach {
                      case e: Xml.Element =>
                        val localName = e.name.localName
                        if (localName == "key") keyXml = e
                        else if (localName == "value") valueXml = e
                      case _ =>
                    }
                    if ((keyXml ne null) && (valueXml ne null)) {
                      // Wrap children in <value> element for codec compatibility
                      val keyWrapped =
                        new Xml.Element(XmlName("value"), Chunk.empty, keyXml.asInstanceOf[Xml.Element].children)
                      val valWrapped =
                        new Xml.Element(XmlName("value"), Chunk.empty, valueXml.asInstanceOf[Xml.Element].children)
                      keyCodec.decodeValue(keyWrapped) match {
                        case Right(k) =>
                          valueCodec.decodeValue(valWrapped) match {
                            case Right(v) => constructor.addObject(builder, k, v)
                            case l        => return l.asInstanceOf[Either[XmlError, Map[Key, Value]]]
                          }
                        case l => return l.asInstanceOf[Either[XmlError, Map[Key, Value]]]
                      }
                    } else return new Left(XmlError.parseError("Map entry missing key or value", 0, 0))
                  case _ => ()
                }
              }
              new Right(constructor.resultObject(builder))
            case _ => new Left(XmlError.parseError("Expected element for map", 0, 0))
          }

          override def encodeValue(x: Map[Key, Value]): Xml = {
            val iter     = deconstructor.deconstruct(x)
            val children = ChunkBuilder.make[Xml]()
            while (iter.hasNext) {
              val kv      = iter.next()
              val keyElem = xmlFieldElement("key", keyCodec.encodeValue(deconstructor.getKey(kv)))
              val valElem = xmlFieldElement("value", valueCodec.encodeValue(deconstructor.getValue(kv)))
              children.addOne(xmlElement("entry", keyElem, valElem))
            }
            xmlElementWithChildren("map", children.result())
          }
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlCodec[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlCodec[DynamicValue]]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      D.instance(wrapped.metadata).map { codec =>
        new XmlCodec[A]() {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec.asInstanceOf[XmlCodec[Wrapped]]

          override def decodeValue(xml: Xml): Either[XmlError, A] = wrappedCodec.decodeValue(xml).map(wrap)

          override def encodeValue(x: A): Xml = wrappedCodec.encodeValue(unwrap(x))
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlCodec[A]]]

  type Elem
  type Key
  type Value
  type Wrapped
  type Col[_]
  type Map[_, _]
  type TC[_]

  private[this] def xmlElement(name: String, children: Xml*): Xml.Element =
    new Xml.Element(XmlName(name), Chunk.empty, Chunk.from(children))

  private[this] def xmlElementWithChildren(name: String, children: Chunk[Xml]): Xml.Element =
    new Xml.Element(XmlName(name), Chunk.empty, children)

  private[this] def xmlFieldElement(name: String, encodedValue: Xml): Xml.Element = encodedValue match {
    case e: Xml.Element =>
      if (e.name.localName == "value") Xml.Element(XmlName(name), Chunk.empty, e.children)
      else Xml.Element(XmlName(name), e.attributes, e.children)
    case other => new Xml.Element(XmlName(name), Chunk.empty, Chunk.single(other))
  }

  private[this] def getXmlAttributeName[F[_, _], A](field: Term[F, A, ?]): Option[String] =
    field.modifiers.collectFirst { case Modifier.config("xml.attribute", value) =>
      if (value.nonEmpty) value
      else field.name
    }

  private[this] def isOptionalField[F[_, _], A](reflect: Reflect[F, A]): Boolean = reflect.typeId.isOption

  private[this] def getDefaultValue[F[_, _], A](reflect: Reflect[F, A]): Option[Any] =
    reflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

  private[this] def parseAttributeValue(
    attrValue: String,
    codec: XmlCodec[Any],
    regs: Registers,
    offset: RegisterOffset.RegisterOffset,
    typeTag: Int
  ): Option[XmlError] =
    codec.decodeValue(new Xml.Element(XmlName("value"), Chunk.empty, Chunk.single(new Xml.Text(attrValue)))) match {
      case Right(v) =>
        (typeTag: @switch) match {
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
        None
      case Left(err) => Some(err)
    }

  private val dayOfWeekCodec: XmlCodec[DayOfWeek] = new XmlCodec[DayOfWeek]() {
    def decodeValue(xml: Xml): Either[XmlError, DayOfWeek] = decodeText(xml)(s => DayOfWeek.valueOf(s.toUpperCase))

    def encodeValue(x: DayOfWeek): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val durationCodec: XmlCodec[Duration] = new XmlCodec[Duration]() {
    def decodeValue(xml: Xml): Either[XmlError, Duration] = decodeText(xml, Json.durationRawCodec)

    def encodeValue(x: Duration): Xml = Xml.Element("value", new Xml.Text(Json.durationRawCodec.encodeToString(x)))
  }

  private val instantCodec: XmlCodec[Instant] = new XmlCodec[Instant]() {
    def decodeValue(xml: Xml): Either[XmlError, Instant] = decodeText(xml, Json.instantRawCodec)

    def encodeValue(x: Instant): Xml = Xml.Element("value", new Xml.Text(Json.instantRawCodec.encodeToString(x)))
  }

  private val localDateCodec: XmlCodec[LocalDate] = new XmlCodec[LocalDate]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalDate] = decodeText(xml, Json.localDateRawCodec)

    def encodeValue(x: LocalDate): Xml = Xml.Element("value", new Xml.Text(Json.localDateRawCodec.encodeToString(x)))
  }

  private val localDateTimeCodec: XmlCodec[LocalDateTime] = new XmlCodec[LocalDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalDateTime] = decodeText(xml, Json.localDateTimeRawCodec)

    def encodeValue(x: LocalDateTime): Xml =
      Xml.Element("value", new Xml.Text(Json.localDateTimeRawCodec.encodeToString(x)))
  }

  private val localTimeCodec: XmlCodec[LocalTime] = new XmlCodec[LocalTime]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalTime] = decodeText(xml, Json.localTimeRawCodec)

    def encodeValue(x: LocalTime): Xml = Xml.Element("value", new Xml.Text(Json.localTimeRawCodec.encodeToString(x)))
  }

  private val monthCodec: XmlCodec[Month] = new XmlCodec[Month]() {
    def decodeValue(xml: Xml): Either[XmlError, Month] = decodeText(xml)(s => Month.valueOf(s.toUpperCase))

    def encodeValue(x: Month): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val monthDayCodec: XmlCodec[MonthDay] = new XmlCodec[MonthDay]() {
    def decodeValue(xml: Xml): Either[XmlError, MonthDay] = decodeText(xml, Json.monthDayRawCodec)

    def encodeValue(x: MonthDay): Xml = Xml.Element("value", new Xml.Text(Json.monthDayRawCodec.encodeToString(x)))
  }

  private val offsetDateTimeCodec: XmlCodec[OffsetDateTime] = new XmlCodec[OffsetDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, OffsetDateTime] = decodeText(xml, Json.offsetDateTimeRawCodec)

    def encodeValue(x: OffsetDateTime): Xml =
      Xml.Element("value", new Xml.Text(Json.offsetDateTimeRawCodec.encodeToString(x)))
  }

  private val offsetTimeCodec: XmlCodec[OffsetTime] = new XmlCodec[OffsetTime]() {
    def decodeValue(xml: Xml): Either[XmlError, OffsetTime] = decodeText(xml, Json.offsetTimeRawCodec)

    def encodeValue(x: OffsetTime): Xml = Xml.Element("value", new Xml.Text(Json.offsetTimeRawCodec.encodeToString(x)))
  }

  private val periodCodec: XmlCodec[Period] = new XmlCodec[Period]() {
    def decodeValue(xml: Xml): Either[XmlError, Period] = decodeText(xml, Json.periodRawCodec)

    def encodeValue(x: Period): Xml = Xml.Element("value", new Xml.Text(Json.periodRawCodec.encodeToString(x)))
  }

  private val yearCodec: XmlCodec[Year] = new XmlCodec[Year]() {
    def decodeValue(xml: Xml): Either[XmlError, Year] = decodeText(xml)(Year.parse)

    def encodeValue(x: Year): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val yearMonthCodec: XmlCodec[YearMonth] = new XmlCodec[YearMonth]() {
    def decodeValue(xml: Xml): Either[XmlError, YearMonth] = decodeText(xml)(YearMonth.parse)

    def encodeValue(x: YearMonth): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val zoneIdCodec: XmlCodec[ZoneId] = new XmlCodec[ZoneId]() {
    def decodeValue(xml: Xml): Either[XmlError, ZoneId] = decodeText(xml)(ZoneId.of)

    def encodeValue(x: ZoneId): Xml = Xml.Element("value", new Xml.Text(x.getId))
  }

  private val zoneOffsetCodec: XmlCodec[ZoneOffset] = new XmlCodec[ZoneOffset]() {
    def decodeValue(xml: Xml): Either[XmlError, ZoneOffset] = decodeText(xml)(ZoneOffset.of)

    def encodeValue(x: ZoneOffset): Xml = Xml.Element("value", new Xml.Text(x.getId))
  }

  private val zonedDateTimeCodec: XmlCodec[ZonedDateTime] = new XmlCodec[ZonedDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, ZonedDateTime] = decodeText(xml, Json.zonedDateTimeRawCodec)

    def encodeValue(x: ZonedDateTime): Xml =
      Xml.Element("value", new Xml.Text(Json.zonedDateTimeRawCodec.encodeToString(x)))
  }

  private val currencyCodec: XmlCodec[Currency] = new XmlCodec[Currency]() {
    def decodeValue(xml: Xml): Either[XmlError, Currency] = decodeText(xml)(Currency.getInstance)

    def encodeValue(x: Currency): Xml = Xml.Element("value", new Xml.Text(x.getCurrencyCode))
  }

  private val uuidCodec: XmlCodec[UUID] = new XmlCodec[UUID]() {
    def decodeValue(xml: Xml): Either[XmlError, UUID] = decodeText(xml)(UUID.fromString)

    def encodeValue(x: UUID): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val dynamicValueCodec: XmlCodec[DynamicValue] = new XmlCodec[DynamicValue]() {
    def decodeValue(xml: Xml): Either[XmlError, DynamicValue] =
      new Left(XmlError.parseError("DynamicValue decoding not supported", 0, 0))

    def encodeValue(x: DynamicValue): Xml = Xml.Element("dynamic", new Xml.Text(x.toString))
  }

  private[this] def decodeText[A](xml: Xml)(parse: String => A): Either[XmlError, A] = xml match {
    case e: Xml.Element if e.name.localName == "value" =>
      e.children.headOption match {
        case Some(t: Xml.Text) =>
          try new Right(parse(t.value.trim))
          catch {
            case e: Exception => new Left(XmlError.parseError(s"Invalid value: ${e.getMessage}", 0, 0))
          }
        case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
      }
    case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
  }

  private[this] def decodeText[A](xml: Xml, codec: JsonCodec[A]): Either[XmlError, A] = xml match {
    case e: Xml.Element if e.name.localName == "value" =>
      e.children.headOption match {
        case Some(t: Xml.Text) =>
          codec.decode(t.value.trim) match {
            case Left(e) => new Left(XmlError.parseError(s"Invalid value: ${e.getMessage}", 0, 0))
            case r       => r.asInstanceOf[Either[XmlError, A]]
          }
        case _ => new Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
      }
    case _ => new Left(XmlError.parseError("Expected <value> element", 0, 0))
  }
}

private case class FieldInfo(
  name: String,
  codec: XmlCodec[Any],
  offset: RegisterOffset,
  typeTag: Int,
  isOptional: Boolean,
  attrName: Option[String]
)
