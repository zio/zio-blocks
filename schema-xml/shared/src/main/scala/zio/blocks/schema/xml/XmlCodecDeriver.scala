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
import zio.blocks.schema.derive.{BindingInstance, Deriver, InstanceOverride}
import zio.blocks.typeid.TypeId
import scala.annotation.switch
import scala.reflect.ClassTag
import scala.util.control.NonFatal

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
            name = name,
            defaultValue = getDefaultValue(fieldReflect),
            codec = D.instance(fieldReflect.metadata).force.asInstanceOf[XmlCodec[Any]],
            offset = offset,
            typeTag = Reflect.typeTag(fieldReflect),
            isOptional = fieldReflect.typeId.isOption,
            attrName = getXmlAttributeName(field),
            span = new DynamicOptic.Node.Field(name)
          )
          offset = RegisterOffset.add(Reflect.registerOffset(fieldReflect), offset)
          idx += 1
        }
      }
      new XmlCodec[A] {
        private[this] val deconstructor = recordBinding.deconstructor
        private[this] val constructor   = recordBinding.constructor
        private[this] val recordName    = typeId.name
        private[this] val nsInfo        = xmlNamespace.getNamespace(modifiers)

        override def decodeValue(xml: Xml): A = xml match {
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
                    val v =
                      try {
                        fieldInfo.codec.decodeValue(
                          new Xml.Element(XmlName("value"), Chunk.empty, Chunk.single(new Xml.Text(attrValue)))
                        )
                      } catch {
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
                      case _ => ()
                    }
                  } else {
                    if (fieldInfo.isOptional) regs.setObject(offset, None)
                    else error(s"Missing required attribute: $attrName")
                  }
                case _ =>
                  val fieldElem = childMap.get(fieldInfo.name)
                  if (fieldElem ne null) {
                    val hasElementChildren = fieldElem.children.exists(_.isInstanceOf[Xml.Element])
                    val xmlToDecode        =
                      if (hasElementChildren) fieldElem
                      else new Xml.Element(XmlName("value"), Chunk.empty, fieldElem.children)
                    val v =
                      try fieldInfo.codec.decodeValue(xmlToDecode)
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
                      case _ => ()
                    }
                  } else {
                    if (fieldInfo.isOptional) regs.setObject(offset, None)
                    else {
                      fieldInfo.defaultValue match {
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
                        case _ => error(s"Missing required field: ${fieldInfo.name}")
                      }
                    }
                  }
              }
              idx += 1
            }
            constructor.construct(regs, 0)
          case _ => error("Expected element for record")
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
              case _ =>
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
            case _ => new Xml.Element(XmlName(recordName), attr, chld)
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
          new XmlCodec[Option[Any]] {
            private[this] val innerCodec = codec.asInstanceOf[XmlCodec[Any]]

            override def decodeValue(xml: Xml): Option[Any] =
              try {
                new Some(innerCodec.decodeValue(xml match {
                  case e: Xml.Element if e.name.localName == "None" && e.children.isEmpty => return None
                  case e: Xml.Element if e.name.localName == "Some"                       =>
                    e.children.headOption match {
                      case Some(child) => child
                      case _           => error("Expected child element in Some")
                    }
                  case other => other
                }))
              } catch {
                case err if NonFatal(err) =>
                  error(new DynamicOptic.Node.Case("Some"), new DynamicOptic.Node.Field("value"), err)
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
          new XmlCodec[A] {
            private[this] val discriminator = binding.asInstanceOf[Binding.Variant[A]].discriminator
            private[this] val codecMap      = caseCodecs.toMap

            override def decodeValue(xml: Xml): A = xml match {
              case Xml.Element(name, _, children) =>
                val caseName = name.localName
                codecMap.get(caseName) match {
                  case Some(codec) =>
                    try {
                      codec.decodeValue(children.headOption match {
                        case Some(e: Xml.Element) if e.name.localName == caseName =>
                          // Old double-wrapped format: <Dog><Dog>...</Dog></Dog>
                          e
                        case Some(_) =>
                          // New format: <Dog><name>...</name></Dog>
                          // Decode the element itself, not its children
                          xml
                        case _ =>
                          // No children, decode as empty case
                          xmlElement(caseName)
                      })
                    } catch {
                      case err if NonFatal(err) => error(new DynamicOptic.Node.Case(caseName), err)
                    }
                  case _ => error(s"Unknown variant case: $caseName")
                }
              case _ => error("Expected element for variant")
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
        new XmlCodec[Col[Elem]] {
          private[this] val deconstructor = seqBinding.deconstructor
          private[this] val constructor   = seqBinding.constructor
          private[this] val elemCodec     = codec.asInstanceOf[XmlCodec[Elem]]
          private[this] val elemClassTag  = element.typeId.classTag.asInstanceOf[ClassTag[Elem]]

          override def decodeValue(xml: Xml): Col[Elem] = xml match {
            case Xml.Element(_, _, children) =>
              val builder = constructor.newBuilder[Elem](children.length)(elemClassTag)
              val iter    = children.iterator
              var idx     = 0
              while (iter.hasNext) {
                iter.next() match {
                  case e: Xml.Element =>
                    val wrappedForDecode = Xml.Element(XmlName("value"), Chunk.empty, e.children)
                    val elem             =
                      try elemCodec.decodeValue(wrappedForDecode)
                      catch {
                        case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                      }
                    idx += 1
                    constructor.add(builder, elem)
                  case _ =>
                }
              }
              constructor.result(builder)
            case _ => error("Expected element for sequence")
          }

          override def encodeValue(x: Col[Elem]): Xml = {
            val iter     = deconstructor.deconstruct(x)
            val len      = deconstructor.size(x)
            val children = new Array[Xml](len)
            var idx      = 0
            while (idx < len) {
              children(idx) = xmlFieldElement("item", elemCodec.encodeValue(iter.next()))
              idx += 1
            }
            xmlElementWithChildren("items", Chunk.fromArray(children))
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
        new XmlCodec[Map[Key, Value]] {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[XmlCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[XmlCodec[Value]]
          private[this] val keyRefl       = key.asInstanceOf[Reflect.Bound[Key]]

          override def decodeValue(xml: Xml): Map[Key, Value] = xml match {
            case Xml.Element(_, _, children) =>
              val builder = constructor.newObjectBuilder[Key, Value](children.length)
              val iter    = children.iterator
              var idx     = 0
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
                      val k =
                        try keyCodec.decodeValue(keyWrapped)
                        catch {
                          case err if NonFatal(err) => error(new DynamicOptic.Node.AtIndex(idx), err)
                        }
                      val v =
                        try valueCodec.decodeValue(valWrapped)
                        catch {
                          case err if NonFatal(err) =>
                            error(new DynamicOptic.Node.AtMapKey(keyRefl.toDynamicValue(k)), err)
                        }
                      idx += 1
                      constructor.addObject(builder, k, v)
                    } else error("Map entry missing key or value")
                  case _ => ()
                }
              }
              constructor.resultObject(builder)
            case _ => error("Expected element for map")
          }

          override def encodeValue(x: Map[Key, Value]): Xml = {
            val iter     = deconstructor.deconstruct(x)
            val len      = deconstructor.size(x)
            val children = new Array[Xml](len)
            var idx      = 0
            while (idx < len) {
              val kv      = iter.next()
              val keyElem = xmlFieldElement("key", keyCodec.encodeValue(deconstructor.getKey(kv)))
              val valElem = xmlFieldElement("value", valueCodec.encodeValue(deconstructor.getValue(kv)))
              children(idx) = xmlElement("entry", keyElem, valElem)
              idx += 1
            }
            xmlElementWithChildren("map", Chunk.fromArray(children))
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
        new XmlCodec[A] {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec.asInstanceOf[XmlCodec[Wrapped]]

          override def decodeValue(xml: Xml): A =
            try wrap(wrappedCodec.decodeValue(xml))
            catch {
              case err if NonFatal(err) => error(DynamicOptic.Node.Wrapped, err)
            }

          override def encodeValue(x: A): Xml = wrappedCodec.encodeValue(unwrap(x))
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlCodec[A]]]

  override def instanceOverrides: IndexedSeq[InstanceOverride] = {
    recursiveRecordCache.remove()
    super.instanceOverrides
  }

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

  private[this] def getDefaultValue[F[_, _], A](reflect: Reflect[F, A]): Option[Any] =
    reflect.asInstanceOf[Reflect[Binding, A]].getDefaultValue

  private[this] val dynamicValueCodec: XmlCodec[DynamicValue] = new XmlCodec[DynamicValue]() {
    def decodeValue(xml: Xml): DynamicValue = error("DynamicValue decoding not supported")

    def encodeValue(x: DynamicValue): Xml = Xml.Element("dynamic", new Xml.Text(x.toString))
  }
}

private class FieldInfo(
  val name: String,
  val defaultValue: Option[?],
  val codec: XmlCodec[Any],
  val offset: RegisterOffset,
  val typeTag: Int,
  val isOptional: Boolean,
  val attrName: Option[String],
  val span: DynamicOptic.Node.Field
)
