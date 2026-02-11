package zio.blocks.schema

import zio.test._

object SchemaParserSpec extends SchemaBaseSpec {
  import SchemaParser._
  import SchemaParser.ParseError._

  def spec = suite("SchemaParserSpec")(
    suite("nominal types")(
      test("parse simple nominal type") {
        assertTrue(parse("Person") == Right(SchemaRepr.Nominal("Person")))
      },
      test("parse nominal type with lowercase start") {
        assertTrue(parse("person") == Right(SchemaRepr.Nominal("person")))
      },
      test("parse nominal type with numbers") {
        assertTrue(parse("Person2") == Right(SchemaRepr.Nominal("Person2")))
      },
      test("parse nominal type with underscores") {
        assertTrue(parse("_Person_Name") == Right(SchemaRepr.Nominal("_Person_Name")))
      },
      test("parse nominal type starting with underscore") {
        assertTrue(parse("_private") == Right(SchemaRepr.Nominal("_private")))
      },
      test("parse nominal type with whitespace") {
        assertTrue(parse("  Person  ") == Right(SchemaRepr.Nominal("Person")))
      }
    ),
    suite("primitive types")(
      test("parse string") {
        assertTrue(parse("string") == Right(SchemaRepr.Primitive("string")))
      },
      test("parse int") {
        assertTrue(parse("int") == Right(SchemaRepr.Primitive("int")))
      },
      test("parse long") {
        assertTrue(parse("long") == Right(SchemaRepr.Primitive("long")))
      },
      test("parse short") {
        assertTrue(parse("short") == Right(SchemaRepr.Primitive("short")))
      },
      test("parse byte") {
        assertTrue(parse("byte") == Right(SchemaRepr.Primitive("byte")))
      },
      test("parse float") {
        assertTrue(parse("float") == Right(SchemaRepr.Primitive("float")))
      },
      test("parse double") {
        assertTrue(parse("double") == Right(SchemaRepr.Primitive("double")))
      },
      test("parse boolean") {
        assertTrue(parse("boolean") == Right(SchemaRepr.Primitive("boolean")))
      },
      test("parse char") {
        assertTrue(parse("char") == Right(SchemaRepr.Primitive("char")))
      },
      test("parse unit") {
        assertTrue(parse("unit") == Right(SchemaRepr.Primitive("unit")))
      },
      test("parse bigint") {
        assertTrue(parse("bigint") == Right(SchemaRepr.Primitive("bigint")))
      },
      test("parse bigdecimal") {
        assertTrue(parse("bigdecimal") == Right(SchemaRepr.Primitive("bigdecimal")))
      },
      test("parse uuid") {
        assertTrue(parse("uuid") == Right(SchemaRepr.Primitive("uuid")))
      },
      test("parse instant") {
        assertTrue(parse("instant") == Right(SchemaRepr.Primitive("instant")))
      },
      test("parse localdate") {
        assertTrue(parse("localdate") == Right(SchemaRepr.Primitive("localdate")))
      },
      test("parse localtime") {
        assertTrue(parse("localtime") == Right(SchemaRepr.Primitive("localtime")))
      },
      test("parse localdatetime") {
        assertTrue(parse("localdatetime") == Right(SchemaRepr.Primitive("localdatetime")))
      },
      test("parse offsettime") {
        assertTrue(parse("offsettime") == Right(SchemaRepr.Primitive("offsettime")))
      },
      test("parse offsetdatetime") {
        assertTrue(parse("offsetdatetime") == Right(SchemaRepr.Primitive("offsetdatetime")))
      },
      test("parse zoneddatetime") {
        assertTrue(parse("zoneddatetime") == Right(SchemaRepr.Primitive("zoneddatetime")))
      },
      test("parse dayofweek") {
        assertTrue(parse("dayofweek") == Right(SchemaRepr.Primitive("dayofweek")))
      },
      test("parse month") {
        assertTrue(parse("month") == Right(SchemaRepr.Primitive("month")))
      },
      test("parse monthday") {
        assertTrue(parse("monthday") == Right(SchemaRepr.Primitive("monthday")))
      },
      test("parse year") {
        assertTrue(parse("year") == Right(SchemaRepr.Primitive("year")))
      },
      test("parse yearmonth") {
        assertTrue(parse("yearmonth") == Right(SchemaRepr.Primitive("yearmonth")))
      },
      test("parse period") {
        assertTrue(parse("period") == Right(SchemaRepr.Primitive("period")))
      },
      test("parse duration") {
        assertTrue(parse("duration") == Right(SchemaRepr.Primitive("duration")))
      },
      test("parse zoneoffset") {
        assertTrue(parse("zoneoffset") == Right(SchemaRepr.Primitive("zoneoffset")))
      },
      test("parse zoneid") {
        assertTrue(parse("zoneid") == Right(SchemaRepr.Primitive("zoneid")))
      }
    ),
    suite("wildcard")(
      test("parse underscore as wildcard") {
        assertTrue(parse("_") == Right(SchemaRepr.Wildcard))
      },
      test("parse wildcard with whitespace") {
        assertTrue(parse("  _  ") == Right(SchemaRepr.Wildcard))
      }
    ),
    suite("record types")(
      test("parse record with single field") {
        assertTrue(
          parse("record { name: string }") == Right(
            SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
          )
        )
      },
      test("parse record with multiple fields") {
        assertTrue(
          parse("record { name: string, age: int }") == Right(
            SchemaRepr.Record(
              Vector(
                "name" -> SchemaRepr.Primitive("string"),
                "age"  -> SchemaRepr.Primitive("int")
              )
            )
          )
        )
      },
      test("parse record with three fields") {
        assertTrue(
          parse("record { name: string, age: int, active: boolean }") == Right(
            SchemaRepr.Record(
              Vector(
                "name"   -> SchemaRepr.Primitive("string"),
                "age"    -> SchemaRepr.Primitive("int"),
                "active" -> SchemaRepr.Primitive("boolean")
              )
            )
          )
        )
      },
      test("parse record with nominal field type") {
        assertTrue(
          parse("record { person: Person }") == Right(
            SchemaRepr.Record(Vector("person" -> SchemaRepr.Nominal("Person")))
          )
        )
      },
      test("parse record with minimal whitespace") {
        assertTrue(
          parse("record{name:string}") == Right(
            SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string")))
          )
        )
      },
      test("parse record with extra whitespace") {
        assertTrue(
          parse("record  {  name  :  string  ,  age  :  int  }") == Right(
            SchemaRepr.Record(
              Vector(
                "name" -> SchemaRepr.Primitive("string"),
                "age"  -> SchemaRepr.Primitive("int")
              )
            )
          )
        )
      },
      test("parse nested record") {
        assertTrue(
          parse("record { address: record { street: string } }") == Right(
            SchemaRepr.Record(
              Vector(
                "address" -> SchemaRepr.Record(Vector("street" -> SchemaRepr.Primitive("string")))
              )
            )
          )
        )
      }
    ),
    suite("variant types")(
      test("parse variant with single case") {
        assertTrue(
          parse("variant { Left: int }") == Right(
            SchemaRepr.Variant(Vector("Left" -> SchemaRepr.Primitive("int")))
          )
        )
      },
      test("parse variant with multiple cases") {
        assertTrue(
          parse("variant { Left: int, Right: string }") == Right(
            SchemaRepr.Variant(
              Vector(
                "Left"  -> SchemaRepr.Primitive("int"),
                "Right" -> SchemaRepr.Primitive("string")
              )
            )
          )
        )
      },
      test("parse variant with three cases") {
        assertTrue(
          parse("variant { A: int, B: string, C: boolean }") == Right(
            SchemaRepr.Variant(
              Vector(
                "A" -> SchemaRepr.Primitive("int"),
                "B" -> SchemaRepr.Primitive("string"),
                "C" -> SchemaRepr.Primitive("boolean")
              )
            )
          )
        )
      },
      test("parse variant with nominal payload") {
        assertTrue(
          parse("variant { Success: Person }") == Right(
            SchemaRepr.Variant(Vector("Success" -> SchemaRepr.Nominal("Person")))
          )
        )
      }
    ),
    suite("sequence types")(
      test("parse list with primitive element") {
        assertTrue(
          parse("list(string)") == Right(SchemaRepr.Sequence(SchemaRepr.Primitive("string")))
        )
      },
      test("parse list with nominal element") {
        assertTrue(
          parse("list(Person)") == Right(SchemaRepr.Sequence(SchemaRepr.Nominal("Person")))
        )
      },
      test("parse set with primitive element") {
        assertTrue(
          parse("set(int)") == Right(SchemaRepr.Sequence(SchemaRepr.Primitive("int")))
        )
      },
      test("parse vector with primitive element") {
        assertTrue(
          parse("vector(double)") == Right(SchemaRepr.Sequence(SchemaRepr.Primitive("double")))
        )
      },
      test("parse nested list") {
        assertTrue(
          parse("list(list(int))") == Right(
            SchemaRepr.Sequence(SchemaRepr.Sequence(SchemaRepr.Primitive("int")))
          )
        )
      },
      test("parse list with whitespace") {
        assertTrue(
          parse("list( string )") == Right(SchemaRepr.Sequence(SchemaRepr.Primitive("string")))
        )
      }
    ),
    suite("map types")(
      test("parse map with primitive types") {
        assertTrue(
          parse("map(string, int)") == Right(
            SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
          )
        )
      },
      test("parse map with nominal value") {
        assertTrue(
          parse("map(string, Person)") == Right(
            SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Nominal("Person"))
          )
        )
      },
      test("parse map with complex value") {
        assertTrue(
          parse("map(string, record { x: int })") == Right(
            SchemaRepr.Map(
              SchemaRepr.Primitive("string"),
              SchemaRepr.Record(Vector("x" -> SchemaRepr.Primitive("int")))
            )
          )
        )
      },
      test("parse map with whitespace") {
        assertTrue(
          parse("map( string , int )") == Right(
            SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
          )
        )
      },
      test("parse nested map") {
        assertTrue(
          parse("map(string, map(int, boolean))") == Right(
            SchemaRepr.Map(
              SchemaRepr.Primitive("string"),
              SchemaRepr.Map(SchemaRepr.Primitive("int"), SchemaRepr.Primitive("boolean"))
            )
          )
        )
      }
    ),
    suite("optional types")(
      test("parse option with primitive") {
        assertTrue(
          parse("option(string)") == Right(SchemaRepr.Optional(SchemaRepr.Primitive("string")))
        )
      },
      test("parse option with nominal") {
        assertTrue(
          parse("option(Person)") == Right(SchemaRepr.Optional(SchemaRepr.Nominal("Person")))
        )
      },
      test("parse option with complex type") {
        assertTrue(
          parse("option(record { name: string })") == Right(
            SchemaRepr.Optional(SchemaRepr.Record(Vector("name" -> SchemaRepr.Primitive("string"))))
          )
        )
      },
      test("parse nested option") {
        assertTrue(
          parse("option(option(int))") == Right(
            SchemaRepr.Optional(SchemaRepr.Optional(SchemaRepr.Primitive("int")))
          )
        )
      }
    ),
    suite("complex nested types")(
      test("record with list field") {
        assertTrue(
          parse("record { items: list(Person) }") == Right(
            SchemaRepr.Record(
              Vector("items" -> SchemaRepr.Sequence(SchemaRepr.Nominal("Person")))
            )
          )
        )
      },
      test("record with optional field") {
        assertTrue(
          parse("record { email: option(string) }") == Right(
            SchemaRepr.Record(
              Vector("email" -> SchemaRepr.Optional(SchemaRepr.Primitive("string")))
            )
          )
        )
      },
      test("record with map field") {
        assertTrue(
          parse("record { scores: map(string, int) }") == Right(
            SchemaRepr.Record(
              Vector(
                "scores" -> SchemaRepr.Map(
                  SchemaRepr.Primitive("string"),
                  SchemaRepr.Primitive("int")
                )
              )
            )
          )
        )
      },
      test("variant with complex payloads") {
        assertTrue(
          parse("variant { Success: record { value: int }, Error: string }") == Right(
            SchemaRepr.Variant(
              Vector(
                "Success" -> SchemaRepr.Record(Vector("value" -> SchemaRepr.Primitive("int"))),
                "Error"   -> SchemaRepr.Primitive("string")
              )
            )
          )
        )
      },
      test("list of records") {
        assertTrue(
          parse("list(record { name: string, age: int })") == Right(
            SchemaRepr.Sequence(
              SchemaRepr.Record(
                Vector(
                  "name" -> SchemaRepr.Primitive("string"),
                  "age"  -> SchemaRepr.Primitive("int")
                )
              )
            )
          )
        )
      },
      test("map with list values") {
        assertTrue(
          parse("map(string, list(int))") == Right(
            SchemaRepr.Map(
              SchemaRepr.Primitive("string"),
              SchemaRepr.Sequence(SchemaRepr.Primitive("int"))
            )
          )
        )
      },
      test("deeply nested structure") {
        assertTrue(
          parse("record { data: option(list(map(string, Person))) }") == Right(
            SchemaRepr.Record(
              Vector(
                "data" -> SchemaRepr.Optional(
                  SchemaRepr.Sequence(
                    SchemaRepr.Map(
                      SchemaRepr.Primitive("string"),
                      SchemaRepr.Nominal("Person")
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("wildcard in record field") {
        assertTrue(
          parse("record { any: _ }") == Right(
            SchemaRepr.Record(Vector("any" -> SchemaRepr.Wildcard))
          )
        )
      },
      test("wildcard in list") {
        assertTrue(
          parse("list(_)") == Right(SchemaRepr.Sequence(SchemaRepr.Wildcard))
        )
      },
      test("wildcard in map value") {
        assertTrue(
          parse("map(string, _)") == Right(
            SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Wildcard)
          )
        )
      }
    ),
    suite("UnexpectedChar errors")(
      test("invalid character at start") {
        val result = parse("@Person")
        assertTrue(result match {
          case Left(UnexpectedChar('@', 0, _)) => true
          case _                               => false
        })
      },
      test("invalid character in record") {
        val result = parse("record { name @ string }")
        assertTrue(result match {
          case Left(UnexpectedChar('@', 14, _)) => true
          case _                                => false
        })
      },
      test("missing colon in record field") {
        val result = parse("record { name string }")
        assertTrue(result match {
          case Left(UnexpectedChar('s', 14, _)) => true
          case _                                => false
        })
      },
      test("missing comma in record") {
        val result = parse("record { name: string age: int }")
        assertTrue(result match {
          case Left(UnexpectedChar('a', 22, _)) => true
          case _                                => false
        })
      },
      test("missing opening brace for record") {
        val result = parse("record name: string }")
        assertTrue(result match {
          case Left(UnexpectedChar('n', 7, _)) => true
          case _                               => false
        })
      },
      test("missing opening paren for list") {
        val result = parse("list string)")
        assertTrue(result match {
          case Left(UnexpectedChar('s', 5, _)) => true
          case _                               => false
        })
      },
      test("missing closing paren for list") {
        val result = parse("list(string")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("missing comma in map") {
        val result = parse("map(string int)")
        assertTrue(result match {
          case Left(UnexpectedChar('i', 11, _)) => true
          case _                                => false
        })
      },
      test("extra content after schema") {
        val result = parse("Person extra")
        assertTrue(result match {
          case Left(UnexpectedChar('e', 7, _)) => true
          case _                               => false
        })
      }
    ),
    suite("EmptyRecord errors")(
      test("empty record braces") {
        val result = parse("record { }")
        assertTrue(result match {
          case Left(EmptyRecord(7)) => true
          case _                    => false
        })
      },
      test("empty record minimal") {
        val result = parse("record{}")
        assertTrue(result match {
          case Left(EmptyRecord(6)) => true
          case _                    => false
        })
      }
    ),
    suite("EmptyVariant errors")(
      test("empty variant braces") {
        val result = parse("variant { }")
        assertTrue(result match {
          case Left(EmptyVariant(8)) => true
          case _                     => false
        })
      },
      test("empty variant minimal") {
        val result = parse("variant{}")
        assertTrue(result match {
          case Left(EmptyVariant(7)) => true
          case _                     => false
        })
      }
    ),
    suite("InvalidIdentifier errors")(
      test("digit at start") {
        val result = parse("123Person")
        assertTrue(result match {
          case Left(UnexpectedChar('1', 0, _)) => true
          case _                               => false
        })
      },
      test("empty identifier in record field") {
        val result = parse("record { : string }")
        assertTrue(result match {
          case Left(InvalidIdentifier(9)) => true
          case _                          => false
        })
      }
    ),
    suite("UnexpectedEnd errors")(
      test("empty input") {
        val result = parse("")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("only whitespace") {
        val result = parse("   ")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("unclosed record brace") {
        val result = parse("record { name: string")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("unclosed list paren") {
        val result = parse("list(string")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("unclosed map paren") {
        val result = parse("map(string, int")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("record with missing field type") {
        val result = parse("record { name: }")
        assertTrue(result match {
          case Left(UnexpectedChar('}', _, _)) => true
          case _                               => false
        })
      }
    ),
    suite("error messages")(
      test("UnexpectedChar message") {
        val err = UnexpectedChar('x', 5, "a digit")
        assertTrue(err.message == "Unexpected character 'x' at position 5. Expected a digit")
      },
      test("InvalidIdentifier message") {
        val err = InvalidIdentifier(10)
        assertTrue(err.message == "Invalid identifier at position 10")
      },
      test("UnexpectedEnd message") {
        val err = UnexpectedEnd("closing brace")
        assertTrue(err.message == "Unexpected end of input. Expected closing brace" && err.position == -1)
      },
      test("EmptyRecord message") {
        val err = EmptyRecord(5)
        assertTrue(err.message == "Empty record at position 5. Records must have at least one field")
      },
      test("EmptyVariant message") {
        val err = EmptyVariant(8)
        assertTrue(err.message == "Empty variant at position 8. Variants must have at least one case")
      },
      test("InvalidSyntax message") {
        val err = InvalidSyntax("Something went wrong", 15)
        assertTrue(err.message == "Something went wrong at position 15")
      }
    ),
    suite("record UnexpectedEnd/UnexpectedChar errors")(
      test("record with nothing after — missing opening brace") {
        val result = parse("record")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("record field name then EOF — missing colon") {
        val result = parse("record { name")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      }
    ),
    suite("variant UnexpectedEnd/UnexpectedChar errors")(
      test("variant with nothing after — missing opening brace") {
        val result = parse("variant")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("variant wrong char after keyword") {
        val result = parse("variant X")
        assertTrue(result match {
          case Left(UnexpectedChar('X', 8, _)) => true
          case _                               => false
        })
      },
      test("variant missing comma between cases") {
        val result = parse("variant { A: int B: string }")
        assertTrue(result match {
          case Left(UnexpectedChar('B', 17, _)) => true
          case _                                => false
        })
      },
      test("variant unclosed brace") {
        val result = parse("variant { A: int")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      }
    ),
    suite("list UnexpectedEnd/UnexpectedChar errors")(
      test("list with nothing after — missing opening paren") {
        val result = parse("list")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("list wrong closing delimiter") {
        val result = parse("list(string}")
        assertTrue(result match {
          case Left(UnexpectedChar('}', _, _)) => true
          case _                               => false
        })
      }
    ),
    suite("map UnexpectedEnd/UnexpectedChar errors")(
      test("map with nothing after — missing opening paren") {
        val result = parse("map")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("map wrong char after keyword") {
        val result = parse("map X")
        assertTrue(result match {
          case Left(UnexpectedChar('X', 4, _)) => true
          case _                               => false
        })
      },
      test("map missing comma after key") {
        val result = parse("map(string")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("map wrong closing delimiter") {
        val result = parse("map(string, int}")
        assertTrue(result match {
          case Left(UnexpectedChar('}', _, _)) => true
          case _                               => false
        })
      }
    ),
    suite("option UnexpectedEnd/UnexpectedChar errors")(
      test("option with nothing after — missing opening paren") {
        val result = parse("option")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("option wrong char after keyword") {
        val result = parse("option X")
        assertTrue(result match {
          case Left(UnexpectedChar('X', 7, _)) => true
          case _                               => false
        })
      },
      test("option unclosed paren") {
        val result = parse("option(string")
        assertTrue(result match {
          case Left(UnexpectedEnd(_)) => true
          case _                      => false
        })
      },
      test("option wrong closing delimiter") {
        val result = parse("option(string}")
        assertTrue(result match {
          case Left(UnexpectedChar('}', _, _)) => true
          case _                               => false
        })
      }
    ),
    suite("roundtrip tests")(
      test("Nominal roundtrip") {
        val original = SchemaRepr.Nominal("Person")
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Primitive roundtrip") {
        val original = SchemaRepr.Primitive("string")
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Wildcard roundtrip") {
        val original = SchemaRepr.Wildcard
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Record roundtrip") {
        val original = SchemaRepr.Record(
          Vector(
            "name" -> SchemaRepr.Primitive("string"),
            "age"  -> SchemaRepr.Primitive("int")
          )
        )
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Variant roundtrip") {
        val original = SchemaRepr.Variant(
          Vector(
            "Left"  -> SchemaRepr.Primitive("int"),
            "Right" -> SchemaRepr.Primitive("string")
          )
        )
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Sequence roundtrip") {
        val original = SchemaRepr.Sequence(SchemaRepr.Primitive("string"))
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Map roundtrip") {
        val original = SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Optional roundtrip") {
        val original = SchemaRepr.Optional(SchemaRepr.Nominal("Person"))
        assertTrue(parse(original.toString) == Right(original))
      },
      test("Complex nested roundtrip") {
        val original = SchemaRepr.Record(
          Vector(
            "items" -> SchemaRepr.Sequence(SchemaRepr.Nominal("Person")),
            "meta"  -> SchemaRepr.Optional(
              SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Wildcard)
            )
          )
        )
        assertTrue(parse(original.toString) == Right(original))
      }
    )
  )
}
