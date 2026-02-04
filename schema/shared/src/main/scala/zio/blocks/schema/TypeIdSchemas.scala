package zio.blocks.schema

import zio.blocks.typeid._
import zio.blocks.chunk.Chunk

/**
 * Hand-rolled Schema instances for all TypeId-related types.
 *
 * These schemas enable serialization/deserialization of TypeId metadata, which
 * is essential for schema reflection and type-safe serialization.
 */
trait TypeIdSchemas {

  // ============================================================================
  // Simple Enums - String-based encoding
  // ============================================================================

  implicit lazy val varianceSchema: Schema[Variance] =
    Schema[String].transform(
      {
        case "Covariant"     => Variance.Covariant
        case "Contravariant" => Variance.Contravariant
        case "Invariant"     => Variance.Invariant
        case other           => throw SchemaError.conversionFailed(Nil, s"Unknown variance: $other")
      },
      {
        case Variance.Covariant     => "Covariant"
        case Variance.Contravariant => "Contravariant"
        case Variance.Invariant     => "Invariant"
      }
    )

  // ============================================================================
  // Kind - Recursive structure
  // ============================================================================

  implicit lazy val kindSchema: Schema[Kind] = {
    lazy val baseSchema: Schema[Kind] = Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          fields.toMap.get("type") match {
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Type"))) =>
              Kind.Type
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Arrow"))) =>
              (for {
                params <- fields.toMap
                            .get("params")
                            .toRight(SchemaError.missingField(Nil, "params"))
                            .flatMap(kindListSchema.fromDynamicValue)
                result <- fields.toMap
                            .get("result")
                            .toRight(SchemaError.missingField(Nil, "result"))
                            .flatMap(baseSchema.fromDynamicValue)
              } yield Kind.Arrow(params, result)).fold(throw _, identity)
            case _ =>
              throw SchemaError.conversionFailed(Nil, "Unknown kind type")
          }
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      kind =>
        kind match {
          case Kind.Type =>
            DynamicValue.Record(Chunk(("type", DynamicValue.Primitive(PrimitiveValue.String("Type")))))
          case Kind.Arrow(params, result) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Arrow"))),
                ("params", kindListSchema.toDynamicValue(params)),
                ("result", baseSchema.toDynamicValue(result))
              )
            )
        }
    )
    baseSchema
  }

  private lazy val kindListSchema: Schema[List[Kind]] = Schema[List[Kind]]

  // ============================================================================
  // Owner.Segment - Simple sum type
  // ============================================================================

  implicit lazy val ownerSegmentSchema: Schema[Owner.Segment] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          fields.toMap.get("type") match {
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Package"))) =>
              fields.toMap
                .get("name")
                .toRight(SchemaError.missingField(Nil, "name"))
                .flatMap(Schema[String].fromDynamicValue)
                .map(Owner.Package.apply)
                .fold(throw _, identity)
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Term"))) =>
              fields.toMap
                .get("name")
                .toRight(SchemaError.missingField(Nil, "name"))
                .flatMap(Schema[String].fromDynamicValue)
                .map(Owner.Term.apply)
                .fold(throw _, identity)
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Type"))) =>
              fields.toMap
                .get("name")
                .toRight(SchemaError.missingField(Nil, "name"))
                .flatMap(Schema[String].fromDynamicValue)
                .map(Owner.Type.apply)
                .fold(throw _, identity)
            case _ =>
              throw SchemaError.conversionFailed(Nil, "Unknown Owner.Segment type")
          }
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      segment =>
        segment match {
          case Owner.Package(name) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Package"))),
                ("name", Schema[String].toDynamicValue(name))
              )
            )
          case Owner.Term(name) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Term"))),
                ("name", Schema[String].toDynamicValue(name))
              )
            )
          case Owner.Type(name) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Type"))),
                ("name", Schema[String].toDynamicValue(name))
              )
            )
        }
    )

  // ============================================================================
  // Owner - Contains list of segments
  // ============================================================================

  implicit lazy val ownerSchema: Schema[Owner] =
    Schema[List[Owner.Segment]].transform(to = Owner.apply, from = _.segments)

  // ============================================================================
  // TermPath.Segment
  // ============================================================================

  implicit lazy val termPathSegmentSchema: Schema[TermPath.Segment] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          fields.toMap.get("type") match {
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Package"))) =>
              fields.toMap
                .get("name")
                .toRight(SchemaError.missingField(Nil, "name"))
                .flatMap(Schema[String].fromDynamicValue)
                .map(TermPath.Package.apply)
                .fold(throw _, identity)
            case Some(DynamicValue.Primitive(PrimitiveValue.String("Term"))) =>
              fields.toMap
                .get("name")
                .toRight(SchemaError.missingField(Nil, "name"))
                .flatMap(Schema[String].fromDynamicValue)
                .map(TermPath.Term.apply)
                .fold(throw _, identity)
            case _ =>
              throw SchemaError.conversionFailed(Nil, "Unknown TermPath.Segment type")
          }
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      segment =>
        segment match {
          case TermPath.Package(name) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Package"))),
                ("name", Schema[String].toDynamicValue(name))
              )
            )
          case TermPath.Term(name) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Term"))),
                ("name", Schema[String].toDynamicValue(name))
              )
            )
        }
    )

  // ============================================================================
  // TermPath
  // ============================================================================

  implicit lazy val termPathSchema: Schema[TermPath] =
    Schema[List[TermPath.Segment]].transform(to = TermPath.apply, from = _.segments)

  // ============================================================================
  // TypeRepr - Highly recursive, all cases
  // ============================================================================

  implicit lazy val typeReprSchema: Schema[TypeRepr] = {
    val baseSchema = Schema[DynamicValue].transform(
      dynamicValue => typeReprFromDynamic(dynamicValue).fold(throw _, identity),
      typeRepr => typeReprToDynamic(typeRepr)
    )
    baseSchema
  }

  private def typeReprFromDynamic(dv: DynamicValue): Either[SchemaError, TypeRepr] = {
    dv match {
      case DynamicValue.Record(fields) =>
        val fieldMap = fields.toMap
        fieldMap.get("type").flatMap {
          case DynamicValue.Primitive(PrimitiveValue.String(tpe)) => Some(tpe)
          case _                                                  => None
        } match {
          case Some("Ref") =>
            for {
              id <- fieldMap
                      .get("id")
                      .toRight(SchemaError.missingField(Nil, "id"))
                      .flatMap(typeIdSchema.fromDynamicValue)
            } yield TypeRepr.Ref(id)

          case Some("ParamRef") =>
            for {
              param <- fieldMap
                         .get("param")
                         .toRight(SchemaError.missingField(Nil, "param"))
                         .flatMap(typeParamSchema.fromDynamicValue)
              binderDepth <- fieldMap
                               .get("binderDepth")
                               .toRight(SchemaError.missingField(Nil, "binderDepth"))
                               .flatMap(Schema[Int].fromDynamicValue)
            } yield TypeRepr.ParamRef(param, binderDepth)

          case Some("Applied") =>
            for {
              tycon <- fieldMap
                         .get("tycon")
                         .toRight(SchemaError.missingField(Nil, "tycon"))
                         .flatMap(typeReprFromDynamic)
              args <- fieldMap
                        .get("args")
                        .toRight(SchemaError.missingField(Nil, "args"))
                        .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
            } yield TypeRepr.Applied(tycon, args)

          case Some("Structural") =>
            for {
              parents <- fieldMap
                           .get("parents")
                           .toRight(SchemaError.missingField(Nil, "parents"))
                           .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              members <- fieldMap
                           .get("members")
                           .toRight(SchemaError.missingField(Nil, "members"))
                           .flatMap(Schema[List[Member]].fromDynamicValue)
            } yield TypeRepr.Structural(parents, members)

          case Some("Intersection") =>
            for {
              types <- fieldMap
                         .get("types")
                         .toRight(SchemaError.missingField(Nil, "types"))
                         .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
            } yield TypeRepr.Intersection(types)

          case Some("Union") =>
            for {
              types <- fieldMap
                         .get("types")
                         .toRight(SchemaError.missingField(Nil, "types"))
                         .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
            } yield TypeRepr.Union(types)

          case Some("Tuple") =>
            for {
              elems <- fieldMap
                         .get("elems")
                         .toRight(SchemaError.missingField(Nil, "elems"))
                         .flatMap(Schema[List[TupleElement]].fromDynamicValue)
            } yield TypeRepr.Tuple(elems)

          case Some("Function") =>
            for {
              params <- fieldMap
                          .get("params")
                          .toRight(SchemaError.missingField(Nil, "params"))
                          .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              result <- fieldMap
                          .get("result")
                          .toRight(SchemaError.missingField(Nil, "result"))
                          .flatMap(typeReprFromDynamic)
            } yield TypeRepr.Function(params, result)

          case Some("ContextFunction") =>
            for {
              params <- fieldMap
                          .get("params")
                          .toRight(SchemaError.missingField(Nil, "params"))
                          .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              result <- fieldMap
                          .get("result")
                          .toRight(SchemaError.missingField(Nil, "result"))
                          .flatMap(typeReprFromDynamic)
            } yield TypeRepr.ContextFunction(params, result)

          case Some("TypeLambda") =>
            for {
              params <- fieldMap
                          .get("params")
                          .toRight(SchemaError.missingField(Nil, "params"))
                          .flatMap(Schema[List[TypeParam]].fromDynamicValue)
              body <- fieldMap
                        .get("body")
                        .toRight(SchemaError.missingField(Nil, "body"))
                        .flatMap(typeReprFromDynamic)
            } yield TypeRepr.TypeLambda(params, body)

          case Some("ByName") =>
            for {
              underlying <- fieldMap
                              .get("underlying")
                              .toRight(SchemaError.missingField(Nil, "underlying"))
                              .flatMap(typeReprFromDynamic)
            } yield TypeRepr.ByName(underlying)

          case Some("Repeated") =>
            for {
              element <- fieldMap
                           .get("element")
                           .toRight(SchemaError.missingField(Nil, "element"))
                           .flatMap(typeReprFromDynamic)
            } yield TypeRepr.Repeated(element)

          case Some("Wildcard") =>
            for {
              bounds <- fieldMap
                          .get("bounds")
                          .toRight(SchemaError.missingField(Nil, "bounds"))
                          .flatMap(typeBoundsSchema.fromDynamicValue)
            } yield TypeRepr.Wildcard(bounds)

          case Some("Singleton") =>
            for {
              path <- fieldMap
                        .get("path")
                        .toRight(SchemaError.missingField(Nil, "path"))
                        .flatMap(termPathSchema.fromDynamicValue)
            } yield TypeRepr.Singleton(path)

          case Some("ThisType") =>
            for {
              owner <- fieldMap
                         .get("owner")
                         .toRight(SchemaError.missingField(Nil, "owner"))
                         .flatMap(ownerSchema.fromDynamicValue)
            } yield TypeRepr.ThisType(owner)

          case Some("TypeProjection") =>
            for {
              qualifier <- fieldMap
                             .get("qualifier")
                             .toRight(SchemaError.missingField(Nil, "qualifier"))
                             .flatMap(typeReprFromDynamic)
              name <- fieldMap
                        .get("name")
                        .toRight(SchemaError.missingField(Nil, "name"))
                        .flatMap(Schema[String].fromDynamicValue)
            } yield TypeRepr.TypeProjection(qualifier, name)

          case Some("TypeSelect") =>
            for {
              qualifier <- fieldMap
                             .get("qualifier")
                             .toRight(SchemaError.missingField(Nil, "qualifier"))
                             .flatMap(typeReprFromDynamic)
              name <- fieldMap
                        .get("name")
                        .toRight(SchemaError.missingField(Nil, "name"))
                        .flatMap(Schema[String].fromDynamicValue)
            } yield TypeRepr.TypeSelect(qualifier, name)

          case Some("Annotated") =>
            for {
              underlying <- fieldMap
                              .get("underlying")
                              .toRight(SchemaError.missingField(Nil, "underlying"))
                              .flatMap(typeReprFromDynamic)
              annotations <- fieldMap
                               .get("annotations")
                               .toRight(SchemaError.missingField(Nil, "annotations"))
                               .flatMap(Schema[List[Annotation]].fromDynamicValue)
            } yield TypeRepr.Annotated(underlying, annotations)

          case Some("IntConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[Int].fromDynamicValue)
            } yield TypeRepr.Constant.IntConst(value)

          case Some("LongConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[Long].fromDynamicValue)
            } yield TypeRepr.Constant.LongConst(value)

          case Some("FloatConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[Float].fromDynamicValue)
            } yield TypeRepr.Constant.FloatConst(value)

          case Some("DoubleConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[Double].fromDynamicValue)
            } yield TypeRepr.Constant.DoubleConst(value)

          case Some("BooleanConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[Boolean].fromDynamicValue)
            } yield TypeRepr.Constant.BooleanConst(value)

          case Some("CharConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[Char].fromDynamicValue)
            } yield TypeRepr.Constant.CharConst(value)

          case Some("StringConst") =>
            for {
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(Schema[String].fromDynamicValue)
            } yield TypeRepr.Constant.StringConst(value)

          case Some("NullConst") =>
            Right(TypeRepr.Constant.NullConst)

          case Some("UnitConst") =>
            Right(TypeRepr.Constant.UnitConst)

          case Some("ClassOfConst") =>
            for {
              tpe <- fieldMap
                       .get("tpe")
                       .toRight(SchemaError.missingField(Nil, "tpe"))
                       .flatMap(typeReprFromDynamic)
            } yield TypeRepr.Constant.ClassOfConst(tpe)

          case Some("AnyType") =>
            Right(TypeRepr.AnyType)

          case Some("NothingType") =>
            Right(TypeRepr.NothingType)

          case Some("NullType") =>
            Right(TypeRepr.NullType)

          case Some("UnitType") =>
            Right(TypeRepr.UnitType)

          case Some("AnyKindType") =>
            Right(TypeRepr.AnyKindType)

          case _ =>
            Left(SchemaError.conversionFailed(Nil, "Unknown TypeRepr type"))
        }
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected a record"))
    }
  }

  private def typeReprToDynamic(tr: TypeRepr): DynamicValue = {
    tr match {
      case TypeRepr.Ref(id) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Ref"))),
            ("id", typeIdSchema.toDynamicValue(id))
          )
        )

      case TypeRepr.ParamRef(param, binderDepth) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ParamRef"))),
            ("param", typeParamSchema.toDynamicValue(param)),
            ("binderDepth", Schema[Int].toDynamicValue(binderDepth))
          )
        )

      case TypeRepr.Applied(tycon, args) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Applied"))),
            ("tycon", typeReprToDynamic(tycon)),
            ("args", Schema[List[TypeRepr]].toDynamicValue(args))
          )
        )

      case TypeRepr.Structural(parents, members) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Structural"))),
            ("parents", Schema[List[TypeRepr]].toDynamicValue(parents)),
            ("members", Schema[List[Member]].toDynamicValue(members))
          )
        )

      case TypeRepr.Intersection(types) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Intersection"))),
            ("types", Schema[List[TypeRepr]].toDynamicValue(types))
          )
        )

      case TypeRepr.Union(types) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Union"))),
            ("types", Schema[List[TypeRepr]].toDynamicValue(types))
          )
        )

      case TypeRepr.Tuple(elems) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Tuple"))),
            ("elems", Schema[List[TupleElement]].toDynamicValue(elems))
          )
        )

      case TypeRepr.Function(params, result) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Function"))),
            ("params", Schema[List[TypeRepr]].toDynamicValue(params)),
            ("result", typeReprToDynamic(result))
          )
        )

      case TypeRepr.ContextFunction(params, result) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ContextFunction"))),
            ("params", Schema[List[TypeRepr]].toDynamicValue(params)),
            ("result", typeReprToDynamic(result))
          )
        )

      case TypeRepr.TypeLambda(params, body) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("TypeLambda"))),
            ("params", Schema[List[TypeParam]].toDynamicValue(params)),
            ("body", typeReprToDynamic(body))
          )
        )

      case TypeRepr.ByName(underlying) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ByName"))),
            ("underlying", typeReprToDynamic(underlying))
          )
        )

      case TypeRepr.Repeated(element) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Repeated"))),
            ("element", typeReprToDynamic(element))
          )
        )

      case TypeRepr.Wildcard(bounds) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Wildcard"))),
            ("bounds", typeBoundsSchema.toDynamicValue(bounds))
          )
        )

      case TypeRepr.Singleton(path) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Singleton"))),
            ("path", termPathSchema.toDynamicValue(path))
          )
        )

      case TypeRepr.ThisType(owner) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ThisType"))),
            ("owner", ownerSchema.toDynamicValue(owner))
          )
        )

      case TypeRepr.TypeProjection(qualifier, name) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("TypeProjection"))),
            ("qualifier", typeReprToDynamic(qualifier)),
            ("name", Schema[String].toDynamicValue(name))
          )
        )

      case TypeRepr.TypeSelect(qualifier, name) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("TypeSelect"))),
            ("qualifier", typeReprToDynamic(qualifier)),
            ("name", Schema[String].toDynamicValue(name))
          )
        )

      case TypeRepr.Annotated(underlying, annotations) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Annotated"))),
            ("underlying", typeReprToDynamic(underlying)),
            ("annotations", Schema[List[Annotation]].toDynamicValue(annotations))
          )
        )

      case TypeRepr.Constant.IntConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("IntConst"))),
            ("value", Schema[Int].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.LongConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("LongConst"))),
            ("value", Schema[Long].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.FloatConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("FloatConst"))),
            ("value", Schema[Float].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.DoubleConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("DoubleConst"))),
            ("value", Schema[Double].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.BooleanConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("BooleanConst"))),
            ("value", Schema[Boolean].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.CharConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("CharConst"))),
            ("value", Schema[Char].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.StringConst(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("StringConst"))),
            ("value", Schema[String].toDynamicValue(value))
          )
        )

      case TypeRepr.Constant.NullConst =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("NullConst")))
          )
        )

      case TypeRepr.Constant.UnitConst =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("UnitConst")))
          )
        )

      case TypeRepr.Constant.ClassOfConst(tpe) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ClassOfConst"))),
            ("tpe", typeReprToDynamic(tpe))
          )
        )

      case TypeRepr.AnyType =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("AnyType")))
          )
        )

      case TypeRepr.NothingType =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("NothingType")))
          )
        )

      case TypeRepr.NullType =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("NullType")))
          )
        )

      case TypeRepr.UnitType =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("UnitType")))
          )
        )

      case TypeRepr.AnyKindType =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("AnyKindType")))
          )
        )
    }
  }

  // ============================================================================
  // TupleElement
  // ============================================================================

  implicit lazy val tupleElementSchema: Schema[TupleElement] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            label <- fieldMap
                       .get("label")
                       .toRight(SchemaError.missingField(Nil, "label"))
                       .flatMap(Schema[Option[String]].fromDynamicValue)
            tpe <- fieldMap
                     .get("tpe")
                     .toRight(SchemaError.missingField(Nil, "tpe"))
                     .flatMap(typeReprSchema.fromDynamicValue)
          } yield TupleElement(label, tpe)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      elem =>
        DynamicValue.Record(
          Chunk(
            ("label", Schema[Option[String]].toDynamicValue(elem.label)),
            ("tpe", typeReprSchema.toDynamicValue(elem.tpe))
          )
        )
    )

  // ============================================================================
  // TypeBounds
  // ============================================================================

  implicit lazy val typeBoundsSchema: Schema[TypeBounds] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            lower <- fieldMap
                       .get("lower")
                       .toRight(SchemaError.missingField(Nil, "lower"))
                       .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
            upper <- fieldMap
                       .get("upper")
                       .toRight(SchemaError.missingField(Nil, "upper"))
                       .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
          } yield TypeBounds(lower, upper)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      bounds =>
        DynamicValue.Record(
          Chunk(
            ("lower", Schema[Option[TypeRepr]].toDynamicValue(bounds.lower)),
            ("upper", Schema[Option[TypeRepr]].toDynamicValue(bounds.upper))
          )
        )
    )

  // ============================================================================
  // TypeParam
  // ============================================================================

  implicit lazy val typeParamSchema: Schema[TypeParam] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            name <- fieldMap
                      .get("name")
                      .toRight(SchemaError.missingField(Nil, "name"))
                      .flatMap(Schema[String].fromDynamicValue)
            index <- fieldMap
                       .get("index")
                       .toRight(SchemaError.missingField(Nil, "index"))
                       .flatMap(Schema[Int].fromDynamicValue)
            variance <- fieldMap
                          .get("variance")
                          .toRight(SchemaError.missingField(Nil, "variance"))
                          .flatMap(varianceSchema.fromDynamicValue)
            bounds <- fieldMap
                        .get("bounds")
                        .toRight(SchemaError.missingField(Nil, "bounds"))
                        .flatMap(typeBoundsSchema.fromDynamicValue)
            kind <- fieldMap
                      .get("kind")
                      .toRight(SchemaError.missingField(Nil, "kind"))
                      .flatMap(kindSchema.fromDynamicValue)
          } yield TypeParam(name, index, variance, bounds, kind)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      param =>
        DynamicValue.Record(
          Chunk(
            ("name", Schema[String].toDynamicValue(param.name)),
            ("index", Schema[Int].toDynamicValue(param.index)),
            ("variance", varianceSchema.toDynamicValue(param.variance)),
            ("bounds", typeBoundsSchema.toDynamicValue(param.bounds)),
            ("kind", kindSchema.toDynamicValue(param.kind))
          )
        )
    )

  // ============================================================================
  // Param
  // ============================================================================

  implicit lazy val paramSchema: Schema[Param] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            name <- fieldMap
                      .get("name")
                      .toRight(SchemaError.missingField(Nil, "name"))
                      .flatMap(Schema[String].fromDynamicValue)
            tpe <- fieldMap
                     .get("tpe")
                     .toRight(SchemaError.missingField(Nil, "tpe"))
                     .flatMap(typeReprSchema.fromDynamicValue)
            isImplicit <- fieldMap
                            .get("isImplicit")
                            .toRight(SchemaError.missingField(Nil, "isImplicit"))
                            .flatMap(Schema[Boolean].fromDynamicValue)
            hasDefault <- fieldMap
                            .get("hasDefault")
                            .toRight(SchemaError.missingField(Nil, "hasDefault"))
                            .flatMap(Schema[Boolean].fromDynamicValue)
          } yield Param(name, tpe, isImplicit, hasDefault)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      param =>
        DynamicValue.Record(
          Chunk(
            ("name", Schema[String].toDynamicValue(param.name)),
            ("tpe", typeReprSchema.toDynamicValue(param.tpe)),
            ("isImplicit", Schema[Boolean].toDynamicValue(param.isImplicit)),
            ("hasDefault", Schema[Boolean].toDynamicValue(param.hasDefault))
          )
        )
    )

  // ============================================================================
  // Member (sum type: Val, Def, TypeMember)
  // ============================================================================

  implicit lazy val memberSchema: Schema[Member] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          fieldMap.get("type").flatMap {
            case DynamicValue.Primitive(PrimitiveValue.String(tpe)) => Some(tpe)
            case _                                                  => None
          } match {
            case Some("Val") =>
              (for {
                name <- fieldMap
                          .get("name")
                          .toRight(SchemaError.missingField(Nil, "name"))
                          .flatMap(Schema[String].fromDynamicValue)
                tpe <- fieldMap
                         .get("tpe")
                         .toRight(SchemaError.missingField(Nil, "tpe"))
                         .flatMap(typeReprSchema.fromDynamicValue)
                isVar <- fieldMap
                           .get("isVar")
                           .toRight(SchemaError.missingField(Nil, "isVar"))
                           .flatMap(Schema[Boolean].fromDynamicValue)
              } yield Member.Val(name, tpe, isVar)).fold(throw _, identity)

            case Some("Def") =>
              (for {
                name <- fieldMap
                          .get("name")
                          .toRight(SchemaError.missingField(Nil, "name"))
                          .flatMap(Schema[String].fromDynamicValue)
                typeParams <- fieldMap
                                .get("typeParams")
                                .toRight(SchemaError.missingField(Nil, "typeParams"))
                                .flatMap(Schema[List[TypeParam]].fromDynamicValue)
                paramLists <- fieldMap
                                .get("paramLists")
                                .toRight(SchemaError.missingField(Nil, "paramLists"))
                                .flatMap(Schema[List[List[Param]]].fromDynamicValue)
                result <- fieldMap
                            .get("result")
                            .toRight(SchemaError.missingField(Nil, "result"))
                            .flatMap(typeReprSchema.fromDynamicValue)
              } yield Member.Def(name, typeParams, paramLists, result)).fold(throw _, identity)

            case Some("TypeMember") =>
              (for {
                name <- fieldMap
                          .get("name")
                          .toRight(SchemaError.missingField(Nil, "name"))
                          .flatMap(Schema[String].fromDynamicValue)
                typeParams <- fieldMap
                                .get("typeParams")
                                .toRight(SchemaError.missingField(Nil, "typeParams"))
                                .flatMap(Schema[List[TypeParam]].fromDynamicValue)
                lowerBound <- fieldMap
                                .get("lowerBound")
                                .toRight(SchemaError.missingField(Nil, "lowerBound"))
                                .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
                upperBound <- fieldMap
                                .get("upperBound")
                                .toRight(SchemaError.missingField(Nil, "upperBound"))
                                .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
              } yield Member.TypeMember(name, typeParams, lowerBound, upperBound)).fold(throw _, identity)

            case _ =>
              throw SchemaError.conversionFailed(Nil, "Unknown Member type")
          }
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      member =>
        member match {
          case Member.Val(name, tpe, isVar) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Val"))),
                ("name", Schema[String].toDynamicValue(name)),
                ("tpe", typeReprSchema.toDynamicValue(tpe)),
                ("isVar", Schema[Boolean].toDynamicValue(isVar))
              )
            )

          case Member.Def(name, typeParams, paramLists, result) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Def"))),
                ("name", Schema[String].toDynamicValue(name)),
                ("typeParams", Schema[List[TypeParam]].toDynamicValue(typeParams)),
                ("paramLists", Schema[List[List[Param]]].toDynamicValue(paramLists)),
                ("result", typeReprSchema.toDynamicValue(result))
              )
            )

          case Member.TypeMember(name, typeParams, lowerBound, upperBound) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("TypeMember"))),
                ("name", Schema[String].toDynamicValue(name)),
                ("typeParams", Schema[List[TypeParam]].toDynamicValue(typeParams)),
                ("lowerBound", Schema[Option[TypeRepr]].toDynamicValue(lowerBound)),
                ("upperBound", Schema[Option[TypeRepr]].toDynamicValue(upperBound))
              )
            )
        }
    )

  // ============================================================================
  // EnumCaseParam
  // ============================================================================

  implicit lazy val enumCaseParamSchema: Schema[EnumCaseParam] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            name <- fieldMap
                      .get("name")
                      .toRight(SchemaError.missingField(Nil, "name"))
                      .flatMap(Schema[String].fromDynamicValue)
            tpe <- fieldMap
                     .get("tpe")
                     .toRight(SchemaError.missingField(Nil, "tpe"))
                     .flatMap(typeReprSchema.fromDynamicValue)
          } yield EnumCaseParam(name, tpe)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      param =>
        DynamicValue.Record(
          Chunk(
            ("name", Schema[String].toDynamicValue(param.name)),
            ("tpe", typeReprSchema.toDynamicValue(param.tpe))
          )
        )
    )

  // ============================================================================
  // EnumCaseInfo
  // ============================================================================

  implicit lazy val enumCaseInfoSchema: Schema[EnumCaseInfo] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            name <- fieldMap
                      .get("name")
                      .toRight(SchemaError.missingField(Nil, "name"))
                      .flatMap(Schema[String].fromDynamicValue)
            ordinal <- fieldMap
                         .get("ordinal")
                         .toRight(SchemaError.missingField(Nil, "ordinal"))
                         .flatMap(Schema[Int].fromDynamicValue)
            params <- fieldMap
                        .get("params")
                        .toRight(SchemaError.missingField(Nil, "params"))
                        .flatMap(Schema[List[EnumCaseParam]].fromDynamicValue)
            isObjectCase <- fieldMap
                              .get("isObjectCase")
                              .toRight(SchemaError.missingField(Nil, "isObjectCase"))
                              .flatMap(Schema[Boolean].fromDynamicValue)
          } yield EnumCaseInfo(name, ordinal, params, isObjectCase)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      info =>
        DynamicValue.Record(
          Chunk(
            ("name", Schema[String].toDynamicValue(info.name)),
            ("ordinal", Schema[Int].toDynamicValue(info.ordinal)),
            ("params", Schema[List[EnumCaseParam]].toDynamicValue(info.params)),
            ("isObjectCase", Schema[Boolean].toDynamicValue(info.isObjectCase))
          )
        )
    )

  // ============================================================================
  // AnnotationArg (recursive sum type)
  // ============================================================================

  implicit lazy val annotationArgSchema: Schema[AnnotationArg] =
    Schema[DynamicValue].transform(
      dv => annotationArgFromDynamic(dv).fold(throw _, identity),
      arg => annotationArgToDynamic(arg)
    )

  private def annotationArgFromDynamic(dv: DynamicValue): Either[SchemaError, AnnotationArg] =
    dv match {
      case DynamicValue.Record(fields) =>
        val fieldMap = fields.toMap
        fieldMap.get("type").flatMap {
          case DynamicValue.Primitive(PrimitiveValue.String(tpe)) => Some(tpe)
          case _                                                  => None
        } match {
          case Some("Const") =>
            fieldMap
              .get("value")
              .toRight(SchemaError.missingField(Nil, "value"))
              .flatMap(Schema[DynamicValue].fromDynamicValue)
              .map(dv => AnnotationArg.Const(dv))

          case Some("ArrayArg") =>
            for {
              values <- fieldMap
                          .get("values")
                          .toRight(SchemaError.missingField(Nil, "values"))
                          .flatMap(Schema[List[AnnotationArg]].fromDynamicValue)
            } yield AnnotationArg.ArrayArg(values)

          case Some("Named") =>
            for {
              name <- fieldMap
                        .get("name")
                        .toRight(SchemaError.missingField(Nil, "name"))
                        .flatMap(Schema[String].fromDynamicValue)
              value <- fieldMap
                         .get("value")
                         .toRight(SchemaError.missingField(Nil, "value"))
                         .flatMap(annotationArgFromDynamic)
            } yield AnnotationArg.Named(name, value)

          case Some("Nested") =>
            for {
              annotation <- fieldMap
                              .get("annotation")
                              .toRight(SchemaError.missingField(Nil, "annotation"))
                              .flatMap(annotationSchema.fromDynamicValue)
            } yield AnnotationArg.Nested(annotation)

          case Some("ClassOf") =>
            for {
              tpe <- fieldMap
                       .get("tpe")
                       .toRight(SchemaError.missingField(Nil, "tpe"))
                       .flatMap(typeReprSchema.fromDynamicValue)
            } yield AnnotationArg.ClassOf(tpe)

          case Some("EnumValue") =>
            for {
              enumType <- fieldMap
                            .get("enumType")
                            .toRight(SchemaError.missingField(Nil, "enumType"))
                            .flatMap(typeIdSchema.fromDynamicValue)
              valueName <- fieldMap
                             .get("valueName")
                             .toRight(SchemaError.missingField(Nil, "valueName"))
                             .flatMap(Schema[String].fromDynamicValue)
            } yield AnnotationArg.EnumValue(enumType, valueName)

          case _ =>
            Left(SchemaError.conversionFailed(Nil, "Unknown AnnotationArg type"))
        }
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected a record"))
    }

  private def annotationArgToDynamic(arg: AnnotationArg): DynamicValue =
    arg match {
      case AnnotationArg.Const(value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Const"))),
            (
              "value",
              Schema[DynamicValue].toDynamicValue(
                value match {
                  case dv: DynamicValue => dv
                  case other            => DynamicValue.Primitive(PrimitiveValue.String(other.toString))
                }
              )
            )
          )
        )

      case AnnotationArg.ArrayArg(values) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ArrayArg"))),
            ("values", Schema[List[AnnotationArg]].toDynamicValue(values))
          )
        )

      case AnnotationArg.Named(name, value) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Named"))),
            ("name", Schema[String].toDynamicValue(name)),
            ("value", annotationArgToDynamic(value))
          )
        )

      case AnnotationArg.Nested(annotation) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("Nested"))),
            ("annotation", annotationSchema.toDynamicValue(annotation))
          )
        )

      case AnnotationArg.ClassOf(tpe) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("ClassOf"))),
            ("tpe", typeReprSchema.toDynamicValue(tpe))
          )
        )

      case AnnotationArg.EnumValue(enumType, valueName) =>
        DynamicValue.Record(
          Chunk(
            ("type", DynamicValue.Primitive(PrimitiveValue.String("EnumValue"))),
            ("enumType", typeIdSchema.toDynamicValue(enumType)),
            ("valueName", Schema[String].toDynamicValue(valueName))
          )
        )
    }

  // ============================================================================
  // Annotation
  // ============================================================================

  implicit lazy val annotationSchema: Schema[Annotation] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            typeId <- fieldMap
                        .get("typeId")
                        .toRight(SchemaError.missingField(Nil, "typeId"))
                        .flatMap(typeIdSchema.fromDynamicValue)
            args <- fieldMap
                      .get("args")
                      .toRight(SchemaError.missingField(Nil, "args"))
                      .flatMap(Schema[List[AnnotationArg]].fromDynamicValue)
          } yield Annotation(typeId, args)).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      annotation =>
        DynamicValue.Record(
          Chunk(
            ("typeId", typeIdSchema.toDynamicValue(annotation.typeId)),
            ("args", Schema[List[AnnotationArg]].toDynamicValue(annotation.args))
          )
        )
    )

  // ============================================================================
  // TypeDefKind (complex sum type)
  // ============================================================================

  implicit lazy val typeDefKindSchema: Schema[TypeDefKind] = {
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          fieldMap.get("type").flatMap {
            case DynamicValue.Primitive(PrimitiveValue.String(tpe)) => Some(tpe)
            case _                                                  => None
          } match {
            case Some("Class") =>
              (for {
                isFinal <- fieldMap
                             .get("isFinal")
                             .toRight(SchemaError.missingField(Nil, "isFinal"))
                             .flatMap(Schema[Boolean].fromDynamicValue)
                isAbstract <- fieldMap
                                .get("isAbstract")
                                .toRight(SchemaError.missingField(Nil, "isAbstract"))
                                .flatMap(Schema[Boolean].fromDynamicValue)
                isCase <- fieldMap
                            .get("isCase")
                            .toRight(SchemaError.missingField(Nil, "isCase"))
                            .flatMap(Schema[Boolean].fromDynamicValue)
                isValue <- fieldMap
                             .get("isValue")
                             .toRight(SchemaError.missingField(Nil, "isValue"))
                             .flatMap(Schema[Boolean].fromDynamicValue)
                bases <- fieldMap
                           .get("bases")
                           .toRight(SchemaError.missingField(Nil, "bases"))
                           .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              } yield TypeDefKind.Class(isFinal, isAbstract, isCase, isValue, bases)).fold(throw _, identity)

            case Some("Trait") =>
              (for {
                isSealed <- fieldMap
                              .get("isSealed")
                              .toRight(SchemaError.missingField(Nil, "isSealed"))
                              .flatMap(Schema[Boolean].fromDynamicValue)
                knownSubtypes <- fieldMap
                                   .get("knownSubtypes")
                                   .toRight(SchemaError.missingField(Nil, "knownSubtypes"))
                                   .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
                bases <- fieldMap
                           .get("bases")
                           .toRight(SchemaError.missingField(Nil, "bases"))
                           .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              } yield TypeDefKind.Trait(isSealed, knownSubtypes, bases)).fold(throw _, identity)

            case Some("Object") =>
              (for {
                bases <- fieldMap
                           .get("bases")
                           .toRight(SchemaError.missingField(Nil, "bases"))
                           .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              } yield TypeDefKind.Object(bases)).fold(throw _, identity)

            case Some("Enum") =>
              (for {
                cases <- fieldMap
                           .get("cases")
                           .toRight(SchemaError.missingField(Nil, "cases"))
                           .flatMap(Schema[List[EnumCaseInfo]].fromDynamicValue)
                bases <- fieldMap
                           .get("bases")
                           .toRight(SchemaError.missingField(Nil, "bases"))
                           .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
              } yield TypeDefKind.Enum(cases, bases)).fold(throw _, identity)

            case Some("EnumCase") =>
              (for {
                parentEnum <- fieldMap
                                .get("parentEnum")
                                .toRight(SchemaError.missingField(Nil, "parentEnum"))
                                .flatMap(typeReprSchema.fromDynamicValue)
                ordinal <- fieldMap
                             .get("ordinal")
                             .toRight(SchemaError.missingField(Nil, "ordinal"))
                             .flatMap(Schema[Int].fromDynamicValue)
                isObjectCase <- fieldMap
                                  .get("isObjectCase")
                                  .toRight(SchemaError.missingField(Nil, "isObjectCase"))
                                  .flatMap(Schema[Boolean].fromDynamicValue)
              } yield TypeDefKind.EnumCase(parentEnum, ordinal, isObjectCase)).fold(throw _, identity)

            case Some("TypeAlias") =>
              TypeDefKind.TypeAlias

            case Some("OpaqueType") =>
              (for {
                publicBounds <- fieldMap
                                  .get("publicBounds")
                                  .toRight(SchemaError.missingField(Nil, "publicBounds"))
                                  .flatMap(typeBoundsSchema.fromDynamicValue)
              } yield TypeDefKind.OpaqueType(publicBounds)).fold(throw _, identity)

            case Some("AbstractType") =>
              TypeDefKind.AbstractType

            case Some("Unknown") =>
              TypeDefKind.Unknown

            case _ =>
              throw SchemaError.conversionFailed(Nil, "Unknown TypeDefKind type")
          }
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      kind =>
        kind match {
          case TypeDefKind.Class(isFinal, isAbstract, isCase, isValue, bases) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Class"))),
                ("isFinal", Schema[Boolean].toDynamicValue(isFinal)),
                ("isAbstract", Schema[Boolean].toDynamicValue(isAbstract)),
                ("isCase", Schema[Boolean].toDynamicValue(isCase)),
                ("isValue", Schema[Boolean].toDynamicValue(isValue)),
                ("bases", Schema[List[TypeRepr]].toDynamicValue(bases))
              )
            )

          case TypeDefKind.Trait(isSealed, knownSubtypes, bases) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Trait"))),
                ("isSealed", Schema[Boolean].toDynamicValue(isSealed)),
                ("knownSubtypes", Schema[List[TypeRepr]].toDynamicValue(knownSubtypes)),
                ("bases", Schema[List[TypeRepr]].toDynamicValue(bases))
              )
            )

          case TypeDefKind.Object(bases) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Object"))),
                ("bases", Schema[List[TypeRepr]].toDynamicValue(bases))
              )
            )

          case TypeDefKind.Enum(cases, bases) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Enum"))),
                ("cases", Schema[List[EnumCaseInfo]].toDynamicValue(cases)),
                ("bases", Schema[List[TypeRepr]].toDynamicValue(bases))
              )
            )

          case TypeDefKind.EnumCase(parentEnum, ordinal, isObjectCase) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("EnumCase"))),
                ("parentEnum", typeReprSchema.toDynamicValue(parentEnum)),
                ("ordinal", Schema[Int].toDynamicValue(ordinal)),
                ("isObjectCase", Schema[Boolean].toDynamicValue(isObjectCase))
              )
            )

          case TypeDefKind.TypeAlias =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("TypeAlias")))
              )
            )

          case TypeDefKind.OpaqueType(publicBounds) =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("OpaqueType"))),
                ("publicBounds", typeBoundsSchema.toDynamicValue(publicBounds))
              )
            )

          case TypeDefKind.AbstractType =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("AbstractType")))
              )
            )

          case TypeDefKind.Unknown =>
            DynamicValue.Record(
              Chunk(
                ("type", DynamicValue.Primitive(PrimitiveValue.String("Unknown")))
              )
            )
        }
    )
  }

  // ============================================================================
  // TypeId[_] - The main recursive structure
  // ============================================================================

  implicit lazy val typeIdSchema: Schema[TypeId[_]] =
    Schema[DynamicValue].transform(
      {
        case DynamicValue.Record(fields) =>
          val fieldMap = fields.toMap
          (for {
            name <- fieldMap
                      .get("name")
                      .toRight(SchemaError.missingField(Nil, "name"))
                      .flatMap(Schema[String].fromDynamicValue)
            owner <- fieldMap
                       .get("owner")
                       .toRight(SchemaError.missingField(Nil, "owner"))
                       .flatMap(ownerSchema.fromDynamicValue)
            typeParams <- fieldMap
                            .get("typeParams")
                            .toRight(SchemaError.missingField(Nil, "typeParams"))
                            .flatMap(Schema[List[TypeParam]].fromDynamicValue)
            typeArgs <- fieldMap
                          .get("typeArgs")
                          .toRight(SchemaError.missingField(Nil, "typeArgs"))
                          .flatMap(Schema[List[TypeRepr]].fromDynamicValue)
            defKind <- fieldMap
                         .get("defKind")
                         .toRight(SchemaError.missingField(Nil, "defKind"))
                         .flatMap(typeDefKindSchema.fromDynamicValue)
            selfType <- fieldMap
                          .get("selfType")
                          .toRight(SchemaError.missingField(Nil, "selfType"))
                          .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
            aliasedTo <- fieldMap
                           .get("aliasedTo")
                           .toRight(SchemaError.missingField(Nil, "aliasedTo"))
                           .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
            representation <- fieldMap
                                .get("representation")
                                .toRight(SchemaError.missingField(Nil, "representation"))
                                .flatMap(Schema[Option[TypeRepr]].fromDynamicValue)
            annotations <- fieldMap
                             .get("annotations")
                             .toRight(SchemaError.missingField(Nil, "annotations"))
                             .flatMap(Schema[List[Annotation]].fromDynamicValue)
          } yield zio.blocks.typeid.TypeId.makeImpl(
            name,
            owner,
            typeParams,
            typeArgs,
            defKind,
            selfType,
            aliasedTo,
            representation,
            annotations
          )).fold(throw _, identity)
        case _ => throw SchemaError.expectationMismatch(Nil, "Expected a record")
      },
      typeId =>
        DynamicValue.Record(
          Chunk(
            ("name", Schema[String].toDynamicValue(typeId.name)),
            ("owner", ownerSchema.toDynamicValue(typeId.owner)),
            ("typeParams", Schema[List[TypeParam]].toDynamicValue(typeId.typeParams)),
            ("typeArgs", Schema[List[TypeRepr]].toDynamicValue(typeId.typeArgs)),
            ("defKind", typeDefKindSchema.toDynamicValue(typeId.defKind)),
            ("selfType", Schema[Option[TypeRepr]].toDynamicValue(typeId.selfType)),
            ("aliasedTo", Schema[Option[TypeRepr]].toDynamicValue(typeId.aliasedTo)),
            ("representation", Schema[Option[TypeRepr]].toDynamicValue(typeId.representation)),
            ("annotations", Schema[List[Annotation]].toDynamicValue(typeId.annotations))
          )
        )
    )
}
