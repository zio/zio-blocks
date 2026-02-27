package zio.blocks.schema.xml

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.codec.BinaryFormat
import zio.blocks.schema.derive.{BindingInstance, Deriver}
import zio.blocks.typeid.TypeId
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import scala.reflect.ClassTag

object XmlFormat extends BinaryFormat("application/xml", XmlBinaryCodecDeriver)

object XmlBinaryCodecDeriver extends XmlBinaryCodecDeriver

class XmlBinaryCodecDeriver extends Deriver[XmlBinaryCodec] {
  import XmlBinaryCodec._

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[XmlBinaryCodec[A]] = {
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
  }.asInstanceOf[Lazy[XmlBinaryCodec[A]]]

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy {
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      val typeName      = typeId.name
      val namespaceOpt  = xmlNamespace.getNamespace(modifiers)
      // (fieldName, codec, isOptional, offset, xmlAttrName: Option[String])
      // xmlAttrName = Some(name) means encode as attribute with that name, None means encode as child element
      val fieldInfos = {
        var offset = RegisterOffset.Zero
        fields.map { field =>
          val fieldValue  = field.value
          val fieldCodec  = D.instance(fieldValue.metadata).force.asInstanceOf[XmlBinaryCodec[Any]]
          val fieldOffset = offset
          offset = RegisterOffset.add(offset, fieldCodec.valueOffset)
          (field.name, fieldCodec, isOptionalField(fieldValue), fieldOffset, getXmlAttributeName(field))
        }
      }
      new XmlBinaryCodec[A]() {
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
              val (fieldName, codec, isOptional, fieldOffset, xmlAttrName) = fieldInfos(idx)
              xmlAttrName match {
                case Some(attrName) =>
                  val attrValue = attrMap.get(attrName)
                  if (attrValue ne null) {
                    parseAttributeValue(attrValue, codec, regs, fieldOffset) match {
                      case Some(err) => return new Left(err)
                      case _         =>
                    }
                  } else {
                    if (isOptional) regs.setObject(fieldOffset, None)
                    else return new Left(XmlError.parseError(s"Missing required attribute: $attrName", 0, 0))
                  }
                case _ =>
                  val fieldElem = childMap.get(fieldName)
                  if (fieldElem ne null) {
                    val hasElementChildren = fieldElem.children.exists(_.isInstanceOf[Xml.Element])
                    val xmlToDecode        =
                      if (hasElementChildren) fieldElem
                      else new Xml.Element(XmlName("value"), Chunk.empty, fieldElem.children)
                    codec.decodeValue(xmlToDecode) match {
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
                      case l => return l.asInstanceOf[Either[XmlError, A]]
                    }
                  } else {
                    if (isOptional) regs.setObject(fieldOffset, None)
                    else {
                      getDefaultValue(fields(idx).value) match {
                        case Some(dv) =>
                          (codec.valueType: @switch) match {
                            case 0 => regs.setObject(fieldOffset, dv.asInstanceOf[AnyRef])
                            case 1 => regs.setInt(fieldOffset, dv.asInstanceOf[Int])
                            case 2 => regs.setLong(fieldOffset, dv.asInstanceOf[Long])
                            case 3 => regs.setFloat(fieldOffset, dv.asInstanceOf[Float])
                            case 4 => regs.setDouble(fieldOffset, dv.asInstanceOf[Double])
                            case 5 => regs.setBoolean(fieldOffset, dv.asInstanceOf[Boolean])
                            case 6 => regs.setByte(fieldOffset, dv.asInstanceOf[Byte])
                            case 7 => regs.setChar(fieldOffset, dv.asInstanceOf[Char])
                            case 8 => regs.setShort(fieldOffset, dv.asInstanceOf[Short])
                            case _ => ()
                          }
                        case _ => return new Left(XmlError.parseError(s"Missing required field: $fieldName", 0, 0))
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
            val (fieldName, codec, isOptional, fieldOffset, xmlAttrName) = fieldInfos(idx)
            xmlAttrName match {
              case Some(attrName) =>
                val xmlName = XmlName(attrName)
                (codec.valueType: @switch) match {
                  case 0 =>
                    val value = regs.getObject(fieldOffset)
                    if (isOptional) {
                      if (value ne None) {
                        attributes.addOne((xmlName, value.asInstanceOf[Some[Any]].get.toString))
                      }
                    } else attributes.addOne((xmlName, value.toString))
                  case 1 => attributes.addOne((xmlName, regs.getInt(fieldOffset).toString))
                  case 2 => attributes.addOne((xmlName, regs.getLong(fieldOffset).toString))
                  case 3 => attributes.addOne((xmlName, regs.getFloat(fieldOffset).toString))
                  case 4 => attributes.addOne((xmlName, regs.getDouble(fieldOffset).toString))
                  case 5 => attributes.addOne((xmlName, regs.getBoolean(fieldOffset).toString))
                  case 6 => attributes.addOne((xmlName, regs.getByte(fieldOffset).toString))
                  case 7 => attributes.addOne((xmlName, regs.getChar(fieldOffset).toString))
                  case 8 => attributes.addOne((xmlName, regs.getShort(fieldOffset).toString))
                  case _ => ()
                }
              case None =>
                (codec.valueType: @switch) match {
                  case 0 =>
                    val value = regs.getObject(fieldOffset)
                    if (isOptional) {
                      if (value ne None) {
                        val encodedValue =
                          codec.encodeValue(value) match {
                            case e: Xml.Element =>
                              if (e.children.isEmpty) xmlElement("value")
                              else e.children.head
                            case _ => xmlElement("value")
                          }
                        children.addOne(xmlFieldElement(fieldName, encodedValue))
                      }
                    } else children.addOne(xmlFieldElement(fieldName, codec.encodeValue(value)))
                  case 1 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Int]].encodeValue(regs.getInt(fieldOffset))
                      )
                    )
                  case 2 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Long]].encodeValue(regs.getLong(fieldOffset))
                      )
                    )
                  case 3 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Float]].encodeValue(regs.getFloat(fieldOffset))
                      )
                    )
                  case 4 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Double]].encodeValue(regs.getDouble(fieldOffset))
                      )
                    )
                  case 5 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Boolean]].encodeValue(regs.getBoolean(fieldOffset))
                      )
                    )
                  case 6 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Byte]].encodeValue(regs.getByte(fieldOffset))
                      )
                    )
                  case 7 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Char]].encodeValue(regs.getChar(fieldOffset))
                      )
                    )
                  case 8 =>
                    children.addOne(
                      xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Short]].encodeValue(regs.getShort(fieldOffset))
                      )
                    )
                  case _ =>
                    children.addOne(
                      xmlFieldElement(fieldName, codec.asInstanceOf[XmlBinaryCodec[Unit]].encodeValue(()))
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
  }.asInstanceOf[Lazy[XmlBinaryCodec[A]]]

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      if (typeId.isOption) {
        D.instance(cases(1).value.asRecord.get.fields(0).value.metadata).map { codec =>
          new XmlBinaryCodec[Option[Any]]() {
            private[this] val innerCodec = codec.asInstanceOf[XmlBinaryCodec[Any]]

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

            override def nullValue: Option[Any] = None
          }
        }
      } else
        Lazy {
          val caseCodecs = cases.map { case_ =>
            val caseName  = case_.name
            val caseCodec = D.instance(case_.value.metadata).force.asInstanceOf[XmlBinaryCodec[A]]
            (caseName, caseCodec)
          }
          new XmlBinaryCodec[A]() {
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
                case e: Xml.Element if e.name.localName == caseName =>
                  // Already wrapped with correct name, return as-is
                  e
                case _ =>
                  // Wrap with case name
                  xmlElement(caseName, inner)
              }
            }
          }
        }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[XmlBinaryCodec[A]]]

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[C[A]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val seqBinding = binding.asInstanceOf[Binding.Seq[Col, Elem]]
      D.instance(element.metadata).map { codec =>
        new XmlBinaryCodec[Col[Elem]]() {
          private[this] val deconstructor = seqBinding.deconstructor
          private[this] val constructor   = seqBinding.constructor
          private[this] val elemCodec     = codec.asInstanceOf[XmlBinaryCodec[Elem]]
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

          override def nullValue: Col[Elem] = constructor.empty[Elem](elemClassTag)
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, A]].instance
  }.asInstanceOf[Lazy[XmlBinaryCodec[C[A]]]]

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[M[K, V]]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val mapBinding = binding.asInstanceOf[Binding.Map[Map, Key, Value]]
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (codec1, codec2) =>
        new XmlBinaryCodec[Map[Key, Value]]() {
          private[this] val deconstructor = mapBinding.deconstructor
          private[this] val constructor   = mapBinding.constructor
          private[this] val keyCodec      = codec1.asInstanceOf[XmlBinaryCodec[Key]]
          private[this] val valueCodec    = codec2.asInstanceOf[XmlBinaryCodec[Value]]

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

          override def nullValue: Map[Key, Value] = constructor.emptyObject[Key, Value]
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlBinaryCodec[M[K, V]]]]

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[DynamicValue]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) Lazy(dynamicValueCodec)
    else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlBinaryCodec[DynamicValue]]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[A]] = {
    if (binding.isInstanceOf[Binding[?, ?]]) {
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
      D.instance(wrapped.metadata).map { codec =>
        new XmlBinaryCodec[A]() {
          private[this] val unwrap       = wrapperBinding.unwrap
          private[this] val wrap         = wrapperBinding.wrap
          private[this] val wrappedCodec = codec.asInstanceOf[XmlBinaryCodec[Wrapped]]

          override def decodeValue(xml: Xml): Either[XmlError, A] = wrappedCodec.decodeValue(xml).map(wrap)

          override def encodeValue(x: A): Xml = wrappedCodec.encodeValue(unwrap(x))
        }
      }
    } else binding.asInstanceOf[BindingInstance[TC, ?, ?]].instance
  }.asInstanceOf[Lazy[XmlBinaryCodec[A]]]

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
    codec: XmlBinaryCodec[Any],
    regs: Registers,
    offset: RegisterOffset.RegisterOffset
  ): Option[XmlError] =
    codec.decodeValue(new Xml.Element(XmlName("value"), Chunk.empty, Chunk.single(new Xml.Text(attrValue)))) match {
      case Right(v) =>
        (codec.valueType: @switch) match {
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

  private val dayOfWeekCodec: XmlBinaryCodec[DayOfWeek] = new XmlBinaryCodec[DayOfWeek]() {
    def decodeValue(xml: Xml): Either[XmlError, DayOfWeek] = decodeText(xml)(s => DayOfWeek.valueOf(s.toUpperCase))

    def encodeValue(x: DayOfWeek): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val durationCodec: XmlBinaryCodec[Duration] = new XmlBinaryCodec[Duration]() {
    def decodeValue(xml: Xml): Either[XmlError, Duration] = decodeText(xml)(Duration.parse)

    def encodeValue(x: Duration): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val instantCodec: XmlBinaryCodec[Instant] = new XmlBinaryCodec[Instant]() {
    def decodeValue(xml: Xml): Either[XmlError, Instant] = decodeText(xml)(Instant.parse)

    def encodeValue(x: Instant): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val localDateCodec: XmlBinaryCodec[LocalDate] = new XmlBinaryCodec[LocalDate]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalDate] = decodeText(xml)(LocalDate.parse)

    def encodeValue(x: LocalDate): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val localDateTimeCodec: XmlBinaryCodec[LocalDateTime] = new XmlBinaryCodec[LocalDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalDateTime] = decodeText(xml)(LocalDateTime.parse)

    def encodeValue(x: LocalDateTime): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val localTimeCodec: XmlBinaryCodec[LocalTime] = new XmlBinaryCodec[LocalTime]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalTime] = decodeText(xml)(LocalTime.parse)

    def encodeValue(x: LocalTime): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val monthCodec: XmlBinaryCodec[Month] = new XmlBinaryCodec[Month]() {
    def decodeValue(xml: Xml): Either[XmlError, Month] = decodeText(xml)(s => Month.valueOf(s.toUpperCase))

    def encodeValue(x: Month): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val monthDayCodec: XmlBinaryCodec[MonthDay] = new XmlBinaryCodec[MonthDay]() {
    def decodeValue(xml: Xml): Either[XmlError, MonthDay] = decodeText(xml)(MonthDay.parse)

    def encodeValue(x: MonthDay): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val offsetDateTimeCodec: XmlBinaryCodec[OffsetDateTime] = new XmlBinaryCodec[OffsetDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, OffsetDateTime] = decodeText(xml)(OffsetDateTime.parse)

    def encodeValue(x: OffsetDateTime): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val offsetTimeCodec: XmlBinaryCodec[OffsetTime] = new XmlBinaryCodec[OffsetTime]() {
    def decodeValue(xml: Xml): Either[XmlError, OffsetTime] = decodeText(xml)(OffsetTime.parse)

    def encodeValue(x: OffsetTime): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val periodCodec: XmlBinaryCodec[Period] = new XmlBinaryCodec[Period]() {
    def decodeValue(xml: Xml): Either[XmlError, Period] = decodeText(xml)(Period.parse)

    def encodeValue(x: Period): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val yearCodec: XmlBinaryCodec[Year] = new XmlBinaryCodec[Year]() {
    def decodeValue(xml: Xml): Either[XmlError, Year] = decodeText(xml)(Year.parse)

    def encodeValue(x: Year): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val yearMonthCodec: XmlBinaryCodec[YearMonth] = new XmlBinaryCodec[YearMonth]() {
    def decodeValue(xml: Xml): Either[XmlError, YearMonth] = decodeText(xml)(YearMonth.parse)

    def encodeValue(x: YearMonth): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val zoneIdCodec: XmlBinaryCodec[ZoneId] = new XmlBinaryCodec[ZoneId]() {
    def decodeValue(xml: Xml): Either[XmlError, ZoneId] = decodeText(xml)(ZoneId.of)

    def encodeValue(x: ZoneId): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val zoneOffsetCodec: XmlBinaryCodec[ZoneOffset] = new XmlBinaryCodec[ZoneOffset]() {
    def decodeValue(xml: Xml): Either[XmlError, ZoneOffset] = decodeText(xml)(ZoneOffset.of)

    def encodeValue(x: ZoneOffset): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val zonedDateTimeCodec: XmlBinaryCodec[ZonedDateTime] = new XmlBinaryCodec[ZonedDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, ZonedDateTime] = decodeText(xml)(ZonedDateTime.parse)

    def encodeValue(x: ZonedDateTime): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val currencyCodec: XmlBinaryCodec[Currency] = new XmlBinaryCodec[Currency]() {
    def decodeValue(xml: Xml): Either[XmlError, Currency] = decodeText(xml)(Currency.getInstance)

    def encodeValue(x: Currency): Xml = Xml.Element("value", new Xml.Text(x.getCurrencyCode))
  }

  private val uuidCodec: XmlBinaryCodec[UUID] = new XmlBinaryCodec[UUID]() {
    def decodeValue(xml: Xml): Either[XmlError, UUID] = decodeText(xml)(UUID.fromString)

    def encodeValue(x: UUID): Xml = Xml.Element("value", new Xml.Text(x.toString))
  }

  private val dynamicValueCodec: XmlBinaryCodec[DynamicValue] = new XmlBinaryCodec[DynamicValue]() {
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
}
