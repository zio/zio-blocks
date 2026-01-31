package zio.blocks.schema.json

import zio.blocks.chunk.ChunkMap
import zio.blocks.schema._
import zio.blocks.schema.json.JsonSchema.{NonNegativeInt, RegexPattern}
import zio.test._

object JsonSchemaToReflectSpec extends SchemaBaseSpec {

  import JsonSchemaToReflect._
  import Shape._

  def spec: Spec[TestEnvironment, Any] = suite("JsonSchemaToReflectSpec")(
    suite("analyze - shape classification")(
      suite("Primitive shapes")(
        test("string type produces Primitive(String)") {
          val schema = JsonSchema.string()
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.String, _) => assertTrue(true)
            case _                             => assertTrue(false)
          }
        },
        test("integer type produces Primitive(Integer)") {
          val schema = JsonSchema.integer()
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.Integer, _) => assertTrue(true)
            case _                              => assertTrue(false)
          }
        },
        test("number type produces Primitive(Number)") {
          val schema = JsonSchema.number()
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.Number, _) => assertTrue(true)
            case _                             => assertTrue(false)
          }
        },
        test("boolean type produces Primitive(Boolean)") {
          val schema = JsonSchema.boolean
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.Boolean, _) => assertTrue(true)
            case _                              => assertTrue(false)
          }
        },
        test("null type produces Primitive(Null)") {
          val schema = JsonSchema.Object(`type` = Some(SchemaType.Single(JsonSchemaType.Null)))
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.Null, _) => assertTrue(true)
            case _                           => assertTrue(false)
          }
        },
        test("string with minLength/maxLength preserves schema object") {
          val schema = JsonSchema.string(NonNegativeInt.unsafe(1), NonNegativeInt.unsafe(100))
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.String, obj) =>
              assertTrue(obj.minLength.isDefined && obj.maxLength.isDefined)
            case _ => assertTrue(false)
          }
        },
        test("string with pattern preserves schema object") {
          val schema = JsonSchema.string(RegexPattern.unsafe("^[a-z]+$"))
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.String, obj) => assertTrue(obj.pattern.isDefined)
            case _                               => assertTrue(false)
          }
        },
        test("integer with minimum/maximum preserves schema object") {
          val schema = JsonSchema.integer(minimum = Some(0), maximum = Some(100))
          val shape  = analyze(schema)
          shape match {
            case Primitive(PrimKind.Integer, obj) =>
              assertTrue(obj.minimum.isDefined && obj.maximum.isDefined)
            case _ => assertTrue(false)
          }
        }
      ),
      suite("Record shapes")(
        test("object with properties produces Record") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer()))
          )
          val shape = analyze(schema)
          shape match {
            case Record(fields, _, _) => assertTrue(fields.length == 2)
            case _                    => assertTrue(false)
          }
        },
        test("Record captures required set") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
            required = Some(Set("name"))
          )
          val shape = analyze(schema)
          shape match {
            case Record(_, required, _) => assertTrue(required == Set("name"))
            case _                      => assertTrue(false)
          }
        },
        test("Record with additionalProperties:false is closed") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string())),
            additionalProperties = Some(JsonSchema.False)
          )
          val shape = analyze(schema)
          shape match {
            case Record(_, _, closed) => assertTrue(closed)
            case _                    => assertTrue(false)
          }
        },
        test("Record without additionalProperties:false is open") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string()))
          )
          val shape = analyze(schema)
          shape match {
            case Record(_, _, closed) => assertTrue(!closed)
            case _                    => assertTrue(false)
          }
        },
        test("Record preserves field order") {
          val schema = JsonSchema.obj(
            properties =
              Some(ChunkMap("z" -> JsonSchema.string(), "a" -> JsonSchema.integer(), "m" -> JsonSchema.boolean))
          )
          val shape = analyze(schema)
          shape match {
            case Record(fields, _, _) => assertTrue(fields.map(_._1) == List("z", "a", "m"))
            case _                    => assertTrue(false)
          }
        }
      ),
      suite("Map shapes")(
        test("object with only additionalProperties produces MapShape") {
          val schema = JsonSchema.obj(
            additionalProperties = Some(JsonSchema.string())
          )
          val shape = analyze(schema)
          shape match {
            case MapShape(_) => assertTrue(true)
            case _           => assertTrue(false)
          }
        },
        test("MapShape captures value schema") {
          val valueSchema = JsonSchema.integer(minimum = Some(0))
          val schema      = JsonSchema.obj(
            additionalProperties = Some(valueSchema)
          )
          val shape = analyze(schema)
          shape match {
            case MapShape(values) => assertTrue(values == valueSchema)
            case _                => assertTrue(false)
          }
        },
        test("object with properties takes precedence over additionalProperties") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string())),
            additionalProperties = Some(JsonSchema.integer())
          )
          val shape = analyze(schema)
          shape match {
            case Record(_, _, _) => assertTrue(true)
            case _               => assertTrue(false)
          }
        }
      ),
      suite("Sequence shapes")(
        test("array with items produces Sequence") {
          val schema = JsonSchema.array(items = Some(JsonSchema.string()))
          val shape  = analyze(schema)
          shape match {
            case Sequence(_) => assertTrue(true)
            case _           => assertTrue(false)
          }
        },
        test("Sequence captures item schema") {
          val itemSchema = JsonSchema.integer(minimum = Some(0))
          val schema     = JsonSchema.array(items = Some(itemSchema))
          val shape      = analyze(schema)
          shape match {
            case Sequence(items) => assertTrue(items == itemSchema)
            case _               => assertTrue(false)
          }
        }
      ),
      suite("Tuple shapes")(
        test("prefixItems with items:false produces Tuple") {
          val schema = JsonSchema.Object(
            prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
            items = Some(JsonSchema.False)
          )
          val shape = analyze(schema)
          shape match {
            case Tuple(prefixItems) => assertTrue(prefixItems.length == 2)
            case _                  => assertTrue(false)
          }
        },
        test("prefixItems without items:false produces Sequence (not tuple)") {
          val schema = JsonSchema.Object(
            prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
            items = Some(JsonSchema.string())
          )
          val shape = analyze(schema)
          shape match {
            case Sequence(_) => assertTrue(true)
            case _           => assertTrue(false)
          }
        },
        test("Tuple captures each prefix item schema") {
          val s1     = JsonSchema.string()
          val s2     = JsonSchema.integer()
          val s3     = JsonSchema.boolean
          val schema = JsonSchema.Object(
            prefixItems = Some(new ::(s1, s2 :: s3 :: Nil)),
            items = Some(JsonSchema.False)
          )
          val shape = analyze(schema)
          shape match {
            case Tuple(items) => assertTrue(items == List(s1, s2, s3))
            case _            => assertTrue(false)
          }
        }
      ),
      suite("Enum shapes")(
        test("string enum produces Enum") {
          val schema = JsonSchema.Object(
            `enum` = Some(new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil))
          )
          val shape = analyze(schema)
          shape match {
            case Enum(cases) => assertTrue(cases == List("Red", "Green", "Blue"))
            case _           => assertTrue(false)
          }
        },
        test("mixed enum (not all strings) does not produce Enum") {
          val schema = JsonSchema.Object(
            `enum` = Some(new ::(Json.String("Red"), Json.Number(42) :: Nil))
          )
          val shape = analyze(schema)
          shape match {
            case Enum(_) => assertTrue(false)
            case _       => assertTrue(true)
          }
        },
        test("single-value string enum produces Enum") {
          val schema = JsonSchema.Object(
            `enum` = Some(new ::(Json.String("Only"), Nil))
          )
          val shape = analyze(schema)
          shape match {
            case Enum(cases) => assertTrue(cases == List("Only"))
            case _           => assertTrue(false)
          }
        }
      ),
      suite("Option shapes")(
        test("union type [T, null] produces OptionOf") {
          val schema = JsonSchema.Object(
            `type` = Some(SchemaType.Union(::(JsonSchemaType.String, List(JsonSchemaType.Null))))
          )
          val shape = analyze(schema)
          shape match {
            case OptionOf(_) => assertTrue(true)
            case _           => assertTrue(false)
          }
        },
        test("anyOf with null schema produces OptionOf") {
          val schema = JsonSchema.Object(
            anyOf = Some(
              new ::(
                JsonSchema.string(),
                JsonSchema.Object(`type` = Some(SchemaType.Single(JsonSchemaType.Null))) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          shape match {
            case OptionOf(_) => assertTrue(true)
            case _           => assertTrue(false)
          }
        },
        test("anyOf with two non-null schemas does not produce OptionOf") {
          val schema = JsonSchema.Object(
            anyOf = Some(
              new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)
            )
          )
          val shape = analyze(schema)
          shape match {
            case OptionOf(_) => assertTrue(false)
            case _           => assertTrue(true)
          }
        }
      ),
      suite("KeyVariant shapes")(
        test("oneOf with single-property wrapper objects produces KeyVariant") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "CreditCard" -> JsonSchema.obj(properties = Some(ChunkMap("ccNum" -> JsonSchema.string())))
                    )
                  ),
                  required = Some(Set("CreditCard"))
                ),
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "BankAccount" -> JsonSchema.obj(properties = Some(ChunkMap("accNo" -> JsonSchema.string())))
                    )
                  ),
                  required = Some(Set("BankAccount"))
                ) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          shape match {
            case KeyVariant(cases) => assertTrue(cases.map(_._1) == List("CreditCard", "BankAccount"))
            case _                 => assertTrue(false)
          }
        },
        test("KeyVariant captures body schemas") {
          val ccBody = JsonSchema.obj(properties = Some(ChunkMap("ccNum" -> JsonSchema.string())))
          val baBody = JsonSchema.obj(properties = Some(ChunkMap("accNo" -> JsonSchema.string())))
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(properties = Some(ChunkMap("CreditCard" -> ccBody)), required = Some(Set("CreditCard"))),
                JsonSchema.obj(
                  properties = Some(ChunkMap("BankAccount" -> baBody)),
                  required = Some(Set("BankAccount"))
                ) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          shape match {
            case KeyVariant(cases) =>
              assertTrue(cases.exists { case (name, body) => name == "CreditCard" && body == ccBody })
            case _ => assertTrue(false)
          }
        },
        test("oneOf with multi-property objects does not produce KeyVariant") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(
                  properties = Some(ChunkMap("a" -> JsonSchema.string(), "b" -> JsonSchema.string())),
                  required = Some(Set("a", "b"))
                ),
                JsonSchema.obj(
                  properties = Some(ChunkMap("c" -> JsonSchema.string())),
                  required = Some(Set("c"))
                ) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          shape match {
            case KeyVariant(_) => assertTrue(false)
            case _             => assertTrue(true)
          }
        }
      ),
      suite("FieldVariant shapes")(
        test("oneOf with common const discriminator field produces FieldVariant") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "type" -> JsonSchema.Object(const = Some(Json.String("dog"))),
                      "name" -> JsonSchema.string()
                    )
                  )
                ),
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "type" -> JsonSchema.Object(const = Some(Json.String("cat"))),
                      "age"  -> JsonSchema.integer()
                    )
                  )
                ) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          shape match {
            case FieldVariant(disc, cases) =>
              assertTrue(disc == "type" && cases.map(_._1) == List("dog", "cat"))
            case _ => assertTrue(false)
          }
        },
        test("FieldVariant excludes discriminator field from body schema") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "kind"  -> JsonSchema.Object(const = Some(Json.String("a"))),
                      "value" -> JsonSchema.string()
                    )
                  )
                ),
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "kind"  -> JsonSchema.Object(const = Some(Json.String("b"))),
                      "count" -> JsonSchema.integer()
                    )
                  )
                ) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          shape match {
            case FieldVariant(_, cases) =>
              val bodyProps = cases.flatMap {
                case (_, bodySchema: JsonSchema.Object) =>
                  bodySchema.properties.map(_.keys.toSet).getOrElse(Set.empty)
                case _ => Set.empty[String]
              }
              assertTrue(!bodyProps.contains("kind"))
            case _ => assertTrue(false)
          }
        }
      ),
      suite("Dynamic fallback")(
        test("JsonSchema.True produces Dynamic") {
          val shape = analyze(JsonSchema.True)
          assertTrue(shape == Dynamic)
        },
        test("JsonSchema.False produces Dynamic") {
          val shape = analyze(JsonSchema.False)
          assertTrue(shape == Dynamic)
        },
        test("empty object schema produces Dynamic") {
          val schema = JsonSchema.Object()
          val shape  = analyze(schema)
          assertTrue(shape == Dynamic)
        },
        test("untagged oneOf produces Dynamic") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(properties = Some(ChunkMap("left" -> JsonSchema.string()))),
                JsonSchema.obj(properties = Some(ChunkMap("right" -> JsonSchema.integer()))) :: Nil
              )
            )
          )
          val shape = analyze(schema)
          assertTrue(shape == Dynamic)
        }
      )
    ),
    suite("toReflect - Reflect generation")(
      suite("Primitive Reflect types")(
        test("string schema produces wrapped Reflect.Primitive") {
          val reflect = toReflect(JsonSchema.string())
          val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          assertTrue(inner.exists(_.isPrimitive))
        },
        test("integer schema produces wrapped Reflect.Primitive") {
          val reflect = toReflect(JsonSchema.integer())
          val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          assertTrue(inner.exists(_.isPrimitive))
        },
        test("number schema produces wrapped Reflect.Primitive") {
          val reflect = toReflect(JsonSchema.number())
          val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          assertTrue(inner.exists(_.isPrimitive))
        },
        test("boolean schema produces wrapped Reflect.Primitive") {
          val reflect = toReflect(JsonSchema.boolean)
          val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          assertTrue(inner.exists(_.isPrimitive))
        }
      ),
      suite("Record Reflect types")(
        test("closed object produces Reflect.Record") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string())),
            additionalProperties = Some(JsonSchema.False)
          )
          val reflect = toReflect(schema)
          assertTrue(reflect.isRecord)
        },
        test("open object produces Reflect.Record with open modifier") {
          val schema  = JsonSchema.obj(properties = Some(ChunkMap("name" -> JsonSchema.string())))
          val reflect = toReflect(schema)
          val hasOpen = reflect.modifiers.exists {
            case Modifier.config("json.closure", "open") => true
            case _                                       => false
          }
          assertTrue(reflect.isRecord && hasOpen)
        },
        test("Record has correct field names") {
          val schema = JsonSchema.obj(
            properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer()))
          )
          val reflect = toReflect(schema)
          val record  = reflect.asRecord
          val names   = record.map(_.fields.map(_.name).toSet).getOrElse(Set.empty)
          assertTrue(names == Set("name", "age"))
        }
      ),
      suite("Sequence Reflect types")(
        test("array schema produces Reflect.Sequence") {
          val schema  = JsonSchema.array(items = Some(JsonSchema.string()))
          val reflect = toReflect(schema)
          val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          assertTrue(inner.exists(_.isSequence))
        }
      ),
      suite("Map Reflect types")(
        test("map object produces Reflect.Map") {
          val schema  = JsonSchema.obj(additionalProperties = Some(JsonSchema.string()))
          val reflect = toReflect(schema)
          val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          assertTrue(inner.exists(_.isMap))
        }
      ),
      suite("Tuple Reflect types")(
        test("tuple schema produces Reflect.Record with positional fields") {
          val schema = JsonSchema.Object(
            prefixItems = Some(new ::(JsonSchema.string(), JsonSchema.integer() :: Nil)),
            items = Some(JsonSchema.False)
          )
          val reflect    = toReflect(schema)
          val inner      = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
          val record     = inner.flatMap(_.asRecord)
          val fieldNames = record.map(_.fields.map(_.name).toList).getOrElse(Nil)
          assertTrue(fieldNames == List("_1", "_2"))
        }
      ),
      suite("Enum Reflect types")(
        test("enum schema produces Reflect.Variant") {
          val schema = JsonSchema.Object(
            `enum` = Some(new ::(Json.String("Red"), Json.String("Green") :: Json.String("Blue") :: Nil))
          )
          val reflect = toReflect(schema)
          assertTrue(reflect.isVariant)
        },
        test("enum cases are empty Records (isEnumeration)") {
          val schema = JsonSchema.Object(
            `enum` = Some(new ::(Json.String("A"), Json.String("B") :: Nil))
          )
          val reflect = toReflect(schema)
          assertTrue(reflect.isEnumeration)
        },
        test("enum has correct case names") {
          val schema = JsonSchema.Object(
            `enum` = Some(new ::(Json.String("X"), Json.String("Y") :: Json.String("Z") :: Nil))
          )
          val reflect   = toReflect(schema)
          val variant   = reflect.asVariant
          val caseNames = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
          assertTrue(caseNames == Set("X", "Y", "Z"))
        }
      ),
      suite("Variant Reflect types")(
        test("key-discriminated oneOf produces Reflect.Variant") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "CreditCard" -> JsonSchema.obj(properties = Some(ChunkMap("ccNum" -> JsonSchema.string())))
                    )
                  ),
                  required = Some(Set("CreditCard"))
                ),
                JsonSchema.obj(
                  properties = Some(
                    ChunkMap(
                      "BankAccount" -> JsonSchema.obj(properties = Some(ChunkMap("accNo" -> JsonSchema.string())))
                    )
                  ),
                  required = Some(Set("BankAccount"))
                ) :: Nil
              )
            )
          )
          val reflect = toReflect(schema)
          assertTrue(reflect.isVariant)
        },
        test("key-discriminated variant has correct case names") {
          val schema = JsonSchema.Object(
            oneOf = Some(
              new ::(
                JsonSchema.obj(properties = Some(ChunkMap("Foo" -> JsonSchema.string())), required = Some(Set("Foo"))),
                JsonSchema.obj(
                  properties = Some(ChunkMap("Bar" -> JsonSchema.integer())),
                  required = Some(Set("Bar"))
                ) :: Nil
              )
            )
          )
          val reflect   = toReflect(schema)
          val variant   = reflect.asVariant
          val caseNames = variant.map(_.cases.map(_.name).toSet).getOrElse(Set.empty)
          assertTrue(caseNames == Set("Foo", "Bar"))
        }
      ),
      suite("Dynamic Reflect types")(
        test("JsonSchema.True produces Reflect.Dynamic") {
          val reflect = toReflect(JsonSchema.True)
          assertTrue(reflect.isDynamic)
        },
        test("JsonSchema.False produces Reflect.Dynamic") {
          val reflect = toReflect(JsonSchema.False)
          assertTrue(reflect.isDynamic)
        }
      )
    ),
    suite("Validation translation")(
      test("string minLength/maxLength translates to Validation.String.Length") {
        val schema = JsonSchema.string(
          minLength = Some(NonNegativeInt.unsafe(5)),
          maxLength = Some(NonNegativeInt.unsafe(10))
        )
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.String =>
                pt.validation match {
                  case Validation.String.Length(Some(5), Some(10)) => assertTrue(true)
                  case _                                           => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("string pattern translates to Validation.String.Pattern") {
        val schema  = JsonSchema.string(pattern = Some(RegexPattern.unsafe("^[a-z]+$")))
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.String =>
                pt.validation match {
                  case Validation.String.Pattern("^[a-z]+$") => assertTrue(true)
                  case _                                     => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("string minLength=1, maxLength=1 translates to Validation.String.NonEmpty") {
        val schema  = JsonSchema.string(NonNegativeInt.one, NonNegativeInt.one)
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.String =>
                pt.validation match {
                  case Validation.String.NonEmpty => assertTrue(true)
                  case _                          => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("string with pattern AND length constraints prioritizes pattern") {
        val schema = JsonSchema.string(
          minLength = Some(NonNegativeInt.unsafe(5)),
          maxLength = Some(NonNegativeInt.unsafe(10)),
          pattern = Some(RegexPattern.unsafe("^[a-z]+$"))
        )
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.String =>
                pt.validation match {
                  case Validation.String.Pattern("^[a-z]+$") => assertTrue(true)
                  case _                                     => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("integer minimum/maximum translates to Validation.Numeric.Range with BigInt") {
        val schema  = JsonSchema.integer(minimum = Some(0), maximum = Some(100))
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.BigInt =>
                pt.validation match {
                  case Validation.Numeric.Range(Some(min), Some(max)) =>
                    assertTrue(min == BigInt(0) && max == BigInt(100))
                  case _ => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("number minimum/maximum translates to Validation.Numeric.Range with BigDecimal") {
        val schema  = JsonSchema.number(minimum = Some(0.5), maximum = Some(99.9))
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.BigDecimal =>
                pt.validation match {
                  case Validation.Numeric.Range(Some(min), Some(max)) =>
                    assertTrue(min == BigDecimal(0.5) && max == BigDecimal(99.9))
                  case _ => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("integer with only minimum translates to Range(Some, None)") {
        val schema  = JsonSchema.integer(minimum = Some(0))
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.BigInt =>
                pt.validation match {
                  case Validation.Numeric.Range(Some(_), None) => assertTrue(true)
                  case _                                       => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("integer with only maximum translates to Range(None, Some)") {
        val schema  = JsonSchema.integer(maximum = Some(100))
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.BigInt =>
                pt.validation match {
                  case Validation.Numeric.Range(None, Some(_)) => assertTrue(true)
                  case _                                       => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      },
      test("exclusiveMinimum/exclusiveMaximum also work") {
        val schema = JsonSchema.Object(
          `type` = Some(SchemaType.Single(JsonSchemaType.Integer)),
          exclusiveMinimum = Some(BigDecimal(0)),
          exclusiveMaximum = Some(BigDecimal(100))
        )
        val reflect = toReflect(schema)
        val inner   = reflect.asWrapperUnknown.map(_.wrapper.wrapped)
        val prim    = inner.flatMap(_.asPrimitive)
        prim match {
          case Some(p) =>
            p.primitiveType match {
              case pt: PrimitiveType.BigInt =>
                pt.validation match {
                  case Validation.Numeric.Range(Some(_), Some(_)) => assertTrue(true)
                  case _                                          => assertTrue(false)
                }
              case _ => assertTrue(false)
            }
          case None => assertTrue(false)
        }
      }
    ),
    suite("Codec integration via Schema.fromJsonSchema")(
      test("string schema decodes successfully") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.string())
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode(""""hello"""")
        assertTrue(result.isRight)
      },
      test("integer schema decodes successfully") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.integer())
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("42")
        assertTrue(result.isRight)
      },
      test("object schema decodes successfully") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(schema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice", "age": 30}""")
        assertTrue(result.isRight)
      },
      test("object schema rejects missing required fields") {
        val schema = JsonSchema.obj(
          properties = Some(ChunkMap("name" -> JsonSchema.string(), "age" -> JsonSchema.integer())),
          required = Some(Set("name", "age"))
        )
        val schemaForJs = Schema.fromJsonSchema(schema)
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""{"name": "Alice"}""")
        assertTrue(result.isLeft)
      },
      test("array schema decodes successfully") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.array(items = Some(JsonSchema.string())))
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""["a", "b", "c"]""")
        assertTrue(result.isRight)
      },
      test("array schema rejects wrong item types") {
        val schemaForJs = Schema.fromJsonSchema(JsonSchema.array(items = Some(JsonSchema.integer())))
        val codec       = schemaForJs.derive(JsonFormat)
        val result      = codec.decode("""[1, "two", 3]""")
        assertTrue(result.isLeft)
      }
    )
  )
}
