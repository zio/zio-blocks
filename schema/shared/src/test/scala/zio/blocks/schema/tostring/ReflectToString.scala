package zio.blocks.schema.tostring

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.test._

object ReflectSpec extends ZIOSpecDefault {

  def spec = suite("Reflect toString")(
    suite("Primitive types")(
      test("renders String") {
        val reflect = Reflect.string[Binding]
        assertTrue(reflect.toString == "String")
      },
      test("renders Int") {
        val reflect = Reflect.int[Binding]
        assertTrue(reflect.toString == "Int")
      },
      test("renders Boolean") {
        val reflect = Reflect.boolean[Binding]
        assertTrue(reflect.toString == "Boolean")
      },
      test("renders Long") {
        val reflect = Reflect.long[Binding]
        assertTrue(reflect.toString == "Long")
      },
      test("renders Double") {
        val reflect = Reflect.double[Binding]
        assertTrue(reflect.toString == "Double")
      },
      test("renders BigDecimal") {
        val reflect = Reflect.bigDecimal[Binding]
        assertTrue(reflect.toString == "BigDecimal")
      },
      test("renders java.time.Instant") {
        val reflect = Reflect.instant[Binding]
        assertTrue(reflect.toString == "java.time.Instant")
      },
      test("renders java.util.UUID") {
        val reflect = Reflect.uuid[Binding]
        assertTrue(reflect.toString == "java.util.UUID")
      }
    ),

    suite("Simple Record")(
      test("renders empty record") {
        val reflect = Reflect.Record[Binding, EmptyRecord](
          fields = Vector.empty,
          typeName = TypeName(Namespace(Nil), "EmptyRecord"),
          recordBinding = Binding.Record(
            constructor = new Constructor[EmptyRecord] {
              def usedRegisters                                    = RegisterOffset.Zero
              def construct(in: Registers, offset: RegisterOffset) = EmptyRecord()
            },
            deconstructor = new Deconstructor[EmptyRecord] {
              def usedRegisters                                                        = RegisterOffset.Zero
              def deconstruct(out: Registers, offset: RegisterOffset, in: EmptyRecord) = ()
            }
          )
        )
        assertTrue(reflect.toString == "record EmptyRecord {}")
      },
      test("renders record with single primitive field") {
        val reflect = Reflect.Record[Binding, Point1D](
          fields = Vector(
            Term("x", Reflect.int[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Point1D"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point1D] {
              def usedRegisters                                    = RegisterOffset(ints = 1)
              def construct(in: Registers, offset: RegisterOffset) = Point1D(in.getInt(offset))
            },
            deconstructor = new Deconstructor[Point1D] {
              def usedRegisters                                                    = RegisterOffset(ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point1D) = out.setInt(offset, in.x)
            }
          )
        )
        val expected =
          """record Point1D {
            |  x: Int
            |}""".stripMargin
        assertTrue(reflect.toString == expected)
      },
      test("renders record with multiple primitive fields") {
        val reflect = Reflect.Record[Binding, Point2D](
          fields = Vector(
            Term("x", Reflect.int[Binding]),
            Term("y", Reflect.int[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Point2D"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Point2D] {
              def usedRegisters                                    = RegisterOffset(ints = 2)
              def construct(in: Registers, offset: RegisterOffset) =
                Point2D(in.getInt(offset), in.getInt(offset + RegisterOffset(ints = 1)))
            },
            deconstructor = new Deconstructor[Point2D] {
              def usedRegisters                                                    = RegisterOffset(ints = 2)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Point2D) = {
                out.setInt(offset, in.x)
                out.setInt(offset + RegisterOffset(ints = 1), in.y)
              }
            }
          )
        )
        val expected =
          """record Point2D {
            |  x: Int
            |  y: Int
            |}""".stripMargin
        assertTrue(reflect.toString == expected)
      },
      test("renders record with mixed primitive types") {
        val reflect = Reflect.Record[Binding, Person](
          fields = Vector(
            Term("name", Reflect.string[Binding]),
            Term("age", Reflect.int[Binding]),
            Term("active", Reflect.boolean[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Person"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Person] {
              def usedRegisters                                    = RegisterOffset(objects = 1, ints = 1, booleans = 1)
              def construct(in: Registers, offset: RegisterOffset) =
                Person(
                  in.getObject(offset).asInstanceOf[String],
                  in.getInt(offset),
                  in.getBoolean(offset)
                )
            },
            deconstructor = new Deconstructor[Person] {
              def usedRegisters                                                   = RegisterOffset(objects = 1, ints = 1, booleans = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Person) = {
                out.setObject(offset, in.name)
                out.setInt(offset, in.age)
                out.setBoolean(offset, in.active)
              }
            }
          )
        )
        val expected =
          """record Person {
            |  name: String
            |  age: Int
            |  active: Boolean
            |}""".stripMargin
        assertTrue(reflect.toString == expected)
      }
    ),

    suite("Nested Record")(
      test("renders record with nested record field") {
        val addressReflect = Reflect.Record[Binding, Address](
          fields = Vector(
            Term("street", Reflect.string[Binding]),
            Term("city", Reflect.string[Binding]),
            Term("zip", Reflect.string[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Address"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Address] {
              def usedRegisters                                    = RegisterOffset(objects = 3)
              def construct(in: Registers, offset: RegisterOffset) =
                Address(
                  in.getObject(offset).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 2)).asInstanceOf[String]
                )
            },
            deconstructor = new Deconstructor[Address] {
              def usedRegisters                                                    = RegisterOffset(objects = 3)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Address) = {
                out.setObject(offset, in.street)
                out.setObject(offset + RegisterOffset(objects = 1), in.city)
                out.setObject(offset + RegisterOffset(objects = 2), in.zip)
              }
            }
          )
        )

        val personWithAddressReflect = Reflect.Record[Binding, PersonWithAddress](
          fields = Vector(
            Term("name", Reflect.string[Binding]),
            Term("age", Reflect.int[Binding]),
            Term("address", addressReflect)
          ),
          typeName = TypeName(Namespace(Nil), "PersonWithAddress"),
          recordBinding = Binding.Record(
            constructor = new Constructor[PersonWithAddress] {
              def usedRegisters                                    = RegisterOffset(objects = 2, ints = 1)
              def construct(in: Registers, offset: RegisterOffset) =
                PersonWithAddress(
                  in.getObject(offset).asInstanceOf[String],
                  in.getInt(offset),
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[Address]
                )
            },
            deconstructor = new Deconstructor[PersonWithAddress] {
              def usedRegisters                                                              = RegisterOffset(objects = 2, ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: PersonWithAddress) = {
                out.setObject(offset, in.name)
                out.setInt(offset, in.age)
                out.setObject(offset + RegisterOffset(objects = 1), in.address)
              }
            }
          )
        )

        val expected =
          """record PersonWithAddress {
            |  name: String
            |  age: Int
            |  address:   record Address {
            |    street: String
            |    city: String
            |    zip: String
            |  }
            |}""".stripMargin
        assertTrue(personWithAddressReflect.toString == expected)
      }
    ),

    suite("Simple Variant")(
      test("renders variant with enum-style cases (no fields)") {
        val trueReflect = Reflect.Record[Binding, BooleanEnum.True.type](
          fields = Vector.empty,
          typeName = TypeName(Namespace(Nil), "True"),
          recordBinding = Binding.Record(
            constructor = new Constructor[BooleanEnum.True.type] {
              def usedRegisters                                    = RegisterOffset.Zero
              def construct(in: Registers, offset: RegisterOffset) = BooleanEnum.True
            },
            deconstructor = new Deconstructor[BooleanEnum.True.type] {
              def usedRegisters                                                                  = RegisterOffset.Zero
              def deconstruct(out: Registers, offset: RegisterOffset, in: BooleanEnum.True.type) = ()
            }
          )
        )

        val falseReflect = Reflect.Record[Binding, BooleanEnum.False.type](
          fields = Vector.empty,
          typeName = TypeName(Namespace(Nil), "False"),
          recordBinding = Binding.Record(
            constructor = new Constructor[BooleanEnum.False.type] {
              def usedRegisters                                    = RegisterOffset.Zero
              def construct(in: Registers, offset: RegisterOffset) = BooleanEnum.False
            },
            deconstructor = new Deconstructor[BooleanEnum.False.type] {
              def usedRegisters                                                                   = RegisterOffset.Zero
              def deconstruct(out: Registers, offset: RegisterOffset, in: BooleanEnum.False.type) = ()
            }
          )
        )

        val reflect = Reflect.Variant[Binding, BooleanEnum](
          cases = Vector(
            Term("True", trueReflect),
            Term("False", falseReflect)
          ),
          typeName = TypeName(Namespace(Nil), "BooleanEnum"),
          variantBinding = Binding.Variant(
            discriminator = new Discriminator[BooleanEnum] {
              def discriminate(a: BooleanEnum) = a match {
                case BooleanEnum.True  => 0
                case BooleanEnum.False => 1
              }
            },
            matchers = Matchers(
              new Matcher[BooleanEnum.True.type] {
                def downcastOrNull(any: Any) = any match {
                  case BooleanEnum.True => BooleanEnum.True
                  case _                => null.asInstanceOf[BooleanEnum.True.type]
                }
              },
              new Matcher[BooleanEnum.False.type] {
                def downcastOrNull(any: Any) = any match {
                  case BooleanEnum.False => BooleanEnum.False
                  case _                 => null.asInstanceOf[BooleanEnum.False.type]
                }
              }
            )
          )
        )

        val expected =
          """variant BooleanEnum {
            |  | True
            |  | False
            |}""".stripMargin
        assertTrue(reflect.toString == expected)
      },
      test("renders Option[Int] variant") {
        val reflect  = Reflect.optionInt[Binding](Reflect.int[Binding])
        val expected =
          """variant Option[Int] {
            |  | None
            |  | Some(value: Int)
            |}""".stripMargin
        assertTrue(reflect.toString == expected)
      }
    ),

    suite("Complex Variant with Different Payloads")(
      test("renders PaymentMethod variant with mixed case structures") {
        // Case 1: Empty case
        val cashReflect = Reflect.Record[Binding, PaymentMethod](
          fields = Vector.empty,
          typeName = TypeName(Namespace(Nil), "Cash"),
          recordBinding = Binding.Record(
            constructor = new Constructor[PaymentMethod] {
              def usedRegisters                                    = RegisterOffset.Zero
              def construct(in: Registers, offset: RegisterOffset) = null.asInstanceOf[PaymentMethod]
            },
            deconstructor = new Deconstructor[PaymentMethod] {
              def usedRegisters                                                          = RegisterOffset.Zero
              def deconstruct(out: Registers, offset: RegisterOffset, in: PaymentMethod) = ()
            }
          )
        )

        // Case 2: Multiple fields
        val creditCardReflect = Reflect.Record[Binding, PaymentMethod](
          fields = Vector(
            Term("number", Reflect.string[Binding]),
            Term("expiry", Reflect.string[Binding]),
            Term("cvv", Reflect.string[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "CreditCard"),
          recordBinding = Binding.Record(
            constructor = new Constructor[PaymentMethod] {
              def usedRegisters                                    = RegisterOffset(objects = 3)
              def construct(in: Registers, offset: RegisterOffset) = null.asInstanceOf[PaymentMethod]
            },
            deconstructor = new Deconstructor[PaymentMethod] {
              def usedRegisters                                                          = RegisterOffset(objects = 3)
              def deconstruct(out: Registers, offset: RegisterOffset, in: PaymentMethod) = ()
            }
          )
        )

        // Case 3: Nested record in payload
        val bankAccountReflect = Reflect.Record[Binding, BankAccount](
          fields = Vector(
            Term("routing", Reflect.string[Binding]),
            Term("number", Reflect.string[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "BankAccount"),
          recordBinding = Binding.Record(
            constructor = new Constructor[BankAccount] {
              def usedRegisters                                    = RegisterOffset(objects = 2)
              def construct(in: Registers, offset: RegisterOffset) = BankAccount("", "")
            },
            deconstructor = new Deconstructor[BankAccount] {
              def usedRegisters                                                        = RegisterOffset(objects = 2)
              def deconstruct(out: Registers, offset: RegisterOffset, in: BankAccount) = ()
            }
          )
        )

        val bankTransferReflect = Reflect.Record[Binding, PaymentMethod](
          fields = Vector(
            Term("account", bankAccountReflect)
          ),
          typeName = TypeName(Namespace(Nil), "BankTransfer"),
          recordBinding = Binding.Record(
            constructor = new Constructor[PaymentMethod] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = null.asInstanceOf[PaymentMethod]
            },
            deconstructor = new Deconstructor[PaymentMethod] {
              def usedRegisters                                                          = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: PaymentMethod) = ()
            }
          )
        )

        val reflect = Reflect.Variant[Binding, PaymentMethod](
          cases = Vector(
            Term("Cash", cashReflect),
            Term("CreditCard", creditCardReflect),
            Term("BankTransfer", bankTransferReflect)
          ),
          typeName = TypeName(Namespace(Nil), "PaymentMethod"),
          variantBinding = Binding.Variant(
            discriminator = new Discriminator[PaymentMethod] {
              def discriminate(a: PaymentMethod) = 0
            },
            matchers = Matchers(Vector())
          )
        )

        val expected =
          """variant PaymentMethod {
            |  | Cash
            |  | CreditCard(
            |      number: String,
            |      expiry: String,
            |      cvv: String
            |    )
            |  | BankTransfer(
            |      account:       record BankAccount {
            |        routing: String
            |        number: String
            |      }
            |    )
            |}""".stripMargin
        assertTrue(reflect.toString == expected)
      }
    ),

    suite("More Primitive types")(
      test("renders Byte") {
        val reflect = Reflect.byte[Binding]
        assertTrue(reflect.toString == "Byte")
      },
      test("renders Short") {
        val reflect = Reflect.short[Binding]
        assertTrue(reflect.toString == "Short")
      },
      test("renders Float") {
        val reflect = Reflect.float[Binding]
        assertTrue(reflect.toString == "Float")
      },
      test("renders Char") {
        val reflect = Reflect.char[Binding]
        assertTrue(reflect.toString == "Char")
      },
      test("renders Unit") {
        val reflect = Reflect.unit[Binding]
        assertTrue(reflect.toString == "Unit")
      },
      test("renders Duration") {
        val reflect = Reflect.duration[Binding]
        assertTrue(reflect.toString == "java.time.Duration")
      },
      test("renders Period") {
        val reflect = Reflect.period[Binding]
        assertTrue(reflect.toString == "java.time.Period")
      },
      test("renders LocalDate") {
        val reflect = Reflect.localDate[Binding]
        assertTrue(reflect.toString == "java.time.LocalDate")
      }
    ),

    suite("Sequence types")(
      test("renders sequence with primitive element") {
        val reflect = Reflect.list[Binding, Int](Reflect.int[Binding])
        assertTrue(reflect.toString == "sequence List[Int]")
      },
      test("renders sequence with complex element") {
        val itemReflect = Reflect.Record[Binding, OrderItem](
          fields = Vector(
            Term("product", Reflect.string[Binding]),
            Term("quantity", Reflect.int[Binding]),
            Term("price", Reflect.bigDecimal[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "OrderItem"),
          recordBinding = Binding.Record(
            constructor = new Constructor[OrderItem] {
              def usedRegisters                                    = RegisterOffset(objects = 2, ints = 1)
              def construct(in: Registers, offset: RegisterOffset) =
                OrderItem(
                  in.getObject(offset).asInstanceOf[String],
                  in.getInt(offset),
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[BigDecimal]
                )
            },
            deconstructor = new Deconstructor[OrderItem] {
              def usedRegisters                                                      = RegisterOffset(objects = 2, ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: OrderItem) = {
                out.setObject(offset, in.product)
                out.setInt(offset, in.quantity)
                out.setObject(offset + RegisterOffset(objects = 1), in.price)
              }
            }
          )
        )

        val reflect  = Reflect.vector[Binding, OrderItem](itemReflect)
        val expected =
          """sequence Vector[
            |  record OrderItem {
            |    product: String
            |    quantity: Int
            |    price: BigDecimal
            |  }
            |]""".stripMargin
        assertTrue(reflect.toString == expected)
      }
    ),

    suite("Map types")(
      test("renders map with primitive types") {
        val reflect = Reflect.map[Binding, String, Int](Reflect.string[Binding], Reflect.int[Binding])
        assertTrue(reflect.toString == "map Map[String, Int]")
      },
      test("renders map with complex value type") {
        val configReflect = Reflect.Record[Binding, Config](
          fields = Vector(
            Term("value", Reflect.string[Binding]),
            Term("enabled", Reflect.boolean[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Config"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Config] {
              def usedRegisters                                    = RegisterOffset(objects = 1, booleans = 1)
              def construct(in: Registers, offset: RegisterOffset) =
                Config(in.getObject(offset).asInstanceOf[String], in.getBoolean(offset))
            },
            deconstructor = new Deconstructor[Config] {
              def usedRegisters                                                   = RegisterOffset(objects = 1, booleans = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Config) = {
                out.setObject(offset, in.value)
                out.setBoolean(offset, in.enabled)
              }
            }
          )
        )

        val reflect  = Reflect.map[Binding, String, Config](Reflect.string[Binding], configReflect)
        val expected =
          """map Map[
            |  String,
            |  record Config {
            |    value: String
            |    enabled: Boolean
            |  }
            |]""".stripMargin
        assertTrue(reflect.toString == expected)
      }
    ),

    suite("Wrapper types")(
      test("renders wrapper with primitive underlying type") {
        val reflect = Reflect.Wrapper[Binding, UserId, String](
          wrapped = Reflect.string[Binding],
          typeName = TypeName(Namespace(Nil), "UserId"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[UserId, String](
            wrap = s => Right(UserId(s)),
            unwrap = _.value
          )
        )
        assertTrue(reflect.toString == "wrapper UserId(String)")
      },
      test("renders wrapper with complex underlying type") {
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

        val reflect = Reflect.Wrapper[Binding, ValidatedEmail, EmailParts](
          wrapped = emailPartsReflect,
          typeName = TypeName(Namespace(Nil), "ValidatedEmail"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[ValidatedEmail, EmailParts](
            wrap = p => Right(ValidatedEmail(p)),
            unwrap = _.parts
          )
        )

        val expected =
          """wrapper ValidatedEmail(
            |  record EmailParts {
            |    local: String
            |    domain: String
            |  }
            |)""".stripMargin
        assertTrue(reflect.toString == expected)
      },
      test("renders nested wrappers") {
        val uuidReflect = Reflect.Wrapper[Binding, UUID, String](
          wrapped = Reflect.string[Binding],
          typeName = TypeName(Namespace(Nil), "UUID"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[UUID, String](
            wrap = s => Right(UUID(s)),
            unwrap = _.value
          )
        )

        val orderIdReflect = Reflect.Wrapper[Binding, OrderId, UUID](
          wrapped = uuidReflect,
          typeName = TypeName(Namespace(Nil), "OrderId"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[OrderId, UUID](
            wrap = u => Right(OrderId(u)),
            unwrap = _.uuid
          )
        )

        assertTrue(orderIdReflect.toString == "wrapper OrderId(wrapper UUID(String))")
      }
    ),

    suite("Deferred types (recursive)")(
      test("renders simple recursive type") {
        lazy val treeReflect: Reflect.Record[Binding, Tree] = Reflect.Record[Binding, Tree](
          fields = Vector(
            Term("value", Reflect.int[Binding]),
            Term("children", Reflect.Deferred(() => Reflect.list[Binding, Tree](treeReflect)))
          ),
          typeName = TypeName(Namespace(Nil), "Tree"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Tree] {
              def usedRegisters                                    = RegisterOffset(ints = 1, objects = 1)
              def construct(in: Registers, offset: RegisterOffset) =
                Tree(in.getInt(offset), in.getObject(offset).asInstanceOf[List[Tree]])
            },
            deconstructor = new Deconstructor[Tree] {
              def usedRegisters                                                 = RegisterOffset(ints = 1, objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Tree) = {
                out.setInt(offset, in.value)
                out.setObject(offset, in.children)
              }
            }
          )
        )

        val expected =
          """record Tree {
            |  value: Int
            |  children:   sequence List[
            |    deferred => Tree
            |  ]
            |}""".stripMargin
        assertTrue(treeReflect.toString == expected)
      },
      test("renders mutually recursive types") {
        lazy val nodeReflect: Reflect.Record[Binding, Node] = Reflect.Record[Binding, Node](
          fields = Vector(
            Term("id", Reflect.int[Binding]),
            Term("edges", Reflect.Deferred(() => Reflect.list[Binding, Edge](edgeReflect)))
          ),
          typeName = TypeName(Namespace(Nil), "Node"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Node] {
              def usedRegisters                                    = RegisterOffset(ints = 1, objects = 1)
              def construct(in: Registers, offset: RegisterOffset) =
                Node(in.getInt(offset), in.getObject(offset).asInstanceOf[List[Edge]])
            },
            deconstructor = new Deconstructor[Node] {
              def usedRegisters                                                 = RegisterOffset(ints = 1, objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Node) = {
                out.setInt(offset, in.id)
                out.setObject(offset, in.edges)
              }
            }
          )
        )

        lazy val edgeReflect: Reflect.Record[Binding, Edge] = Reflect.Record[Binding, Edge](
          fields = Vector(
            Term("label", Reflect.string[Binding]),
            Term("target", Reflect.Deferred(() => nodeReflect))
          ),
          typeName = TypeName(Namespace(Nil), "Edge"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Edge] {
              def usedRegisters                                    = RegisterOffset(objects = 2)
              def construct(in: Registers, offset: RegisterOffset) =
                Edge(
                  in.getObject(offset).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[Node]
                )
            },
            deconstructor = new Deconstructor[Edge] {
              def usedRegisters                                                 = RegisterOffset(objects = 2)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Edge) = {
                out.setObject(offset, in.label)
                out.setObject(offset + RegisterOffset(objects = 1), in.target)
              }
            }
          )
        )

        val expected =
          """record Node {
            |  id: Int
            |  edges:   sequence List[
            |    record Edge {
            |      label: String
            |      target: deferred => Node
            |    }
            |  ]
            |}""".stripMargin
        assertTrue(nodeReflect.toString == expected)
      }
    ),

    suite("Complex nested example")(
      test("renders full complex schema") {
        // This test demonstrates a real-world schema with multiple levels of nesting

        val addressReflect = Reflect.Record[Binding, Address](
          fields = Vector(
            Term("street", Reflect.string[Binding]),
            Term("city", Reflect.string[Binding]),
            Term("country", Reflect.string[Binding])
          ),
          typeName = TypeName(Namespace(Nil), "Address"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Address] {
              def usedRegisters                                    = RegisterOffset(objects = 3)
              def construct(in: Registers, offset: RegisterOffset) =
                Address(
                  in.getObject(offset).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 2)).asInstanceOf[String]
                )
            },
            deconstructor = new Deconstructor[Address] {
              def usedRegisters                                                    = RegisterOffset(objects = 3)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Address) = {
                out.setObject(offset, in.street)
                out.setObject(offset + RegisterOffset(objects = 1), in.city)
                out.setObject(offset + RegisterOffset(objects = 2), in.zip)
              }
            }
          )
        )

        val emailReflect = Reflect.Wrapper[Binding, Email, String](
          wrapped = Reflect.string[Binding],
          typeName = TypeName(Namespace(Nil), "Email"),
          wrapperPrimitiveType = None,
          wrapperBinding = Binding.Wrapper[Email, String](
            wrap = s => Right(Email(s)),
            unwrap = _.value
          )
        )

        val customerReflect = Reflect.Record[Binding, Customer](
          fields = Vector(
            Term("name", Reflect.string[Binding]),
            Term("email", emailReflect),
            Term("address", addressReflect)
          ),
          typeName = TypeName(Namespace(Nil), "Customer"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Customer] {
              def usedRegisters                                    = RegisterOffset(objects = 3)
              def construct(in: Registers, offset: RegisterOffset) =
                Customer(
                  in.getObject(offset).asInstanceOf[String],
                  in.getObject(offset + RegisterOffset(objects = 1)).asInstanceOf[Email],
                  in.getObject(offset + RegisterOffset(objects = 2)).asInstanceOf[Address]
                )
            },
            deconstructor = new Deconstructor[Customer] {
              def usedRegisters                                                     = RegisterOffset(objects = 3)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Customer) = {
                out.setObject(offset, in.name)
                out.setObject(offset + RegisterOffset(objects = 1), in.email.asInstanceOf[AnyRef])
                out.setObject(offset + RegisterOffset(objects = 2), in.address)
              }
            }
          )
        )

        val expected =
          """record Customer {
            |  name: String
            |  email: wrapper Email(String)
            |  address:   record Address {
            |    street: String
            |    city: String
            |    country: String
            |  }
            |}""".stripMargin
        assertTrue(customerReflect.toString == expected)
      }
    ),

