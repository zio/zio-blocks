package golem.runtime

import golem.BaseAgent
import golem.runtime.annotations.{DurabilityMode, agentDefinition, agentImplementation, description, prompt}
import golem.runtime.autowire.{AgentDefinition, AgentImplementation, AgentMode}
import org.scalatest.funsuite.AnyFunSuite
import zio.blocks.schema.Schema

import scala.concurrent.Future

final class AgentRegistrationMetadataSpec extends AnyFunSuite {

  @agentDefinition("meta-agent")
  @description("An agent used for metadata tests.")
  trait MetaAgent extends BaseAgent[Unit] {
    @description("Echoes input.")
    @prompt("Say hello.")
    def echo(s: String): Future[String]

    @description("Adds two ints.")
    def add(a: Int, b: Int): Future[Int]

    def noAnnotation(): Future[String]
  }

  @agentImplementation()
  final class MetaAgentImpl() extends MetaAgent {
    override def echo(s: String): Future[String]  = Future.successful(s)
    override def add(a: Int, b: Int): Future[Int] = Future.successful(a + b)
    override def noAnnotation(): Future[String]   = Future.successful("ok")
  }

  private lazy val defn: AgentDefinition[MetaAgent] =
    AgentImplementation.register[MetaAgent]("meta-agent")(new MetaAgentImpl())

  test("registered agent has correct typeName") {
    assert(defn.typeName == "meta-agent")
  }

  test("metadata contains all methods") {
    val names = defn.methodMetadata.map(_.metadata.name).toSet
    assert(names == Set("echo", "add", "noAnnotation"))
  }

  test("method count matches trait method count") {
    assert(defn.methodMetadata.size == 3)
  }

  test("echo method has description from @description") {
    val echo = defn.methodMetadata.find(_.metadata.name == "echo").get
    assert(echo.metadata.description.contains("Echoes input."))
  }

  test("echo method has prompt from @prompt") {
    val echo = defn.methodMetadata.find(_.metadata.name == "echo").get
    assert(echo.metadata.prompt.contains("Say hello."))
  }

  test("add method has description but no prompt") {
    val add = defn.methodMetadata.find(_.metadata.name == "add").get
    assert(add.metadata.description.contains("Adds two ints."))
    assert(add.metadata.prompt.isEmpty)
  }

  test("unannotated method has no description and no prompt") {
    val m = defn.methodMetadata.find(_.metadata.name == "noAnnotation").get
    assert(m.metadata.description.isEmpty)
    assert(m.metadata.prompt.isEmpty)
  }

  test("default mode is Durable") {
    assert(defn.mode == AgentMode.Durable)
  }

  // ---------------------------------------------------------------------------
  // Ephemeral mode
  // ---------------------------------------------------------------------------

  @agentDefinition("ephemeral-meta-agent", mode = DurabilityMode.Ephemeral)
  trait EphemeralMetaAgent extends BaseAgent[Unit] {
    def ping(): Future[String]
  }

  @agentImplementation()
  final class EphemeralMetaAgentImpl() extends EphemeralMetaAgent {
    override def ping(): Future[String] = Future.successful("pong")
  }

  private lazy val ephDefn: AgentDefinition[EphemeralMetaAgent] =
    AgentImplementation.register[EphemeralMetaAgent]("ephemeral-meta-agent")(new EphemeralMetaAgentImpl())

  test("ephemeral agent has Ephemeral mode") {
    assert(ephDefn.mode == AgentMode.Ephemeral)
  }

  // ---------------------------------------------------------------------------
  // Constructor type agent
  // ---------------------------------------------------------------------------

  final case class MetaConfig(host: String, port: Int)
  object MetaConfig { implicit val schema: Schema[MetaConfig] = Schema.derived }

  @agentDefinition("ctor-meta-agent")
  @description("Agent with case class constructor.")
  trait CtorMetaAgent extends BaseAgent[MetaConfig] {
    def info(): Future[String]
  }

  @agentImplementation()
  final class CtorMetaAgentImpl(private val config: MetaConfig) extends CtorMetaAgent {
    override def info(): Future[String] = Future.successful(s"${config.host}:${config.port}")
  }

  private lazy val ctorDefn: AgentDefinition[CtorMetaAgent] =
    AgentImplementation.register[CtorMetaAgent]("ctor-meta-agent")(new CtorMetaAgentImpl(MetaConfig("h", 80)))

  test("constructor agent registration succeeds") {
    assert(ctorDefn.typeName == "ctor-meta-agent")
  }

  test("constructor agent has correct method count") {
    assert(ctorDefn.methodMetadata.size == 1)
    assert(ctorDefn.methodMetadata.head.metadata.name == "info")
  }

  // ---------------------------------------------------------------------------
  // Explicit Durable mode
  // ---------------------------------------------------------------------------

  @agentDefinition("explicit-durable-agent", mode = DurabilityMode.Durable)
  trait ExplicitDurableAgent extends BaseAgent[Unit] {
    def ping(): Future[String]
  }

  @agentImplementation()
  final class ExplicitDurableAgentImpl() extends ExplicitDurableAgent {
    override def ping(): Future[String] = Future.successful("pong")
  }

  private lazy val durDefn: AgentDefinition[ExplicitDurableAgent] =
    AgentImplementation.register[ExplicitDurableAgent]("explicit-durable-agent")(new ExplicitDurableAgentImpl())

  test("explicit durable agent has Durable mode") {
    assert(durDefn.mode == AgentMode.Durable)
  }

  // ---------------------------------------------------------------------------
  // Schema verification: method input/output schemas are structurally correct
  // ---------------------------------------------------------------------------

  test("echo method inputSchema has tuple tag") {
    val echo  = defn.methodMetadata.find(_.metadata.name == "echo").get
    val input = echo.inputSchema
    assert(input.selectDynamic("tag").asInstanceOf[String] == "tuple")
  }

  test("echo method outputSchema has tuple tag") {
    val echo   = defn.methodMetadata.find(_.metadata.name == "echo").get
    val output = echo.outputSchema
    assert(output.selectDynamic("tag").asInstanceOf[String] == "tuple")
  }

  test("add method inputSchema has tuple tag with elements") {
    val add   = defn.methodMetadata.find(_.metadata.name == "add").get
    val input = add.inputSchema
    assert(input.selectDynamic("tag").asInstanceOf[String] == "tuple")
  }

  // ---------------------------------------------------------------------------
  // Multiple descriptions on different methods
  // ---------------------------------------------------------------------------

  test("multiple methods can have different descriptions") {
    val echo = defn.methodMetadata.find(_.metadata.name == "echo").get
    val add  = defn.methodMetadata.find(_.metadata.name == "add").get
    assert(echo.metadata.description != add.metadata.description)
  }

  test("agent trait description is captured in metadata") {
    assert(defn.metadata.description.contains("An agent used for metadata tests."))
  }
}
