package golem

import golem.data.multimodal.Multimodal
import golem.data.UnstructuredBinaryValue
import golem.data.UnstructuredTextValue
import golem.data.unstructured.{AllowedLanguages, AllowedMimeTypes, BinarySegment, TextSegment}
import golem.runtime.autowire.AgentImplementation
import org.scalatest.funsuite.AnyFunSuite
import zio.blocks.schema.Schema

import scala.concurrent.Future

final class AgentDefinitionCompileSpec extends AnyFunSuite {

  // ---------------------------------------------------------------------------
  // Constructor patterns
  // ---------------------------------------------------------------------------

  @agentDefinition("unit-ctor-agent", mode = DurabilityMode.Durable)
  trait UnitCtorAgent extends BaseAgent[Unit] {
    def ping(): Future[String]
  }

  @agentDefinition("string-ctor-agent")
  trait StringCtorAgent extends BaseAgent[String] {
    def echo(): Future[String]
  }

  @agentDefinition("int-ctor-agent")
  trait IntCtorAgent extends BaseAgent[Int] {
    def value(): Future[Int]
  }

  final case class MyConfig(host: String, port: Int)
  object MyConfig { implicit val schema: Schema[MyConfig] = Schema.derived }

  @agentDefinition("case-class-ctor-agent")
  trait CaseClassCtorAgent extends BaseAgent[MyConfig] {
    def info(): Future[String]
  }

  @agentDefinition("tuple2-ctor-agent")
  trait Tuple2CtorAgent extends BaseAgent[(String, Int)] {
    def combined(): Future[String]
  }

  @agentDefinition("tuple3-ctor-agent")
  trait Tuple3CtorAgent extends BaseAgent[(String, Int, Boolean)] {
    def all(): Future[String]
  }

  @agentDefinition("tuple4-ctor-agent")
  trait Tuple4CtorAgent extends BaseAgent[(String, Int, Boolean, Double)] {
    def data(): Future[String]
  }

  @agentDefinition("tuple5-ctor-agent")
  trait Tuple5CtorAgent extends BaseAgent[(String, Int, Boolean, Double, Long)] {
    def data(): Future[String]
  }

  // ---------------------------------------------------------------------------
  // Method return type patterns
  // ---------------------------------------------------------------------------

  @agentDefinition("return-types-agent")
  trait ReturnTypesAgent extends BaseAgent[Unit] {
    def asyncString(): Future[String]
    def asyncInt(): Future[Int]
    def asyncOption(): Future[Option[String]]
    def asyncList(): Future[List[Int]]
    def asyncCaseClass(): Future[MyConfig]
    def syncString(): String
    def syncInt(): Int
    def syncUnit(): Unit
  }

  // ---------------------------------------------------------------------------
  // Method parameter patterns
  // ---------------------------------------------------------------------------

  final case class Nested(inner: String, count: Int)
  object Nested { implicit val schema: Schema[Nested] = Schema.derived }

  @agentDefinition("param-types-agent")
  trait ParamTypesAgent extends BaseAgent[Unit] {
    def singlePrimitive(s: String): Future[String]
    def multipleParams(a: String, b: Int, c: Boolean): Future[String]
    def caseClassParam(config: MyConfig): Future[String]
    def optionParam(value: Option[String]): Future[String]
    def listParam(values: List[Int]): Future[String]
    def nestedParam(n: Nested): Future[String]
  }

  // ---------------------------------------------------------------------------
  // Kitchen-sink agent: many method signatures (mirrors Rust SDK's Echo agent)
  // ---------------------------------------------------------------------------

  final case class KitchenPayload(tag: String, count: Int)
  object KitchenPayload { implicit val schema: Schema[KitchenPayload] = Schema.derived }

  @agentDefinition("kitchen-sink-agent")
  @description("Agent with many method signature patterns.")
  trait KitchenSinkAgent extends BaseAgent[String] {
    def echoString(message: String): Future[String]
    def echoInt(value: Int): Future[Int]
    def echoBoolean(flag: Boolean): Future[Boolean]
    def echoLong(value: Long): Future[Long]
    def echoDouble(value: Double): Future[Double]
    def echoFloat(value: Float): Future[Float]