    suite("Dynamic type")(
      test("renders dynamic as its type name") {
        val reflect = Reflect.dynamic[Binding]
        assertTrue(reflect.toString == "DynamicValue")
      }
    ),

    suite("Edge cases")(
      test("handles deeply nested records") {
        // Level 3
        val level3 = Reflect.Record[Binding, Level3](
          fields = Vector(Term("value", Reflect.int[Binding])),
          typeName = TypeName(Namespace(Nil), "Level3"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Level3] {
              def usedRegisters                                    = RegisterOffset(ints = 1)
              def construct(in: Registers, offset: RegisterOffset) = Level3(in.getInt(offset))
            },
            deconstructor = new Deconstructor[Level3] {
              def usedRegisters                                                   = RegisterOffset(ints = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Level3) = out.setInt(offset, in.value)
            }
          )
        )

        // Level 2
        val level2 = Reflect.Record[Binding, Level2](
          fields = Vector(Term("nested", level3)),
          typeName = TypeName(Namespace(Nil), "Level2"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Level2] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = Level2(in.getObject(offset).asInstanceOf[Level3])
            },
            deconstructor = new Deconstructor[Level2] {
              def usedRegisters                                                   = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Level2) = out.setObject(offset, in.nested)
            }
          )
        )

        // Level 1
        val level1 = Reflect.Record[Binding, Level1](
          fields = Vector(Term("nested", level2)),
          typeName = TypeName(Namespace(Nil), "Level1"),
          recordBinding = Binding.Record(
            constructor = new Constructor[Level1] {
              def usedRegisters                                    = RegisterOffset(objects = 1)
              def construct(in: Registers, offset: RegisterOffset) = Level1(in.getObject(offset).asInstanceOf[Level2])
            },
            deconstructor = new Deconstructor[Level1] {
              def usedRegisters                                                   = RegisterOffset(objects = 1)
              def deconstruct(out: Registers, offset: RegisterOffset, in: Level1) = out.setObject(offset, in.nested)
            }
          )
        )

        val expected =
          """record Level1 {
            |  nested:   record Level2 {
            |    nested:   record Level3 {
            |      value: Int
            |    }
            |  }
            |}""".stripMargin
        assertTrue(level1.toString == expected)
      }
    )
  )

  // Test data types
  case class EmptyRecord()
  case class Point1D(x: Int)
  case class Point2D(x: Int, y: Int)
  case class Person(name: String, age: Int, active: Boolean)
  case class Address(street: String, city: String, zip: String)
  case class PersonWithAddress(name: String, age: Int, address: Address)

  sealed trait PaymentMethod
  case class BankAccount(routing: String, number: String)

  sealed trait BooleanEnum
  object BooleanEnum {
    case object True  extends BooleanEnum
    case object False extends BooleanEnum
  }

  case class OrderItem(product: String, quantity: Int, price: BigDecimal)
  case class Config(value: String, enabled: Boolean)

  case class UserId(value: String) extends AnyVal
  case class EmailParts(local: String, domain: String)
  case class ValidatedEmail(parts: EmailParts)
  case class UUID(value: String) extends AnyVal
  case class OrderId(uuid: UUID)

  case class Tree(value: Int, children: List[Tree])

  case class Node(id: Int, edges: List[Edge])
  case class Edge(label: String, target: Node)

  case class Email(value: String) extends AnyVal
  case class Customer(name: String, email: Email, address: Address)

  case class Level3(value: Int)
  case class Level2(nested: Level3)
  case class Level1(nested: Level2)
}
