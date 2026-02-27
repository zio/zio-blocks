package zio.blocks.smithy

import zio.test._

object SmithyIntegrationSpec extends ZIOSpecDefault {

  private def parseOk(input: String): SmithyModel =
    SmithyParser.parse(input) match {
      case Right(m)  => m
      case Left(err) => throw new AssertionError("Parse failed: " + err.formatMessage)
    }

  private def fullPipeline(input: String) = {
    val model1  = SmithyParser.parse(input)
    val printed = model1.map(SmithyPrinter.print)
    val model2  = printed.flatMap(SmithyParser.parse)
    (model1, printed, model2)
  }

  def spec = suite("SmithyIntegration")(
    suite("Weather Service API")(
      test("parses a realistic weather service model") {
        val input =
          """$version: "2"
            |namespace com.example.weather
            |
            |/// The Weather Service provides weather data.
            |service WeatherService {
            |    version: "2024-01-01"
            |    operations: [GetCurrentWeather, GetForecast]
            |    errors: [InternalServerError]
            |}
            |
            |/// Get current weather for a city.
            |@http(method: "GET", uri: "/weather/{city}")
            |operation GetCurrentWeather {
            |    input: GetCurrentWeatherInput
            |    output: GetCurrentWeatherOutput
            |    errors: [CityNotFoundError, ValidationError]
            |}
            |
            |/// Get multi-day forecast.
            |@http(method: "GET", uri: "/forecast/{city}")
            |operation GetForecast {
            |    input: GetForecastInput
            |    output: GetForecastOutput
            |    errors: [CityNotFoundError]
            |}
            |
            |structure GetCurrentWeatherInput {
            |    @required
            |    city: String
            |}
            |
            |structure GetCurrentWeatherOutput {
            |    @required
            |    temperature: Float
            |    @required
            |    humidity: Integer
            |    description: String
            |    windSpeed: Double
            |}
            |
            |structure GetForecastInput {
            |    @required
            |    city: String
            |    days: Integer
            |}
            |
            |structure GetForecastOutput {
            |    @required
            |    forecasts: ForecastList
            |}
            |
            |list ForecastList {
            |    member: ForecastDay
            |}
            |
            |structure ForecastDay {
            |    @required
            |    date: String
            |    highTemp: Float
            |    lowTemp: Float
            |    description: String
            |}
            |
            |@error("client")
            |structure CityNotFoundError {
            |    @required
            |    message: String
            |    city: String
            |}
            |
            |@error("client")
            |structure ValidationError {
            |    @required
            |    message: String
            |}
            |
            |@error("server")
            |structure InternalServerError {
            |    message: String
            |}""".stripMargin

        val model = parseOk(input)
        assertTrue(
          model.namespace == "com.example.weather",
          model.shapes.length == 12,
          model.findShape("WeatherService").isDefined,
          model.findShape("WeatherService").get.shape.isInstanceOf[ServiceShape],
          model.findShape("GetCurrentWeather").get.shape.isInstanceOf[OperationShape],
          model.findShape("GetForecast").get.shape.isInstanceOf[OperationShape],
          model.findShape("ForecastList").get.shape.isInstanceOf[ListShape],
          model.findShape("CityNotFoundError").get.shape.isInstanceOf[StructureShape]
        )
      },
      test("weather service round-trips through print and re-parse") {
        val input =
          """$version: "2"
            |namespace com.example.weather
            |
            |service WeatherService {
            |    version: "2024-01-01"
            |    operations: [GetCurrentWeather]
            |    errors: [ServerError]
            |}
            |
            |@http(method: "GET", uri: "/weather/{city}")
            |operation GetCurrentWeather {
            |    input: GetWeatherInput
            |    output: GetWeatherOutput
            |    errors: [NotFound]
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
            |structure NotFound {
            |    message: String
            |}
            |
            |@error("server")
            |structure ServerError {
            |    message: String
            |}""".stripMargin

        val (m1, printed, m2) = fullPipeline(input)
        assertTrue(
          m1.isRight,
          m2.isRight,
          printed.isRight,
          m1 == m2
        )
      }
    ),
    suite("CRUD API with Resources")(
      test("parses a CRUD resource-based API") {
        val input =
          """$version: "2"
            |namespace com.example.crud
            |
            |service ItemService {
            |    version: "1.0"
            |    resources: [ItemResource]
            |    errors: [ServerError]
            |}
            |
            |resource ItemResource {
            |    identifiers: {itemId: String}
            |    create: CreateItem
            |    read: GetItem
            |    update: UpdateItem
            |    delete: DeleteItem
            |    list: ListItems
            |}
            |
            |@http(method: "POST", uri: "/items")
            |operation CreateItem {
            |    input: CreateItemInput
            |    output: CreateItemOutput
            |}
            |
            |@http(method: "GET", uri: "/items/{itemId}")
            |operation GetItem {
            |    input: GetItemInput
            |    output: GetItemOutput
            |}
            |
            |@http(method: "PUT", uri: "/items/{itemId}")
            |operation UpdateItem {
            |    input: UpdateItemInput
            |    output: UpdateItemOutput
            |}
            |
            |@http(method: "DELETE", uri: "/items/{itemId}")
            |operation DeleteItem {
            |    input: DeleteItemInput
            |    output: DeleteItemOutput
            |}
            |
            |@http(method: "GET", uri: "/items")
            |operation ListItems {
            |    input: ListItemsInput
            |    output: ListItemsOutput
            |}
            |
            |structure CreateItemInput {
            |    @required
            |    name: String
            |    description: String
            |}
            |
            |structure CreateItemOutput {
            |    @required
            |    itemId: String
            |}
            |
            |structure GetItemInput {
            |    @required
            |    itemId: String
            |}
            |
            |structure GetItemOutput {
            |    @required
            |    item: Item
            |}
            |
            |structure UpdateItemInput {
            |    @required
            |    itemId: String
            |    name: String
            |    description: String
            |}
            |
            |structure UpdateItemOutput {
            |    @required
            |    item: Item
            |}
            |
            |structure DeleteItemInput {
            |    @required
            |    itemId: String
            |}
            |
            |structure DeleteItemOutput {}
            |
            |structure ListItemsInput {
            |    maxResults: Integer
            |    nextToken: String
            |}
            |
            |structure ListItemsOutput {
            |    @required
            |    items: ItemList
            |    nextToken: String
            |}
            |
            |structure Item {
            |    @required
            |    itemId: String
            |    @required
            |    name: String
            |    description: String
            |    createdAt: Timestamp
            |}
            |
            |list ItemList {
            |    member: Item
            |}
            |
            |@error("server")
            |structure ServerError {
            |    message: String
            |}""".stripMargin

        val model    = parseOk(input)
        val resource = model.findShape("ItemResource").get.shape.asInstanceOf[ResourceShape]
        assertTrue(
          model.namespace == "com.example.crud",
          model.shapes.length == 20,
          resource.identifiers == Map("itemId" -> ShapeId("", "String")),
          resource.create.isDefined,
          resource.read.isDefined,
          resource.update.isDefined,
          resource.delete.isDefined,
          resource.list.isDefined
        )
      },
      test("CRUD API round-trips correctly") {
        val input =
          """$version: "2"
            |namespace com.example.crud
            |
            |service ItemService {
            |    version: "1.0"
            |    resources: [ItemResource]
            |}
            |
            |resource ItemResource {
            |    identifiers: {itemId: String}
            |    create: CreateItem
            |    read: GetItem
            |    delete: DeleteItem
            |}
            |
            |operation CreateItem {
            |    input: CreateItemInput
            |    output: CreateItemOutput
            |}
            |
            |operation GetItem {
            |    input: GetItemInput
            |    output: GetItemOutput
            |}
            |
            |operation DeleteItem {
            |    input: DeleteItemInput
            |    output: DeleteItemOutput
            |}
            |
            |structure CreateItemInput {
            |    @required
            |    name: String
            |}
            |
            |structure CreateItemOutput {
            |    @required
            |    id: String
            |}
            |
            |structure GetItemInput {
            |    @required
            |    id: String
            |}
            |
            |structure GetItemOutput {
            |    name: String
            |}
            |
            |structure DeleteItemInput {
            |    @required
            |    id: String
            |}
            |
            |structure DeleteItemOutput {}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      }
    ),
    suite("Enum-heavy model")(
      test("parses multiple enums and intEnums") {
        val input =
          """$version: "2"
            |namespace com.example.enums
            |
            |enum Suit {
            |    SPADES
            |    HEARTS
            |    DIAMONDS
            |    CLUBS
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
            |    CRITICAL = 3
            |}
            |
            |intEnum HttpStatus {
            |    OK = 200
            |    NOT_FOUND = 404
            |    INTERNAL_ERROR = 500
            |}
            |
            |/// A card in a deck
            |structure Card {
            |    @required
            |    suit: Suit
            |    @required
            |    rank: Integer
            |}""".stripMargin

        val model      = parseOk(input)
        val suit       = model.findShape("Suit").get.shape.asInstanceOf[EnumShape]
        val color      = model.findShape("Color").get.shape.asInstanceOf[EnumShape]
        val priority   = model.findShape("Priority").get.shape.asInstanceOf[IntEnumShape]
        val httpStatus = model.findShape("HttpStatus").get.shape.asInstanceOf[IntEnumShape]
        assertTrue(
          model.shapes.length == 5,
          suit.members.length == 4,
          suit.members.head.name == "SPADES",
          suit.members.head.value.isEmpty,
          color.members.length == 3,
          color.members.head.value == Some("red"),
          priority.members.length == 4,
          priority.members.head.value == 0,
          priority.members.last.value == 3,
          httpStatus.members.length == 3,
          httpStatus.members.head.value == 200
        )
      },
      test("enum-heavy model round-trips correctly") {
        val input =
          """$version: "2"
            |namespace com.example.enums
            |
            |enum Suit {
            |    SPADES
            |    HEARTS
            |    DIAMONDS
            |    CLUBS
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
            |}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      }
    ),
    suite("full pipeline inspection")(
      test("can inspect model structure after parsing") {
        val input =
          """$version: "2"
            |namespace com.example
            |
            |/// A user in the system
            |structure User {
            |    @required
            |    name: String
            |    email: String
            |    age: Integer
            |}""".stripMargin

        val model = parseOk(input)
        val user  = model.findShape("User").get.shape.asInstanceOf[StructureShape]
        assertTrue(
          model.allShapeIds == List(ShapeId("com.example", "User")),
          user.members.length == 3,
          user.members.exists(_.name == "name"),
          user.members.find(_.name == "name").get.traits == List(TraitApplication.required),
          user.traits == List(TraitApplication.documentation("A user in the system"))
        )
      },
      test("printed output is valid Smithy IDL") {
        val input =
          """$version: "2"
            |namespace com.example
            |
            |string Name
            |
            |structure User {
            |    @required
            |    name: Name
            |}""".stripMargin

        val model   = parseOk(input)
        val printed = SmithyPrinter.print(model)
        assertTrue(
          printed.startsWith("$version:"),
          printed.contains("namespace com.example"),
          printed.contains("string Name"),
          printed.contains("structure User")
        )
      },
      test("model with metadata, use statements, and shapes") {
        val input =
          """$version: "2"
            |metadata authors = ["alice", "bob"]
            |namespace com.example
            |use smithy.api#String
            |
            |structure Config {
            |    @required
            |    name: String
            |    value: Integer
            |}""".stripMargin

        val model = parseOk(input)
        assertTrue(
          model.metadata.contains("authors"),
          model.metadata("authors") == NodeValue.ArrayValue(
            List(NodeValue.StringValue("alice"), NodeValue.StringValue("bob"))
          ),
          model.useStatements == List(ShapeId("smithy.api", "String")),
          model.shapes.length == 1
        )
      }
    ),
    suite("cross-namespace references")(
      test("members referencing shapes in same namespace use short names") {
        val input =
          """$version: "2"
            |namespace com.example
            |
            |string MyString
            |
            |structure Foo {
            |    bar: MyString
            |}""".stripMargin

        val model   = parseOk(input)
        val printed = SmithyPrinter.print(model)
        assertTrue(
          printed.contains("bar: MyString"),
          !printed.contains("com.example#MyString")
        )
      }
    ),
    suite("metadata after namespace")(
      test("metadata can appear both before and after namespace") {
        val input =
          """$version: "2"
            |metadata before = "yes"
            |namespace com.example
            |metadata after = "also"
            |string Foo""".stripMargin

        val model = parseOk(input)
        assertTrue(
          model.metadata.size == 2,
          model.metadata("before") == NodeValue.StringValue("yes"),
          model.metadata("after") == NodeValue.StringValue("also")
        )
      }
    ),
    suite("comments in input")(
      test("line comments are ignored during parsing") {
        val input =
          """$version: "2"
            |// This is a comment
            |namespace com.example
            |// Another comment
            |string Foo""".stripMargin

        val model = parseOk(input)
        assertTrue(
          model.shapes.length == 1,
          model.shapes.head.name == "Foo"
        )
      }
    ),
    suite("map and list with member traits")(
      test("list member with traits round-trips") {
        val input =
          """$version: "2"
            |namespace com.example
            |list TagList {
            |    @required
            |    member: String
            |}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      },
      test("map key/value with traits round-trips") {
        val input =
          """$version: "2"
            |namespace com.example
            |map TagMap {
            |    @required
            |    key: String
            |    @required
            |    value: String
            |}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      }
    ),
    suite("union with traits")(
      test("union members with traits round-trip") {
        val input =
          """$version: "2"
            |namespace com.example
            |union Payload {
            |    /// String payload
            |    text: String
            |    /// Binary payload
            |    binary: Blob
            |}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      }
    ),
    suite("enum members with traits")(
      test("enum members with documentation round-trip") {
        val input =
          """$version: "2"
            |namespace com.example
            |enum Status {
            |    /// Active status
            |    ACTIVE
            |    /// Inactive status
            |    INACTIVE
            |}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      },
      test("intEnum members with documentation round-trip") {
        val input =
          """$version: "2"
            |namespace com.example
            |intEnum Level {
            |    /// Low level
            |    LOW = 1
            |    /// High level
            |    HIGH = 2
            |}""".stripMargin

        val (m1, _, m2) = fullPipeline(input)
        assertTrue(m1.isRight, m2.isRight, m1 == m2)
      }
    ),
    suite("string escape coverage")(
      test("metadata with tab, carriage return, backslash, slash, backspace, formfeed escapes") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata s1 = "tab\there"
            |metadata s2 = "cr\rhere"
            |metadata s3 = "bs\\here"
            |metadata s4 = "sl\/here"
            |metadata s5 = "bf\bhere"
            |metadata s6 = "ff\fhere"
            |metadata s7 = "qt\"here"
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(
          model.metadata("s1") == NodeValue.StringValue("tab\there"),
          model.metadata("s2") == NodeValue.StringValue("cr\rhere"),
          model.metadata("s3") == NodeValue.StringValue("bs\\here"),
          model.metadata("s4") == NodeValue.StringValue("sl/here"),
          model.metadata("s5") == NodeValue.StringValue("bf\bhere"),
          model.metadata("s6") == NodeValue.StringValue("ff\fhere"),
          model.metadata("s7") == NodeValue.StringValue("qt\"here")
        )
      }
    ),
    suite("number parsing coverage")(
      test("negative number in metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata neg = -42
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("neg") == NodeValue.NumberValue(BigDecimal(-42)))
      },
      test("decimal number in metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata pi = 3.14
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("pi") == NodeValue.NumberValue(BigDecimal("3.14")))
      },
      test("number with exponent in metadata") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata big = 1e10
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("big") == NodeValue.NumberValue(BigDecimal("1E+10")))
      },
      test("number with negative exponent") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata small = 5E-3
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("small") == NodeValue.NumberValue(BigDecimal("5E-3")))
      },
      test("number with positive exponent sign") {
        val input =
          """$version: "2"
            |namespace com.example
            |metadata val1 = 2E+5
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("val1") == NodeValue.NumberValue(BigDecimal("2E+5")))
      }
    ),
    suite("parser error paths")(
      test("error on missing version") {
        val result = SmithyParser.parse("namespace com.example")
        assertTrue(result.isLeft)
      },
      test("error on missing namespace") {
        val result = SmithyParser.parse("$version: \"2\"\nstring Foo")
        assertTrue(result.isLeft)
      },
      test("error on unknown shape keyword") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nfoobar MyShape")
        assertTrue(result.isLeft)
      },
      test("error on empty shape name") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nstructure {}")
        assertTrue(result.isLeft)
      },
      test("error on list missing member") {
        val result =
          SmithyParser.parse("$version: \"2\"\nnamespace com.example\nlist MyList {\n    notMember: String\n}")
        assertTrue(result.isLeft)
      },
      test("error on map missing key") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nmap MyMap {\n    value: String\n}")
        assertTrue(result.isLeft)
      },
      test("error on map missing value") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nmap MyMap {\n    key: String\n}")
        assertTrue(result.isLeft)
      },
      test("error on unknown service property") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nservice S {\n    unknown: true\n}")
        assertTrue(result.isLeft)
      },
      test("error on unknown operation property") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\noperation Op {\n    unknown: Foo\n}")
        assertTrue(result.isLeft)
      },
      test("error on unknown resource property") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nresource R {\n    unknown: Foo\n}")
        assertTrue(result.isLeft)
      },
      test("error on apply without traits") {
        val result = SmithyParser.parse("$version: \"2\"\nnamespace com.example\nstring Foo\napply Foo")
        assertTrue(result.isLeft)
      },
      test("error on unexpected identifier in value position") {
        val result = SmithyParser.parse("$version: \"2\"\nmetadata x = undefined\nnamespace com.example")
        assertTrue(result.isLeft)
      },
      test("error on unexpected character in value position") {
        val result = SmithyParser.parse("$version: \"2\"\nmetadata x = !\nnamespace com.example")
        assertTrue(result.isLeft)
      }
    ),
    suite("printer coverage")(
      test("prints fully qualified trait ID for non-smithy.api traits") {
        val model = SmithyModel(
          "2",
          "com.example",
          Nil,
          Map.empty,
          List(
            ShapeDefinition(
              "Foo",
              StringShape(
                "Foo",
                List(TraitApplication(ShapeId("com.custom", "myTrait"), None))
              )
            )
          )
        )
        val printed = SmithyPrinter.print(model)
        assertTrue(printed.contains("@com.custom#myTrait"))
      },
      test("escapes carriage return and backslash in printed strings") {
        val model = SmithyModel(
          "2",
          "com.example",
          Nil,
          Map("cr" -> NodeValue.StringValue("a\rb"), "bs" -> NodeValue.StringValue("a\\b")),
          Nil
        )
        val printed = SmithyPrinter.print(model)
        assertTrue(
          printed.contains("metadata cr = \"a\\rb\""),
          printed.contains("metadata bs = \"a\\\\b\"")
        )
      },
      test("prints resource with multiple identifiers separated by commas") {
        val model = SmithyModel(
          "2",
          "com.example",
          Nil,
          Map.empty,
          List(
            ShapeDefinition(
              "MyResource",
              ResourceShape(
                "MyResource",
                Nil,
                Map(
                  "id1" -> ShapeId("", "String"),
                  "id2" -> ShapeId("", "Integer")
                )
              )
            )
          )
        )
        val printed = SmithyPrinter.print(model)
        assertTrue(
          printed.contains("identifiers: {"),
          printed.contains("}")
        )
      },
      test("prints non-documentation trait as @trait when doc value is not a string") {
        val model = SmithyModel(
          "2",
          "com.example",
          Nil,
          Map.empty,
          List(
            ShapeDefinition(
              "Foo",
              StringShape(
                "Foo",
                List(
                  TraitApplication(
                    ShapeId("smithy.api", "documentation"),
                    Some(NodeValue.NumberValue(BigDecimal(42)))
                  )
                )
              )
            )
          )
        )
        val printed = SmithyPrinter.print(model)
        assertTrue(printed.contains("@documentation(42)"))
      }
    ),
    suite("ShapeId.parse coverage")(
      test("parses a member reference") {
        val result = ShapeId.parse("com.example#MyShape$member")
        assertTrue(
          result == Right(ShapeId.Member(ShapeId("com.example", "MyShape"), "member"))
        )
      },
      test("error on empty namespace in ShapeId") {
        val result = ShapeId.parse("#MyShape")
        assertTrue(result.isLeft)
      },
      test("error on empty name in member reference") {
        val result = ShapeId.parse("com.example#$member")
        assertTrue(result.isLeft)
      },
      test("error on empty member name") {
        val result = ShapeId.parse("com.example#MyShape$")
        assertTrue(result.isLeft)
      }
    ),
    suite("metadata after namespace with use")(
      test("metadata and use statements interleaved after namespace") {
        val input =
          """$version: "2"
            |namespace com.example
            |use smithy.api#String
            |metadata after = "yes"
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(
          model.useStatements == List(ShapeId("smithy.api", "String")),
          model.metadata("after") == NodeValue.StringValue("yes")
        )
      }
    ),
    suite("node value parsing coverage")(
      test("parses empty array") {
        val input =
          """$version: "2"
            |metadata arr = []
            |namespace com.example
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("arr") == NodeValue.ArrayValue(Nil))
      },
      test("parses empty object") {
        val input =
          """$version: "2"
            |metadata obj = {}
            |namespace com.example
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(model.metadata("obj") == NodeValue.ObjectValue(Nil))
      },
      test("parses object with quoted keys") {
        val input =
          """$version: "2"
            |metadata obj = {"quoted-key": "value"}
            |namespace com.example
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(
          model.metadata("obj") == NodeValue.ObjectValue(
            List("quoted-key" -> NodeValue.StringValue("value"))
          )
        )
      },
      test("parses array with trailing comma") {
        val input =
          """$version: "2"
            |metadata arr = [1, 2, 3,]
            |namespace com.example
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(
          model.metadata("arr") == NodeValue.ArrayValue(
            List(
              NodeValue.NumberValue(BigDecimal(1)),
              NodeValue.NumberValue(BigDecimal(2)),
              NodeValue.NumberValue(BigDecimal(3))
            )
          )
        )
      }
    ),
    suite("unknown escape in string")(
      test("unknown escape sequence is preserved literally") {
        val input =
          """$version: "2"
            |metadata s = "hello\xworld"
            |namespace com.example
            |string Foo""".stripMargin
        val model = parseOk(input)
        assertTrue(
          model.metadata("s") == NodeValue.StringValue("hello\\xworld")
        )
      }
    )
  )
}
