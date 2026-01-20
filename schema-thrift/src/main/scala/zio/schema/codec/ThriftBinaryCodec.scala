package zio.blocks.schema.codec

import org.apache.thrift.protocol._
import org.apache.thrift.transport.{TMemoryBuffer, TMemoryInputTransport}
import zio._ // Import all ZIO types including Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

import java.nio.ByteBuffer
import scala.util.control.NonFatal

object ThriftBinaryCodec {

  implicit def thriftCodec[A](implicit schema: Schema[A]): BinaryCodec[A] =
    new BinaryCodec[A] {
      override def encode(value: A, output: ByteBuffer): Unit = {
        val buffer   = new TMemoryBuffer(1024)
        val protocol = new TBinaryProtocol(buffer)
        Encoder.encode(schema.reflect, value, protocol)

        if (output.remaining() < buffer.length()) {
          throw new java.nio.BufferOverflowException()
        }
        output.put(buffer.getArray, 0, buffer.length())
      }

      override def decode(input: ByteBuffer): Either[SchemaError, A] =
        try {
          val transport = if (input.hasArray) {
            new TMemoryInputTransport(input.array(), input.arrayOffset() + input.position(), input.remaining())
          } else {
            val arr    = new Array[Byte](input.remaining())
            val oldPos = input.position()
            input.get(arr)
            input.position(oldPos)
            new TMemoryInputTransport(arr)
          }

          val protocol = new TBinaryProtocol(transport)
          val res      = Decoder.decode(schema.reflect, protocol)

          val consumed = input.remaining() - transport.getBytesRemainingInBuffer
          input.position(input.position() + consumed)

          Right(res)
        } catch {
          case NonFatal(e) => Left(SchemaError.expectationMismatch(Nil, s"Read error: ${e.getMessage}"))
        }
    }

  private object Encoder {
    def encode[A](reflect: Reflect[Binding, A], value: A, protocol: TProtocol): Unit =
      reflect match {
        case p: Reflect.Primitive[Binding, A] =>
          encodePrimitive(p.primitiveType, value, protocol)

        case _ if reflect.isSequence =>
          val seq         = reflect.asSequenceUnknown.get.sequence
          val list        = value.asInstanceOf[Iterable[Any]]
          val elemReflect = seq.element.asInstanceOf[Reflect[Binding, Any]]
          val elemType    = getTType(elemReflect)
          protocol.writeListBegin(new TList(elemType, list.size))
          list.foreach(item => encode(elemReflect, item, protocol))
          protocol.writeListEnd()

        case _ =>
          if (reflect.isOption) {
            val opt      = value.asInstanceOf[Option[Any]]
            val inner    = reflect.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
            val elemType = getTType(inner)
            val size     = if (opt.isDefined) 1 else 0
            protocol.writeListBegin(new TList(elemType, size))
            opt.foreach(v => encode(inner, v, protocol))
            protocol.writeListEnd()
          } else {
            val dyn = reflect.toDynamicValue(value)
            encodeDynamic(dyn, reflect, protocol)
          }
      }

