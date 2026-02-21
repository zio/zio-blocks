package golem.data

import golem.data.DataType._
import golem.data.DataValue._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue => DV, PrimitiveType, PrimitiveValue, Schema}
import zio.blocks.typeid.TypeId
import zio.blocks.schema.Reflect

import scala.collection.immutable.ListMap
object DataInterop {

  def schemaToDataType[A](schema: Schema[A]): DataType =
    reflectToDataType(schema.reflect)

  def toData[A](value: A)(implicit schema: Schema[A]): DataValue =
    IntoDataValue[A].toData(value)

  def fromData[A](value: DataValue)(implicit schema: Schema[A]): Either[String, A] =
    FromDataValue[A].fromData(value)

  def dataTypeOf[A](implicit schema: Schema[A]): DataType =
    IntoDataType[A].dataType

  private def reflectToDataType[A](reflect: Reflect.Bound[A]): DataType = {
    // Wrapper: treat as underlying for type-encoding purposes.
    reflect.asWrapperUnknown match {
      case Some(unknown) =>
        return reflectToDataType(unknown.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]]).asInstanceOf[DataType]
      case None => ()
    }

    // Optional: many schemas represent Option as Variant(None, Some(value)).
    optionInfo(reflect) match {
      case Some((innerRef, _)) =>
        return Optional(reflectToDataType(innerRef))
      case None => ()
    }

    reflect.asPrimitive match {
      case Some(p) => primitiveToDataType(p.primitiveType)
      case None    =>
        reflect.asRecord match {
          case Some(rec) =>
            // Tuple2/3: represent as TupleType for better RPC ergonomics.
            if (isTupleRecord(rec)) {
              val ordered = rec.fields.sortBy(_.name)
              TupleType(ordered.map(f => reflectToDataType(f.value.asInstanceOf[Reflect.Bound[Any]])).toList)
            } else {
              StructType(
                rec.fields.map { field =>
                  Field(
                    name = field.name,
                    dataType = reflectToDataType(field.value.asInstanceOf[Reflect.Bound[Any]]),
                    optional = false
                  )
                }.toList
              )
            }

          case None =>
            reflect.asSequenceUnknown match {
              case Some(seqUnknown) =>
                // Treat Set specially; everything else is a list.
                if (isSetTypeId(reflect.typeId))
                  SetType(reflectToDataType(seqUnknown.sequence.element.asInstanceOf[Reflect.Bound[Any]]))
                else ListType(reflectToDataType(seqUnknown.sequence.element.asInstanceOf[Reflect.Bound[Any]]))

              case None =>
                reflect.asMapUnknown match {
                  case Some(mapUnknown) =>
                    val keyDt = reflectToDataType(mapUnknown.map.key.asInstanceOf[Reflect.Bound[Any]])
                    keyDt match {
                      case StringType =>
                        MapType(reflectToDataType(mapUnknown.map.value.asInstanceOf[Reflect.Bound[Any]]))
                      case other =>
                        throw new IllegalArgumentException(s"Only string map keys are supported, found: $other")
                    }

                  case None =>
                    reflect.asVariant match {
                      case Some(variant) =>
                        EnumType(
                          variant.cases.map { c =>
                            val payloadDt =
                              c.value.asRecord match {
                                case Some(r) if r.fields.isEmpty                                      => None
                                case Some(r) if r.fields.length == 1 && r.fields.head.name == "value" =>
                                  Some(reflectToDataType(r.fields.head.value.asInstanceOf[Reflect.Bound[Any]]))
                                case Some(r) =>
                                  Some(reflectToDataType(r.asInstanceOf[Reflect.Bound[Any]]))
                                case None =>
                                  Some(reflectToDataType(c.value.asInstanceOf[Reflect.Bound[Any]]))
                              }
                            EnumCase(c.name, payloadDt)
                          }.toList
                        )

                      case None =>
                        if (reflect.isDynamic) StructType(Nil)
                        else throw new IllegalArgumentException(s"Unsupported schema reflect: ${reflect.nodeType}")
                    }
                }
            }
        }
    }
  }

  private def isSetTypeId(typeId: TypeId[?]): Boolean =
    TypeId.normalize(typeId).fullName == TypeId.set.fullName

  private def isTupleRecord(rec: Reflect.Record[_root_.zio.blocks.schema.binding.Binding, _]): Boolean = {
    val names = rec.fields.map(_.name).toSet
    (rec.fields.length == 2 && names == Set("_1", "_2")) ||
    (rec.fields.length == 3 && names == Set("_1", "_2", "_3"))
  }

  /**
   * Detects an Option-like schema (Variant(None, Some(value))) and returns the
   * inner `value` field reflect.
   */
  private def optionInfo(reflect: Reflect.Bound[?]): Option[(Reflect.Bound[Any], Boolean)] =
    reflect.asVariant.flatMap { variant =>
      def simpleCaseName(name: String): String = {
        val afterDot =
          name.lastIndexOf('.') match {
            case -1 => name
            case i  => name.substring(i + 1)
          }
        if (afterDot.endsWith("$")) afterDot.dropRight(1) else afterDot
      }

      val noneCase = variant.cases.find(t => simpleCaseName(t.name) == "None")
      val someCase = variant.cases.find(t => simpleCaseName(t.name) == "Some")

      if (noneCase.isEmpty || someCase.isEmpty) None
      else {
        val someValue = someCase.get.value.asInstanceOf[Reflect.Bound[Any]]
        someValue.asRecord match {
          case Some(someRec) =>
            someRec.fieldByName("value") match {
              case Some(valueField) =>
                Some((valueField.value.asInstanceOf[Reflect.Bound[Any]], true))
              case None =>
                Some((someValue, false))
            }
          case None =>
            Some((someValue, false))
        }
      }
    }

  private def primitiveToDataType(pt: PrimitiveType[?]): DataType =
    pt match {
      case PrimitiveType.Unit          => UnitType
      case _: PrimitiveType.String     => StringType
      case _: PrimitiveType.Boolean    => BoolType
      case _: PrimitiveType.Byte       => IntType
      case _: PrimitiveType.Short      => IntType
      case _: PrimitiveType.Int        => IntType
      case _: PrimitiveType.Long       => LongType
      case _: PrimitiveType.Float      => DoubleType
      case _: PrimitiveType.Double     => DoubleType
      case _: PrimitiveType.BigDecimal => BigDecimalType
      case _: PrimitiveType.UUID       => UUIDType
      case other                       =>
        throw new IllegalArgumentException(s"Unsupported primitive: ${other.getClass.getName}")
    }

  private def dynamicToDataValue[A](reflect: Reflect.Bound[A], d: DV): DataValue = {
    // Wrapper: handle using wrapped schema.
    reflect.asWrapperUnknown match {
      case Some(unknown) =>
        return dynamicToDataValue(unknown.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]], d)
      case None => ()
    }

    // Option: Variant(None/Some(value)).
    optionInfo(reflect) match {
      case Some((valueRef, usesRecordWrapper)) =>
        d match {
          case DV.Variant("None", _)       => return OptionalValue(None)
          case DV.Variant("Some", payload) =>
            if (usesRecordWrapper) {
              payload match {
                case DV.Record(fields) =>
                  fields.find(_._1 == "value") match {
                    case Some((_, inner)) => return OptionalValue(Some(dynamicToDataValue(valueRef, inner)))
                    case None             => throw new IllegalArgumentException("Option(Some) payload missing value field")
                  }
                case other =>
                  throw new IllegalArgumentException(s"Option(Some) payload expected record, got $other")
              }
            } else {
              return OptionalValue(Some(dynamicToDataValue(valueRef, payload)))
            }
          case other =>
            throw new IllegalArgumentException(s"Option dynamic value expected Variant, got $other")
        }
      case None => ()
    }

    // Tuple2/3: Record(_1,_2,_3) <-> TupleValue
    if (reflect.asRecord.exists(isTupleRecord)) {
      val rec = reflect.asRecord.getOrElse(
        throw new IllegalArgumentException(s"Tuple reflect expected record, got ${reflect.nodeType}")
      )
      d match {
        case DV.Record(fields) =>
          val map     = fields.toMap
          val ordered = rec.fields
            .sortBy(_.name)
            .map { f =>
              val dv = map.getOrElse(f.name, throw new IllegalArgumentException(s"Tuple field '${f.name}' missing"))
              dynamicToDataValue(f.value.asInstanceOf[Reflect.Bound[Any]], dv)
            }
            .toList
          return TupleValue(ordered)
        case other =>
          throw new IllegalArgumentException(s"Tuple dynamic value expected record, got $other")
      }
    }

    reflect.asPrimitive match {
      case Some(_) =>
        d match {
          case DV.Primitive(pv) => primitiveValue(pv)
          case other            => throw new IllegalArgumentException(s"Expected primitive dynamic value, found: $other")
        }

      case None =>
        reflect.asRecord match {
          case Some(rec) =>
            d match {
              case DV.Record(fields) =>
                val map = fields.toMap
                if (isTupleRecord(rec)) {
                  val ordered = rec.fields
                    .sortBy(_.name)
                    .map { f =>
                      val dv =
                        map.getOrElse(f.name, throw new IllegalArgumentException(s"Tuple field '${f.name}' missing"))
                      dynamicToDataValue(f.value.asInstanceOf[Reflect.Bound[Any]], dv)
                    }
                    .toList
                  TupleValue(ordered)
                } else {
                  StructValue(
                    rec.fields.map { f =>
                      val fv = map.getOrElse(f.name, throw new IllegalArgumentException(s"Missing field '${f.name}'"))
                      f.name -> dynamicToDataValue(f.value.asInstanceOf[Reflect.Bound[Any]], fv)
                    }.toMap
                  )
                }
              case other =>
                throw new IllegalArgumentException(s"Expected record dynamic value, found: $other")
            }

          case None =>
            reflect.asSequenceUnknown match {
              case Some(seqUnknown) =>
                d match {
                  case DV.Sequence(values) =>
                    val elemRef   = seqUnknown.sequence.element.asInstanceOf[Reflect.Bound[Any]]
                    val converted = values.map(v => dynamicToDataValue(elemRef, v)).toList
                    if (isSetTypeId(reflect.typeId)) SetValue(converted.toSet)
                    else ListValue(converted)
                  case other =>
                    throw new IllegalArgumentException(s"Expected sequence dynamic value, found: $other")
                }

              case None =>
                reflect.asMapUnknown match {
                  case Some(mapUnknown) =>
                    d match {
                      case DV.Map(entries) =>
                        val keyRef   = mapUnknown.map.key.asInstanceOf[Reflect.Bound[Any]]
                        val valueRef = mapUnknown.map.value.asInstanceOf[Reflect.Bound[Any]]
                        val out      = entries.map { case (k, v) =>
                          dynamicToDataValue(keyRef, k) match {
                            case StringValue(str) => str -> dynamicToDataValue(valueRef, v)
                            case other            => throw new IllegalArgumentException(s"Map keys must be strings, got $other")
                          }
                        }.toMap
                        MapValue(out)
                      case other =>
                        throw new IllegalArgumentException(s"Expected map dynamic value, found: $other")
                    }

                  case None =>
                    reflect.asVariant match {
                      case Some(variant) =>
                        d match {
                          case DV.Variant(name, payload) =>
                            val caseTerm = variant
                              .caseByName(name)
                              .getOrElse(
                                throw new IllegalArgumentException(s"Unknown variant case '$name'")
                              )
                            val payloadRef = caseTerm.value.asInstanceOf[Reflect.Bound[Any]]
                            // Convention: cases are often Record(value = X); flatten to payload X.
                            payloadRef.asRecord match {
                              case Some(r) if r.fields.isEmpty =>
                                EnumValue(name, None)
                              case Some(r) if r.fields.length == 1 && r.fields.head.name == "value" =>
                                payload match {
                                  case DV.Record(fields) =>
                                    val inner = fields
                                      .find(_._1 == "value")
                                      .map(_._2)
                                      .getOrElse(
                                        throw new IllegalArgumentException(
                                          s"Variant case '$name' missing 'value' field"
                                        )
                                      )
                                    val innerRef = r.fields.head.value.asInstanceOf[Reflect.Bound[Any]]
                                    EnumValue(name, Some(dynamicToDataValue(innerRef, inner)))
                                  case other =>
                                    EnumValue(name, Some(dynamicToDataValue(payloadRef, other)))
                                }
                              case _ =>
                                EnumValue(name, Some(dynamicToDataValue(payloadRef, payload)))
                            }
                          case other =>
                            throw new IllegalArgumentException(s"Expected variant dynamic value, found: $other")
                        }
                      case None =>
                        // Dynamic fallback
                        StringValue(d.toString)
                    }
                }
            }
        }
    }
  }

  private def primitiveValue(pv: PrimitiveValue): DataValue =
    pv match {
      case PrimitiveValue.Unit          => NullValue
      case PrimitiveValue.String(v)     => StringValue(v)
      case PrimitiveValue.Boolean(v)    => BoolValue(v)
      case PrimitiveValue.Byte(v)       => IntValue(v.toInt)
      case PrimitiveValue.Short(v)      => IntValue(v.toInt)
      case PrimitiveValue.Int(v)        => IntValue(v)
      case PrimitiveValue.Long(v)       => LongValue(v)
      case PrimitiveValue.Float(v)      => DoubleValue(v.toDouble)
      case PrimitiveValue.Double(v)     => DoubleValue(v)
      case PrimitiveValue.BigDecimal(v) => BigDecimalValue(v)
      case PrimitiveValue.UUID(v)       => UUIDValue(v)
      case other                        =>
        throw new IllegalArgumentException(s"Unsupported primitive value: ${other.getClass.getName}")
    }

  private def dataValueToDynamic[A](reflect: Reflect.Bound[A], value: DataValue): DV = {
    // Wrapper: pass through.
    reflect.asWrapperUnknown match {
      case Some(unknown) =>
        return dataValueToDynamic(unknown.wrapper.wrapped.asInstanceOf[Reflect.Bound[Any]], value)
      case None => ()
    }

    optionInfo(reflect) match {
      case Some((innerRef, usesRecordWrapper)) =>
        value match {
          case OptionalValue(None) =>
            return DV.Variant("None", DV.Record(Chunk.empty))
          case OptionalValue(Some(v)) =>
            val dynInner = dataValueToDynamic(innerRef, v)
            val payload  =
              if (usesRecordWrapper) DV.Record(Chunk("value" -> dynInner))
              else dynInner
            return DV.Variant("Some", payload)
          case other =>
            throw new IllegalArgumentException(s"Expected OptionalValue for Option, got $other")
        }
      case None => ()
    }

    if (reflect.asRecord.exists(isTupleRecord)) {
      val rec = reflect.asRecord.get
      value match {
        case TupleValue(values) =>
          val orderedFields = rec.fields.sortBy(_.name)
          if (values.length != orderedFields.length)
            throw new IllegalArgumentException(
              s"Tuple arity mismatch. Expected ${orderedFields.length}, found ${values.length}"
            )
          val dynFields = orderedFields
            .zip(values)
            .map { case (f, dv) =>
              f.name -> dataValueToDynamic(f.value.asInstanceOf[Reflect.Bound[Any]], dv)
            }
            .toVector
          DV.Record(Chunk.fromIterable(dynFields))
        case other =>
          throw new IllegalArgumentException(s"Expected TupleValue for tuple, got $other")
      }
    }

    reflect.asPrimitive match {
      case Some(p) =>
        value match {
          case NullValue =>
            p.primitiveType.toDynamicValue(().asInstanceOf[A])
          case StringValue(v) =>
            DV.Primitive(PrimitiveValue.String(v))
          case BoolValue(v) =>
            DV.Primitive(PrimitiveValue.Boolean(v))
          case IntValue(v) =>
            DV.Primitive(PrimitiveValue.Int(v))
          case LongValue(v) =>
            DV.Primitive(PrimitiveValue.Long(v))
          case DoubleValue(v) =>
            DV.Primitive(PrimitiveValue.Double(v))
          case BigDecimalValue(v) =>
            DV.Primitive(PrimitiveValue.BigDecimal(v))
          case UUIDValue(v) =>
            DV.Primitive(PrimitiveValue.UUID(v))
          case BytesValue(_) =>
            throw new IllegalArgumentException("Binary values are not supported by zio.blocks.schema primitives")
          case other =>
            throw new IllegalArgumentException(s"Unsupported primitive data value: $other")
        }

      case None =>
        reflect.asRecord match {
          case Some(rec) =>
            value match {
              case StructValue(fields) =>
                val map       = ListMap.from(fields)
                val dynFields = rec.fields.map { f =>
                  val dv = map.getOrElse(f.name, throw new IllegalArgumentException(s"Missing field '${f.name}'"))
                  f.name -> dataValueToDynamic(f.value.asInstanceOf[Reflect.Bound[Any]], dv)
                }.toVector
                DV.Record(Chunk.fromIterable(dynFields))
              case TupleValue(values) if isTupleRecord(rec) =>
                val orderedFields = rec.fields.sortBy(_.name)
                if (values.length != orderedFields.length)
                  throw new IllegalArgumentException(
                    s"Tuple arity mismatch. Expected ${orderedFields.length}, found ${values.length}"
                  )
                val dynFields = orderedFields
                  .zip(values)
                  .map { case (f, dv) =>
                    f.name -> dataValueToDynamic(f.value.asInstanceOf[Reflect.Bound[Any]], dv)
                  }
                  .toVector
                DV.Record(Chunk.fromIterable(dynFields))
              case other =>
                throw new IllegalArgumentException(s"Expected StructValue for record, got $other")
            }

          case None =>
            reflect.asSequenceUnknown match {
              case Some(seqUnknown) =>
                val elemRef = seqUnknown.sequence.element.asInstanceOf[Reflect.Bound[Any]]
                value match {
                  case ListValue(values) =>
                    DV.Sequence(Chunk.fromIterable(values.map(v => dataValueToDynamic(elemRef, v))))
                  case SetValue(values) =>
                    DV.Sequence(Chunk.fromIterable(values.map(v => dataValueToDynamic(elemRef, v))))
                  case other =>
                    throw new IllegalArgumentException(s"Expected ListValue/SetValue for sequence, got $other")
                }

              case None =>
                reflect.asMapUnknown match {
                  case Some(mapUnknown) =>
                    val keyRef   = mapUnknown.map.key.asInstanceOf[Reflect.Bound[Any]]
                    val valueRef = mapUnknown.map.value.asInstanceOf[Reflect.Bound[Any]]
                    value match {
                      case MapValue(entries) =>
                        val dynEntries = entries.toVector.map { case (k, v) =>
                          val dk = dataValueToDynamic(keyRef, StringValue(k))
                          val dv = dataValueToDynamic(valueRef, v)
                          (dk, dv)
                        }
                        DV.Map(Chunk.fromIterable(dynEntries))
                      case other =>
                        throw new IllegalArgumentException(s"Expected MapValue for map, got $other")
                    }

                  case None =>
                    reflect.asVariant match {
                      case Some(_) =>
                        value match {
                          case EnumValue(caseName, maybePayload) =>
                            val payloadDyn = maybePayload match {
                              case None     => DV.Record(Chunk.empty)
                              case Some(pv) =>
                                val payloadRef =
                                  reflect.asVariant.get.caseByName(caseName).get.value.asInstanceOf[Reflect.Bound[Any]]
                                payloadRef.asRecord match {
                                  case Some(rec) if rec.fields.length == 1 && rec.fields.head.name == "value" =>
                                    // Some schemas model variant payload as a wrapper record(value = ...).
                                    DV.Record(
                                      Chunk(
                                        "value" -> dataValueToDynamic(
                                          rec.fields.head.value.asInstanceOf[Reflect.Bound[Any]],
                                          pv
                                        )
                                      )
                                    )
                                  case _ =>
                                    // Otherwise, payload is represented directly as the dynamic value for the case.
                                    dataValueToDynamic(payloadRef, pv)
                                }
                            }
                            DV.Variant(caseName, payloadDyn)
                          case other =>
                            throw new IllegalArgumentException(s"Expected EnumValue for variant, got $other")
                        }
                      case None =>
                        DV.Primitive(PrimitiveValue.String(value.toString))
                    }
                }
            }
        }
    }
  }

  trait IntoDataType[A] {
    def dataType: DataType
  }

  trait IntoDataValue[A] {
    def toData(value: A): DataValue
  }

  trait FromDataValue[A] {
    def fromData(value: DataValue): Either[String, A]
  }

  object IntoDataType {
    def apply[A](implicit ev: IntoDataType[A]): IntoDataType[A] = ev

    implicit def derived[A](implicit schema: Schema[A]): IntoDataType[A] =
      new IntoDataType[A] {
        override def dataType: DataType = schemaToDataType(schema)
      }
  }

  object IntoDataValue {
    def apply[A](implicit ev: IntoDataValue[A]): IntoDataValue[A] = ev

    implicit def derived[A](implicit schema: Schema[A]): IntoDataValue[A] =
      new IntoDataValue[A] {
        override def toData(value: A): DataValue = dynamicToDataValue(schema.reflect, schema.toDynamicValue(value))
      }
  }

  object FromDataValue {
    def apply[A](implicit ev: FromDataValue[A]): FromDataValue[A] = ev

    implicit def derived[A](implicit schema: Schema[A]): FromDataValue[A] =
      new FromDataValue[A] {
        override def fromData(value: DataValue): Either[String, A] =
          schema.fromDynamicValue(dataValueToDynamic(schema.reflect, value)).left.map(_.toString)
      }
  }
}
