package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.{Owner, TypeId}

private[schema] object JsonSchemaToReflect {

  private def extractTitle(schema: JsonSchema): Option[String] = schema match {
    case obj: JsonSchema.Object => obj.title
    case _                      => None
  }

  private def typeIdFromTitle(title: Option[String]): TypeId[DynamicValue] =
    title match {
      case Some(name) => TypeId.nominal[DynamicValue](name, Owner.Root)
      case None       => TypeId.of[DynamicValue]
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

    case class Primitive(kind: PrimKind, schema: JsonSchema.Object)                               extends Shape
    case class Record(fields: List[(String, JsonSchema)], required: Set[String], closed: Boolean) extends Shape
    case class MapShape(values: JsonSchema)                                                       extends Shape
    case class Sequence(items: JsonSchema)                                                        extends Shape
    case class Tuple(prefixItems: List[JsonSchema])                                               extends Shape
    case class Enum(cases: List[String])                                                          extends Shape
    case class KeyVariant(cases: List[(String, JsonSchema)])                                      extends Shape
    case class FieldVariant(discriminator: String, cases: List[(String, JsonSchema)])             extends Shape
    case class OptionOf(inner: JsonSchema)                                                        extends Shape
    case object Dynamic                                                                           extends Shape
  }

  def toReflect(jsonSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val shape = analyze(jsonSchema)
    val base  = build(shape, jsonSchema)
    wrapWithValidation(jsonSchema, shape, base)
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

  private def analyzeEnum(obj: JsonSchema.Object): Option[Shape] =
    obj.`enum` match {
      case Some(values) =>
        // values is ::[Json], guaranteed non-empty by type
        val strings = values.collect { case Json.String(s) => s }
        if (strings.length == values.length) Some(Shape.Enum(strings))
        else None
      case None => None
    }

  private def analyzeOption(obj: JsonSchema.Object): Option[Shape] =
    obj.`type` match {
      case Some(SchemaType.Union(types)) if types.length == 2 && types.contains(JsonSchemaType.Null) =>
        val nonNull = types.filterNot(_ == JsonSchemaType.Null).headOption
        nonNull.flatMap { jt =>
          val innerSchema = JsonSchema.Object(`type` = Some(SchemaType.Single(jt)))
          Some(Shape.OptionOf(innerSchema))
        }
      case _ =>
        obj.anyOf match {
          case Some(schemas) if schemas.length == 2 =>
            val nullSchema   = schemas.find(isNullSchema)
            val otherSchemas = schemas.filterNot(isNullSchema)
            if (nullSchema.isDefined && otherSchemas.length == 1) {
              Some(Shape.OptionOf(otherSchemas.head))
            } else None
          case _ => None
        }
    }

  private def isNullSchema(schema: JsonSchema): Boolean = schema match {
    case obj: JsonSchema.Object =>
      obj.`type` match {
        case Some(SchemaType.Single(JsonSchemaType.Null)) => true
        case _                                            => false
      }
    case _ => false
  }

  private def analyzeVariant(obj: JsonSchema.Object): Option[Shape] =
    obj.oneOf match {
      case Some(cases) =>
        analyzeKeyDiscriminatedVariant(cases)
          .orElse(analyzeFieldDiscriminatedVariant(cases))
      case _ => None
    }

  private def analyzeKeyDiscriminatedVariant(cases: ::[JsonSchema]): Option[Shape.KeyVariant] = {
    val extracted = cases.flatMap {
      case caseObj: JsonSchema.Object =>
        caseObj.properties match {
          case Some(props) if props.size == 1 =>
            val (caseName, bodySchema) = props.head
            caseObj.required match {
              case Some(req) if req.size == 1 && req.contains(caseName) =>
                Some((caseName, bodySchema))
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
    if (extracted.length == cases.length) Some(Shape.KeyVariant(extracted))
    else None
  }

  private def analyzeFieldDiscriminatedVariant(cases: ::[JsonSchema]): Option[Shape.FieldVariant] = {
    val caseObjects = cases.collect { case obj: JsonSchema.Object => obj }
    if (caseObjects.length != cases.length) return None

    val allConstFields: List[Set[String]] = caseObjects.map { obj =>
      obj.properties
        .getOrElse(ChunkMap.empty)
        .collect {
          case (name, propSchema: JsonSchema.Object) if propSchema.const.isDefined => name
        }
        .toSet
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
                properties = if (bodyProps.nonEmpty) Some(bodyProps) else None,
                required = bodyRequired,
                additionalProperties = obj.additionalProperties
              )
              (caseName, bodySchema)
            }
          case _ => None
        }
      }
      if (extracted.length == cases.length) Some(Shape.FieldVariant(discField, extracted))
      else None
    }
  }

  private def analyzeTuple(obj: JsonSchema.Object): Option[Shape] =
    obj.prefixItems match {
      case Some(items) if items.nonEmpty && obj.items.contains(JsonSchema.False) =>
        Some(Shape.Tuple(items))
      case _ => None
    }

  private def analyzeSequence(obj: JsonSchema.Object): Option[Shape] =
    obj.items match {
      case Some(itemsSchema) if itemsSchema != JsonSchema.False =>
        Some(Shape.Sequence(itemsSchema))
      case _ => None
    }

  private def analyzeMapOrRecord(obj: JsonSchema.Object): Option[Shape] = {
    val hasProperties          = obj.properties.exists(_.nonEmpty)
    val hasOnlyAdditionalProps = !hasProperties &&
      obj.additionalProperties.isDefined &&
      obj.additionalProperties.get != JsonSchema.False

    if (hasOnlyAdditionalProps) {
      Some(Shape.MapShape(obj.additionalProperties.get))
    } else if (hasProperties) {
      val props    = obj.properties.getOrElse(ChunkMap.empty).toList
      val required = obj.required.getOrElse(Set.empty)
      val closed   = obj.additionalProperties.contains(JsonSchema.False)
      Some(Shape.Record(props, required, closed))
    } else {
      None
    }
  }

  private def analyzePrimitive(obj: JsonSchema.Object): Option[Shape] =
    obj.`type` match {
      case Some(SchemaType.Single(JsonSchemaType.String))  => Some(Shape.Primitive(Shape.PrimKind.String, obj))
      case Some(SchemaType.Single(JsonSchemaType.Integer)) => Some(Shape.Primitive(Shape.PrimKind.Integer, obj))
      case Some(SchemaType.Single(JsonSchemaType.Number))  => Some(Shape.Primitive(Shape.PrimKind.Number, obj))
      case Some(SchemaType.Single(JsonSchemaType.Boolean)) => Some(Shape.Primitive(Shape.PrimKind.Boolean, obj))
      case Some(SchemaType.Single(JsonSchemaType.Null))    => Some(Shape.Primitive(Shape.PrimKind.Null, obj))
      case _                                               => None
    }

  private def build(shape: Shape, originalSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val title = extractTitle(originalSchema)
    shape match {
      case Shape.Primitive(kind, schemaObj)       => buildPrimitive(kind, schemaObj)
      case Shape.Record(fields, required, closed) => buildRecord(fields, required, closed, title)
      case Shape.MapShape(values)                 => buildMap(values)
      case Shape.Sequence(items)                  => buildSequence(items)
      case Shape.Tuple(prefixItems)               => buildTuple(prefixItems)
      case Shape.Enum(cases)                      => buildEnum(cases, title)
      case Shape.KeyVariant(cases)                => buildKeyVariant(cases, title)
      case Shape.FieldVariant(disc, cases)        => buildFieldVariant(disc, cases, title)
      case Shape.OptionOf(inner)                  => toReflect(inner)
      case Shape.Dynamic                          => Reflect.dynamic[Binding]
    }
  }

  private def buildPrimitive(kind: Shape.PrimKind, schemaObj: JsonSchema.Object): Reflect[Binding, DynamicValue] =
    kind match {
      case Shape.PrimKind.String =>
        val validation    = buildStringValidation(schemaObj)
        val primitiveType = new PrimitiveType.String(validation)
        wrapPrimitive(primitiveType)

      case Shape.PrimKind.Integer =>
        val validation    = buildBigIntValidation(schemaObj)
        val primitiveType = new PrimitiveType.BigInt(validation)
        wrapPrimitive(primitiveType)

      case Shape.PrimKind.Number =>
        val validation    = buildBigDecimalValidation(schemaObj)
        val primitiveType = new PrimitiveType.BigDecimal(validation)
        wrapPrimitive(primitiveType)

      case Shape.PrimKind.Boolean =>
        val primitiveType = new PrimitiveType.Boolean(Validation.None)
        wrapPrimitive(primitiveType)

      case Shape.PrimKind.Null =>
        Reflect.dynamic[Binding]
    }

  private def buildStringValidation(obj: JsonSchema.Object): Validation[String] = {
    val minLen = obj.minLength.map(_.value)
    val maxLen = obj.maxLength.map(_.value)
    val pat    = obj.pattern.map(_.value)

    (minLen, maxLen, pat) match {
      case (_, _, Some(regex)) =>
        // Pattern takes precedence; length constraints cannot be combined in Validation ADT
        Validation.String.Pattern(regex)
      case (Some(min), Some(max), None) if min == max && min == 1 =>
        Validation.String.NonEmpty
      case (Some(min), _, None) if min > 0 =>
        Validation.String.Length(Some(min), maxLen)
      case (_, Some(max), None) =>
        Validation.String.Length(minLen, Some(max))
      case _ =>
        Validation.None
    }
  }

  private def buildBigIntValidation(obj: JsonSchema.Object): Validation[BigInt] = {
    val min = obj.minimum.orElse(obj.exclusiveMinimum).map(_.toBigInt)
    val max = obj.maximum.orElse(obj.exclusiveMaximum).map(_.toBigInt)

    (min, max) match {
      case (Some(minVal), Some(maxVal)) => Validation.Numeric.Range(Some(minVal), Some(maxVal))
      case (Some(minVal), None)         => Validation.Numeric.Range(Some(minVal), None)
      case (None, Some(maxVal))         => Validation.Numeric.Range(None, Some(maxVal))
      case (None, None)                 => Validation.None
    }
  }

  private def buildBigDecimalValidation(obj: JsonSchema.Object): Validation[BigDecimal] = {
    val min = obj.minimum.orElse(obj.exclusiveMinimum)
    val max = obj.maximum.orElse(obj.exclusiveMaximum)

    (min, max) match {
      case (Some(minVal), Some(maxVal)) => Validation.Numeric.Range(Some(minVal), Some(maxVal))
      case (Some(minVal), None)         => Validation.Numeric.Range(Some(minVal), None)
      case (None, Some(maxVal))         => Validation.Numeric.Range(None, Some(maxVal))
      case (None, None)                 => Validation.None
    }
  }

  private def wrapPrimitive[A](primitiveType: PrimitiveType[A]): Reflect[Binding, DynamicValue] = {
    val innerReflect: Reflect[Binding, A] = new Reflect.Primitive[Binding, A](
      primitiveType = primitiveType,
      typeId = primitiveType.typeId,
      primitiveBinding = primitiveType.binding
    )

    val wrapperBinding = new Binding.Wrapper[DynamicValue, A](
      wrap = a => primitiveType.toDynamicValue(a),
      unwrap = dv =>
        primitiveType.fromDynamicValue(dv) match {
          case Right(a) => a
          case Left(_)  =>
            primitiveType match {
              case _: PrimitiveType.String     => "".asInstanceOf[A]
              case _: PrimitiveType.BigInt     => BigInt(0).asInstanceOf[A]
              case _: PrimitiveType.BigDecimal => BigDecimal(0).asInstanceOf[A]
              case _: PrimitiveType.Boolean    => false.asInstanceOf[A]
              case PrimitiveType.Unit          => ().asInstanceOf[A]
              case _                           => null.asInstanceOf[A]
            }
        }
    )

    new Reflect.Wrapper[Binding, DynamicValue, A](
      wrapped = innerReflect,
      typeId = TypeId.of[DynamicValue],
      wrapperBinding = wrapperBinding
    )
  }

  private def buildRecord(
    fields: List[(String, JsonSchema)],
    @scala.annotation.unused required: Set[String],
    closed: Boolean,
    title: Option[String]
  ): Reflect[Binding, DynamicValue] = {
    val fieldCount = fields.length

    val fieldTerms: IndexedSeq[Term[Binding, DynamicValue, DynamicValue]] = fields.zipWithIndex.map {
      case ((fieldName, fieldSchema), _) =>
        val fieldReflect = toReflect(fieldSchema)
        new Term[Binding, DynamicValue, DynamicValue](fieldName, fieldReflect)
    }.toIndexedSeq

    val fieldNames = fields.map(_._1).toArray

    val recordBinding = new Binding.Record[DynamicValue](
      constructor = new Constructor[DynamicValue] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = fieldCount)

        def construct(in: Registers, offset: RegisterOffset): DynamicValue = {
          val builder = Chunk.newBuilder[(String, DynamicValue)]
          var idx     = 0
          while (idx < fieldCount) {
            val elem = in.getObject(offset + idx).asInstanceOf[DynamicValue]
            builder.addOne((fieldNames(idx), if (elem != null) elem else DynamicValue.Null))
            idx += 1
          }
          DynamicValue.Record(builder.result())
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

    val baseRecord = new Reflect.Record[Binding, DynamicValue](
      fields = fieldTerms,
      typeId = typeIdFromTitle(title),
      recordBinding = recordBinding
    )

    if (closed) {
      baseRecord
    } else {
      baseRecord.modifier(Modifier.config("json.closure", "open"))
    }
  }

  private def buildMap(valuesSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val valueReflect                               = toReflect(valuesSchema)
    val keyReflect: Reflect[Binding, DynamicValue] = Reflect.dynamic[Binding]

    val mapReflect: Reflect[Binding, Map[DynamicValue, DynamicValue]] =
      Reflect.map[Binding, DynamicValue, DynamicValue](keyReflect, valueReflect)

    val wrapperBinding = new Binding.Wrapper[DynamicValue, Map[DynamicValue, DynamicValue]](
      wrap = map => DynamicValue.Map(Chunk.from(map.toSeq)),
      unwrap = {
        case DynamicValue.Map(entries) => entries.toMap
        case _                         => Map.empty
      }
    )

    new Reflect.Wrapper[Binding, DynamicValue, Map[DynamicValue, DynamicValue]](
      wrapped = mapReflect,
      typeId = TypeId.of[DynamicValue],
      wrapperBinding = wrapperBinding
    )
  }

  private def buildSequence(itemsSchema: JsonSchema): Reflect[Binding, DynamicValue] = {
    val elementReflect = toReflect(itemsSchema)

    val seqReflect: Reflect[Binding, Chunk[DynamicValue]] =
      Reflect.chunk[Binding, DynamicValue](elementReflect)

    val wrapperBinding = new Binding.Wrapper[DynamicValue, Chunk[DynamicValue]](
      wrap = chunk => DynamicValue.Sequence(chunk),
      unwrap = {
        case DynamicValue.Sequence(elements) => elements
        case _                               => Chunk.empty
      }
    )

    new Reflect.Wrapper[Binding, DynamicValue, Chunk[DynamicValue]](
      wrapped = seqReflect,
      typeId = TypeId.of[DynamicValue],
      wrapperBinding = wrapperBinding
    )
  }

  private def buildTuple(prefixItems: List[JsonSchema]): Reflect[Binding, DynamicValue] = {
    val fieldTerms: IndexedSeq[Term[Binding, DynamicValue, DynamicValue]] = prefixItems.zipWithIndex.map {
      case (schema, idx) =>
        val fieldReflect = toReflect(schema)
        new Term[Binding, DynamicValue, DynamicValue](s"_${idx + 1}", fieldReflect)
    }.toIndexedSeq

    val tupleSize = prefixItems.length

    val recordBinding = new Binding.Record[DynamicValue](
      constructor = new Constructor[DynamicValue] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = tupleSize)

        def construct(in: Registers, offset: RegisterOffset): DynamicValue = {
          val builder = Chunk.newBuilder[DynamicValue]
          var idx     = 0
          while (idx < tupleSize) {
            val elem = in.getObject(offset + idx).asInstanceOf[DynamicValue]
            builder.addOne(if (elem != null) elem else DynamicValue.Null)
            idx += 1
          }
          DynamicValue.Sequence(builder.result())
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

    new Reflect.Record[Binding, DynamicValue](
      fields = fieldTerms,
      typeId = TypeId.of[DynamicValue],
      recordBinding = recordBinding
    )
  }

  private def buildEnum(cases: List[String], title: Option[String]): Reflect[Binding, DynamicValue] = {
    val caseTerms: IndexedSeq[Term[Binding, DynamicValue, DynamicValue]] = cases.map { caseName =>
      val emptyRecordBinding = new Binding.Record[DynamicValue](
        constructor = new Constructor[DynamicValue] {
          def usedRegisters: RegisterOffset                                  = 0L
          def construct(in: Registers, offset: RegisterOffset): DynamicValue =
            DynamicValue.Record(Chunk.empty)
        },
        deconstructor = new Deconstructor[DynamicValue] {
          def usedRegisters: RegisterOffset                                               = 0L
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicValue): Unit = ()
        }
      )

      val caseReflect: Reflect[Binding, DynamicValue] = new Reflect.Record[Binding, DynamicValue](
        fields = IndexedSeq.empty,
        typeId = TypeId.of[DynamicValue],
        recordBinding = emptyRecordBinding
      )

      new Term[Binding, DynamicValue, DynamicValue](caseName, caseReflect)
    }.toIndexedSeq

    val caseNames = cases.toArray

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
        caseNames.toIndexedSeq.map { cn =>
          new Matcher[DynamicValue] {
            override def downcastOrNull(any: Any): DynamicValue = any match {
              case dv @ DynamicValue.Variant(name, _) if name == cn => dv
              case _                                                => null.asInstanceOf[DynamicValue]
            }
          }
        }
      )
    )

    new Reflect.Variant[Binding, DynamicValue](
      cases = caseTerms,
      typeId = typeIdFromTitle(title),
      variantBinding = variantBinding
    )
  }

  private def buildKeyVariant(
    cases: List[(String, JsonSchema)],
    title: Option[String]
  ): Reflect[Binding, DynamicValue] = {
    val caseTerms: IndexedSeq[Term[Binding, DynamicValue, DynamicValue]] = cases.map { case (caseName, bodySchema) =>
      val bodyReflect = toReflect(bodySchema)
      new Term[Binding, DynamicValue, DynamicValue](caseName, bodyReflect)
    }.toIndexedSeq

    val caseNames = cases.map(_._1).toArray

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
        caseNames.toIndexedSeq.map { cn =>
          new Matcher[DynamicValue] {
            override def downcastOrNull(any: Any): DynamicValue = any match {
              case dv @ DynamicValue.Variant(name, _) if name == cn => dv
              case _                                                => null.asInstanceOf[DynamicValue]
            }
          }
        }
      )
    )

    new Reflect.Variant[Binding, DynamicValue](
      cases = caseTerms,
      typeId = typeIdFromTitle(title),
      variantBinding = variantBinding
    )
  }

  private def buildFieldVariant(
    @scala.annotation.unused discriminator: String,
    cases: List[(String, JsonSchema)],
    title: Option[String]
  ): Reflect[Binding, DynamicValue] =
    buildKeyVariant(cases, title)

  private def wrapWithValidation(
    @scala.annotation.unused schema: JsonSchema,
    shape: Shape,
    built: Reflect[Binding, DynamicValue]
  ): Reflect[Binding, DynamicValue] =
    shape match {
      case Shape.Tuple(prefixItems) =>
        wrapTupleWithValidation(prefixItems.length, built)
      case _ =>
        built
    }

  private def wrapTupleWithValidation(
    expectedLength: Int,
    inner: Reflect[Binding, DynamicValue]
  ): Reflect[Binding, DynamicValue] = {
    val validatingBinding = new Binding.Wrapper[DynamicValue, DynamicValue](
      wrap = dv => {
        validateTuple(dv, expectedLength) match {
          case Some(error) => throw error
          case None        => dv
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

  private def validateTuple(dv: DynamicValue, expectedLength: Int): Option[SchemaError] = dv match {
    case DynamicValue.Sequence(elements) =>
      if (elements.length != expectedLength) {
        Some(SchemaError.expectationMismatch(Nil, s"Expected tuple of length $expectedLength, got ${elements.length}"))
      } else {
        None
      }
    case _ =>
      Some(SchemaError.expectationMismatch(Nil, "Expected a sequence (tuple)"))
  }
}