    def encodeDynamic(dyn: DynamicValue, reflect: Reflect[Binding, _], protocol: TProtocol): Unit =
      if (reflect.isOption) {
        val innerReflect = reflect.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
        val elemType     = getTType(innerReflect)

        dyn match {
          case DynamicValue.Variant("Some", innerRecord) =>
            innerRecord match {
              case DynamicValue.Record(fields) =>
                val valField =
                  fields.find(_._1 == "value").map(_._2).getOrElse(throw new Exception("Missing value field in Some"))
                protocol.writeListBegin(new TList(elemType, 1))
                encodeDynamic(valField, innerReflect, protocol)
                protocol.writeListEnd()
              case _ => throw new Exception("Expected Record inside Some")
            }
          case DynamicValue.Variant("None", _) =>
            protocol.writeListBegin(new TList(elemType, 0))
            protocol.writeListEnd()
          case _ => throw new Exception(s"Unexpected Value for Option: $dyn")
        }
      } else {
        dyn match {
          case DynamicValue.Record(fields) =>
            protocol.writeStructBegin(new TStruct("record"))
            reflect match {
              case r: Reflect.Record[Binding, _] =>
                var fieldId = 1
                r.fields.foreach { term =>
                  val fieldName   = term.name
                  val matchingVal = fields.find(_._1 == fieldName).map(_._2)
                  if (matchingVal.isDefined) {
                    val fType = getTType(term.value)
                    protocol.writeFieldBegin(new TField(fieldName, fType, fieldId.toShort))
                    encodeDynamic(matchingVal.get, term.value, protocol)
                    protocol.writeFieldEnd()
                  }
                  fieldId += 1
                }
              case _ => throw new Exception(s"Mismatch: DynamicValue.Record but reflect is $reflect")
            }
            protocol.writeFieldStop()
            protocol.writeStructEnd()

          case DynamicValue.Variant(name, innerValue) =>
            protocol.writeStructBegin(new TStruct("union"))
            reflect match {
              case v: Reflect.Variant[Binding, _] =>
                val caseIdx = v.cases.indexWhere(_.name == name)
                if (caseIdx != -1) {
                  val caseTerm = v.cases(caseIdx)
                  val fieldId  = (caseIdx + 1).toShort
                  val fType    = getTType(caseTerm.value)
                  protocol.writeFieldBegin(new TField(name, fType, fieldId))
                  encodeDynamic(innerValue, caseTerm.value, protocol)
                  protocol.writeFieldEnd()
                } else {
                  throw new Exception(s"Unknown variant case: $name")
                }
              case _ => throw new Exception(s"Mismatch: DynamicValue.Variant but reflect is $reflect")
            }
            protocol.writeFieldStop()
            protocol.writeStructEnd()

          case DynamicValue.Primitive(p) =>
            encodePrimitiveValue(p, protocol)

          case DynamicValue.Sequence(values) =>
            if (reflect.isSequence) {
              val s           = reflect.asSequenceUnknown.get.sequence
              val elemReflect = s.element
              val elemType    = getTType(elemReflect)
              protocol.writeListBegin(new TList(elemType, values.size))
              values.foreach(v => encodeDynamic(v, elemReflect, protocol))
              protocol.writeListEnd()
            } else throw new Exception("Mismatch sequence")

          case _ => throw new Exception(s"Unsupported dynamic value: $dyn")
        }
      }

    def encodePrimitive[A](primitiveType: PrimitiveType[A], value: A, protocol: TProtocol): Unit =
      primitiveType match {
        case PrimitiveType.Unit             => ()
        case _: PrimitiveType.Boolean       => protocol.writeBool(value.asInstanceOf[Boolean])
        case _: PrimitiveType.Byte          => protocol.writeByte(value.asInstanceOf[Byte])
        case _: PrimitiveType.Short         => protocol.writeI16(value.asInstanceOf[Short])
        case _: PrimitiveType.Int           => protocol.writeI32(value.asInstanceOf[Int])
        case _: PrimitiveType.Long          => protocol.writeI64(value.asInstanceOf[Long])
        case _: PrimitiveType.Float         => protocol.writeDouble(value.asInstanceOf[Float].toDouble)
        case _: PrimitiveType.Double        => protocol.writeDouble(value.asInstanceOf[Double])
        case _: PrimitiveType.String        => protocol.writeString(value.asInstanceOf[String])
        case _: PrimitiveType.Char          => protocol.writeString(value.toString)
        case _: PrimitiveType.UUID          => protocol.writeString(value.toString)
        case _: PrimitiveType.BigDecimal    => protocol.writeString(value.toString)
        case _: PrimitiveType.BigInt        => protocol.writeString(value.toString)
        case _: PrimitiveType.DayOfWeek     => protocol.writeString(value.toString)
        case _: PrimitiveType.Month         => protocol.writeString(value.toString)
        case _: PrimitiveType.MonthDay      => protocol.writeString(value.toString)
        case _: PrimitiveType.Period        => protocol.writeString(value.toString)
        case _: PrimitiveType.Year          => protocol.writeI32(value.asInstanceOf[java.time.Year].getValue)
        case _: PrimitiveType.YearMonth     => protocol.writeString(value.toString)
        case _: PrimitiveType.ZoneId        => protocol.writeString(value.toString)
        case _: PrimitiveType.ZoneOffset    => protocol.writeString(value.toString)
        case _: PrimitiveType.Duration      => protocol.writeString(value.toString)
        case _: PrimitiveType.Instant       => protocol.writeString(value.toString)
        case _: PrimitiveType.LocalDate     => protocol.writeString(value.toString)
        case _: PrimitiveType.LocalTime     => protocol.writeString(value.toString)
        case _: PrimitiveType.LocalDateTime => protocol.writeString(value.toString)
        case _                              => protocol.writeString(value.toString)
      }

