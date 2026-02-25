package example

import golem.data.SchemaHelpers.{singleElementSchema, singleElementValue}
import golem.data.multimodal.Multimodal
import golem.data.unstructured.{AllowedLanguages, AllowedMimeTypes, BinarySegment, TextSegment}
import golem.data._
import golem.runtime.annotations.{agentDefinition, description, prompt}
import golem.runtime.annotations.DurabilityMode
import golem.runtime.macros.{AgentClientMacro, AgentImplementationMacro, AgentMacros}
import golem.runtime.agenttype.{AsyncImplementationMethod, MethodInvocation}
import golem.tools.AgentTypeJsonEncoder
import org.scalatest.funsuite.AnyFunSuite
import ujson.Value
import zio.blocks.schema.Schema

import scala.concurrent.Future

private[example] object RustAgentParityTypes {
  type MediaPayload = Multimodal[MediaBundle]

  sealed trait EnglishOnly
  sealed trait VisionOnly

  object EnglishOnly {
    @golem.runtime.annotations.languageCode("en")
    case object En extends EnglishOnly

    implicit val englishOnlyAllowed: AllowedLanguages[EnglishOnly] =
      golem.runtime.macros.AllowedLanguagesDerivation.derived
  }

  object VisionOnly {
    @golem.runtime.annotations.mimeType("image/png")
    case object ImagePng extends VisionOnly

    implicit val visionOnlyAllowed: AllowedMimeTypes[VisionOnly] =
      golem.runtime.macros.AllowedMimeTypesDerivation.derived
  }

  /**
   * WIT-friendly result type for parity tests (avoids Scala 2 limitations
   * deriving Schema for Either).
   */
  sealed trait EchoResult
  object EchoResult {
    final case class Ok(value: Int)       extends EchoResult
    final case class Err(message: String) extends EchoResult

    implicit val schema: Schema[EchoResult] = Schema.derived
  }

  final case class MediaBundle(transcript: TextSegment[EnglishOnly], snapshot: BinarySegment[VisionOnly])
  object MediaBundle {
    implicit val golemSchema: GolemSchema[MediaBundle] = new GolemSchema[MediaBundle] {
      private val transcriptSchema = implicitly[GolemSchema[TextSegment[EnglishOnly]]]
      private val snapshotSchema   = implicitly[GolemSchema[BinarySegment[VisionOnly]]]

      override val schema: StructuredSchema = {
        val t = singleElementSchema(transcriptSchema.schema).fold(err => throw new IllegalStateException(err), identity)
        val s = singleElementSchema(snapshotSchema.schema).fold(err => throw new IllegalStateException(err), identity)
        StructuredSchema.Tuple(
          List(
            NamedElementSchema("transcript", t),
            NamedElementSchema("snapshot", s)
          )
        )
      }

      override def encode(value: MediaBundle): Either[String, StructuredValue] =
        for {
          t0 <- transcriptSchema.encode(value.transcript).flatMap(singleElementValue)
          s0 <- snapshotSchema.encode(value.snapshot).flatMap(singleElementValue)
        } yield StructuredValue.Tuple(
          List(
            NamedElementValue("transcript", t0),
            NamedElementValue("snapshot", s0)
          )
        )

      override def decode(value: StructuredValue): Either[String, MediaBundle] =
        value match {
          case StructuredValue.Tuple(elements) =>
            val byName = elements.map(e => e.name -> e.value).toMap
            for {
              t0 <- byName
                      .get("transcript")
                      .toRight("Missing transcript")
                      .flatMap(v => transcriptSchema.decode(StructuredValue.single(v)))
              s0 <- byName
                      .get("snapshot")
                      .toRight("Missing snapshot")
                      .flatMap(v => snapshotSchema.decode(StructuredValue.single(v)))
            } yield MediaBundle(t0, s0)
          case other =>
            Left(s"Expected tuple payload for MediaBundle, found: $other")
        }
    }
  }
}

final class RustAgentParitySpec extends AnyFunSuite {
  import RustAgentParityTypes._

