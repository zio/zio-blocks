package zio.blocks.smithy

import zio.test._

object SmithyRoundTripSpec extends ZIOSpecDefault {

  private def roundTrip(input: String): Either[SmithyError, (SmithyModel, SmithyModel)] = {
    val model1 = SmithyParser.parse(input)
    model1.flatMap { m1 =>
      val printed = SmithyPrinter.print(m1)
      SmithyParser.parse(printed).map(m2 => (m1, m2))
    }
  }

  private def assertRoundTrip(input: String) = {
    val result = roundTrip(input)
    assertTrue(
      result.isRight,
      result.toOption.get._1 == result.toOption.get._2
    )
  }

  def spec = suite("SmithyRoundTrip")(
    suite("simple shapes")(
      test("version + namespace + simple string shape") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |string MyString""".stripMargin
        )
      },
      test("all 13 simple shape types") {
        assertRoundTrip(
          """$version: "2.0"
            |namespace com.example
            |blob MyBlob
            |boolean MyBool
            |string MyStr
            |byte MyByte
            |short MyShort
            |integer MyInt
            |long MyLong
            |float MyFloat
            |double MyDouble
            |bigInteger MyBigInt
            |bigDecimal MyBigDec
            |timestamp MyTimestamp
            |document MyDoc""".stripMargin
        )
      },
      test("simple shape with trait") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |@sensitive
            |string SecretString""".stripMargin
        )
      }
    ),
    suite("structures")(
      test("empty structure") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |structure Empty {}""".stripMargin
        )
      },
      test("structure with members") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |structure User {
            |    name: String
            |    age: Integer
            |}""".stripMargin
        )
      },
      test("structure with traits on members") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |structure User {
            |    @required
            |    name: String
            |    age: Integer
            |}""".stripMargin
        )
      },
      test("structure with documentation trait") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |/// A user record
            |structure User {
            |    /// The user's name
            |    @required
            |    name: String
            |    /// The user's age
            |    age: Integer
            |}""".stripMargin
        )
      },
      test("structure with shape-level traits") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |@error("client")
            |structure BadRequest {
            |    message: String
            |}""".stripMargin
        )
      }
    ),
    suite("unions")(
      test("union with members") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |union Value {
            |    intValue: Integer
            |    strValue: String
            |    boolValue: Boolean
            |}""".stripMargin
        )
      },
      test("empty union") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |union Empty {}""".stripMargin
        )
      }
    ),
    suite("list and map")(
      test("list shape") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |list StringList {
            |    member: String
            |}""".stripMargin
        )
      },
      test("map shape") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |map StringMap {
            |    key: String
            |    value: Integer
            |}""".stripMargin
        )
      }
    ),
    suite("enum shapes")(
      test("string enum without values") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |enum Suit {
            |    SPADES
            |    HEARTS
            |    DIAMONDS
            |    CLUBS
            |}""".stripMargin
        )
      },
      test("string enum with explicit values") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |enum Color {
            |    RED = "red"
            |    GREEN = "green"
            |    BLUE = "blue"
            |}""".stripMargin
        )
      },
      test("intEnum") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |intEnum FaceCard {
            |    JACK = 11
            |    QUEEN = 12
            |    KING = 13
            |}""".stripMargin
        )
      },
      test("empty enum") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |enum Empty {}""".stripMargin
        )
      },
      test("empty intEnum") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |intEnum Empty {}""".stripMargin
        )
      }
    ),
    suite("service shapes")(
      test("service with version and operations") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |service MyService {
            |    version: "1.0"
            |    operations: [GetFoo, CreateFoo]
            |}""".stripMargin
        )
      },
      test("service with resources and errors") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |service MyService {
            |    version: "2.0"
            |    operations: [GetFoo]
            |    resources: [FooResource]
            |    errors: [BadRequest]
            |}""".stripMargin
        )
      }
    ),
    suite("operation shapes")(
      test("operation with input and output") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |operation GetFoo {
            |    input: GetFooInput
            |    output: GetFooOutput
            |}""".stripMargin
        )
      },
      test("operation with errors") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |operation GetFoo {
            |    input: GetFooInput
            |    output: GetFooOutput
            |    errors: [NotFound, BadRequest]
            |}""".stripMargin
        )
      }
    ),
    suite("resource shapes")(
      test("resource with identifiers and lifecycle ops") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |resource FooResource {
            |    identifiers: {id: FooId}
            |    read: GetFoo
            |    list: ListFoos
            |}""".stripMargin
        )
      },
      test("resource with create, update, delete") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |resource FooResource {
            |    identifiers: {id: String}
            |    create: CreateFoo
            |    update: UpdateFoo
            |    delete: DeleteFoo
            |}""".stripMargin
        )
      },
      test("resource with operations and collectionOperations") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |resource FooResource {
            |    identifiers: {id: String}
            |    operations: [CustomOp]
            |    collectionOperations: [BatchOp]
            |    resources: [ChildResource]
            |}""".stripMargin
        )
      }
    ),
    suite("metadata")(
      test("model with string metadata") {
        assertRoundTrip(
          """$version: "2"
            |metadata greeting = "hello"
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("model with numeric metadata") {
        assertRoundTrip(
          """$version: "2"
            |metadata count = 42
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("model with boolean metadata") {
        assertRoundTrip(
          """$version: "2"
            |metadata debug = true
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("model with null metadata") {
        assertRoundTrip(
          """$version: "2"
            |metadata nothing = null
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("model with array metadata") {
        assertRoundTrip(
          """$version: "2"
            |metadata tags = ["a", "b", "c"]
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("model with object metadata") {
        assertRoundTrip(
          """$version: "2"
            |metadata config = {key: "value", num: 1}
            |namespace com.example
            |string Foo""".stripMargin
        )
      }
    ),
    suite("use statements")(
      test("model with use statements") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |use smithy.api#String
            |use com.other#Widget
            |string Foo""".stripMargin
        )
      }
    ),
    suite("complex models")(
      test("model with multiple shape types") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |
            |string Name
            |
            |integer Age
            |
            |structure User {
            |    @required
            |    name: Name
            |    age: Age
            |}
            |
            |list Users {
            |    member: User
            |}
            |
            |enum Status {
            |    ACTIVE
            |    INACTIVE
            |}""".stripMargin
        )
      },
      test("full API model with service, operations, structures") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example.api
            |
            |service WeatherService {
            |    version: "1.0"
            |    operations: [GetWeather]
            |    errors: [ServiceError]
            |}
            |
            |operation GetWeather {
            |    input: GetWeatherInput
            |    output: GetWeatherOutput
            |    errors: [NotFoundError]
            |}
            |
            |structure GetWeatherInput {
            |    @required
            |    city: String
            |}
            |
            |structure GetWeatherOutput {
            |    @required
            |    temperature: Float
            |    description: String
            |}
            |
            |@error("client")
            |structure NotFoundError {
            |    message: String
            |}
            |
            |@error("server")
            |structure ServiceError {
            |    message: String
            |}""".stripMargin
        )
      },
      test("model with all shape categories") {
        assertRoundTrip(
          """$version: "2.0"
            |namespace com.example
            |
            |string MyString
            |integer MyInt
            |boolean MyBool
            |
            |structure Foo {
            |    @required
            |    name: String
            |    value: Integer
            |}
            |
            |union FooOrBar {
            |    foo: Foo
            |    bar: String
            |}
            |
            |list FooList {
            |    member: Foo
            |}
            |
            |map FooMap {
            |    key: String
            |    value: Foo
            |}
            |
            |enum Color {
            |    RED = "red"
            |    GREEN = "green"
            |    BLUE = "blue"
            |}
            |
            |intEnum Priority {
            |    LOW = 0
            |    MEDIUM = 1
            |    HIGH = 2
            |}
            |
            |service MyService {
            |    version: "1.0"
            |    operations: [GetFoo]
            |    resources: [FooResource]
            |}
            |
            |operation GetFoo {
            |    input: GetFooInput
            |    output: GetFooOutput
            |}
            |
            |structure GetFooInput {
            |    @required
            |    id: String
            |}
            |
            |structure GetFooOutput {
            |    foo: Foo
            |}
            |
            |resource FooResource {
            |    identifiers: {fooId: String}
            |    read: GetFoo
            |}""".stripMargin
        )
      }
    ),
    suite("trait round-trips")(
      test("trait with object value") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |@http(method: "GET", uri: "/foo")
            |operation GetFoo {
            |    input: GetFooInput
            |    output: GetFooOutput
            |}""".stripMargin
        )
      },
      test("multiple traits on a shape") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |/// A documented string
            |@sensitive
            |string SecretString""".stripMargin
        )
      },
      test("trait with empty parentheses") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |@tags()
            |string Foo""".stripMargin
        )
      }
    ),
    suite("apply statements")(
      test("apply statements are preserved in model but not printed") {
        val input =
          """$version: "2"
            |namespace com.example
            |string Foo
            |apply Foo @sensitive""".stripMargin
        val model1 = SmithyParser.parse(input)
        assertTrue(
          model1.isRight,
          model1.toOption.get.applyStatements.length == 1,
          model1.toOption.get.applyStatements.head.target == "Foo"
        )
      },
      test("round-trip without apply statements is exact") {
        val input =
          """$version: "2"
            |namespace com.example
            |string Foo""".stripMargin
        val model1  = SmithyParser.parse(input)
        val printed = model1.map(SmithyPrinter.print)
        val model2  = printed.flatMap(SmithyParser.parse)
        assertTrue(
          model1.isRight,
          model2.isRight,
          model1.toOption.get.applyStatements.isEmpty,
          model2.toOption.get.applyStatements.isEmpty,
          model1 == model2
        )
      }
    ),
    suite("edge cases")(
      test("string value with escape characters") {
        assertRoundTrip(
          """$version: "2"
            |metadata escaped = "hello\nworld\ttab"
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("multiple metadata entries") {
        assertRoundTrip(
          """$version: "2"
            |metadata key1 = "val1"
            |metadata key2 = 42
            |metadata key3 = true
            |namespace com.example
            |string Foo""".stripMargin
        )
      },
      test("deeply nested trait value") {
        assertRoundTrip(
          """$version: "2"
            |namespace com.example
            |@examples([{title: "test", input: {id: "1"}}])
            |operation GetFoo {
            |    input: GetFooInput
            |    output: GetFooOutput
            |}""".stripMargin
        )
      }
    )
  )
}
