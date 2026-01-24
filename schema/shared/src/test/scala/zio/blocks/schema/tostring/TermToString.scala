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
        assertTrue(term.toString == "fieldName: scala.String")
      },
      test("renders Int term") {
        val term = Term("age", Reflect.int[Binding])
        assertTrue(term.toString == "age: scala.Int")
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
            |  x: scala.Int
            |  y: scala.Int
            |}""".stripMargin

        assertTrue(term.toString == expected)
      },

      test("renders term with sequence value") {
        val listReflect = Reflect.list[Binding, Int](Reflect.int[Binding])
        val term        = Term("items", listReflect)
        assertTrue(term.toString == "items: sequence List[scala.Int]")
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
            |    x: scala.Int
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
          """maybeInt: variant scala.Option[scala.Int] {
            |  | None
            |  | Some(value: scala.Int)
            |}""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("Map Term")(
      test("renders term with simple map value") {
        val mapReflect = Reflect.map[Binding, String, Int](Reflect.string[Binding], Reflect.int[Binding])
        val term       = Term("config", mapReflect)
        assertTrue(term.toString == "config: map Map[scala.String, scala.Int]")
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
            |  scala.String,
            |  record Point {
            |    x: scala.Int
            |    y: scala.Int
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
        assertTrue(term.toString == "userId: wrapper UserId(scala.String)")
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
            |    local: scala.String
            |    domain: scala.String
            |  }
            |)""".stripMargin

        assertTrue(term.toString == expected)
      }
    ),

    suite("All Primitive Terms")(
      test("renders Boolean term") {
        val term = Term("active", Reflect.boolean[Binding])
        assertTrue(term.toString == "active: scala.Boolean")
      },
      test("renders Byte term") {
        val term = Term("byteValue", Reflect.byte[Binding])
        assertTrue(term.toString == "byteValue: scala.Byte")
      },
      test("renders Short term") {
        val term = Term("shortValue", Reflect.short[Binding])
        assertTrue(term.toString == "shortValue: scala.Short")
      },
      test("renders Long term") {
        val term = Term("timestamp", Reflect.long[Binding])
        assertTrue(term.toString == "timestamp: scala.Long")
      },
      test("renders Float term") {
        val term = Term("floatValue", Reflect.float[Binding])
        assertTrue(term.toString == "floatValue: scala.Float")
      },
      test("renders Double term") {
        val term = Term("doubleValue", Reflect.double[Binding])
        assertTrue(term.toString == "doubleValue: scala.Double")
      },
      test("renders Char term") {
        val term = Term("charValue", Reflect.char[Binding])
        assertTrue(term.toString == "charValue: scala.Char")
      },
      test("renders BigInt term") {
        val term = Term("bigIntValue", Reflect.bigInt[Binding])
        assertTrue(term.toString == "bigIntValue: scala.BigInt")
      },
      test("renders BigDecimal term") {
        val term = Term("price", Reflect.bigDecimal[Binding])
        assertTrue(term.toString == "price: scala.BigDecimal")
      },
      test("renders Unit term") {
        val term = Term("unitValue", Reflect.unit[Binding])
        assertTrue(term.toString == "unitValue: scala.Unit")
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
            |        value: scala.Int
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
            |  data:   map Map[scala.String, sequence List[
            |    record InnerRecord {
            |      x: scala.Int
            |    }
            |  ]]
            |}""".stripMargin

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
            |  | Leaf(value: scala.Int)
            |  | Branch(
            |      value: scala.Int,
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

  sealed trait Tree
  case class Leaf(value: Int)                            extends Tree
  case class Branch(value: Int, left: Tree, right: Tree) extends Tree
}