    def echoOption(opt: Option[String]): Future[Option[String]]
    def echoOptionInt(opt: Option[Int]): Future[Option[Int]]
    def echoList(items: List[String]): Future[List[String]]
    def echoListInt(items: List[Int]): Future[List[Int]]
    def echoCaseClass(payload: KitchenPayload): Future[KitchenPayload]
    def echoNested(n: Nested): Future[Nested]

    def multiParam(a: String, b: Int, c: Boolean): Future[String]
    def multiParamComplex(name: String, config: MyConfig, tags: List[String]): Future[String]

    def syncVoid(): Unit
    def syncReturn(): String
    def asyncVoid(): Future[Unit]

    @description("A described method.")
    @prompt("Use this to echo with metadata.")
    def describedMethod(input: String): Future[String]
  }

  @agentImplementation()
  final class KitchenSinkAgentImpl(private val name: String) extends KitchenSinkAgent {
    override def echoString(message: String): Future[String]                                           = Future.successful(message)
    override def echoInt(value: Int): Future[Int]                                                      = Future.successful(value)
    override def echoBoolean(flag: Boolean): Future[Boolean]                                           = Future.successful(flag)
    override def echoLong(value: Long): Future[Long]                                                   = Future.successful(value)
    override def echoDouble(value: Double): Future[Double]                                             = Future.successful(value)
    override def echoFloat(value: Float): Future[Float]                                                = Future.successful(value)
    override def echoOption(opt: Option[String]): Future[Option[String]]                               = Future.successful(opt)
    override def echoOptionInt(opt: Option[Int]): Future[Option[Int]]                                  = Future.successful(opt)
    override def echoList(items: List[String]): Future[List[String]]                                   = Future.successful(items)
    override def echoListInt(items: List[Int]): Future[List[Int]]                                      = Future.successful(items)
    override def echoCaseClass(payload: KitchenPayload): Future[KitchenPayload]                        = Future.successful(payload)
    override def echoNested(n: Nested): Future[Nested]                                                 = Future.successful(n)
    override def multiParam(a: String, b: Int, c: Boolean): Future[String]                             = Future.successful(s"$a-$b-$c")
    override def multiParamComplex(name: String, config: MyConfig, tags: List[String]): Future[String] =
      Future.successful(s"$name-${config.host}-${tags.mkString(",")}")
    override def syncVoid(): Unit                               = ()
    override def syncReturn(): String                           = name
    override def asyncVoid(): Future[Unit]                      = Future.successful(())
    override def describedMethod(input: String): Future[String] = Future.successful(s"described: $input")
  }

  // ---------------------------------------------------------------------------
  // Annotation patterns
  // ---------------------------------------------------------------------------

  @agentDefinition(typeName = "explicit-name-agent")
  @description("An agent with explicit type name.")
  trait ExplicitNameAgent extends BaseAgent[Unit] {
    @description("Says hello.")
    @prompt("Greet the user warmly.")
    def greet(name: String): Future[String]
  }

  @agentDefinition(mode = DurabilityMode.Ephemeral)
  trait EphemeralAgent extends BaseAgent[String] {
    def process(): Future[String]
  }

  // ---------------------------------------------------------------------------
  // Implementation patterns
  // ---------------------------------------------------------------------------

  @agentImplementation()
  final class UnitCtorAgentImpl() extends UnitCtorAgent {
    override def ping(): Future[String] = Future.successful("pong")
  }

  @agentImplementation()
  final class StringCtorAgentImpl(private val name: String) extends StringCtorAgent {
    override def echo(): Future[String] = Future.successful(name)
  }

  @agentImplementation()
  final class CaseClassCtorAgentImpl(private val config: MyConfig) extends CaseClassCtorAgent {
    override def info(): Future[String] = Future.successful(s"${config.host}:${config.port}")
  }

  @agentImplementation()
  final class Tuple2CtorAgentImpl(private val name: String, private val id: Int) extends Tuple2CtorAgent {
    override def combined(): Future[String] = Future.successful(s"$name-$id")
  }

