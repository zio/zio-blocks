package zio.blocks.schema.tostring

import zio.blocks.schema._

import zio.test.{Spec, TestEnvironment, assertTrue}

object TypeNameSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("TypeName toString")(
    suite("renders simple types") {
      test("renders scala.Int") {
        assertTrue(TypeName.int.toString == "scala.Int")
      } +
        test("renders scala.String") {
          assertTrue(TypeName.string.toString == "scala.String")
        } +
        test("renders scala.Boolean") {
          assertTrue(TypeName.boolean.toString == "scala.Boolean")
        } +
        test("renders scala.Unit") {
          assertTrue(TypeName.unit.toString == "scala.Unit")
        } +
        test("renders scala.Byte") {
          assertTrue(TypeName.byte.toString == "scala.Byte")
        } +
        test("renders scala.Short") {
          assertTrue(TypeName.short.toString == "scala.Short")
        } +
        test("renders scala.Long") {
          assertTrue(TypeName.long.toString == "scala.Long")
        } +
        test("renders scala.Float") {
          assertTrue(TypeName.float.toString == "scala.Float")
        } +
        test("renders scala.Double") {
          assertTrue(TypeName.double.toString == "scala.Double")
        } +
        test("renders scala.Char") {
          assertTrue(TypeName.char.toString == "scala.Char")
        } +
        test("renders scala.BigInt") {
          assertTrue(TypeName.bigInt.toString == "scala.BigInt")
        } +
        test("renders scala.BigDecimal") {
          assertTrue(TypeName.bigDecimal.toString == "scala.BigDecimal")
        }
    },

    suite("renders java.time types") {
      test("renders java.time.Instant") {
        assertTrue(TypeName.instant.toString == "java.time.Instant")
      } +
        test("renders java.time.LocalDate") {
          assertTrue(TypeName.localDate.toString == "java.time.LocalDate")
        } +
        test("renders java.time.LocalDateTime") {
          assertTrue(TypeName.localDateTime.toString == "java.time.LocalDateTime")
        } +
        test("renders java.time.LocalTime") {
          assertTrue(TypeName.localTime.toString == "java.time.LocalTime")
        } +
        test("renders java.time.Duration") {
          assertTrue(TypeName.duration.toString == "java.time.Duration")
        } +
        test("renders java.time.Period") {
          assertTrue(TypeName.period.toString == "java.time.Period")
        } +
        test("renders java.time.ZonedDateTime") {
          assertTrue(TypeName.zonedDateTime.toString == "java.time.ZonedDateTime")
        } +
        test("renders java.time.OffsetDateTime") {
          assertTrue(TypeName.offsetDateTime.toString == "java.time.OffsetDateTime")
        } +
        test("renders java.time.OffsetTime") {
          assertTrue(TypeName.offsetTime.toString == "java.time.OffsetTime")
        } +
        test("renders java.time.ZoneId") {
          assertTrue(TypeName.zoneId.toString == "java.time.ZoneId")
        } +
        test("renders java.time.ZoneOffset") {
          assertTrue(TypeName.zoneOffset.toString == "java.time.ZoneOffset")
        } +
        test("renders java.time.Year") {
          assertTrue(TypeName.year.toString == "java.time.Year")
        } +
        test("renders java.time.YearMonth") {
          assertTrue(TypeName.yearMonth.toString == "java.time.YearMonth")
        } +
        test("renders java.time.MonthDay") {
          assertTrue(TypeName.monthDay.toString == "java.time.MonthDay")
        } +
        test("renders java.time.Month") {
          assertTrue(TypeName.month.toString == "java.time.Month")
        } +
        test("renders java.time.DayOfWeek") {
          assertTrue(TypeName.dayOfWeek.toString == "java.time.DayOfWeek")
        }
    },

    suite("renders java.util types") {
      test("renders java.util.UUID") {
        assertTrue(TypeName.uuid.toString == "java.util.UUID")
      } +
        test("renders java.util.Currency") {
          assertTrue(TypeName.currency.toString == "java.util.Currency")
        }
    },

    suite("renders simple parameterized types") {
      test("renders scala.Option[scala.String]") {
        assertTrue(TypeName.option(TypeName.string).toString == "scala.Option[scala.String]")
      } +
        test("renders scala.Option[scala.Int]") {
          assertTrue(TypeName.option(TypeName.int).toString == "scala.Option[scala.Int]")
        } +
        test("renders scala.Some[scala.String]") {
          assertTrue(TypeName.some(TypeName.string).toString == "scala.Some[scala.String]")
        } +
        test("renders scala.collection.immutable.List[scala.Int]") {
          assertTrue(TypeName.list(TypeName.int).toString == "scala.collection.immutable.List[scala.Int]")
        } +
        test("renders scala.collection.immutable.Vector[scala.String]") {
          assertTrue(TypeName.vector(TypeName.string).toString == "scala.collection.immutable.Vector[scala.String]")
        } +
        test("renders scala.collection.immutable.Set[scala.Boolean]") {
          assertTrue(TypeName.set(TypeName.boolean).toString == "scala.collection.immutable.Set[scala.Boolean]")
        } +
        test("renders scala.collection.immutable.Seq[scala.Long]") {
          assertTrue(TypeName.seq(TypeName.long).toString == "scala.collection.immutable.Seq[scala.Long]")
        } +
        test("renders scala.collection.immutable.IndexedSeq[scala.Byte]") {
          assertTrue(TypeName.indexedSeq(TypeName.byte).toString == "scala.collection.immutable.IndexedSeq[scala.Byte]")
        } +
        test("renders scala.collection.immutable.ArraySeq[scala.Char]") {
          assertTrue(TypeName.arraySeq(TypeName.char).toString == "scala.collection.immutable.ArraySeq[scala.Char]")
        }
    },

    suite("renders two-parameter types") {
      test("renders scala.collection.immutable.Map[scala.String, scala.Int]") {
        assertTrue(
          TypeName.map(TypeName.string, TypeName.int).toString ==
            "scala.collection.immutable.Map[scala.String, scala.Int]"
        )
      } +
        test("renders scala.collection.immutable.Map[scala.Int, scala.String]") {
          assertTrue(
            TypeName.map(TypeName.int, TypeName.string).toString ==
              "scala.collection.immutable.Map[scala.Int, scala.String]"
          )
        } +
        test("renders scala.collection.immutable.Map[scala.Long, scala.Boolean]") {
          assertTrue(
            TypeName.map(TypeName.long, TypeName.boolean).toString ==
              "scala.collection.immutable.Map[scala.Long, scala.Boolean]"
          )
        }
    },

    suite("renders nested parameterized types") {
      test("renders scala.Option[scala.collection.immutable.List[scala.Int]]") {
        assertTrue(
          TypeName.option(TypeName.list(TypeName.int)).toString ==
            "scala.Option[scala.collection.immutable.List[scala.Int]]"
        )
      } +
        test("renders scala.collection.immutable.List[scala.Option[scala.String]]") {
          assertTrue(
            TypeName.list(TypeName.option(TypeName.string)).toString ==
              "scala.collection.immutable.List[scala.Option[scala.String]]"
          )
        } +
        test("renders scala.collection.immutable.Vector[scala.collection.immutable.List[scala.Int]]") {
          assertTrue(
            TypeName.vector(TypeName.list(TypeName.int)).toString ==
              "scala.collection.immutable.Vector[scala.collection.immutable.List[scala.Int]]"
          )
        } +
        test("renders scala.collection.immutable.Map[scala.String, scala.collection.immutable.List[scala.Int]]") {
          assertTrue(
            TypeName.map(TypeName.string, TypeName.list(TypeName.int)).toString ==
              "scala.collection.immutable.Map[scala.String, scala.collection.immutable.List[scala.Int]]"
          )
        } +
        test(
          "renders scala.collection.immutable.Map[scala.Option[scala.String], scala.collection.immutable.Vector[scala.Int]]"
        ) {
          assertTrue(
            TypeName.map(TypeName.option(TypeName.string), TypeName.vector(TypeName.int)).toString ==
              "scala.collection.immutable.Map[scala.Option[scala.String], scala.collection.immutable.Vector[scala.Int]]"
          )
        }
    },

    suite("renders deeply nested types") {
      test("renders scala.Option[scala.Option[scala.Option[scala.Int]]]") {
        assertTrue(
          TypeName.option(TypeName.option(TypeName.option(TypeName.int))).toString ==
            "scala.Option[scala.Option[scala.Option[scala.Int]]]"
        )
      } +
        test(
          "renders scala.collection.immutable.List[scala.collection.immutable.Map[scala.String, scala.collection.immutable.Vector[scala.Int]]]"
        ) {
          assertTrue(
            TypeName.list(TypeName.map(TypeName.string, TypeName.vector(TypeName.int))).toString ==
              "scala.collection.immutable.List[scala.collection.immutable.Map[scala.String, scala.collection.immutable.Vector[scala.Int]]]"
          )
        } +
        test(
          "renders scala.collection.immutable.Map[scala.collection.immutable.List[scala.String], scala.collection.immutable.Set[scala.Int]]"
        ) {
          assertTrue(
            TypeName.map(TypeName.list(TypeName.string), TypeName.set(TypeName.int)).toString ==
              "scala.collection.immutable.Map[scala.collection.immutable.List[scala.String], scala.collection.immutable.Set[scala.Int]]"
          )
        }
    },

    suite("renders custom types") {
      test("renders custom type with namespace") {
        val customType = TypeName[Any](Namespace(List("com", "example")), "Person")
        assertTrue(customType.toString == "com.example.Person")
      } +
        test("renders custom parameterized type") {
          val customType = TypeName[Any](
            Namespace(List("com", "example")),
            "Container",
            Seq(TypeName.string)
          )
          assertTrue(customType.toString == "com.example.Container[scala.String]")
        } +
        test("renders custom type with multiple parameters") {
          val customType = TypeName[Any](
            Namespace(List("com", "example")),
            "Pair",
            Seq(TypeName.string, TypeName.int)
          )
          assertTrue(customType.toString == "com.example.Pair[scala.String, scala.Int]")
        } +
        test("renders type with empty namespace") {
          val customType = TypeName[Any](Namespace(Nil), "SimpleType")
          assertTrue(customType.toString == "SimpleType")
        }
    },

    suite("renders zio-blocks types") {
      test("renders zio.blocks.schema.DynamicValue") {
        assertTrue(TypeName.dynamicValue.toString == "zio.blocks.schema.DynamicValue")
      }
    },

    suite("edge cases") {
      test("renders scala.None") {
        assertTrue(TypeName.none.toString == "scala.None")
      } +
        test("renders type with complex java.time parameter") {
          assertTrue(
            TypeName.option(TypeName.instant).toString ==
              "scala.Option[java.time.Instant]"
          )
        } +
        test("renders type with BigDecimal parameter") {
          assertTrue(
            TypeName.list(TypeName.bigDecimal).toString ==
              "scala.collection.immutable.List[scala.BigDecimal]"
          )
        } +
        test("renders Map with UUID keys") {
          assertTrue(
            TypeName.map(TypeName.uuid, TypeName.string).toString ==
              "scala.collection.immutable.Map[java.util.UUID, scala.String]"
          )
        }
    },

    suite("beauty and readability") {
      test("output is valid Scala type syntax") {
        assertTrue(
          TypeName.map(TypeName.string, TypeName.list(TypeName.int)).toString ==
            "scala.collection.immutable.Map[scala.String, scala.collection.immutable.List[scala.Int]]"
        )
      } +
        test("output is copy-pasteable") {
          assertTrue(
            TypeName.option(TypeName.string).toString ==
              "scala.Option[scala.String]"
          )
        } +
        test("complex nested types are readable") {
          val complexType = TypeName.map(
            TypeName.option(TypeName.string),
            TypeName.list(TypeName.vector(TypeName.int))
          )
          val typeString = complexType.toString
          // Balanced brackets ensure well-formed type syntax
          val openCount  = typeString.count(_ == '[')
          val closeCount = typeString.count(_ == ']')
          assertTrue(
            typeString == "scala.collection.immutable.Map[scala.Option[scala.String], scala.collection.immutable.List[scala.collection.immutable.Vector[scala.Int]]]" &&
              openCount == closeCount &&
              openCount == 4
          )
        }
    }
  )
}