    def encodePrimitiveValue(p: PrimitiveValue, protocol: TProtocol): Unit =
      p match {
        case PrimitiveValue.Unit       => ()
        case PrimitiveValue.Boolean(v) => protocol.writeBool(v)
        case PrimitiveValue.Byte(v)    => protocol.writeByte(v)
        case PrimitiveValue.Short(v)   => protocol.writeI16(v)
        case PrimitiveValue.Int(v)     => protocol.writeI32(v)
        case PrimitiveValue.Long(v)    => protocol.writeI64(v)
        case PrimitiveValue.Float(v)   => protocol.writeDouble(v.toDouble)
        case PrimitiveValue.Double(v)  => protocol.writeDouble(v)
        case PrimitiveValue.String(v)  => protocol.writeString(v)
        case _                         => protocol.writeString(p.toString)
      }

    def getTType(reflect: Reflect[Binding, _]): Byte =
      reflect match {
        case p: Reflect.Primitive[Binding, _] =>
          p.primitiveType match {
            case _: PrimitiveType.Boolean => TType.BOOL
            case _: PrimitiveType.Byte    => TType.BYTE
            case _: PrimitiveType.Short   => TType.I16
            case _: PrimitiveType.Int     => TType.I32
            case _: PrimitiveType.Long    => TType.I64
            case _: PrimitiveType.Double  => TType.DOUBLE
            case _: PrimitiveType.Float   => TType.DOUBLE
            case _: PrimitiveType.String  => TType.STRING
            case _                        => TType.STRING
          }
        case _: Reflect.Record[Binding, _] => TType.STRUCT
        case _ if reflect.isSequence       => TType.LIST
        case _                             =>
          if (reflect.isOption) TType.LIST
          else TType.STRING
      }
  }

  private object Decoder {
    def decode[A](reflect: Reflect[Binding, A], protocol: TProtocol): A =
      reflect match {
        case p: Reflect.Primitive[Binding, A] =>
          decodePrimitive(p.primitiveType, protocol)

        case _ if reflect.isSequence =>
          val seq         = reflect.asSequenceUnknown.get.sequence
          val list        = protocol.readListBegin()
          val buf         = scala.collection.mutable.ListBuffer[Any]()
          val elemReflect = seq.element.asInstanceOf[Reflect[Binding, Any]]
          for (_ <- 0 until list.size) {
            buf += decode(elemReflect, protocol)
          }
          protocol.readListEnd()
          val dynSeq = DynamicValue.Sequence(Vector.from(buf.map(elemReflect.toDynamicValue(_))))
          reflect.fromDynamicValue(dynSeq).fold(e => throw new Exception(e.toString), identity)

        case _ =>
          // Option, Record, Variant
          if (reflect.isOption) {
            val list = protocol.readListBegin()
            val res  = if (list.size > 0) {
              val inner = reflect.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
              val v     = decode(inner, protocol)
              Some(v)
            } else {
              None
            }
            for (_ <- 1 until list.size) {
              val inner = reflect.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
              decode(inner, protocol)
            }
            protocol.readListEnd()
            res.asInstanceOf[A]
          } else {
            val dyn = decodeDynamic(reflect, protocol)
            reflect.fromDynamicValue(dyn).fold(e => throw new Exception(e.toString), identity)
          }
      }