  @agentImplementation()
  final class ReturnTypesAgentImpl() extends ReturnTypesAgent {
    override def asyncString(): Future[String]         = Future.successful("hello")
    override def asyncInt(): Future[Int]               = Future.successful(42)
    override def asyncOption(): Future[Option[String]] = Future.successful(Some("x"))
    override def asyncList(): Future[List[Int]]        = Future.successful(List(1, 2, 3))
    override def asyncCaseClass(): Future[MyConfig]    = Future.successful(MyConfig("h", 80))
    override def syncString(): String                  = "sync"
    override def syncInt(): Int                        = 7
    override def syncUnit(): Unit                      = ()
  }

  // ---------------------------------------------------------------------------
  // Tests: Constructor patterns compile
  // ---------------------------------------------------------------------------

  test("BaseAgent[Unit] constructor compiles") {
    val impl = new UnitCtorAgentImpl()
    val defn = AgentImplementation.register[UnitCtorAgent]("unit-ctor-agent")(impl)
    assert(defn.methodMetadata.nonEmpty)
  }

  test("BaseAgent[String] constructor compiles") {
    val impl = new StringCtorAgentImpl("test")
    val defn = AgentImplementation.register[StringCtorAgent]("string-ctor-agent")(impl)
    assert(defn.methodMetadata.nonEmpty)
  }

  test("BaseAgent[CaseClass] constructor compiles") {
    val impl = new CaseClassCtorAgentImpl(MyConfig("localhost", 8080))
    val defn = AgentImplementation.register[CaseClassCtorAgent]("case-class-ctor-agent")(impl)
    assert(defn.methodMetadata.nonEmpty)
  }

  test("BaseAgent[(String, Int)] constructor compiles") {
    val impl = new Tuple2CtorAgentImpl("test", 42)
    val defn = AgentImplementation.register[Tuple2CtorAgent]("tuple2-ctor-agent")(impl)
    assert(defn.methodMetadata.nonEmpty)
  }

  // ---------------------------------------------------------------------------
  // Tests: Method return types compile
  // ---------------------------------------------------------------------------

  test("async and sync return types compile") {
    val impl        = new ReturnTypesAgentImpl()
    val defn        = AgentImplementation.register[ReturnTypesAgent]("return-types-agent")(impl)
    val methodNames = defn.methodMetadata.map(_.metadata.name).toSet
    assert(methodNames.contains("asyncString"))
    assert(methodNames.contains("asyncInt"))
    assert(methodNames.contains("asyncOption"))
    assert(methodNames.contains("asyncList"))
    assert(methodNames.contains("asyncCaseClass"))
    assert(methodNames.contains("syncString"))
    assert(methodNames.contains("syncInt"))
    assert(methodNames.contains("syncUnit"))
  }

  test("method count is correct for ReturnTypesAgent") {
    val impl = new ReturnTypesAgentImpl()
    val defn = AgentImplementation.register[ReturnTypesAgent]("return-types-agent-2")(impl)
    assert(defn.methodMetadata.size == 8)
  }

  // ---------------------------------------------------------------------------
  // Tests: Kitchen-sink agent compiles and registers
  // ---------------------------------------------------------------------------

  test("kitchen-sink agent with 18 methods registers correctly") {
    val impl = new KitchenSinkAgentImpl("test")
    val defn = AgentImplementation.register[KitchenSinkAgent]("kitchen-sink-agent")(impl)
    assert(defn.methodMetadata.size == 18)
  }

  test("kitchen-sink agent method names are all present") {
    val impl        = new KitchenSinkAgentImpl("test")
    val defn        = AgentImplementation.register[KitchenSinkAgent]("kitchen-sink-agent-names")(impl)
    val methodNames = defn.methodMetadata.map(_.metadata.name).toSet
    val expected    = Set(
      "echoString",
      "echoInt",
      "echoBoolean",
      "echoLong",
      "echoDouble",
      "echoFloat",
      "echoOption",
      "echoOptionInt",
      "echoList",
      "echoListInt",
      "echoCaseClass",
      "echoNested",
      "multiParam",
      "multiParamComplex",
      "syncVoid",
      "syncReturn",
      "asyncVoid",
      "describedMethod"
    )
    assert(methodNames == expected)
  }