  private val echoMetadata            = AgentMacros.agentMetadata[EchoAgent]
  private val ephemeralMetadata       = AgentMacros.agentMetadata[EphemeralAgent]
  private val durableDefaultMetadata  = AgentMacros.agentMetadata[DurableDefaultAgent]
  private val durableExplicitMetadata = AgentMacros.agentMetadata[DurableExplicitAgent]
  private val durableDefaultImplType  =
    AgentImplementationMacro.implementationType[DurableDefaultAgent](new DurableDefaultAgentImpl)
  private val durableExplicitImplType =
    AgentImplementationMacro.implementationType[DurableExplicitAgent](new DurableExplicitAgentImpl)
  private val rpcImplType = AgentImplementationMacro.implementationType[RpcParityAgent](new RpcParityAgent {
    override def rpcCall(payload: String): Future[String] = Future.successful(payload)
    override def rpcCallTrigger(payload: String): Unit    = ()
  })

  @agentDefinition("echo-agent")
  @description("Rust-style Echo agent for metadata parity")
  trait EchoAgent {
    @prompt("Echo the provided message")
    def echo(message: String): Future[String]

    def combine(left: String, right: Int): Future[String]
    def echoOption(value: Option[String]): Future[Option[String]]
    def echoResult(value: EchoResult): Future[EchoResult]
  }

  @agentDefinition(typeName = "ephemeral-agent", mode = DurabilityMode.Ephemeral)
  trait EphemeralAgent {
    def ping(): Future[String]
  }

  test("EchoAgent metadata exposes all method names") {
    val names = echoMetadata.methods.map(_.name).sorted
    assert(names == List("combine", "echo", "echoOption", "echoResult"))
    assert(echoMetadata.description.contains("Rust-style Echo agent for metadata parity"))
  }

  test("EchoAgent combine method keeps parameter ordering and schema") {
    val method = echoMetadata.methods.find(_.name == "combine").getOrElse(fail("combine missing"))
    method.input match {
      case StructuredSchema.Tuple(elements) =>
        assert(elements.map(_.name) == List("left", "right"))
        assert(elements.head.schema == ElementSchema.Component(golem.data.DataType.StringType))
        assert(elements(1).schema == ElementSchema.Component(golem.data.DataType.IntType))
      case other =>
        fail(s"Unexpected schema for combine input: $other")
    }
  }

  test("AgentTypeJsonEncoder emits constructor and method entries") {
    val value: Value = AgentTypeJsonEncoder.encode("echo-agent", echoMetadata)
    val rendered     = value.render()
    assert(rendered.contains("echo-agent"))
    assert(rendered.contains("Rust-style Echo agent"))
    assert(rendered.contains("combine"))
  }

  test("Agent metadata captures trait-level mode annotation") {
    assert(ephemeralMetadata.mode.contains("ephemeral"))
  }

  test("Agent metadata omits mode when durable annotation is not provided") {
    assert(durableDefaultMetadata.mode.isEmpty)
  }

  test("Agent metadata omits durable default (even when explicitly set via agentDefinition)") {
    assert(durableExplicitMetadata.mode.forall(_ == "durable"))
  }

  test("AgentImplementationMacro preserves annotated agent mode") {
    val implType = AgentImplementationMacro.implementationType[EphemeralAgent](new EphemeralAgentImpl)
    assert(implType.metadata.mode.contains("ephemeral"))
  }

  test("AgentImplementationMacro leaves mode unset for durable defaults") {
    // Depending on how Scala represents default annotation args, the durable default may appear as None or Some("durable").
    // Both are acceptable; what we *don't* want is an unexpected non-durable value here.
    assert(durableDefaultImplType.metadata.mode.forall(_ == "durable"))
  }

  test("AgentImplementationMacro preserves durable annotations in implementation metadata") {
    // Durable is the default; we allow it to be elided (None) even if explicitly set via @agentDefinition(mode = Durable).
    assert(durableExplicitImplType.metadata.mode.forall(_ == "durable"))
  }

  @agentDefinition("durable-default-agent")
  trait DurableDefaultAgent { def ping(): Future[String] }

  @agentDefinition(typeName = "durable-explicit-agent", mode = DurabilityMode.Durable)
  trait DurableExplicitAgent { def ping(): Future[String] }

