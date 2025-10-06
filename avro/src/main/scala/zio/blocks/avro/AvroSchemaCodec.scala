package zio.blocks.avro

import zio.blocks.schema._
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.binding.Binding
import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.control.NonFatal

trait AvroSchemaCodec {
  def encode(schema: Schema[?]): String

  def decode(avroSchemaJson: String): Either[Throwable, Schema[?]]
}

object AvroSchemaCodec extends AvroSchemaCodec {
  def toSchema(avroSchema: AvroSchema): Either[Throwable, Schema[?]] = toReflect(avroSchema).map(x => new Schema(x))

  def toAvroSchema(schema: Schema[?]): AvroSchema = toAvroSchema(schema.reflect)

  override def encode(schema: Schema[?]): String = toAvroSchema(schema).toString

  override def decode(avroSchemaJson: String): Either[Throwable, Schema[?]] = {
    val avroSchemaParser = new AvroSchema.Parser
    try toSchema(avroSchemaParser.parse(avroSchemaJson))
    catch { case error if NonFatal(error) => new Left(error) }
  }

  private[this] def toReflect(avroSchema: AvroSchema): Either[Throwable, Reflect[Binding, ?]] =
    avroSchema.getType match {
      case AvroSchema.Type.NULL    => new Right(Schema.unit.reflect)
      case AvroSchema.Type.BOOLEAN => new Right(Schema.boolean.reflect)
      case AvroSchema.Type.LONG    => new Right(Schema.long.reflect)
      case AvroSchema.Type.FLOAT   => new Right(Schema.float.reflect)
      case AvroSchema.Type.DOUBLE  => new Right(Schema.double.reflect)
      case AvroSchema.Type.STRING  =>
        avroSchema.getProp(primitiveTypePropName) match {
          case "ZoneId" => new Right(Schema.zoneId.reflect)
          case _        => new Right(Schema.string.reflect)
        }
      case AvroSchema.Type.BYTES =>
        avroSchema.getProp(primitiveTypePropName) match {
          case "BigInt" => new Right(Schema.bigInt.reflect)
          case _        => unsupportedAvroSchema(avroSchema)
        }
      case AvroSchema.Type.FIXED =>
        avroSchema.getProp(primitiveTypePropName) match {
          case "Currency" => new Right(Schema.currency.reflect)
          case "UUID"     => new Right(Schema.uuid.reflect)
          case _          => unsupportedAvroSchema(avroSchema)
        }
      case AvroSchema.Type.INT =>
        avroSchema.getProp(primitiveTypePropName) match {
          case "Byte"       => new Right(Schema.byte.reflect)
          case "Char"       => new Right(Schema.char.reflect)
          case "Short"      => new Right(Schema.short.reflect)
          case "DayOfWeek"  => new Right(Schema.dayOfWeek.reflect)
          case "Month"      => new Right(Schema.month.reflect)
          case "Year"       => new Right(Schema.year.reflect)
          case "ZoneOffset" => new Right(Schema.zoneOffset.reflect)
          case _            => new Right(Schema.int.reflect)
        }
      case AvroSchema.Type.RECORD =>
        avroSchema.getProp(primitiveTypePropName) match {
          case "BigDecimal"     => new Right(Schema.bigDecimal.reflect)
          case "Duration"       => new Right(Schema.duration.reflect)
          case "Instant"        => new Right(Schema.instant.reflect)
          case "LocalDate"      => new Right(Schema.localDate.reflect)
          case "LocalDateTime"  => new Right(Schema.localDateTime.reflect)
          case "LocalTime"      => new Right(Schema.localTime.reflect)
          case "MonthDay"       => new Right(Schema.monthDay.reflect)
          case "OffsetDateTime" => new Right(Schema.offsetDateTime.reflect)
          case "OffsetTime"     => new Right(Schema.offsetTime.reflect)
          case "Period"         => new Right(Schema.period.reflect)
          case "YearMonth"      => new Right(Schema.yearMonth.reflect)
          case "ZonedDateTime"  => new Right(Schema.zonedDateTime.reflect)
          case _                =>
            val fields = Vector.newBuilder[Term[Binding, ?, ?]]
            val it     = avroSchema.getFields.iterator()
            while (it.hasNext) {
              val field = it.next()
              toReflect(field.schema) match {
                case Right(reflect) => fields.addOne(new Term(field.name, reflect))
                case left           => return left
              }
            }
            val record = Reflect.Record[Binding, Any](
              fields = fields.result().asInstanceOf[IndexedSeq[Term[Binding, Any, Any]]],
              typeName = toTypeName(avroSchema).asInstanceOf[TypeName[Any]],
              recordBinding = null
            )
            new Right(record)
        }
      case AvroSchema.Type.UNION =>
        // sealed traits
        ???
      case AvroSchema.Type.ARRAY =>
        // Seq[_], Map[_, _]
        ???
      case AvroSchema.Type.MAP =>
        // Map[String, _]
        ???
      case _ => unsupportedAvroSchema(avroSchema)
    }