  test("kitchen-sink agent described method has annotations") {
    val impl = new KitchenSinkAgentImpl("test")
    val defn = AgentImplementation.register[KitchenSinkAgent]("kitchen-sink-agent-ann")(impl)
    val m    = defn.methodMetadata.find(_.metadata.name == "describedMethod").get
    assert(m.metadata.description.contains("A described method."))
    assert(m.metadata.prompt.contains("Use this to echo with metadata."))
  }

  // ---------------------------------------------------------------------------
  // Tests: Annotation field access
  // ---------------------------------------------------------------------------

  test("@agentDefinition mode defaults and overrides") {
    assert(new agentDefinition(mode = DurabilityMode.Durable).mode == DurabilityMode.Durable)
    assert(new agentDefinition(mode = DurabilityMode.Ephemeral).mode == DurabilityMode.Ephemeral)
  }

  test("@description and @prompt store their values") {
    assert(new description("test description").value == "test description")
    assert(new prompt("test prompt").value == "test prompt")
  }

  // ---------------------------------------------------------------------------
  // Tests: Factory constructor pattern (register with Ctor => Trait)
  // ---------------------------------------------------------------------------

  @agentDefinition("factory-ctor-agent")
  trait FactoryCtorAgent extends BaseAgent[MyConfig] {
    def info(): Future[String]
  }

  @agentImplementation()
  final class FactoryCtorAgentImpl(private val config: MyConfig) extends FactoryCtorAgent {
    override def info(): Future[String] = Future.successful(s"${config.host}:${config.port}")
  }

  test("register with factory constructor (Ctor => Trait) compiles") {
    val defn = AgentImplementation.register[FactoryCtorAgent, MyConfig] { config =>
      new FactoryCtorAgentImpl(config)
    }
    assert(defn.methodMetadata.nonEmpty)
    assert(defn.typeName == "factory-ctor-agent")
  }

  // ---------------------------------------------------------------------------
  // Agent with zero methods (only constructor, like Rust's AgentWithOnlyConstructor)
  // ---------------------------------------------------------------------------

  @agentDefinition("no-methods-agent")
  trait NoMethodsAgent extends BaseAgent[String]

  @agentImplementation()
  final class NoMethodsAgentImpl(private val name: String) extends NoMethodsAgent

  test("agent with zero methods (only constructor) compiles and registers") {
    val impl = new NoMethodsAgentImpl("test")
    val defn = AgentImplementation.register[NoMethodsAgent]("no-methods-agent")(impl)
    assert(defn.methodMetadata.isEmpty)
    assert(defn.typeName == "no-methods-agent")
  }

  // ---------------------------------------------------------------------------
  // Agent with single method (boundary case)
  // ---------------------------------------------------------------------------

  @agentDefinition("single-method-agent")
  trait SingleMethodAgent extends BaseAgent[Unit] {
    def only(): Future[String]
  }

  @agentImplementation()
  final class SingleMethodAgentImpl() extends SingleMethodAgent {
    override def only(): Future[String] = Future.successful("only")
  }

  test("agent with single method compiles and registers") {
    val impl = new SingleMethodAgentImpl()
    val defn = AgentImplementation.register[SingleMethodAgent]("single-method-agent")(impl)
    assert(defn.methodMetadata.size == 1)
    assert(defn.methodMetadata.head.metadata.name == "only")
  }

  // ---------------------------------------------------------------------------
  // Agent with Multimodal, TextSegment, BinarySegment method parameters
  // (mirrors Rust SDK's echo_multimodal, echo_unstructured_text, etc.)
  // ---------------------------------------------------------------------------

  final case class MultimodalPayload(text: String, count: Int)
  object MultimodalPayload { implicit val schema: Schema[MultimodalPayload] = Schema.derived }

