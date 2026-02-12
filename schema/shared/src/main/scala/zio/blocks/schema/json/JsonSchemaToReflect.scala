package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder, ChunkMap, NonEmptyChunk}
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.{Owner, TypeId}

private[schema] object JsonSchemaToReflect {

  private[this] def extractTitle(schema: JsonSchema): Option[String] = schema match {
    case obj: JsonSchema.Object => obj.title
    case _                      => None
  }

  private[this] def typeIdFromTitle(title: Option[String]): TypeId[DynamicValue] = title match {
    case Some(name) => TypeId.nominal[DynamicValue](name, Owner.Root)
    case _          => TypeId.of[DynamicValue]
  }

  private[json] sealed trait Shape
  private[json] object Shape {
    sealed trait PrimKind
    object PrimKind {
      case object String  extends PrimKind
      case object Integer extends PrimKind
      case object Number  extends PrimKind
      case object Boolean extends PrimKind
      case object Null    extends PrimKind
    }

    case class Primitive(kind: PrimKind, schema: JsonSchema.Object)                                 extends Shape
    case class Record(fields: ChunkMap[String, JsonSchema], required: Set[String], closed: Boolean) extends Shape
    case class MapShape(values: JsonSchema)                                                         extends Shape
    case class Sequence(items: JsonSchema)                                                          extends Shape
    case class Tuple(prefixItems: Chunk[JsonSchema])                                                extends Shape
    case class Enum(cases: Chunk[String])                                                           extends Shape
    case class KeyVariant(cases: ChunkMap[String, JsonSchema])                                      extends Shape
    case class FieldVariant(discriminator: String, cases: ChunkMap[String, JsonSchema])             extends Shape
    case class OptionOf(inner: JsonSchema)                                                          extends Shape
    case object Dynamic                                                                             extends Shape
  }

  def toReflect(jsonSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val shape = analyze(jsonSchema)
    wrapWithValidation(jsonSchema, shape, build(shape, jsonSchema))
  }

  private[json] def analyze(schema: JsonSchema): Shape = schema match {
    case JsonSchema.True        => Shape.Dynamic
    case JsonSchema.False       => Shape.Dynamic
    case obj: JsonSchema.Object =>
      analyzeEnum(obj)
        .orElse(analyzeOption(obj))
        .orElse(analyzeVariant(obj))
        .orElse(analyzeTuple(obj))
        .orElse(analyzeSequence(obj))
        .orElse(analyzeMapOrRecord(obj))
        .orElse(analyzePrimitive(obj))
        .getOrElse(Shape.Dynamic)
  }

  private[this] def analyzeEnum(obj: JsonSchema.Object): Option[Shape] =
    obj.`enum` match {
      case Some(values) =>
        val strings = values.collect { case Json.String(s) => s }
        if (strings.length == values.length) new Some(new Shape.Enum(strings))
        else None
      case _ => None
    }

  private[this] def analyzeOption(obj: JsonSchema.Object): Option[Shape] =
    obj.`type` match {
      case Some(SchemaType.Union(types)) if types.length == 2 && types.contains(JsonSchemaType.Null) =>
        types.collectFirst { case jt if jt ne JsonSchemaType.Null => jt } match {
          case Some(jt) =>
            new Some(new Shape.OptionOf(new JsonSchema.Object(`type` = new Some(new SchemaType.Single(jt)))))
          case _ => None
        }
      case _ =>
        obj.anyOf match {
          case Some(schemas) if schemas.length == 2 =>
            val nullSchema   = schemas.find(isNullSchema)
            val otherSchemas = schemas.filterNot(isNullSchema)
            if (nullSchema.isDefined && otherSchemas.length == 1) new Some(new Shape.OptionOf(otherSchemas.head))
            else None
          case _ => None
        }
    }

  private[this] def isNullSchema(schema: JsonSchema): Boolean = schema match {
    case obj: JsonSchema.Object =>
      obj.`type` match {
        case Some(SchemaType.Single(JsonSchemaType.Null)) => true
        case _                                            => false
      }
    case _ => false
  }

  private[this] def analyzeVariant(obj: JsonSchema.Object): Option[Shape] = obj.oneOf match {
    case Some(cases) => analyzeKeyDiscriminatedVariant(cases).orElse(analyzeFieldDiscriminatedVariant(cases))
    case _           => None
  }

  private[this] def analyzeKeyDiscriminatedVariant(cases: NonEmptyChunk[JsonSchema]): Option[Shape.KeyVariant] = {
    val extracted = cases.toChunk.flatMap {
      case caseObj: JsonSchema.Object =>
        caseObj.properties match {
          case Some(props) if props.size == 1 =>
            val kv = props.head
            caseObj.required match {
              case Some(req) if req.size == 1 && req.contains(kv._1) => new Some(kv)
              case _                                                 => None
            }
          case _ => None
        }
      case _ => None
    }
    if (extracted.length == cases.length) new Some(new Shape.KeyVariant(ChunkMap.fromChunk(extracted)))
    else None
  }

  private[this] def analyzeFieldDiscriminatedVariant(cases: NonEmptyChunk[JsonSchema]): Option[Shape.FieldVariant] = {
    val caseObjects = cases.collect { case obj: JsonSchema.Object => obj }
    if (caseObjects.length != cases.length) return None
    val allConstFields = caseObjects.map { obj =>
      val fields = Set.newBuilder[String]
      obj.properties
        .getOrElse(ChunkMap.empty)
        .foreach {
          case (name, propSchema: JsonSchema.Object) if propSchema.const.isDefined => fields.addOne(name)
          case _                                                                   =>
        }
      fields.result()
    }
    val commonConstFields = allConstFields.reduceOption(_ intersect _).getOrElse(Set.empty)
    commonConstFields.headOption.flatMap { discField =>
      val extracted = caseObjects.flatMap { obj =>
        val props = obj.properties.getOrElse(ChunkMap.empty)
        props.get(discField) match {
          case Some(propSchema: JsonSchema.Object) =>
            propSchema.const.collect { case Json.String(caseName) =>
              val bodyProps    = props.removed(discField)
              val bodyRequired = obj.required.map(_ - discField).filter(_.nonEmpty)
              val bodySchema   = JsonSchema.obj(
                properties = if (bodyProps.nonEmpty) new Some(bodyProps) else None,
                required = bodyRequired,
                additionalProperties = obj.additionalProperties
              )
              (caseName, bodySchema)
            }
          case _ => None
        }
      }
      if (extracted.length == cases.length) new Some(new Shape.FieldVariant(discField, ChunkMap.fromChunk(extracted)))
      else None
    }
  }

  private[this] def analyzeTuple(obj: JsonSchema.Object): Option[Shape] = obj.prefixItems match {
    case Some(items) if obj.items.contains(JsonSchema.False) => new Some(new Shape.Tuple(items))
    case _                                                   => None
  }

  private[this] def analyzeSequence(obj: JsonSchema.Object): Option[Shape] = obj.items match {
    case Some(itemsSchema) if itemsSchema ne JsonSchema.False => new Some(new Shape.Sequence(itemsSchema))
    case _                                                    => None
  }

  private[this] def analyzeMapOrRecord(obj: JsonSchema.Object): Option[Shape] = {
    val hasProperties          = obj.properties.exists(_.nonEmpty)
    val hasOnlyAdditionalProps = !hasProperties &&
      obj.additionalProperties.isDefined &&
      (obj.additionalProperties.get ne JsonSchema.False)
    if (hasOnlyAdditionalProps) new Some(Shape.MapShape(obj.additionalProperties.get))
    else if (hasProperties) {
      val props    = obj.properties.getOrElse(ChunkMap.empty)
      val required = obj.required.getOrElse(Set.empty)
      val closed   = obj.additionalProperties.contains(JsonSchema.False)
      new Some(Shape.Record(props, required, closed))
    } else None
  }

  private[this] def analyzePrimitive(obj: JsonSchema.Object): Option[Shape] = obj.`type` match {
    case Some(st) =>
      st match {
        case SchemaType.Single(jst) =>
          new Some(
            new Shape.Primitive(
              jst match {
                case JsonSchemaType.String  => Shape.PrimKind.String
                case JsonSchemaType.Integer => Shape.PrimKind.Integer
                case JsonSchemaType.Number  => Shape.PrimKind.Number
                case JsonSchemaType.Boolean => Shape.PrimKind.Boolean
                case JsonSchemaType.Null    => Shape.PrimKind.Null
                case _                      => return None
              },
              obj
            )
          )
        case _ => None
      }
    case _ => None
  }

  private[this] def build(shape: Shape, originalSchema: JsonSchema): Reflect[Binding, DynamicValue] = shape match {
    case Shape.Primitive(kind, schemaObj)       => buildPrimitive(kind, schemaObj)
    case Shape.Record(fields, required, closed) => buildRecord(fields, required, closed, extractTitle(originalSchema))
    case Shape.MapShape(values)                 => buildMap(values)
    case Shape.Sequence(items)                  => buildSequence(items)
    case Shape.Tuple(prefixItems)               => buildTuple(prefixItems)
    case Shape.Enum(cases)                      => buildEnum(cases, extractTitle(originalSchema))
    case Shape.KeyVariant(cases)                => buildKeyVariant(cases, extractTitle(originalSchema))
    case Shape.FieldVariant(disc, cases)        => buildFieldVariant(disc, cases, extractTitle(originalSchema))
    case Shape.OptionOf(inner)                  => toReflect(inner)
    case Shape.Dynamic                          => Reflect.dynamic[Binding]
  }

  private[this] def buildPrimitive(kind: Shape.PrimKind, schemaObj: JsonSchema.Object): Reflect[Binding, DynamicValue] =
    wrapPrimitive((kind match {
      case Shape.PrimKind.String  => new PrimitiveType.String(buildStringValidation(schemaObj))
      case Shape.PrimKind.Integer => new PrimitiveType.BigInt(buildBigIntValidation(schemaObj))
      case Shape.PrimKind.Number  => new PrimitiveType.BigDecimal(buildBigDecimalValidation(schemaObj))
      case Shape.PrimKind.Boolean => new PrimitiveType.Boolean(Validation.None)
      case Shape.PrimKind.Null    => return Reflect.dynamic[Binding]
    }).asInstanceOf[PrimitiveType[?]])

  private[this] def buildStringValidation(obj: JsonSchema.Object): Validation[String] =
    obj.pattern match { // Pattern takes precedence; length constraints cannot be combined in Validation ADT
      case Some(regex) => Validation.String.Pattern(regex.value)
      case _           =>
        val minLen = obj.minLength.map(_.value)
        val maxLen = obj.maxLength.map(_.value)
        if ((minLen ne None) || (maxLen ne None)) {
          if ((minLen ne None) && (maxLen ne None) && minLen.get == maxLen.get && minLen.get == 1) {
            Validation.String.NonEmpty
          } else new Validation.String.Length(minLen, maxLen)
        } else Validation.None
    }

  private[this] def buildBigIntValidation(obj: JsonSchema.Object): Validation[BigInt] = {
    val min = obj.minimum.orElse(obj.exclusiveMinimum).map(_.toBigInt)
    val max = obj.maximum.orElse(obj.exclusiveMaximum).map(_.toBigInt)
    if (min.isDefined || max.isDefined) new Validation.Numeric.Range(min, max)
    else Validation.None
  }

  private[this] def buildBigDecimalValidation(obj: JsonSchema.Object): Validation[BigDecimal] = {
    val min = obj.minimum.orElse(obj.exclusiveMinimum)
    val max = obj.maximum.orElse(obj.exclusiveMaximum)
    if (min.isDefined || max.isDefined) new Validation.Numeric.Range(min, max)
    else Validation.None
  }

  private[this] def wrapPrimitive[A](primitiveType: PrimitiveType[A]): Reflect[Binding, DynamicValue] = {
    val innerReflect   = new Reflect.Primitive[Binding, A](primitiveType, primitiveType.typeId, primitiveType.binding)
    val wrapperBinding = new Binding.Wrapper[DynamicValue, A](
      wrap = a => primitiveType.toDynamicValue(a),
      unwrap = dv =>
        primitiveType.fromDynamicValue(dv) match {
          case Right(a) => a
          case _        =>
            primitiveType match {
              case _: PrimitiveType.String     => ""
              case _: PrimitiveType.BigInt     => BigInt(0)
              case _: PrimitiveType.BigDecimal => BigDecimal(0)
              case _: PrimitiveType.Boolean    => false
              case PrimitiveType.Unit          => ()
              case _                           => null.asInstanceOf[A]
            }
        }
    )
    new Reflect.Wrapper(innerReflect, TypeId.of[DynamicValue], wrapperBinding)
  }

  private[this] def buildRecord(
    fields: ChunkMap[String, JsonSchema],
    @scala.annotation.unused required: Set[String],
    closed: Boolean,
    title: Option[String]
  ): Reflect[Binding, DynamicValue] = {
    val fieldCount = fields.size
    val fieldTerms = Chunk.from(fields).map { case (fieldName, fieldSchema) =>
      new Term[Binding, DynamicValue, DynamicValue](fieldName, toReflect(fieldSchema))
    }
    val fieldNames    = fields.toArray.map(_._1)
    val recordBinding = new Binding.Record[DynamicValue](
      constructor = new Constructor[DynamicValue] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = fieldCount)

        def construct(in: Registers, offset: RegisterOffset): DynamicValue = {
          val builder = ChunkBuilder.make[(String, DynamicValue)]()
          var idx     = 0
          while (idx < fieldCount) {
            var elem = in.getObject(offset + idx).asInstanceOf[DynamicValue]
            if (elem eq null) elem = DynamicValue.Null
            builder.addOne((fieldNames(idx), elem))
            idx += 1
          }
          new DynamicValue.Record(builder.result())
        }
      },
      deconstructor = new Deconstructor[DynamicValue] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = fieldCount)

        def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicValue): Unit = in match {
          case DynamicValue.Record(recordFields) =>
            val fieldMap = recordFields.toMap
            var idx      = 0
            while (idx < fieldCount) {
              val value = fieldMap.getOrElse(fieldNames(idx), DynamicValue.Null)
              out.setObject(offset + idx, value.asInstanceOf[AnyRef])
              idx += 1
            }
          case _ =>
            var idx = 0
            while (idx < fieldCount) {
              out.setObject(offset + idx, DynamicValue.Null.asInstanceOf[AnyRef])
              idx += 1
            }
        }
      }
    )
    val baseRecord = new Reflect.Record(fieldTerms, typeIdFromTitle(title), recordBinding)
    if (closed) baseRecord
    else baseRecord.modifier(Modifier.config("json.closure", "open"))
  }

  private[this] def buildMap(valuesSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val valueReflect   = toReflect(valuesSchema)
    val keyReflect     = Reflect.dynamic[Binding]
    val mapReflect     = Reflect.map[Binding, DynamicValue, DynamicValue](keyReflect, valueReflect)
    val wrapperBinding = new Binding.Wrapper[DynamicValue, Map[DynamicValue, DynamicValue]](
      wrap = map => new DynamicValue.Map(Chunk.from(map)),
      unwrap = {
        case DynamicValue.Map(entries) => entries.toMap
        case _                         => Map.empty
      }
    )
    new Reflect.Wrapper(mapReflect, TypeId.of[DynamicValue], wrapperBinding)
  }

  private[this] def buildSequence(itemsSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val elementReflect                                    = toReflect(itemsSchema)
    val seqReflect: Reflect[Binding, Chunk[DynamicValue]] = Reflect.chunk[Binding, DynamicValue](elementReflect)
    val wrapperBinding                                    = new Binding.Wrapper[DynamicValue, Chunk[DynamicValue]](
      wrap = chunk => DynamicValue.Sequence(chunk),
      unwrap = {
        case DynamicValue.Sequence(elements) => elements
        case _                               => Chunk.empty
      }
    )
    new Reflect.Wrapper(seqReflect, TypeId.of[DynamicValue], wrapperBinding)
  }

  private[this] def buildTuple(prefixItems: Chunk[JsonSchema]): Reflect[Binding, DynamicValue] = {
    val fieldTerms = prefixItems.map {
      var idx = 0
      schema =>
        idx += 1
        new Term[Binding, DynamicValue, DynamicValue](s"_$idx", toReflect(schema))
    }
    val tupleSize     = fieldTerms.length
    val recordBinding = new Binding.Record[DynamicValue](
      constructor = new Constructor[DynamicValue] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = tupleSize)

        def construct(in: Registers, offset: RegisterOffset): DynamicValue = {
          val builder = ChunkBuilder.make[DynamicValue](tupleSize)
          var idx     = 0
          while (idx < tupleSize) {
            val elem = in.getObject(offset + idx).asInstanceOf[DynamicValue]
            builder.addOne(if (elem != null) elem else DynamicValue.Null)
            idx += 1
          }
          new DynamicValue.Sequence(builder.result())
        }
      },
      deconstructor = new Deconstructor[DynamicValue] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = tupleSize)

        def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicValue): Unit = in match {
          case DynamicValue.Sequence(elements) =>
            var idx = 0
            while (idx < tupleSize) {
              val elem = if (idx < elements.length) elements(idx) else DynamicValue.Null
              out.setObject(offset + idx, elem.asInstanceOf[AnyRef])
              idx += 1
            }
          case _ =>
            var idx = 0
            while (idx < tupleSize) {
              out.setObject(offset + idx, DynamicValue.Null.asInstanceOf[AnyRef])
              idx += 1
            }
        }
      }
    )
    new Reflect.Record[Binding, DynamicValue](fieldTerms, TypeId.of[DynamicValue], recordBinding)
  }

  private[this] def buildEnum(cases: Chunk[String], title: Option[String]): Reflect[Binding, DynamicValue] = {
    val caseTerms = cases.map { caseName =>
      val emptyRecordBinding = new Binding.Record[DynamicValue](
        constructor = new Constructor[DynamicValue] {
          def usedRegisters: RegisterOffset                                  = 0L
          def construct(in: Registers, offset: RegisterOffset): DynamicValue = DynamicValue.Record(Chunk.empty)
        },
        deconstructor = new Deconstructor[DynamicValue] {
          def usedRegisters: RegisterOffset                                               = 0L
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicValue): Unit = ()
        }
      )
      val caseReflect =
        new Reflect.Record[Binding, DynamicValue](Chunk.empty, TypeId.of[DynamicValue], emptyRecordBinding)
      new Term[Binding, DynamicValue, DynamicValue](caseName, caseReflect)
    }
    val caseNames      = cases.toArray
    val variantBinding = new Binding.Variant[DynamicValue](
      discriminator = new Discriminator[DynamicValue] {
        def discriminate(a: DynamicValue): Int = a match {
          case DynamicValue.Variant(name, _) =>
            val idx = caseNames.indexOf(name)
            if (idx >= 0) idx else 0
          case _ => 0
        }
      },
      matchers = new Matchers[DynamicValue](
        Chunk.fromArray(caseNames.map { cn =>
          new Matcher[DynamicValue] {
            override def downcastOrNull(any: Any): DynamicValue = any match {
              case dv @ DynamicValue.Variant(name, _) if name == cn => dv
              case _                                                => null.asInstanceOf[DynamicValue]
            }
          }
        })
      )
    )
    new Reflect.Variant(caseTerms, typeIdFromTitle(title), variantBinding)
  }

  private[this] def buildKeyVariant(
    cases: ChunkMap[String, JsonSchema],
    title: Option[String]
  ): Reflect[Binding, DynamicValue] = {
    val caseTerms = Chunk.fromArray {
      val caseNames = cases.keysChunk
      val schemas   = cases.valuesChunk
      val len       = cases.size
      val arr       = new Array[Term[Binding, DynamicValue, DynamicValue]](len)
      var idx       = 0
      while (idx < len) {
        arr(idx) = new Term[Binding, DynamicValue, DynamicValue](caseNames(idx), toReflect(schemas(idx)))
        idx += 1
      }
      arr
    }
    val caseNames      = cases.keysChunk.toArray
    val variantBinding = new Binding.Variant[DynamicValue](
      discriminator = new Discriminator[DynamicValue] {
        def discriminate(a: DynamicValue): Int = a match {
          case DynamicValue.Variant(name, _) =>
            val idx = caseNames.indexOf(name)
            if (idx >= 0) idx else 0
          case _ => 0
        }
      },
      matchers = new Matchers[DynamicValue](
        Chunk.fromArray(caseNames.map { cn =>
          new Matcher[DynamicValue] {
            override def downcastOrNull(any: Any): DynamicValue = any match {
              case dv @ DynamicValue.Variant(name, _) if name == cn => dv
              case _                                                => null.asInstanceOf[DynamicValue]
            }
          }
        })
      )
    )
    new Reflect.Variant(caseTerms, typeIdFromTitle(title), variantBinding)
  }

  private[this] def buildFieldVariant(
    @scala.annotation.unused discriminator: String,
    cases: ChunkMap[String, JsonSchema],
    title: Option[String]
  ): Reflect[Binding, DynamicValue] = buildKeyVariant(cases, title)

  private[this] def wrapWithValidation(
    @scala.annotation.unused schema: JsonSchema,
    shape: Shape,
    built: Reflect[Binding, DynamicValue]
  ): Reflect[Binding, DynamicValue] = shape match {
    case Shape.Tuple(prefixItems) => wrapTupleWithValidation(prefixItems.length, built)
    case _                        => built
  }

  private[this] def wrapTupleWithValidation(
    expectedLength: Int,
    inner: Reflect[Binding, DynamicValue]
  ): Reflect[Binding, DynamicValue] = {
    val validatingBinding = new Binding.Wrapper[DynamicValue, DynamicValue](
      wrap = dv => {
        validateTuple(dv, expectedLength) match {
          case Some(error) => throw error
          case _           => dv
        }
      },
      unwrap = dv => dv
    )
    new Reflect.Wrapper[Binding, DynamicValue, DynamicValue](
      wrapped = inner,
      typeId = TypeId.of[DynamicValue],
      wrapperBinding = validatingBinding
    )
  }

  private[this] def validateTuple(dv: DynamicValue, expectedLength: Int): Option[SchemaError] = dv match {
    case DynamicValue.Sequence(elements) =>
      val len = elements.length
      if (len == expectedLength) None
      else new Some(SchemaError.expectationMismatch(Nil, s"Expected tuple of length $expectedLength, got $len"))
    case _ => new Some(SchemaError.expectationMismatch(Nil, "Expected a sequence (tuple)"))
  }
}