  private[this] def toTypeName(avroSchema: AvroSchema): TypeName[?] = {
    val path               = Option(avroSchema.getNamespace).fold(Array[String]())(_.split('.'))
    val (packages, values) = path.splitAt(path.indexWhere(_.headOption.forall(_.isUpper)))
    new TypeName(new Namespace(packages.toList, values.toList), avroSchema.getName)
  }

  private[this] def unsupportedAvroSchema(avroSchema: AvroSchema): Either[Throwable, Reflect[Binding, ?]] =
    new Left(new RuntimeException(s"Unsupported Avro schema: ${avroSchema.getName}"))

  private[this] def toAvroSchema(reflect: Reflect[Binding, ?]): AvroSchema = reflect match {
    case primitive: Reflect.Primitive[Binding, _] =>
      val primitiveType = primitive.primitiveType
      val typeName  = primitiveType.typeName
      val name      = typeName.name
      val namespace = typeName.namespace.elements.mkString(".")
      primitiveType match {
        case _: PrimitiveType.Unit.type  => AvroSchema.create(AvroSchema.Type.NULL)
        case _: PrimitiveType.Boolean    => AvroSchema.create(AvroSchema.Type.BOOLEAN)
        case _: PrimitiveType.Byte       => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.Short      => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.Int        => AvroSchema.create(AvroSchema.Type.INT)
        case _: PrimitiveType.Long       => AvroSchema.create(AvroSchema.Type.LONG)
        case _: PrimitiveType.Float      => AvroSchema.create(AvroSchema.Type.FLOAT)
        case _: PrimitiveType.Double     => AvroSchema.create(AvroSchema.Type.DOUBLE)
        case _: PrimitiveType.Char       => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.String     => AvroSchema.create(AvroSchema.Type.STRING)
        case _: PrimitiveType.BigInt     => createAvroSchema(AvroSchema.Type.BYTES, name)
        case _: PrimitiveType.BigDecimal =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("mantissa", AvroSchema.create(AvroSchema.Type.BYTES)))
            add(new AvroSchema.Field("scale", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("precision", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("roundingMode", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.DayOfWeek => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.Duration  =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("seconds", AvroSchema.create(AvroSchema.Type.LONG)))
            add(new AvroSchema.Field("nanos", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Instant =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("epochSecond", AvroSchema.create(AvroSchema.Type.LONG)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.LocalDate =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.LocalDateTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.LocalTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Month    => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.MonthDay =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.OffsetDateTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("offsetSecond", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.OffsetTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("offsetSecond", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Period =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("years", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("days", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Year      => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.YearMonth =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.ZoneId        => createAvroSchema(AvroSchema.Type.STRING, name)
        case _: PrimitiveType.ZoneOffset    => createAvroSchema(AvroSchema.Type.INT, name)
        case _: PrimitiveType.ZonedDateTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("offsetSecond", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("zoneId", AvroSchema.create(AvroSchema.Type.STRING)))
          })
          avroSchema
        case _: PrimitiveType.Currency => createFixedAvroSchema(3, name)
        case _: PrimitiveType.UUID     => createFixedAvroSchema(16, name)
        case null                      => sys.error(s"Unsupported primitive type: $name")
      }
    case record: Reflect.Record[Binding, _] =>
      val typeName = record.typeName
      var name = typeName.name
      val params = typeName.params
      if (params.nonEmpty) name += params.mkString("[", ",", "]")
      val namespace = typeName.namespace.elements.mkString(".")
      val fields = record.fields.map(field => new AvroSchema.Field(field.name, toAvroSchema(field.value))).asJava
      AvroSchema.createRecord(name, null, namespace, false, fields)
    case _ =>
      // non-primitive types
      ???
  }

  private def createAvroSchema(tpe: AvroSchema.Type, name: String): AvroSchema = {
    val avroSchema = AvroSchema.create(tpe)
    avroSchema.addProp(primitiveTypePropName, name)
    avroSchema
  }

  private def createFixedAvroSchema(size: Int, name: String): AvroSchema = {
    val avroSchema = AvroSchema.createFixed(name, null, null, size)
    avroSchema.addProp(primitiveTypePropName, name)
    avroSchema
  }

  private[this] val primitiveTypePropName: String = "zio.blocks.avro.primitiveType"
}