  sealed trait SupportedLang
  object SupportedLang {
    implicit val allowed: AllowedLanguages[SupportedLang] = new AllowedLanguages[SupportedLang] {
      override val codes: Option[List[String]] = Some(List("en", "es"))
    }
  }

  sealed trait SupportedMime
  object SupportedMime {
    implicit val allowed: AllowedMimeTypes[SupportedMime] = new AllowedMimeTypes[SupportedMime] {
      override val mimeTypes: Option[List[String]] = Some(List("image/png", "application/json"))
    }
  }

  @agentDefinition("multimodal-agent")
  @description("Agent with multimodal and unstructured type methods.")
  trait MultimodalAgent extends BaseAgent[Unit] {
    def echoMultimodal(input: Multimodal[MultimodalPayload]): Future[Multimodal[MultimodalPayload]]
    def echoText(input: TextSegment[SupportedLang]): Future[TextSegment[SupportedLang]]
    def echoTextAny(input: TextSegment[AllowedLanguages.Any]): Future[TextSegment[AllowedLanguages.Any]]
    def echoBinary(input: BinarySegment[SupportedMime]): Future[BinarySegment[SupportedMime]]
    def echoBinaryAny(input: BinarySegment[AllowedMimeTypes.Any]): Future[BinarySegment[AllowedMimeTypes.Any]]
  }

  @agentImplementation()
  final class MultimodalAgentImpl() extends MultimodalAgent {
    override def echoMultimodal(input: Multimodal[MultimodalPayload]): Future[Multimodal[MultimodalPayload]] =
      Future.successful(input)
    override def echoText(input: TextSegment[SupportedLang]): Future[TextSegment[SupportedLang]] =
      Future.successful(input)
    override def echoTextAny(input: TextSegment[AllowedLanguages.Any]): Future[TextSegment[AllowedLanguages.Any]] =
      Future.successful(input)
    override def echoBinary(input: BinarySegment[SupportedMime]): Future[BinarySegment[SupportedMime]] =
      Future.successful(input)
    override def echoBinaryAny(
      input: BinarySegment[AllowedMimeTypes.Any]
    ): Future[BinarySegment[AllowedMimeTypes.Any]] =
      Future.successful(input)
  }

  test("multimodal agent compiles and registers with 5 methods") {
    val impl = new MultimodalAgentImpl()
    val defn = AgentImplementation.register[MultimodalAgent]("multimodal-agent")(impl)
    assert(defn.methodMetadata.size == 5)
    val names = defn.methodMetadata.map(_.metadata.name).toSet
    assert(names == Set("echoMultimodal", "echoText", "echoTextAny", "echoBinary", "echoBinaryAny"))
  }

  test("Multimodal wraps and unwraps payload") {
    val payload = MultimodalPayload("hello", 1)
    val mm      = Multimodal(payload)
    assert(mm.value == payload)
  }

  test("TextSegment.inline sets data and language code") {
    val seg = TextSegment.inline[SupportedLang]("hello", Some("en"))
    seg.value match {
      case UnstructuredTextValue.Inline(data, lang) =>
        assert(data == "hello")
        assert(lang.contains("en"))
      case _ => fail("expected Inline")
    }
  }

  test("TextSegment.url sets URL") {
    val seg = TextSegment.url[SupportedLang]("http://example.com/text.txt")
    seg.value match {
      case UnstructuredTextValue.Url(u) => assert(u == "http://example.com/text.txt")
      case _                            => fail("expected Url")
    }
  }

  test("BinarySegment.inline sets data and MIME type") {
    val seg = BinarySegment.inline[SupportedMime](Array[Byte](1, 2), "image/png")
    seg.value match {
      case UnstructuredBinaryValue.Inline(data, mime) =>
        assert(data.toList == List[Byte](1, 2))
        assert(mime == "image/png")
      case _ => fail("expected Inline")
    }
  }

  test("BinarySegment.url sets URL") {
    val seg = BinarySegment.url[SupportedMime]("http://example.com/data.png")
    seg.value match {
      case UnstructuredBinaryValue.Url(u) => assert(u == "http://example.com/data.png")
      case _                              => fail("expected Url")
    }
  }
}
