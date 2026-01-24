package zio.blocks.schema.tostring

import zio.blocks.schema._

import zio.test.{Spec, TestEnvironment, assertTrue}

object DynamicValueToStringSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValue toString (EJSON)")(
    suite("renders primitives") {
      test("renders string") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        assertTrue(value.toString == "\"hello\"")
      } +
        test("renders integer") {
          val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
          assertTrue(value.toString == "42")
        } +
        test("renders double") {
          val value = DynamicValue.Primitive(PrimitiveValue.Double(3.14))
          assertTrue(value.toString == "3.14")
        } +
        test("renders boolean true") {
          val value = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
          assertTrue(value.toString == "true")
        } +
        test("renders boolean false") {
          val value = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
          assertTrue(value.toString == "false")
        } +
        test("renders null (Unit)") {
          val value = DynamicValue.Primitive(PrimitiveValue.Unit)
          assertTrue(value.toString == "null")
        } +
        test("renders byte") {
          val value = DynamicValue.Primitive(PrimitiveValue.Byte(127))
          assertTrue(value.toString == "127")
        } +
        test("renders short") {
          val value = DynamicValue.Primitive(PrimitiveValue.Short(1000))
          assertTrue(value.toString == "1000")
        } +
        test("renders long") {
          val value = DynamicValue.Primitive(PrimitiveValue.Long(9223372036854775807L))
          assertTrue(value.toString == "9223372036854775807")
        } +
        test("renders float") {
          val value = DynamicValue.Primitive(PrimitiveValue.Float(2.5f))
          assertTrue(value.toString == "2.5")
        } +
        test("renders char") {
          val value = DynamicValue.Primitive(PrimitiveValue.Char('A'))
          assertTrue(value.toString == "\"A\"")
        } +
        test("renders BigInt") {
          val value = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt("12345678901234567890")))
          assertTrue(value.toString == "12345678901234567890")
        } +
        test("renders BigDecimal") {
          val value = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456")))
          assertTrue(value.toString == "123.456")
        }
    },

    suite("renders typed primitives with @ metadata") {
      test("renders Instant with type metadata") {
        val instant = java.time.Instant.ofEpochMilli(1705312800000L)
        val value   = DynamicValue.Primitive(PrimitiveValue.Instant(instant))
        assertTrue(value.toString == "1705312800000 @ {type: \"instant\"}")
      } +
        test("renders Period with type metadata") {
          val period = java.time.Period.parse("P1Y2M3D")
          val value  = DynamicValue.Primitive(PrimitiveValue.Period(period))
          assertTrue(value.toString == "\"P1Y2M3D\" @ {type: \"period\"}")
        } +
        test("renders Duration with type metadata") {
          val duration = java.time.Duration.parse("PT1H30M")
          val value    = DynamicValue.Primitive(PrimitiveValue.Duration(duration))
          assertTrue(value.toString == "\"PT1H30M\" @ {type: \"duration\"}")
        } +
        test("renders LocalDate with type metadata") {
          val localDate = java.time.LocalDate.parse("2024-01-15")
          val value     = DynamicValue.Primitive(PrimitiveValue.LocalDate(localDate))
          assertTrue(value.toString == "\"2024-01-15\" @ {type: \"localDate\"}")
        }
    },

    suite("renders records with unquoted keys") {
      test("renders empty record") {
        val value = DynamicValue.Record(Vector.empty)
        assertTrue(value.toString == "{}")
      } +
        test("renders simple record with one field") {
          val value = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )
          val expected =
            """{
              |  name: "John"
              |}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders record with multiple fields") {
          val value = DynamicValue.Record(
            Vector(
              "name"   -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"    -> DynamicValue.Primitive(PrimitiveValue.Int(30)),
              "active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            )
          )
          val expected =
            """{
              |  name: "John",
              |  age: 30,
              |  active: true
              |}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders record with special characters in string values") {
          val value = DynamicValue.Record(
            Vector(
              "message" -> DynamicValue.Primitive(PrimitiveValue.String("Hello \"World\""))
            )
          )
          val expected =
            """{
              |  message: "Hello \"World\""
              |}""".stripMargin
          assertTrue(value.toString == expected)
        }
    },

    suite("renders sequences") {
      test("renders empty sequence") {
        val value = DynamicValue.Sequence(Vector.empty)
        assertTrue(value.toString == "[]")
      } +
        test("renders sequence with one element") {
          val value = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
          assertTrue(value.toString == "[42]")
        } +
        test("renders sequence with multiple elements") {
          val value = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2)),
              DynamicValue.Primitive(PrimitiveValue.Int(3))
            )
          )
          val expected =
            """[
              |  1,
              |  2,
              |  3
              |]""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders sequence of strings") {
          val value = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("apple")),
              DynamicValue.Primitive(PrimitiveValue.String("banana")),
              DynamicValue.Primitive(PrimitiveValue.String("cherry"))
            )
          )
          val expected =
            """[
              |  "apple",
              |  "banana",
              |  "cherry"
              |]""".stripMargin
          assertTrue(value.toString == expected)
        }
    },

    suite("renders maps with quoted string keys") {
      test("renders empty map") {
        val value = DynamicValue.Map(Vector.empty)
        assertTrue(value.toString == "{}")
      } +
        test("renders map with one string key") {
          val value = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("name")) ->
                DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )
          val expected =
            """{
              |  "name": "John"
              |}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders map with multiple string keys") {
          val value = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("name")) ->
                DynamicValue.Primitive(PrimitiveValue.String("John")),
              DynamicValue.Primitive(PrimitiveValue.String("age-group")) ->
                DynamicValue.Primitive(PrimitiveValue.String("30-40"))
            )
          )
          val expected =
            """{
              |  "name": "John",
              |  "age-group": "30-40"
              |}""".stripMargin
          assertTrue(value.toString == expected)
        }
    },

    suite("renders maps with non-string keys") {
      test("renders map with boolean keys") {
        val value = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.Boolean(true)) ->
              DynamicValue.Primitive(PrimitiveValue.String("yes")),
            DynamicValue.Primitive(PrimitiveValue.Boolean(false)) ->
              DynamicValue.Primitive(PrimitiveValue.String("no"))
          )
        )
        val expected =
          """{
            |  true: "yes",
            |  false: "no"
            |}""".stripMargin
        assertTrue(value.toString == expected)
      } +
        test("renders map with integer keys") {
          val value = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)) ->
                DynamicValue.Primitive(PrimitiveValue.String("one")),
              DynamicValue.Primitive(PrimitiveValue.Int(2)) ->
                DynamicValue.Primitive(PrimitiveValue.String("two")),
              DynamicValue.Primitive(PrimitiveValue.Int(3)) ->
                DynamicValue.Primitive(PrimitiveValue.String("three"))
            )
          )
          val expected =
            """{
              |  1: "one",
              |  2: "two",
              |  3: "three"
              |}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders map with mixed key types") {
          val value = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Boolean(true)) ->
                DynamicValue.Primitive(PrimitiveValue.String("bool")),
              DynamicValue.Primitive(PrimitiveValue.Int(42)) ->
                DynamicValue.Primitive(PrimitiveValue.String("answer"))
            )
          )
          val expected =
            """{
              |  true: "bool",
              |  42: "answer"
              |}""".stripMargin
          assertTrue(value.toString == expected)
        }
    },

    suite("renders variants with @ metadata") {
      test("renders variant with empty record payload") {
        val value = DynamicValue.Variant("None", DynamicValue.Record(Vector.empty))
        assertTrue(value.toString == "{} @ {tag: \"None\"}")
      } +
        test("renders variant with single field payload") {
          val payload = DynamicValue.Record(
            Vector(
              "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
          val value    = DynamicValue.Variant("Some", payload)
          val expected =
            """{
              |  value: 42
              |} @ {tag: "Some"}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders variant with complex payload") {
          val payload = DynamicValue.Record(
            Vector(
              "ccnum"  -> DynamicValue.Primitive(PrimitiveValue.Long(4111111111111111L)),
              "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25"))
            )
          )
          val value    = DynamicValue.Variant("CreditCard", payload)
          val expected =
            """{
              |  ccnum: 4111111111111111,
              |  expiry: "12/25"
              |} @ {tag: "CreditCard"}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders variant for Either Left") {
          val payload = DynamicValue.Record(
            Vector(
              "value" -> DynamicValue.Primitive(PrimitiveValue.String("error message"))
            )
          )
          val value    = DynamicValue.Variant("Left", payload)
          val expected =
            """{
              |  value: "error message"
              |} @ {tag: "Left"}""".stripMargin
          assertTrue(value.toString == expected)
        }
    },

    suite("renders nested structures") {
      test("renders nested record in record") {
        val address = DynamicValue.Record(
          Vector(
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
            "city"   -> DynamicValue.Primitive(PrimitiveValue.String("New York"))
          )
        )
        val person = DynamicValue.Record(
          Vector(
            "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "address" -> address
          )
        )
        val expected =
          """{
            |  name: "Alice",
            |  address: {
            |    street: "123 Main St",
            |    city: "New York"
            |  }
            |}""".stripMargin
        assertTrue(person.toString == expected)
      } +
        test("renders sequence of records") {
          val item1 = DynamicValue.Record(
            Vector(
              "name"  -> DynamicValue.Primitive(PrimitiveValue.String("apple")),
              "price" -> DynamicValue.Primitive(PrimitiveValue.Int(100))
            )
          )
          val item2 = DynamicValue.Record(
            Vector(
              "name"  -> DynamicValue.Primitive(PrimitiveValue.String("banana")),
              "price" -> DynamicValue.Primitive(PrimitiveValue.Int(80))
            )
          )
          val value    = DynamicValue.Sequence(Vector(item1, item2))
          val expected =
            """[
              |  {
              |    name: "apple",
              |    price: 100
              |  },
              |  {
              |    name: "banana",
              |    price: 80
              |  }
              |]""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders variant nested in record") {
          val paymentVariant = DynamicValue.Variant(
            "CreditCard",
            DynamicValue.Record(
              Vector(
                "ccnum" -> DynamicValue.Primitive(PrimitiveValue.Long(4111111111111111L))
              )
            )
          )
          val user = DynamicValue.Record(
            Vector(
              "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
              "payment" -> paymentVariant
            )
          )
          val expected =
            """{
              |  name: "Alice",
              |  payment: {
              |    ccnum: 4111111111111111
              |  } @ {tag: "CreditCard"}
              |}""".stripMargin
          assertTrue(user.toString == expected)
        } +
        test("renders complex nested structure with multiple types") {
          val instant = java.time.Instant.ofEpochMilli(1705312800000L)
          val scores  = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(95)),
              DynamicValue.Primitive(PrimitiveValue.Int(87)),
              DynamicValue.Primitive(PrimitiveValue.Int(92))
            )
          )
          val metadata = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("verified")) ->
                DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
              DynamicValue.Primitive(PrimitiveValue.Int(1)) ->
                DynamicValue.Primitive(PrimitiveValue.String("first")),
              DynamicValue.Primitive(PrimitiveValue.Int(2)) ->
                DynamicValue.Primitive(PrimitiveValue.String("second"))
            )
          )
          val payment = DynamicValue.Variant(
            "CreditCard",
            DynamicValue.Record(
              Vector(
                "ccnum" -> DynamicValue.Primitive(PrimitiveValue.Long(4111111111111111L))
              )
            )
          )
          val user = DynamicValue.Record(
            Vector(
              "name"      -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
              "payment"   -> payment,
              "scores"    -> scores,
              "metadata"  -> metadata,
              "createdAt" -> DynamicValue.Primitive(PrimitiveValue.Instant(instant))
            )
          )
          val value = DynamicValue.Record(
            Vector(
              "user" -> user
            )
          )
          val expected =
            """{
              |  user: {
              |    name: "Alice",
              |    payment: {
              |      ccnum: 4111111111111111
              |    } @ {tag: "CreditCard"},
              |    scores: [
              |      95,
              |      87,
              |      92
              |    ],
              |    metadata: {
              |      "verified": true,
              |      1: "first",
              |      2: "second"
              |    },
              |    createdAt: 1705312800000 @ {type: "instant"}
              |  }
              |}""".stripMargin
          assertTrue(value.toString == expected)
        }
    },

    suite("renders edge cases") {
      test("renders string with escape characters") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("Line1\nLine2\tTab"))
        assertTrue(value.toString == "\"Line1\\nLine2\\tTab\"")
      } +
        test("renders string with quotes") {
          val value = DynamicValue.Primitive(PrimitiveValue.String("He said \"Hello\""))
          assertTrue(value.toString == "\"He said \\\"Hello\\\"\"")
        } +
        test("renders string with backslash") {
          val value = DynamicValue.Primitive(PrimitiveValue.String("C:\\path\\file.txt"))
          assertTrue(value.toString == "\"C:\\\\path\\\\file.txt\"")
        } +
        test("renders empty string") {
          val value = DynamicValue.Primitive(PrimitiveValue.String(""))
          assertTrue(value.toString == "\"\"")
        } +
        test("renders negative numbers") {
          val value = DynamicValue.Record(
            Vector(
              "negInt"    -> DynamicValue.Primitive(PrimitiveValue.Int(-42)),
              "negDouble" -> DynamicValue.Primitive(PrimitiveValue.Double(-3.14))
            )
          )
          val expected =
            """{
              |  negInt: -42,
              |  negDouble: -3.14
              |}""".stripMargin
          assertTrue(value.toString == expected)
        } +
        test("renders zero") {
          val value = DynamicValue.Primitive(PrimitiveValue.Int(0))
          assertTrue(value.toString == "0")
        } +
        test("renders deeply nested sequence") {
          val innerSeq = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
          val middleSeq = DynamicValue.Sequence(Vector(innerSeq))
          val outerSeq  = DynamicValue.Sequence(Vector(middleSeq))
          val expected  =
            """[
              |  [
              |    [
              |      1,
              |      2
              |    ]
              |  ]
              |]""".stripMargin
          assertTrue(outerSeq.toString == expected)
        }
    },

    suite("EJSON format properties") {
      test("distinguishes records from maps (unquoted vs quoted keys)") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val map = DynamicValue.Map(
          Vector(
            DynamicValue.Primitive(PrimitiveValue.String("name")) ->
              DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val recordStr = record.toString
        val mapStr    = map.toString

        // Record has unquoted key: { name: "John" }
        // Map has quoted key: { "name": "John" }
        assertTrue(
          recordStr.contains("name:") && !recordStr.contains("\"name\":") &&
            mapStr.contains("\"name\":") && !mapStr.contains("name:")
        )
      } +
        test("uses @ metadata for variants") {
          val variant = DynamicValue.Variant(
            "Some",
            DynamicValue.Record(
              Vector(
                "value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
          val str = variant.toString
          assertTrue(str.contains("@ {tag: \"Some\"}"))
        } +
        test("uses @ metadata for typed primitives") {
          val instant = java.time.Instant.ofEpochMilli(1705312800000L)
          val value   = DynamicValue.Primitive(PrimitiveValue.Instant(instant))
          val str     = value.toString
          assertTrue(str.contains("@ {type: \"instant\"}"))
        } +
        test("proper indentation for nested structures") {
          val nested = DynamicValue.Record(
            Vector(
              "outer" -> DynamicValue.Record(
                Vector(
                  "inner" -> DynamicValue.Primitive(PrimitiveValue.String("value"))
                )
              )
            )
          )
          val str = nested.toString
          // Should have proper indentation levels
          assertTrue(
            str.contains("{\n  outer: {\n    inner: \"value\"\n  }\n}")
          )
        }
    },

    suite("java.time types") {
      test("renders DayOfWeek") {
        val value = DynamicValue.Primitive(PrimitiveValue.DayOfWeek(java.time.DayOfWeek.MONDAY))
        assertTrue(value.toString == "\"MONDAY\"")
      } +
        test("renders Month") {
          val value = DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.JANUARY))
          assertTrue(value.toString == "\"JANUARY\"")
        } +
        test("renders LocalTime") {
          val time  = java.time.LocalTime.parse("10:15:30")
          val value = DynamicValue.Primitive(PrimitiveValue.LocalTime(time))
          assertTrue(value.toString == "\"10:15:30\"")
        } +
        test("renders LocalDateTime") {
          val dateTime = java.time.LocalDateTime.parse("2024-01-15T10:15:30")
          val value    = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(dateTime))
          assertTrue(value.toString == "\"2024-01-15T10:15:30\"")
        } +
        test("renders Year") {
          val year  = java.time.Year.of(2024)
          val value = DynamicValue.Primitive(PrimitiveValue.Year(year))
          assertTrue(value.toString == "2024")
        } +
        test("renders YearMonth") {
          val yearMonth = java.time.YearMonth.parse("2024-01")
          val value     = DynamicValue.Primitive(PrimitiveValue.YearMonth(yearMonth))
          assertTrue(value.toString == "\"2024-01\"")
        } +
        test("renders MonthDay") {
          val monthDay = java.time.MonthDay.parse("--01-15")
          val value    = DynamicValue.Primitive(PrimitiveValue.MonthDay(monthDay))
          assertTrue(value.toString == "\"--01-15\"")
        } +
        test("renders OffsetDateTime") {
          val offsetDateTime = java.time.OffsetDateTime.parse("2024-01-15T10:15:30+01:00")
          val value          = DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(offsetDateTime))
          assertTrue(value.toString == "\"2024-01-15T10:15:30+01:00\"")
        } +
        test("renders OffsetTime") {
          val offsetTime = java.time.OffsetTime.parse("10:15:30+01:00")
          val value      = DynamicValue.Primitive(PrimitiveValue.OffsetTime(offsetTime))
          assertTrue(value.toString == "\"10:15:30+01:00\"")
        } +
        test("renders ZonedDateTime") {
          val zonedDateTime = java.time.ZonedDateTime.parse("2024-01-15T10:15:30+01:00[Europe/Paris]")
          val value         = DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(zonedDateTime))
          assertTrue(value.toString == "\"2024-01-15T10:15:30+01:00[Europe/Paris]\"")
        } +
        test("renders ZoneId") {
          val zoneId = java.time.ZoneId.of("America/New_York")
          val value  = DynamicValue.Primitive(PrimitiveValue.ZoneId(zoneId))
          assertTrue(value.toString == "\"America/New_York\"")
        } +
        test("renders ZoneOffset") {
          val zoneOffset = java.time.ZoneOffset.ofHours(5)
          val value      = DynamicValue.Primitive(PrimitiveValue.ZoneOffset(zoneOffset))
          assertTrue(value.toString == "\"+05:00\"")
        }
    },

    suite("java.util types") {
      test("renders UUID") {
        val uuid  = java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val value = DynamicValue.Primitive(PrimitiveValue.UUID(uuid))
        assertTrue(value.toString == "\"123e4567-e89b-12d3-a456-426614174000\"")
      } +
        test("renders Currency") {
          val currency = java.util.Currency.getInstance("USD")
          val value    = DynamicValue.Primitive(PrimitiveValue.Currency(currency))
          assertTrue(value.toString == "\"USD\"")
        }
    },

    suite("Metadata Composition") {
      test("renders variant with typed primitive inside") {
        val instant = java.time.Instant.ofEpochMilli(1705312800000L)
        val variant = DynamicValue.Variant(
          "Created",
          DynamicValue.Record(
            Vector("at" -> DynamicValue.Primitive(PrimitiveValue.Instant(instant)))
          )
        )
        val expected =
          """{
            |  at: 1705312800000 @ {type: "instant"}
            |} @ {tag: "Created"}""".stripMargin
        assertTrue(variant.toString == expected)
      } +
        test("renders nested variants") {
          val innerVariant = DynamicValue.Variant(
            "Some",
            DynamicValue.Record(
              Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
            )
          )
          val outerVariant = DynamicValue.Variant(
            "Right",
            DynamicValue.Record(
              Vector("value" -> innerVariant)
            )
          )
          val expected =
            """{
              |  value: {
              |    value: 42
              |  } @ {tag: "Some"}
              |} @ {tag: "Right"}""".stripMargin
          assertTrue(outerVariant.toString == expected)
        }
    },

    suite("Complex Real-World Scenarios") {
      test("renders payment method variant with multiple case types") {
        val cashVariant = DynamicValue.Variant("Cash", DynamicValue.Record(Vector.empty))

        val creditCardVariant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("4111111111111111")),
              "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25")),
              "cvv"    -> DynamicValue.Primitive(PrimitiveValue.String("123"))
            )
          )
        )

        val bankAccount = DynamicValue.Record(
          Vector(
            "routing" -> DynamicValue.Primitive(PrimitiveValue.String("021000021")),
            "number"  -> DynamicValue.Primitive(PrimitiveValue.String("1234567890"))
          )
        )
        val bankTransferVariant = DynamicValue.Variant(
          "BankTransfer",
          DynamicValue.Record(Vector("account" -> bankAccount))
        )

        assertTrue(
          cashVariant.toString == "{} @ {tag: \"Cash\"}",
          creditCardVariant.toString ==
            """{
              |  number: "4111111111111111",
              |  expiry: "12/25",
              |  cvv: "123"
              |} @ {tag: "CreditCard"}""".stripMargin,
          bankTransferVariant.toString ==
            """{
              |  account: {
              |    routing: "021000021",
              |    number: "1234567890"
              |  }
              |} @ {tag: "BankTransfer"}""".stripMargin
        )
      } +
        test("renders deeply nested structure with all types") {
          val address = DynamicValue.Record(
            Vector(
              "street"  -> DynamicValue.Primitive(PrimitiveValue.String("123 Main St")),
              "city"    -> DynamicValue.Primitive(PrimitiveValue.String("New York")),
              "country" -> DynamicValue.Primitive(PrimitiveValue.String("USA"))
            )
          )

          val orderItem = DynamicValue.Record(
            Vector(
              "product"  -> DynamicValue.Primitive(PrimitiveValue.String("Widget")),
              "quantity" -> DynamicValue.Primitive(PrimitiveValue.Int(5)),
              "price"    -> DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("29.99")))
            )
          )

          val items = DynamicValue.Sequence(Vector(orderItem))

          val status = DynamicValue.Variant(
            "Shipped",
            DynamicValue.Record(
              Vector("trackingNumber" -> DynamicValue.Primitive(PrimitiveValue.String("TRACK123")))
            )
          )

          val metadata = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("source")) ->
                DynamicValue.Primitive(PrimitiveValue.String("web")),
              DynamicValue.Primitive(PrimitiveValue.String("priority")) ->
                DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )

          val order = DynamicValue.Record(
            Vector(
              "id"       -> DynamicValue.Primitive(PrimitiveValue.String("ORD-001")),
              "address"  -> address,
              "items"    -> items,
              "status"   -> status,
              "metadata" -> metadata
            )
          )

          val str = order.toString
          assertTrue(
            str.contains("id: \"ORD-001\""),
            str.contains("street: \"123 Main St\""),
            str.contains("product: \"Widget\""),
            str.contains("} @ {tag: \"Shipped\"}"),
            str.contains("\"source\": \"web\"")
          )
        }
    }
  )
}