    def decodeDynamic(reflect: Reflect[Binding, _], protocol: TProtocol): DynamicValue =
      if (reflect.isOption) {
        val list = protocol.readListBegin()
        val res  = if (list.size > 0) {
          val inner      = reflect.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
          val valDecoded = decodeDynamic(inner, protocol)

          DynamicValue.Variant("Some", DynamicValue.Record(Vector("value" -> valDecoded)))
        } else {
          DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
        }
        for (_ <- 1 until list.size) {
          val inner = reflect.optionInnerType.get.asInstanceOf[Reflect[Binding, Any]]
          decodeDynamic(inner, protocol)
        }
        protocol.readListEnd()
        res
      } else {
        reflect match {
          case r: Reflect.Record[Binding, _] =>
            protocol.readStructBegin()
            val values  = scala.collection.mutable.Map[String, DynamicValue]()
            var calling = true
            while (calling) {
              val field = protocol.readFieldBegin()
              if (field.`type` == TType.STOP) {
                calling = false
              } else {
                if (field.id > 0 && field.id <= r.fields.size) {
                  val term       = r.fields(field.id - 1)
                  val valDecoded = decodeDynamic(term.value, protocol)
                  values(term.name) = valDecoded
                } else {
                  TProtocolUtil.skip(protocol, field.`type`)
                }
                protocol.readFieldEnd()
              }
            }
            protocol.readStructEnd()
            DynamicValue.Record(Vector.from(values))

          case v: Reflect.Variant[Binding, _] =>
            protocol.readStructBegin()
            val field = protocol.readFieldBegin()
            if (field.`type` == TType.STOP) throw new Exception("Empty union")

            val res = if (field.id > 0 && field.id <= v.cases.size) {
              val caseTerm   = v.cases(field.id - 1)
              val valDecoded = decodeDynamic(caseTerm.value, protocol)
              DynamicValue.Variant(caseTerm.name, valDecoded)
            } else {
              throw new Exception(s"Unknown enum id: ${field.id}")
            }
            protocol.readFieldEnd()
            protocol.readStructEnd()
            res

          case _ => throw new Exception(s"Unsupported decode dynamic: $reflect")
        }
      }

    def decodePrimitive[A](primitiveType: PrimitiveType[A], protocol: TProtocol): A =
      (primitiveType match {
        case PrimitiveType.Unit             => ()
        case _: PrimitiveType.Boolean       => protocol.readBool()
        case _: PrimitiveType.Byte          => protocol.readByte()
        case _: PrimitiveType.Short         => protocol.readI16()
        case _: PrimitiveType.Int           => protocol.readI32()
        case _: PrimitiveType.Long          => protocol.readI64()
        case _: PrimitiveType.Float         => protocol.readDouble().toFloat
        case _: PrimitiveType.Double        => protocol.readDouble()
        case _: PrimitiveType.String        => protocol.readString()
        case _: PrimitiveType.Char          => protocol.readString().head
        case _: PrimitiveType.UUID          => java.util.UUID.fromString(protocol.readString())
        case _: PrimitiveType.BigDecimal    => BigDecimal(protocol.readString())
        case _: PrimitiveType.BigInt        => BigInt(protocol.readString())
        case _: PrimitiveType.DayOfWeek     => java.time.DayOfWeek.valueOf(protocol.readString())
        case _: PrimitiveType.Month         => java.time.Month.valueOf(protocol.readString())
        case _: PrimitiveType.MonthDay      => java.time.MonthDay.parse(protocol.readString())
        case _: PrimitiveType.Period        => java.time.Period.parse(protocol.readString())
        case _: PrimitiveType.Year          => java.time.Year.of(protocol.readI32())
        case _: PrimitiveType.YearMonth     => java.time.YearMonth.parse(protocol.readString())
        case _: PrimitiveType.ZoneId        => java.time.ZoneId.of(protocol.readString())
        case _: PrimitiveType.ZoneOffset    => java.time.ZoneOffset.of(protocol.readString())
        case _: PrimitiveType.Duration      => java.time.Duration.parse(protocol.readString())
        case _: PrimitiveType.Instant       => java.time.Instant.parse(protocol.readString())
        case _: PrimitiveType.LocalDate     => java.time.LocalDate.parse(protocol.readString())
        case _: PrimitiveType.LocalTime     => java.time.LocalTime.parse(protocol.readString())
        case _: PrimitiveType.LocalDateTime => java.time.LocalDateTime.parse(protocol.readString())
        case _                              => throw new Exception(s"Unsupported primitive decode: $primitiveType")
      }).asInstanceOf[A]
  }
}
