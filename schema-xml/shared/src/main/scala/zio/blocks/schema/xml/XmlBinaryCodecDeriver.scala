package zio.blocks.schema.xml

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

object XmlFormat extends BinaryFormat("application/xml", XmlBinaryCodecDeriver)

object XmlBinaryCodecDeriver extends XmlBinaryCodecDeriver

class XmlBinaryCodecDeriver extends Deriver[XmlBinaryCodec] {
  import XmlBinaryCodec._

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[XmlBinaryCodec[A]] =
    Lazy(deriveCodec(new Reflect.Primitive(primitiveType, typeId, binding, doc, modifiers)))

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[A]] = Lazy {
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
    binding: Binding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[A]] = Lazy {
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
    binding: Binding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[C[A]]] = Lazy {
    deriveCodec(
      new Reflect.Sequence(element.asInstanceOf[Reflect[Binding, A]], typeId, binding, doc, modifiers)
    )
  }

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding[BindingType.Map[M], M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[M[K, V]]] = Lazy {
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
    binding: Binding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[DynamicValue]] =
    Lazy(deriveCodec(new Reflect.Dynamic(binding, TypeId.of[DynamicValue], doc, modifiers)))

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[XmlBinaryCodec[A]] = Lazy {
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

  private def xmlElement(name: String, children: Xml*): Xml.Element =
    Xml.Element(XmlName(name), Chunk.empty, Chunk.from(children))

  private def xmlElementWithChildren(name: String, children: Chunk[Xml]): Xml.Element =
    Xml.Element(XmlName(name), Chunk.empty, children)

  private def xmlFieldElement(name: String, encodedValue: Xml): Xml.Element =
    encodedValue match {
      case Xml.Element(n, _, children) if n.localName == "value" =>
        Xml.Element(XmlName(name), Chunk.empty, children)
      case Xml.Element(_, attrs, children) =>
        Xml.Element(XmlName(name), attrs, children)
      case other =>
        Xml.Element(XmlName(name), Chunk.empty, Chunk(other))
    }

  private[this] def deriveCodec[F[_, _], A](reflect: Reflect[F, A]): XmlBinaryCodec[A] = {
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
            val innerCodec = deriveCodec(value).asInstanceOf[XmlBinaryCodec[Any]]
            new XmlBinaryCodec[Option[Any]]() {
              override def decodeValue(xml: Xml): Either[XmlError, Option[Any]] = xml match {
                case Xml.Element(name, _, children) if name.localName == "None" && children.isEmpty =>
                  Right(None)
                case Xml.Element(name, _, children) if name.localName == "Some" =>
                  children.headOption match {
                    case Some(child) => innerCodec.decodeValue(child).map(Some(_))
                    case None        => Left(XmlError.parseError("Expected child element in Some", 0, 0))
                  }
                case other =>
                  innerCodec.decodeValue(other).map(Some(_))
              }

              override def encodeValue(x: Option[Any]): Xml = x match {
                case None        => xmlElement("None")
                case Some(value) => xmlElement("Some", innerCodec.encodeValue(value))
              }

              override def nullValue: Option[Any] = None
            }
          case _ =>
            val discr      = variant.variantBinding.asInstanceOf[Binding.Variant[A]].discriminator
            val cases      = variant.cases
            val caseCodecs = cases.map { case_ =>
              val caseName  = case_.name
              val caseCodec = deriveCodec(case_.value).asInstanceOf[XmlBinaryCodec[A]]
              (caseName, caseCodec)
            }

            new XmlBinaryCodec[A]() {
              private[this] val discriminator = discr
              private[this] val codecMap      = caseCodecs.toMap

              override def decodeValue(xml: Xml): Either[XmlError, A] = xml match {
                case Xml.Element(name, _, children) =>
                  val caseName = name.localName
                  codecMap.get(caseName) match {
                    case Some(codec) =>
                      children.headOption match {
                        case Some(child @ Xml.Element(childName, _, _)) if childName.localName == caseName =>
                          // Old double-wrapped format: <Dog><Dog>...</Dog></Dog>
                          codec.decodeValue(child)
                        case Some(_) =>
                          // New format: <Dog><name>...</name></Dog>
                          // Decode the element itself, not its children
                          codec.decodeValue(xml)
                        case None =>
                          // No children, decode as empty case
                          codec.decodeValue(xmlElement(caseName))
                      }
                    case None =>
                      Left(XmlError.parseError(s"Unknown variant case: $caseName", 0, 0))
                  }
                case _ =>
                  Left(XmlError.parseError("Expected element for variant", 0, 0))
              }

              override def encodeValue(x: A): Xml = {
                val caseIdx   = discriminator.discriminate(x)
                val caseName  = cases(caseIdx).name
                val caseCodec = codecMap(caseName)
                val inner     = caseCodec.encodeValue(x)
                inner match {
                  case elem: Xml.Element if elem.name.localName == caseName =>
                    // Already wrapped with correct name, return as-is
                    elem
                  case _ =>
                    // Wrap with case name
                    xmlElement(caseName, inner)
                }
              }
            }
        }
      } else variant.variantBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isSequence) {
      val sequence = reflect.asSequenceUnknown.get.sequence
      if (sequence.seqBinding.isInstanceOf[Binding[?, ?]]) {
        val binding      = sequence.seqBinding.asInstanceOf[Binding.Seq[Col, Elem]]
        val elemCodec    = deriveCodec(sequence.element).asInstanceOf[XmlBinaryCodec[Elem]]
        val elemClassTag = sequence.elemClassTag.asInstanceOf[ClassTag[Elem]]

        new XmlBinaryCodec[Col[Elem]]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          override def decodeValue(xml: Xml): Either[XmlError, Col[Elem]] = xml match {
            case Xml.Element(_, _, children) =>
              val builder         = constructor.newBuilder[Elem](children.length)(elemClassTag)
              var error: XmlError = null
              val iter            = children.iterator
              while (iter.hasNext && error == null) {
                iter.next() match {
                  case elem @ Xml.Element(name, _, _) if name.localName == "item" =>
                    val wrappedForDecode = Xml.Element(XmlName("value"), Chunk.empty, elem.children)
                    elemCodec.decodeValue(wrappedForDecode) match {
                      case Right(v)  => constructor.add(builder, v)
                      case Left(err) => error = err
                    }
                  case elem @ Xml.Element(_, _, _) =>
                    val wrappedForDecode = Xml.Element(XmlName("value"), Chunk.empty, elem.children)
                    elemCodec.decodeValue(wrappedForDecode) match {
                      case Right(v)  => constructor.add(builder, v)
                      case Left(err) => error = err
                    }
                  case _ => ()
                }
              }
              if (error != null) Left(error)
              else Right(constructor.result(builder))
            case _ =>
              Left(XmlError.parseError("Expected element for sequence", 0, 0))
          }

          override def encodeValue(x: Col[Elem]): Xml = {
            val iter     = deconstructor.deconstruct(x)
            val children = Chunk.newBuilder[Xml]
            while (iter.hasNext) {
              val elem = iter.next()
              children += xmlFieldElement("item", elemCodec.encodeValue(elem))
            }
            xmlElementWithChildren("items", children.result())
          }

          override def nullValue: Col[Elem] = constructor.empty[Elem](elemClassTag)
        }
      } else sequence.seqBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map
      if (map.mapBinding.isInstanceOf[Binding[?, ?]]) {
        val binding  = map.mapBinding.asInstanceOf[Binding.Map[Map, Key, Value]]
        val keyCodec = deriveCodec(map.key).asInstanceOf[XmlBinaryCodec[Key]]
        val valCodec = deriveCodec(map.value).asInstanceOf[XmlBinaryCodec[Value]]

        new XmlBinaryCodec[Map[Key, Value]]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor

          override def decodeValue(xml: Xml): Either[XmlError, Map[Key, Value]] = xml match {
            case Xml.Element(_, _, children) =>
              val builder         = constructor.newObjectBuilder[Key, Value](children.length)
              var error: XmlError = null
              val iter            = children.iterator
              while (iter.hasNext && error == null) {
                iter.next() match {
                  case Xml.Element(name, _, entryChildren) if name.localName == "entry" =>
                    var keyXml: Xml   = null
                    var valueXml: Xml = null
                    entryChildren.foreach {
                      case elem @ Xml.Element(n, _, _) if n.localName == "key"   => keyXml = elem
                      case elem @ Xml.Element(n, _, _) if n.localName == "value" => valueXml = elem
                      case _                                                     =>
                    }
                    if (keyXml != null && valueXml != null) {
                      // Wrap children in <value> element for codec compatibility
                      val keyWrapped =
                        Xml.Element(XmlName("value"), Chunk.empty, keyXml.asInstanceOf[Xml.Element].children)
                      val valWrapped =
                        Xml.Element(XmlName("value"), Chunk.empty, valueXml.asInstanceOf[Xml.Element].children)
                      (keyCodec.decodeValue(keyWrapped), valCodec.decodeValue(valWrapped)) match {
                        case (Right(k), Right(v)) => constructor.addObject(builder, k, v)
                        case (Left(err), _)       => error = err
                        case (_, Left(err))       => error = err
                      }
                    } else {
                      error = XmlError.parseError("Map entry missing key or value", 0, 0)
                    }
                  case _ => ()
                }
              }
              if (error != null) Left(error)
              else Right(constructor.resultObject(builder))
            case _ =>
              Left(XmlError.parseError("Expected element for map", 0, 0))
          }

          override def encodeValue(x: Map[Key, Value]): Xml = {
            val iter     = deconstructor.deconstruct(x)
            val children = Chunk.newBuilder[Xml]
            while (iter.hasNext) {
              val kv      = iter.next()
              val k       = deconstructor.getKey(kv)
              val v       = deconstructor.getValue(kv)
              val keyElem = xmlFieldElement("key", keyCodec.encodeValue(k))
              val valElem = xmlFieldElement("value", valCodec.encodeValue(v))
              children += xmlElement("entry", keyElem, valElem)
            }
            xmlElementWithChildren("map", children.result())
          }

          override def nullValue: Map[Key, Value] = constructor.emptyObject[Key, Value]
        }
      } else map.mapBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isRecord) {
      val record = reflect.asRecord.get
      if (record.recordBinding.isInstanceOf[Binding[?, ?]]) {
        val binding      = record.recordBinding.asInstanceOf[Binding.Record[A]]
        val fields       = record.fields
        val typeName     = record.typeId.name
        val namespaceOpt = xmlNamespace.getNamespace(record.modifiers)

        // (fieldName, codec, isOptional, offset, xmlAttrName: Option[String])
        // xmlAttrName = Some(name) means encode as attribute with that name, None means encode as child element
        val fieldInfos
          : IndexedSeq[(String, XmlBinaryCodec[Any], Boolean, RegisterOffset.RegisterOffset, Option[String])] = {
          var offset = RegisterOffset.Zero
          fields.map { field =>
            val fieldName     = field.name
            val fieldCodec    = deriveCodec(field.value).asInstanceOf[XmlBinaryCodec[Any]]
            val isOptional    = isOptionalField(field.value)
            val fieldOffset   = offset
            val attributeName = getXmlAttributeName(field)
            offset = RegisterOffset.add(offset, fieldCodec.valueOffset)
            (fieldName, fieldCodec, isOptional, fieldOffset, attributeName)
          }
        }

        new XmlBinaryCodec[A]() {
          private[this] val deconstructor = binding.deconstructor
          private[this] val constructor   = binding.constructor
          private[this] val recordName    = typeName
          private[this] val nsInfo        = namespaceOpt

          override def decodeValue(xml: Xml): Either[XmlError, A] = xml match {
            case Xml.Element(_, attributes, children) =>
              val childMap = children.collect { case elem @ Xml.Element(name, _, _) =>
                (name.localName, elem)
              }.toMap
              val attrMap = attributes.map { case (name, value) => (name.localName, value) }.toMap

              val regs            = Registers(constructor.usedRegisters)
              var error: XmlError = null
              var idx             = 0
              while (idx < fieldInfos.length && error == null) {
                val (fieldName, codec, isOptional, fieldOffset, xmlAttrName) = fieldInfos(idx)
                xmlAttrName match {
                  case Some(attrName) =>
                    attrMap.get(attrName) match {
                      case Some(attrValue) =>
                        parseAttributeValue(attrValue, codec, regs, fieldOffset) match {
                          case Some(err) => error = err
                          case None      => ()
                        }
                      case None =>
                        if (isOptional) {
                          regs.setObject(fieldOffset, None)
                        } else {
                          error = XmlError.parseError(s"Missing required attribute: $attrName", 0, 0)
                        }
                    }
                  case None =>
                    childMap.get(fieldName) match {
                      case Some(fieldElem) =>
                        val hasElementChildren = fieldElem.children.exists(_.isInstanceOf[Xml.Element])
                        val xmlToDecode        =
                          if (hasElementChildren) fieldElem
                          else Xml.Element(XmlName("value"), Chunk.empty, fieldElem.children)
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
                            case None => error = XmlError.parseError(s"Missing required field: $fieldName", 0, 0)
                          }
                        }
                    }
                }
                idx += 1
              }
              if (error != null) Left(error)
              else Right(constructor.construct(regs, 0))
            case _ =>
              Left(XmlError.parseError("Expected element for record", 0, 0))
          }

          override def encodeValue(x: A): Xml = {
            val regs = Registers(deconstructor.usedRegisters)
            deconstructor.deconstruct(regs, 0, x)
            val attributes = Chunk.newBuilder[(XmlName, String)]
            val children   = Chunk.newBuilder[Xml]
            var idx        = 0
            while (idx < fieldInfos.length) {
              val (fieldName, codec, isOptional, fieldOffset, xmlAttrName) = fieldInfos(idx)
              xmlAttrName match {
                case Some(attrName) =>
                  (codec.valueType: @switch) match {
                    case 0 =>
                      val value = regs.getObject(fieldOffset)
                      if (isOptional && value == None) {
                        // Skip None values
                      } else if (isOptional && value.isInstanceOf[Some[?]]) {
                        val inner = value.asInstanceOf[Some[Any]].get
                        attributes += ((XmlName(attrName), inner.toString))
                      } else {
                        attributes += ((XmlName(attrName), value.toString))
                      }
                    case 1 => attributes += ((XmlName(attrName), regs.getInt(fieldOffset).toString))
                    case 2 => attributes += ((XmlName(attrName), regs.getLong(fieldOffset).toString))
                    case 3 => attributes += ((XmlName(attrName), regs.getFloat(fieldOffset).toString))
                    case 4 => attributes += ((XmlName(attrName), regs.getDouble(fieldOffset).toString))
                    case 5 => attributes += ((XmlName(attrName), regs.getBoolean(fieldOffset).toString))
                    case 6 => attributes += ((XmlName(attrName), regs.getByte(fieldOffset).toString))
                    case 7 => attributes += ((XmlName(attrName), regs.getChar(fieldOffset).toString))
                    case 8 => attributes += ((XmlName(attrName), regs.getShort(fieldOffset).toString))
                    case _ => ()
                  }
                case None =>
                  (codec.valueType: @switch) match {
                    case 0 =>
                      val value = regs.getObject(fieldOffset)
                      if (isOptional && value == None) {}
                      else if (isOptional && value.isInstanceOf[Some[?]]) {
                        val inner      = value.asInstanceOf[Some[Any]].get
                        val innerCodec = getInnerCodec(codec)
                        children += xmlFieldElement(fieldName, innerCodec.encodeValue(inner))
                      } else {
                        children += xmlFieldElement(fieldName, codec.encodeValue(value))
                      }
                    case 1 =>
                      val value = regs.getInt(fieldOffset)
                      children += xmlFieldElement(fieldName, codec.asInstanceOf[XmlBinaryCodec[Int]].encodeValue(value))
                    case 2 =>
                      val value = regs.getLong(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Long]].encodeValue(value)
                      )
                    case 3 =>
                      val value = regs.getFloat(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Float]].encodeValue(value)
                      )
                    case 4 =>
                      val value = regs.getDouble(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Double]].encodeValue(value)
                      )
                    case 5 =>
                      val value = regs.getBoolean(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Boolean]].encodeValue(value)
                      )
                    case 6 =>
                      val value = regs.getByte(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Byte]].encodeValue(value)
                      )
                    case 7 =>
                      val value = regs.getChar(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Char]].encodeValue(value)
                      )
                    case 8 =>
                      val value = regs.getShort(fieldOffset)
                      children += xmlFieldElement(
                        fieldName,
                        codec.asInstanceOf[XmlBinaryCodec[Short]].encodeValue(value)
                      )
                    case _ =>
                      children += xmlFieldElement(fieldName, codec.asInstanceOf[XmlBinaryCodec[Unit]].encodeValue(()))
                  }
              }
              idx += 1
            }
            val (elementName, finalAttrs) = nsInfo match {
              case Some((uri, prefix)) if prefix.nonEmpty =>
                val prefixedName = XmlName(recordName, Some(prefix), None)
                val nsAttr       = (XmlName(prefix, Some("xmlns"), None), uri)
                (prefixedName, Chunk.single(nsAttr) ++ attributes.result())
              case Some((uri, _)) =>
                val nsAttr = (XmlName("xmlns"), uri)
                (XmlName(recordName), Chunk.single(nsAttr) ++ attributes.result())
              case None =>
                (XmlName(recordName), attributes.result())
            }
            Xml.Element(elementName, finalAttrs, children.result())
          }
        }
      } else record.recordBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else if (reflect.isWrapper) {
      val wrapper = reflect.asWrapperUnknown.get.wrapper
      if (wrapper.wrapperBinding.isInstanceOf[Binding[?, ?]]) {
        val binding      = wrapper.wrapperBinding.asInstanceOf[Binding.Wrapper[A, Wrapped]]
        val wrappedCodec = deriveCodec(wrapper.wrapped).asInstanceOf[XmlBinaryCodec[Wrapped]]

        new XmlBinaryCodec[A]() {
          private[this] val unwrap = binding.unwrap
          private[this] val wrap   = binding.wrap

          override def decodeValue(xml: Xml): Either[XmlError, A] =
            wrappedCodec.decodeValue(xml).map(wrap)

          override def encodeValue(x: A): Xml =
            wrappedCodec.encodeValue(unwrap(x))
        }
      } else wrapper.wrapperBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    } else {
      val dynamic = reflect.asDynamic.get
      if (dynamic.dynamicBinding.isInstanceOf[Binding[?, ?]]) dynamicValueCodec
      else dynamic.dynamicBinding.asInstanceOf[BindingInstance[TC, ?, A]].instance.force
    }
  }.asInstanceOf[XmlBinaryCodec[A]]

  private[this] def getXmlAttributeName[F[_, _], A](field: Term[F, A, ?]): Option[String] =
    field.modifiers.collectFirst { case Modifier.config("xml.attribute", value) =>
      if (value.nonEmpty) value else field.name
    }

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

  private[this] def getInnerCodec(optionCodec: XmlBinaryCodec[Any]): XmlBinaryCodec[Any] = {
    val codec = optionCodec.asInstanceOf[XmlBinaryCodec[Option[Any]]]
    new XmlBinaryCodec[Any]() {
      override def decodeValue(xml: Xml): Either[XmlError, Any] =
        codec.decodeValue(xml).map(_.getOrElse(null))

      override def encodeValue(x: Any): Xml =
        codec.encodeValue(Some(x)) match {
          case Xml.Element(_, _, children) => children.headOption.getOrElse(xmlElement("value"))
          case _                           => xmlElement("value")
        }
    }
  }

  private[this] def parseAttributeValue(
    attrValue: String,
    codec: XmlBinaryCodec[Any],
    regs: Registers,
    offset: RegisterOffset.RegisterOffset
  ): Option[XmlError] = {
    val wrappedXml = Xml.Element(XmlName("value"), Chunk.empty, Chunk(Xml.Text(attrValue)))
    codec.decodeValue(wrappedXml) match {
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
  }

  private val dayOfWeekCodec: XmlBinaryCodec[DayOfWeek] = new XmlBinaryCodec[DayOfWeek]() {
    def decodeValue(xml: Xml): Either[XmlError, DayOfWeek] = decodeText(xml)(s => DayOfWeek.valueOf(s.toUpperCase))
    def encodeValue(x: DayOfWeek): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val durationCodec: XmlBinaryCodec[Duration] = new XmlBinaryCodec[Duration]() {
    def decodeValue(xml: Xml): Either[XmlError, Duration] = decodeText(xml)(Duration.parse)
    def encodeValue(x: Duration): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val instantCodec: XmlBinaryCodec[Instant] = new XmlBinaryCodec[Instant]() {
    def decodeValue(xml: Xml): Either[XmlError, Instant] = decodeText(xml)(Instant.parse)
    def encodeValue(x: Instant): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val localDateCodec: XmlBinaryCodec[LocalDate] = new XmlBinaryCodec[LocalDate]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalDate] = decodeText(xml)(LocalDate.parse)
    def encodeValue(x: LocalDate): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val localDateTimeCodec: XmlBinaryCodec[LocalDateTime] = new XmlBinaryCodec[LocalDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalDateTime] = decodeText(xml)(LocalDateTime.parse)
    def encodeValue(x: LocalDateTime): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val localTimeCodec: XmlBinaryCodec[LocalTime] = new XmlBinaryCodec[LocalTime]() {
    def decodeValue(xml: Xml): Either[XmlError, LocalTime] = decodeText(xml)(LocalTime.parse)
    def encodeValue(x: LocalTime): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val monthCodec: XmlBinaryCodec[Month] = new XmlBinaryCodec[Month]() {
    def decodeValue(xml: Xml): Either[XmlError, Month] = decodeText(xml)(s => Month.valueOf(s.toUpperCase))
    def encodeValue(x: Month): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val monthDayCodec: XmlBinaryCodec[MonthDay] = new XmlBinaryCodec[MonthDay]() {
    def decodeValue(xml: Xml): Either[XmlError, MonthDay] = decodeText(xml)(MonthDay.parse)
    def encodeValue(x: MonthDay): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val offsetDateTimeCodec: XmlBinaryCodec[OffsetDateTime] = new XmlBinaryCodec[OffsetDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, OffsetDateTime] = decodeText(xml)(OffsetDateTime.parse)
    def encodeValue(x: OffsetDateTime): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val offsetTimeCodec: XmlBinaryCodec[OffsetTime] = new XmlBinaryCodec[OffsetTime]() {
    def decodeValue(xml: Xml): Either[XmlError, OffsetTime] = decodeText(xml)(OffsetTime.parse)
    def encodeValue(x: OffsetTime): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val periodCodec: XmlBinaryCodec[Period] = new XmlBinaryCodec[Period]() {
    def decodeValue(xml: Xml): Either[XmlError, Period] = decodeText(xml)(Period.parse)
    def encodeValue(x: Period): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val yearCodec: XmlBinaryCodec[Year] = new XmlBinaryCodec[Year]() {
    def decodeValue(xml: Xml): Either[XmlError, Year] = decodeText(xml)(Year.parse)
    def encodeValue(x: Year): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val yearMonthCodec: XmlBinaryCodec[YearMonth] = new XmlBinaryCodec[YearMonth]() {
    def decodeValue(xml: Xml): Either[XmlError, YearMonth] = decodeText(xml)(YearMonth.parse)
    def encodeValue(x: YearMonth): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val zoneIdCodec: XmlBinaryCodec[ZoneId] = new XmlBinaryCodec[ZoneId]() {
    def decodeValue(xml: Xml): Either[XmlError, ZoneId] = decodeText(xml)(ZoneId.of)
    def encodeValue(x: ZoneId): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val zoneOffsetCodec: XmlBinaryCodec[ZoneOffset] = new XmlBinaryCodec[ZoneOffset]() {
    def decodeValue(xml: Xml): Either[XmlError, ZoneOffset] = decodeText(xml)(ZoneOffset.of)
    def encodeValue(x: ZoneOffset): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val zonedDateTimeCodec: XmlBinaryCodec[ZonedDateTime] = new XmlBinaryCodec[ZonedDateTime]() {
    def decodeValue(xml: Xml): Either[XmlError, ZonedDateTime] = decodeText(xml)(ZonedDateTime.parse)
    def encodeValue(x: ZonedDateTime): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val currencyCodec: XmlBinaryCodec[Currency] = new XmlBinaryCodec[Currency]() {
    def decodeValue(xml: Xml): Either[XmlError, Currency] = decodeText(xml)(Currency.getInstance)
    def encodeValue(x: Currency): Xml                     = Xml.Element("value", Xml.Text(x.getCurrencyCode))
  }

  private val uuidCodec: XmlBinaryCodec[UUID] = new XmlBinaryCodec[UUID]() {
    def decodeValue(xml: Xml): Either[XmlError, UUID] = decodeText(xml)(UUID.fromString)
    def encodeValue(x: UUID): Xml                     = Xml.Element("value", Xml.Text(x.toString))
  }

  private val dynamicValueCodec: XmlBinaryCodec[DynamicValue] = new XmlBinaryCodec[DynamicValue]() {
    def decodeValue(xml: Xml): Either[XmlError, DynamicValue] =
      Left(XmlError.parseError("DynamicValue decoding not supported", 0, 0))

    def encodeValue(x: DynamicValue): Xml =
      Xml.Element("dynamic", Xml.Text(x.toString))
  }

  private def decodeText[A](xml: Xml)(parse: String => A): Either[XmlError, A] = xml match {
    case Xml.Element(name, _, children) if name.localName == "value" =>
      children.headOption match {
        case Some(Xml.Text(text)) =>
          try Right(parse(text.trim))
          catch {
            case e: Exception => Left(XmlError.parseError(s"Invalid value: ${e.getMessage}", 0, 0))
          }
        case None => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
        case _    => Left(XmlError.parseError("Expected text content in <value> element", 0, 0))
      }
    case _ => Left(XmlError.parseError("Expected <value> element", 0, 0))
  }
}
