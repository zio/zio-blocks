package zio.blocks.avro

import zio.blocks.schema._
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.binding.Binding
import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.control.NonFatal

object AvroSchemaCodec {
  def encode(schema: Schema[?]): String = toAvroSchema(schema).toString

  def toAvroSchema(schema: Schema[?]): AvroSchema = toAvroSchema(schema.reflect)

  private[avro] def decode(avroSchemaJson: String): Either[Throwable, Schema[?]] = {
    val avroSchemaParser = new AvroSchema.Parser
    try toSchema(avroSchemaParser.parse(avroSchemaJson))
    catch { case error if NonFatal(error) => new Left(error) }
  }

  private[avro] def toSchema(avroSchema: AvroSchema): Either[Throwable, Schema[?]] =
    toReflect(avroSchema).map(x => new Schema(x))

  private[this] def toReflect(avroSchema: AvroSchema): Either[Throwable, Reflect[Binding, ?]] =
    avroSchema.getType match {
      case AvroSchema.Type.NULL    => new Right(Schema.unit.reflect)
      case AvroSchema.Type.BOOLEAN => new Right(Schema.boolean.reflect)
      case AvroSchema.Type.LONG    => new Right(Schema.long.reflect)
      case AvroSchema.Type.FLOAT   => new Right(Schema.float.reflect)
      case AvroSchema.Type.DOUBLE  => new Right(Schema.double.reflect)
      case AvroSchema.Type.STRING  =>
        avroSchema.getProp(primitiveTypePropName) match {
          case TypeName.zoneId.name => new Right(Schema.zoneId.reflect)
          case _                    => new Right(Schema.string.reflect)
        }
      case AvroSchema.Type.BYTES =>
        avroSchema.getProp(primitiveTypePropName) match {
          case TypeName.bigInt.name => new Right(Schema.bigInt.reflect)
          case _                    => unsupportedAvroSchema(avroSchema)
        }
      case AvroSchema.Type.FIXED =>
        avroSchema.getProp(primitiveTypePropName) match {
          case TypeName.currency.name => new Right(Schema.currency.reflect)
          case TypeName.uuid.name     => new Right(Schema.uuid.reflect)
          case _                      => unsupportedAvroSchema(avroSchema)
        }
      case AvroSchema.Type.INT =>
        avroSchema.getProp(primitiveTypePropName) match {
          case TypeName.byte.name       => new Right(Schema.byte.reflect)
          case TypeName.char.name       => new Right(Schema.char.reflect)
          case TypeName.short.name      => new Right(Schema.short.reflect)
          case TypeName.dayOfWeek.name  => new Right(Schema.dayOfWeek.reflect)
          case TypeName.month.name      => new Right(Schema.month.reflect)
          case TypeName.year.name       => new Right(Schema.year.reflect)
          case TypeName.zoneOffset.name => new Right(Schema.zoneOffset.reflect)
          case _                        => new Right(Schema.int.reflect)
        }
      case AvroSchema.Type.RECORD =>
        avroSchema.getProp(primitiveTypePropName) match {
          case TypeName.bigDecimal.name     => new Right(Schema.bigDecimal.reflect)
          case TypeName.duration.name       => new Right(Schema.duration.reflect)
          case TypeName.instant.name        => new Right(Schema.instant.reflect)
          case TypeName.localDate.name      => new Right(Schema.localDate.reflect)
          case TypeName.localDateTime.name  => new Right(Schema.localDateTime.reflect)
          case TypeName.localTime.name      => new Right(Schema.localTime.reflect)
          case TypeName.monthDay.name       => new Right(Schema.monthDay.reflect)
          case TypeName.offsetDateTime.name => new Right(Schema.offsetDateTime.reflect)
          case TypeName.offsetTime.name     => new Right(Schema.offsetTime.reflect)
          case TypeName.period.name         => new Right(Schema.period.reflect)
          case TypeName.yearMonth.name      => new Right(Schema.yearMonth.reflect)
          case TypeName.zonedDateTime.name  => new Right(Schema.zonedDateTime.reflect)
          case _                            =>
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
        val cases = Vector.newBuilder[Term[Binding, ?, ?]]
        val it    = avroSchema.getTypes.iterator()
        while (it.hasNext) {
          val case_ = it.next()
          toReflect(case_) match {
            case Right(reflect) => cases.addOne(new Term(reflect.typeName.name, reflect))
            case left           => return left
          }
        }
        val variant = Reflect.Variant[Binding, Any](
          cases = cases.result().asInstanceOf[IndexedSeq[Term[Binding, Any, Any]]],
          typeName = new TypeName[Any](new Namespace(Nil, Nil), "|"), // same as for Scala 3 union type
          variantBinding = null
        )
        new Right(variant)
      case AvroSchema.Type.ARRAY =>
        // TODO add support for Map[_, _] as array of tuples
        val element = toReflect(avroSchema.getElementType) match {
          case Right(reflect) => reflect
          case left           => return left
        }
        val sequence = new Reflect.Sequence[Binding, Any, List](
          element = element.asInstanceOf[Reflect[Binding, Any]],
          typeName = TypeName.list(element.typeName.asInstanceOf[TypeName[Any]]),
          seqBinding = null
        )
        new Right(sequence)
      case AvroSchema.Type.MAP =>
        val value = toReflect(avroSchema.getValueType) match {
          case Right(reflect) => reflect
          case left           => return left
        }
        val map = new Reflect.Map[Binding, String, Any, Map](
          key = Reflect.string,
          value = value.asInstanceOf[Reflect[Binding, Any]],
          typeName = TypeName.map(TypeName.string, value.typeName.asInstanceOf[TypeName[Any]]),
          mapBinding = null
        )
        new Right(map)
      case _ => unsupportedAvroSchema(avroSchema)
    }

  private[this] def toTypeName(avroSchema: AvroSchema): TypeName[?] = {
    val namespace = avroSchema.getNamespace
    val path      =
      if (namespace eq null) Array[String]()
      else namespace.split('.')
    val valueIdx           = path.indexWhere(segment => segment.nonEmpty && Character.isUpperCase(segment.charAt(0)))
    val (packages, values) =
      if (valueIdx < 0) (path, Array[String]())
      else path.splitAt(valueIdx)
    new TypeName(new Namespace(packages.toSeq, values.toSeq), avroSchema.getName)
  }

  private[this] def unsupportedAvroSchema(avroSchema: AvroSchema): Either[Throwable, Reflect[Binding, ?]] =
    new Left(new RuntimeException(s"Unsupported Avro schema: ${avroSchema.getName}"))

  private[this] def toAvroSchema(
    reflect: Reflect[Binding, ?],
    avroSchemas: mutable.HashMap[TypeName[?], AvroSchema] = new mutable.HashMap
  ): AvroSchema = {
    if (reflect.isPrimitive) {
      val primitive     = reflect.asPrimitive.get
      val primitiveType = primitive.primitiveType
      val typeName      = primitiveType.typeName
      val name          = typeName.name
      val namespace     = typeName.namespace.elements.mkString(".")
      primitiveType match {
        case _: PrimitiveType.Unit.type  => AvroSchema.create(AvroSchema.Type.NULL)
        case _: PrimitiveType.Boolean    => AvroSchema.create(AvroSchema.Type.BOOLEAN)
        case _: PrimitiveType.Byte       => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.Short      => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.Int        => AvroSchema.create(AvroSchema.Type.INT)
        case _: PrimitiveType.Long       => AvroSchema.create(AvroSchema.Type.LONG)
        case _: PrimitiveType.Float      => AvroSchema.create(AvroSchema.Type.FLOAT)
        case _: PrimitiveType.Double     => AvroSchema.create(AvroSchema.Type.DOUBLE)
        case _: PrimitiveType.Char       => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.String     => AvroSchema.create(AvroSchema.Type.STRING)
        case _: PrimitiveType.BigInt     => createAvroSchema(AvroSchema.Type.BYTES, typeName, reflect.typeName)
        case _: PrimitiveType.BigDecimal =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("mantissa", AvroSchema.create(AvroSchema.Type.BYTES)))
            add(new AvroSchema.Field("scale", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("precision", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("roundingMode", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.DayOfWeek => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.Duration  =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("seconds", AvroSchema.create(AvroSchema.Type.LONG)))
            add(new AvroSchema.Field("nanos", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Instant =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("epochSecond", AvroSchema.create(AvroSchema.Type.LONG)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.LocalDate =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.LocalDateTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
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
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("hour", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("minute", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("second", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("nano", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Month    => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.MonthDay =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("day", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.OffsetDateTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
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
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
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
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("years", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("days", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.Year      => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.YearMonth =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema.setFields(new util.ArrayList[AvroSchema.Field] {
            add(new AvroSchema.Field("year", AvroSchema.create(AvroSchema.Type.INT)))
            add(new AvroSchema.Field("month", AvroSchema.create(AvroSchema.Type.INT)))
          })
          avroSchema
        case _: PrimitiveType.ZoneId        => createAvroSchema(AvroSchema.Type.STRING, typeName, reflect.typeName)
        case _: PrimitiveType.ZoneOffset    => createAvroSchema(AvroSchema.Type.INT, typeName, reflect.typeName)
        case _: PrimitiveType.ZonedDateTime =>
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(primitiveTypePropName, name)
          if (typeName != reflect.typeName) avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
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
        case _: PrimitiveType.Currency => createFixedAvroSchema(3, typeName, reflect.typeName)
        case _: PrimitiveType.UUID     => createFixedAvroSchema(16, typeName, reflect.typeName)
        case null                      => sys.error(s"Unsupported primitive type: $name")
      }
    } else if (reflect.isVariant) {
      val variant    = reflect.asVariant.get
      val cases      = variant.cases
      val avroSchema = AvroSchema.createUnion(cases.map(case_ => toAvroSchema(case_.value, avroSchemas)).asJava)
      avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
      avroSchema
    } else if (reflect.isSequence) {
      val avroSchema = AvroSchema.createArray(toAvroSchema(reflect.asSequenceUnknown.get.sequence.element, avroSchemas))
      avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
      avroSchema
    } else if (reflect.isMap) {
      val map = reflect.asMapUnknown.get.map
      map.key.asPrimitive match {
        case Some(primitiveKey) if primitiveKey.primitiveType.isInstanceOf[PrimitiveType.String] =>
          val avroSchema = AvroSchema.createMap(toAvroSchema(map.value, avroSchemas))
          avroSchema.addProp(typeNamePropName, toPropValue(reflect.typeName))
          avroSchema
        case _ => sys.error(s"Expected string keys only")
      }
    } else {
      val record   = reflect.asRecord.get
      val typeName = record.typeName
      avroSchemas.get(typeName) match {
        case Some(avroSchema) => avroSchema
        case _                =>
          val name       = typeName.name
          val namespace  = typeName.namespace.elements.mkString(".")
          val avroSchema = AvroSchema.createRecord(name, null, namespace, false)
          avroSchema.addProp(typeNamePropName, toPropValue(typeName))
          avroSchemas.put(typeName, avroSchema)
          val fields = record.fields.map { field =>
            new AvroSchema.Field(field.name, toAvroSchema(field.value, avroSchemas))
          }.asJava
          avroSchema.setFields(fields)
          avroSchema
      }
    }
  }

  private def createAvroSchema(
    tpe: AvroSchema.Type,
    privateTypeName: TypeName[?],
    typeName: TypeName[?]
  ): AvroSchema = {
    val avroSchema = AvroSchema.create(tpe)
    avroSchema.addProp(primitiveTypePropName, privateTypeName.name)
    if (privateTypeName != typeName) avroSchema.addProp(typeNamePropName, toPropValue(typeName))
    avroSchema
  }

  private def createFixedAvroSchema(size: Int, privateTypeName: TypeName[?], typeName: TypeName[?]): AvroSchema = {
    val name       = privateTypeName.name
    val avroSchema = AvroSchema.createFixed(name, null, null, size)
    avroSchema.addProp(primitiveTypePropName, name)
    if (privateTypeName != typeName) avroSchema.addProp(typeNamePropName, toPropValue(typeName))
    avroSchema
  }

  private def toPropValue(typeName: TypeName[?]): AnyRef =
    new util.HashMap[String, AnyRef] {
      put("namespace", toPropValue(typeName.namespace))
      put("name", typeName.name)
      if (typeName.params.nonEmpty) put("params", typeName.params.map(param => toPropValue(param)).asJava)
    }

  private def toPropValue(namespace: Namespace): AnyRef =
    new util.HashMap[String, AnyRef] {
      if (namespace.packages.nonEmpty) put("packages", namespace.packages.asJava)
      if (namespace.values.nonEmpty) put("values", namespace.values.asJava)
    }

  private[this] val primitiveTypePropName: String = "zio.blocks.avro.primitiveType"
  private[this] val typeNamePropName: String      = "zio.blocks.avro.typeName"
}
