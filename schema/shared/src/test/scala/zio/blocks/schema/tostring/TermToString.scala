package zio.blocks.schema.tostring

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.test._

object TermToString extends ZIOSpecDefault {

  def spec = suite("Term toString")(
    suite("Primitive Term")(
      test("renders String term") {
        val term = Term("fieldName", Reflect.string[Binding])
        assertTrue(term.toString == "fieldName: String")
      },
      test("renders Int term") {
        val term = Term("age", Reflect.int[Binding])
        assertTrue(term.toString == "age: Int")
      }
    ),

    suite("Complex Term")(
      test("renders term with record value") {
        val pointReflect = Reflect.Record[Binding, Point](
          fields = Vector(
            Term("x", Reflect.int[Binding]),
            Term("y", Reflect.int[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Point"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(ints = 2)
              def construct(in: Registers, offset: RegisterOffset) =
                Point(in.getInt(offset), in.getInt(offset + RegisterOffset(ints = 1)))
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(ints = 2)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = {
                out.setInt(offset, in.x)
                out.setInt(offset + RegisterOffset(ints = 1), in.y)
              }
            }
          )
        )

        val term = Term("location", pointReflect)

        // ReflectPrinter.printTerm(indent=0) -> "name: record ..."
        // The record body is indented because it's multiline.
        val expected =
          """location: record Point {
            |  x: Int
            |  y: Int
            |}""".stripMargin

        assertTrue(term.toString == expected)
      },

      test("renders term with sequence value") {
        val listReflect = Reflect.list[Binding, Int](Reflect.int[Binding])
        val term        = Term("items", listReflect)
        assertTrue(term.toString == "items: sequence List[Int]")
      },

      test("renders term with list of records (multiline sequence)") {
        // reuse pointReflect
        val pointReflect = Reflect.Record[Binding, Point](
          fields = Vector(
            Term("x", Reflect.int[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Point"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(ints = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(in.getInt(offset), 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = out.setInt(offset, in.x)
            }
          )
        )

        val listReflect = Reflect.list[Binding, Point](pointReflect)
        val term        = Term("points", listReflect)

        val expected =
          """points: sequence List[
            |  record Point {
            |    x: Int
            |  }
            |]""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("Variant Term (Field)")(
      test("renders term with variant value") {
        val optionReflect = Reflect.optionInt[Binding](Reflect.int[Binding])
        val term          = Term("maybeInt", optionReflect)

        val expected =
          """maybeInt: variant Option[Int] {
            |  | None
            |  | Some(value: Int)
            |}""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("Map Term")(
      test("renders term with simple map value") {
        val mapReflect = Reflect.map[Binding, String, Int](Reflect.string[Binding], Reflect.int[Binding])
        val term       = Term("config", mapReflect)
        assertTrue(term.toString == "config: map Map[String, Int]")
      },
      test("renders term with complex map value (multiline)") {
        val pointReflect = Reflect.Record[Binding, Point](
          fields = Vector(
            Term("x", Reflect.int[Binding]),
            Term("y", Reflect.int[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Point"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(ints = 2)
              def construct(in: Registers, offset: RegisterOffset) =
                Point(in.getInt(offset), in.getInt(offset + RegisterOffset(ints = 1)))
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(ints = 2)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = {
                out.setInt(offset, in.x)
                out.setInt(offset + RegisterOffset(ints = 1), in.y)
              }
            }
          )
        )

        val mapReflect = Reflect.map[Binding, String, Point](Reflect.string[Binding], pointReflect)
        val term       = Term("locations", mapReflect)

        val expected =
          """locations: map Map[
            |  String,
            |  record Point {
            |    x: Int
            |    y: Int
            |  }
            |]""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("Wrapper Term")(
      test("renders term with simple wrapper") {
        val userIdReflect = Reflect.Wrapper[Binding, String, String](
          wrapped = Reflect.string[Binding],
          typeName = TypeName(Namespace(Nil), "UserId"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[String, String](s => Right(s), identity)
        )
        val term = Term("userId", userIdReflect)
        assertTrue(term.toString == "userId: wrapper UserId(String)")
      },
      test("renders term with complex wrapper") {
        val emailPartsReflect = Reflect.Record[Binding, EmailParts](
          fields = Vector(
            Term("local", Reflect.string[Binding]),
            Term("domain", Reflect.string[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "EmailParts"),
          recordBinding = Binding.Record(
            constructor = new Constructor[EmailParts] {
              def usedRegisters                                    = RegisterOffset(objects = 2)
              def construct(in: Registers, offset: RegisterOffset) =
                EmailParts(
                  in.getObject(offset).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[String]
                )
            },
            deconstructor = new Deconstructor[EmailParts] {
              def usedRegisters                                                       = RegisterOffset(objects = 2)
              def deconstruct(out: Registers, offset: RegisterOffset, in: EmailParts) = {
                out.setObject(offset, in.local)
                out.setObject(offset + RegisterOffset(objects = 1), in.domain)
              }
            }
          )
        )

        val validatedEmailReflect = Reflect.Wrapper[Binding, EmailParts, EmailParts](
          wrapped = emailPartsReflect,
          typeName = TypeName(Namespace(Nil), "ValidatedEmail"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[EmailParts, EmailParts](e => Right(e), identity)
        )

        val term = Term("email", validatedEmailReflect)

        val expected =
          """email: wrapper ValidatedEmail(
            |  record EmailParts {
            |    local: String
            |    domain: String
            |  }
            |)""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("All Primitive Terms")(
      test("renders Boolean term") {
        val term = Term("active", Reflect.boolean[Binding])
        assertTrue(term.toString == "active: Boolean")
      },
      test("renders Byte term") {
        val term = Term("byteValue", Reflect.byte[Binding])
        assertTrue(term.toString == "byteValue: Byte")
      },
      test("renders Short term") {
        val term = Term("shortValue", Reflect.short[Binding])
        assertTrue(term.toString == "shortValue: Short")
      },
      test("renders Long term") {
        val term = Term("timestamp", Reflect.long[Binding])
        assertTrue(term.toString == "timestamp: Long")
      },
      test("renders Float term") {
        val term = Term("floatValue", Reflect.float[Binding])
        assertTrue(term.toString == "floatValue: Float")
      },
      test("renders Double term") {
        val term = Term("doubleValue", Reflect.double[Binding])
        assertTrue(term.toString == "doubleValue: Double")
      },
      test("renders Char term") {
        val term = Term("charValue", Reflect.char[Binding])
        assertTrue(term.toString == "charValue: Char")
      },
      test("renders BigInt term") {
        val term = Term("bigIntValue", Reflect.bigInt[Binding])
        assertTrue(term.toString == "bigIntValue: BigInt")
      },
      test("renders BigDecimal term") {
        val term = Term("price", Reflect.bigDecimal[Binding])
        assertTrue(term.toString == "price: BigDecimal")
      },
      test("renders Unit term") {
        val term = Term("unitValue", Reflect.unit[Binding])
        assertTrue(term.toString == "unitValue: Unit")
      },
      test("renders Instant term") {
        val term = Term("createdAt", Reflect.instant[Binding])
        assertTrue(term.toString == "createdAt: java.time.Instant")
      },
      test("renders Duration term") {
        val term = Term("elapsed", Reflect.duration[Binding])
        assertTrue(term.toString == "elapsed: java.time.Duration")
      },
      test("renders Period term") {
        val term = Term("age", Reflect.period[Binding])
        assertTrue(term.toString == "age: java.time.Period")
      },
      test("renders LocalDate term") {
        val term = Term("birthDate", Reflect.localDate[Binding])
        assertTrue(term.toString == "birthDate: java.time.LocalDate")
      },
      test("renders UUID term") {
        val term = Term("id", Reflect.uuid[Binding])
        assertTrue(term.toString == "id: java.util.UUID")
      }
    ),

    suite("Very Deep Nesting")(
      test("renders 5-level deep nested records") {
        val level5 = Reflect.Record[Binding, Point](
          fields = Vector(Term("value", Reflect.int[Binding])),
          typeName = TypeName(Namespace(Nil), "Level5"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(ints = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(in.getInt(offset), 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = out.setInt(offset, in.x)
            }
          )
        )

        val level4 = Reflect.Record[Binding, Point](
          fields = Vector(Term("nested", level5)),
          typeName = TypeName(Namespace(Nil), "Level4"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(0, 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = ()
            }
          )
        )

        val level3 = Reflect.Record[Binding, Point](
          fields = Vector(Term("nested", level4)),
          typeName = TypeName(Namespace(Nil), "Level3"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(0, 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = ()
            }
          )
        )

        val level2 = Reflect.Record[Binding, Point](
          fields = Vector(Term("nested", level3)),
          typeName = TypeName(Namespace(Nil), "Level2"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(0, 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = ()
            }
          )
        )

        val term = Term("root", level2)

        val expected =
          """root: record Level2 {
            |  nested:   record Level3 {
            |    nested:   record Level4 {
            |      nested:   record Level5 {
            |        value: Int
            |      }
            |    }
            |  }
            |}""".stripMargin

        assertTrue(term.toString == expected)
      },
      test("renders term with deeply nested sequence in map in record") {
        val innerRecord = Reflect.Record[Binding, Point](
          fields = Vector(Term("x", Reflect.int[Binding])),
          typeName = TypeName(Namespace(Nil), "InnerRecord"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(ints = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(in.getInt(offset), 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = out.setInt(offset, in.x)
            }
          )
        )

        val listReflect = Reflect.list[Binding, Point](innerRecord)
        val mapReflect  = Reflect.map[Binding, String, List[Point]](Reflect.string[Binding], listReflect)

        val outerRecord = Reflect.Record[Binding, Point](
          fields = Vector(Term("data", mapReflect)),
          typeName = TypeName(Namespace(Nil), "OuterRecord"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point(0, 0)
            },
            deconstructor = new Deconstructor[Point] {
              def usedRegisters                                                  = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point) = ()
            }
          )
        )

        val term = Term("complex", outerRecord)

        val expected =
          """complex: record OuterRecord {
            |  data:   map Map[String, sequence List[
            |    record InnerRecord {
            |      x: Int
            |    }
            |  ]]
            |}""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("Primitive Term with validations")(
      test("renders String term with Length validation") {
        val primitiveReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.Length(Some(5), Some(10))),
          TypeName.string,
          Binding.Primitive()
        )
        val term = Term("name", primitiveReflect)
        assertTrue(term.toString == "name: String @Length(min=5, max=10)")
      },
      test("renders String term with NonEmpty validation") {
        val primitiveReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.NonEmpty),
          TypeName.string,
          Binding.Primitive()
        )
        val term = Term("title", primitiveReflect)
        assertTrue(term.toString == "title: String @NonEmpty")
      },
      test("renders String term with Pattern validation") {
        val primitiveReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.Pattern("^[a-zA-Z]+$")),
          TypeName.string,
          Binding.Primitive()
        )
        val term = Term("code", primitiveReflect)
        assertTrue(term.toString == "code: String @Pattern(\"^[a-zA-Z]+$\")")
      },
      test("renders Int term with Positive validation") {
        val primitiveReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.Positive),
          TypeName.int,
          Binding.Primitive()
        )
        val term = Term("age", primitiveReflect)
        assertTrue(term.toString == "age: Int @Positive")
      },
      test("renders Int term with Range validation") {
        val primitiveReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.Range(Some(1), Some(100))),
          TypeName.int,
          Binding.Primitive()
        )
        val term = Term("percentage", primitiveReflect)
        assertTrue(term.toString == "percentage: Int @Range(min=1, max=100)")
      },
      test("renders Long term with NonNegative validation") {
        val primitiveReflect = Reflect.Primitive[Binding, Long](
          new PrimitiveType.Long(Validation.Numeric.NonNegative),
          TypeName.long,
          Binding.Primitive()
        )
        val term = Term("count", primitiveReflect)
        assertTrue(term.toString == "count: Long @NonNegative")
      },
      test("renders term with record containing validated fields") {
        // Derive schema to get proper binding, then replace fields with validated versions
        lazy implicit val schema: Schema[ValidatedPerson] = Schema.derived[ValidatedPerson]
        val derivedRecord                                 = schema.reflect.asRecord.get

        // Create validated primitives
        val nameReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.NonEmpty),
          TypeName.string,
          Binding.Primitive()
        )
        val ageReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.Range(Some(0), Some(120))),
          TypeName.int,
          Binding.Primitive()
        )

        // Create new record with validated fields, reusing derived binding
        val validatedRecord = derivedRecord.copy(
          fields = Vector(
            Term[Binding, ValidatedPerson, String]("name", nameReflect),
            Term[Binding, ValidatedPerson, Int]("age", ageReflect)
          )
        )

        val term = Term("person", validatedRecord)

        val expected =
          """person: record ValidatedPerson {
            |  name: String @NonEmpty
            |  age: Int @Range(min=0, max=120)
            |}""".stripMargin

        assertTrue(term.toString == expected)
      },
      test("renders term with sequence of validated elements") {
        val intReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.Positive),
          TypeName.int,
          Binding.Primitive()
        )

        val listReflect = Reflect.Sequence[Binding, Int, List](
          element = intReflect,
          typeName = TypeName.list(TypeName.int),
          seqBinding = Binding.Seq.list
        )

        val term = Term("positiveNumbers", listReflect)
        assertTrue(term.toString == "positiveNumbers: sequence List[Int @Positive]")
      },
      test("renders term with map having validated keys and values") {
        val keyReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.NonBlank),
          TypeName.string,
          Binding.Primitive()
        )
        val valueReflect = Reflect.Primitive[Binding, Int](
          new PrimitiveType.Int(Validation.Numeric.NonNegative),
          TypeName.int,
          Binding.Primitive()
        )

        val mapReflect = Reflect.Map[Binding, String, Int, scala.collection.immutable.Map](
          key = keyReflect,
          value = valueReflect,
          typeName = TypeName.map(TypeName.string, TypeName.int),
          mapBinding = Binding.Map.map
        )

        val term = Term("scores", mapReflect)
        assertTrue(term.toString == "scores: map Map[String @NonBlank, Int @NonNegative]")
      },
      test("renders term with nested record containing validated fields inside sequence") {
        // Derive schema to get proper binding
        lazy implicit val transactionSchema: Schema[Transaction] = Schema.derived[Transaction]
        val derivedRecord                                        = transactionSchema.reflect.asRecord.get

        // Create validated primitives
        val codeReflect = Reflect.Primitive[Binding, String](
          new PrimitiveType.String(Validation.String.Pattern("^[A-Z]{3}$")),
          TypeName.string,
          Binding.Primitive()
        )
        val amountReflect = Reflect.Primitive[Binding, BigDecimal](
          new PrimitiveType.BigDecimal(Validation.Numeric.Positive),
          TypeName.bigDecimal,
          Binding.Primitive()
        )

        // Create new record with validated fields
        val validatedRecord = derivedRecord.copy(
          fields = Vector(
            Term[Binding, Transaction, String]("currencyCode", codeReflect),
            Term[Binding, Transaction, BigDecimal]("amount", amountReflect)
          )
        )

        // Create sequence of validated records
        lazy implicit val listSchema: Schema[List[Transaction]] = Schema.list[Transaction]
        val derivedList                                         = listSchema.reflect.asSequence.get
        val validatedList                                       = derivedList.copy(element = validatedRecord)

        val term = Term("transactions", validatedList)

        val expected =
          """transactions: sequence List[
            |  record Transaction {
            |    currencyCode: String @Pattern("^[A-Z]{3}$")
            |    amount: BigDecimal @Positive
            |  }
            |]""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("Deferred Term")(
      test("renders deferred term (recursive type)") {
        lazy val treeReflect: Reflect.Deferred[Binding, Tree] = Reflect.Deferred(() => treeVariant)
        lazy val treeVariant: Reflect.Variant[Binding, Tree]  = Reflect.Variant[Binding, Tree](
          cases = Vector(
            Term(
              "Leaf",
              Reflect.Record[Binding, Tree](
                fields = Vector(Term("value", Reflect.int[Binding])),
                typeName = TypeName(Namespace(Nil), "Leaf"),
                recordBinding = Binding.Record(
                  constructor = new Constructor[Tree] {
                    def usedRegisters                                    = RegisterOffset(ints = 1)
                    def construct(in: Registers, offset: RegisterOffset) =
                      Leaf(in.getInt(offset))
                  },
                  deconstructor = new Deconstructor[Tree] {
                    def usedRegisters                                                 = RegisterOffset(ints = 1)
                    def deconstruct(out: Registers, offset: RegisterOffset, in: Tree) =
                      out.setInt(offset, in.asInstanceOf[Leaf].value)
                  }
                )
              )
            ),
            Term(
              "Branch",
              Reflect.Record[Binding, Tree](
                fields = Vector(
                  Term("value", Reflect.int[Binding]),
                  Term("left", treeReflect),
                  Term("right", treeReflect)
                ),
                typeName = TypeName(Namespace(Nil), "Branch"),
                recordBinding = Binding.Record(
                  constructor = new Constructor[Tree] {
                    def usedRegisters                                    = RegisterOffset(ints = 1, objects = 2)
                    def construct(in: Registers, offset: RegisterOffset) =
                      Branch(
                        in.getInt(offset),
                        in.getObject(offset + RegisterOffset(ints = 1)).asInstanceOf[Tree],
                        in.getObject(offset + RegisterOffset(ints = 1, objects = 1)).asInstanceOf[Tree]
                      )
                  },
                  deconstructor = new Deconstructor[Tree] {
                    def usedRegisters                                                 = RegisterOffset(ints = 1, objects = 2)
                    def deconstruct(out: Registers, offset: RegisterOffset, in: Tree) = {
                      val b = in.asInstanceOf[Branch]
                      out.setInt(offset, b.value)
                      out.setObject(offset + RegisterOffset(ints = 1), b.left)
                      out.setObject(offset + RegisterOffset(ints = 1, objects = 1), b.right)
                    }
                  }
                )
              )
            )
          ),
          typeName = TypeName(Namespace(Nil), "Tree"),
          variantBinding = Binding.Variant(
            discriminator = new Discriminator[Tree] {
              def discriminate(in: Tree): Int = in match {
                case _: Leaf   => 0
                case _: Branch => 1
              }
            },
            matchers = Matchers(
              Vector(
                new Matcher[Tree] {
                  def downcastOrNull(any: Any): Tree = any match {
                    case l: Leaf => l
                    case _       => null.asInstanceOf[Tree]
                  }
                },
                new Matcher[Tree] {
                  def downcastOrNull(any: Any): Tree = any match {
                    case b: Branch => b
                    case _         => null.asInstanceOf[Tree]
                  }
                }
              )
            )
          )
        )

        val term = Term("tree", treeReflect)

        val expected =
          """tree: variant Tree {
            |  | Leaf(value: Int)
            |  | Branch(
            |      value: Int,
            |      left: deferred => Tree,
            |      right: deferred => Tree
            |    )
            |}""".stripMargin

        assertTrue(term.toString == expected)
      }
    )
  )

  case class Point(x: Int, y: Int)
  case class EmailParts(local: String, domain: String)

  // For validation tests
  case class ValidatedPerson(name: String, age: Int)
  case class Transaction(currencyCode: String, amount: BigDecimal)

  sealed trait Tree
  case class Leaf(value: Int)                            extends Tree
  case class Branch(value: Int, left: Tree, right: Tree) extends Tree
}