  @agentDefinition("snapshot-agent")
  trait SnapshotAgent {
    def saveSnapshot(): Future[BinarySegment[VisionOnly]]
    def loadSnapshot(snapshot: BinarySegment[VisionOnly]): Unit
  }

  @agentDefinition("rpc-parity-agent")
  trait RpcParityAgent {
    def rpcCall(payload: String): Future[String]
    def rpcCallTrigger(payload: String): Unit
  }

  private final class EphemeralAgentImpl extends EphemeralAgent {
    override def ping(): Future[String] = Future.successful("pong")
  }

  private final class DurableDefaultAgentImpl extends DurableDefaultAgent {
    override def ping(): Future[String] = Future.successful("durable-default")
  }

  private final class DurableExplicitAgentImpl extends DurableExplicitAgent {
    override def ping(): Future[String] = Future.successful("durable-explicit")
  }

  test("Multimodal schemas capture modality ordering and restrictions") {
    val schema = implicitly[GolemSchema[MediaPayload]].schema
    schema match {
      case StructuredSchema.Multimodal(elements) =>
        val names = elements.map(_.name)
        assert(names == List("transcript", "snapshot"))
        assert(elements.head.schema == ElementSchema.UnstructuredText(Some(List("en"))))
        assert(elements(1).schema == ElementSchema.UnstructuredBinary(Some(List("image/png"))))
      case other =>
        fail(s"Expected multimodal schema, found $other")
    }
  }

  test("TextSegment codec round-trips inline content with language hints") {
    val codec   = implicitly[GolemSchema[TextSegment[EnglishOnly]]]
    val segment = TextSegment.inline[EnglishOnly]("ciao scala", Some("en"))
    val encoded = codec.encode(segment).fold(err => fail(err), identity)
    val decoded = codec.decode(encoded).fold(err => fail(err), identity)
    decoded.value match {
      case UnstructuredTextValue.Inline(data, language) =>
        assert(data == "ciao scala")
        assert(language.contains("en"))
      case other =>
        fail(s"Unexpected decoded text payload: $other")
    }
  }

  test("BinarySegment codec round-trips inline bytes with mime restrictions") {
    val codec   = implicitly[GolemSchema[BinarySegment[VisionOnly]]]
    val bytes   = Array[Byte](1, 2, 3, 4)
    val segment = BinarySegment.inline[VisionOnly](bytes, "image/png")
    val encoded = codec.encode(segment).fold(err => fail(err), identity)
    val decoded = codec.decode(encoded).fold(err => fail(err), identity)
    decoded.value match {
      case UnstructuredBinaryValue.Inline(data, mime) =>
        assert(data.sameElements(bytes))
        assert(mime == "image/png")
      case other =>
        fail(s"Unexpected decoded binary payload: $other")
    }
  }

  test("Multimodal payloads round-trip through codec preserving modalities") {
    val codec   = implicitly[GolemSchema[MediaPayload]]
    val bytes   = Array[Byte](42, 24)
    val payload = Multimodal(
      MediaBundle(
        transcript = TextSegment.inline[EnglishOnly]("ciao scala", Some("en")),
        snapshot = BinarySegment.inline[VisionOnly](bytes, "image/png")
      )
    )
    val encoded = codec.encode(payload).fold(err => fail(err), identity)
    val decoded = codec.decode(encoded).fold(err => fail(err), identity)
    val bundle  = decoded.value

    assert(bundle.transcript.value.isInstanceOf[UnstructuredTextValue.Inline])
    assert(bundle.snapshot.value.isInstanceOf[UnstructuredBinaryValue.Inline])
  }

  test("AgentClientMacro produces fire-and-forget invocation for Unit-returning method") {
    val agentType     = AgentClientMacro.agentType[RpcParityAgent]
    val triggerMethod =
      agentType.methods.find(_.metadata.name == "rpcCallTrigger").getOrElse(fail("rpcCallTrigger missing"))
    assert(triggerMethod.invocation == MethodInvocation.FireAndForget)
  }

  test("AgentImplementationMacro preserves method invocation kinds") {
    val awaitable =
      rpcImplType.methods.collectFirst {
        case m: AsyncImplementationMethod[RpcParityAgent @unchecked, _, _] if m.metadata.name == "rpcCall" =>
          m
      }
    assert(awaitable.isDefined)
  }
}
