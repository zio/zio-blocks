package golem.ai

import org.scalatest.funsuite.AnyFunSuite

final class LlmCompileSpec extends AnyFunSuite {
  import Llm._

  test("Role — all 4 variants and fromTag roundtrip") {
    val roles = List(Role.User, Role.Assistant, Role.System, Role.Tool)
    assert(roles.size == 4)
    roles.foreach(r => assert(Role.fromTag(r.tag) eq r))
    assert(Role.fromTag("unknown") == Role.User)
  }

  test("ErrorCode — all 6 variants and fromTag roundtrip") {
    val codes = List(
      ErrorCode.InvalidRequest,
      ErrorCode.AuthenticationFailed,
      ErrorCode.RateLimitExceeded,
      ErrorCode.InternalError,
      ErrorCode.Unsupported,
      ErrorCode.Unknown
    )
    assert(codes.size == 6)
    codes.foreach(c => assert(ErrorCode.fromTag(c.tag) eq c))
  }

  test("FinishReason — all 6 variants and fromTag roundtrip") {
    val reasons = List(
      FinishReason.Stop,
      FinishReason.Length,
      FinishReason.ToolCalls,
      FinishReason.ContentFilter,
      FinishReason.Error,
      FinishReason.Other
    )
    assert(reasons.size == 6)
    reasons.foreach(r => assert(FinishReason.fromTag(r.tag) eq r))
  }

  test("ImageDetail — all 3 variants and fromTag roundtrip") {
    val details = List(ImageDetail.Low, ImageDetail.High, ImageDetail.Auto)
    assert(details.size == 3)
    details.foreach(d => assert(ImageDetail.fromTag(d.tag) eq d))
  }

  test("ContentPart — Text and Image variants") {
    val text: ContentPart  = ContentPart.Text("hello")
    val image: ContentPart = ContentPart.Image(ImageReference.Url(ImageUrl("http://img", None)))
    val desc               = (text, image) match {
      case (ContentPart.Text(v), ContentPart.Image(_)) => v
      case _                                           => fail("pattern match failed")
    }
    assert(desc == "hello")
  }

  test("ImageReference — Url and Inline variants") {
    val url: ImageReference    = ImageReference.Url(ImageUrl("http://img", Some(ImageDetail.High)))
    val inline: ImageReference = ImageReference.Inline(ImageSource(Array[Byte](1, 2), "image/png", None))
    assert(url.isInstanceOf[ImageReference.Url])
    assert(inline.isInstanceOf[ImageReference.Inline])
  }

  test("Message construction and factory methods") {
    val m1 = Message(Role.User, Some("user1"), List(ContentPart.Text("hi")))
    val m2 = Message.system("sys prompt")
    val m3 = Message.user("hello")
    val m4 = Message.assistant("response", Some("bot"))
    assert(m1.role == Role.User)
    assert(m2.role == Role.System)
    assert(m3.content.size == 1)
    assert(m4.name.contains("bot"))
  }

  test("ToolDefinition and ToolCall construction") {
    val td = ToolDefinition("myTool", Some("description"), "{}")
    val tc = ToolCall("call-1", "myTool", "{\"arg\":1}")
    assert(td.name == "myTool")
    assert(tc.id == "call-1")
  }

  test("ToolResult — Success and Error variants") {
    val success: ToolResult = ToolResult.Success(ToolSuccess("1", "fn", "{}", Some(100)))
    val error: ToolResult   = ToolResult.Error(ToolFailure("2", "fn", "oops", None))
    val desc                = success match {
      case ToolResult.Success(v) => v.resultJson
      case ToolResult.Error(_)   => fail("wrong variant")
    }
    assert(desc == "{}")
    assert(error.isInstanceOf[ToolResult.Error])
  }

  test("Config construction with all optional fields") {
    val config = Config(
      model = "gpt-4",
      temperature = Some(0.7f),
      maxTokens = Some(1000),
      stopSequences = Some(List("STOP")),
      tools = Some(List(ToolDefinition("t", None, "{}"))),
      toolChoice = Some("auto"),
      providerOptions = Some(List(Kv("k", "v")))
    )
    assert(config.model == "gpt-4")
    assert(config.temperature.contains(0.7f))
    assert(config.maxTokens.contains(1000))
  }

  test("Config construction with None defaults") {
    val config = Config(model = "claude-3")
    assert(config.temperature.isEmpty)
    assert(config.tools.isEmpty)
  }

  test("Response construction") {
    val resp = Response(
      id = "resp-1",
      content = List(ContentPart.Text("hello")),
      toolCalls = Nil,
      metadata = ResponseMetadata(Some(FinishReason.Stop), None, None, None, None)
    )
    assert(resp.id == "resp-1")
    assert(resp.metadata.finishReason.contains(FinishReason.Stop))
  }

  test("Error construction") {
    val err = Error(ErrorCode.RateLimitExceeded, "slow down", Some("{}"))
    assert(err.code == ErrorCode.RateLimitExceeded)
    assert(err.providerErrorJson.contains("{}"))
  }

  test("Event — all 3 variants and factory methods") {
    val e1: Event = Event.MessageEvent(Message.user("hi"))
    val e2: Event = Event.message(Message.user("hi"))
    val e3: Event = Event.ResponseEvent(
      Response("1", Nil, Nil, ResponseMetadata(None, None, None, None, None))
    )
    val e4: Event = Event.ToolResultsEvent(Nil)
    assert(e1.isInstanceOf[Event.MessageEvent])
    assert(e2.isInstanceOf[Event.MessageEvent])
    assert(e3.isInstanceOf[Event.ResponseEvent])
    assert(e4.isInstanceOf[Event.ToolResultsEvent])
  }

  test("StreamEvent — Delta and Finish variants") {
    val delta: StreamEvent  = StreamEvent.Delta(StreamDelta(Some(List(ContentPart.Text("x"))), None))
    val finish: StreamEvent = StreamEvent.Finish(ResponseMetadata(Some(FinishReason.Stop), None, None, None, None))
    assert(delta.isInstanceOf[StreamEvent.Delta])
    assert(finish.isInstanceOf[StreamEvent.Finish])
  }

  test("Usage and ResponseMetadata construction") {
    val usage = Usage(Some(10), Some(20), Some(30))
    val meta  = ResponseMetadata(
      finishReason = Some(FinishReason.Length),
      usage = Some(usage),
      providerId = Some("openai"),
      timestamp = Some("2025-01-01"),
      providerMetadataJson = None
    )
    assert(usage.totalTokens.contains(30))
    assert(meta.providerId.contains("openai"))
  }
}
