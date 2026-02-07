package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, JSName}
import scala.scalajs.js.typedarray.Uint8Array
import golem.wasi.Environment

/**
 * Scala.js wrapper for `golem:llm/llm@1.0.0`.
 *
 * Public API avoids `scala.scalajs.js.*` types.
 */
object Llm {
  // ----- Public Scala model types -----------------------------------------------------------

  sealed trait Role extends Product with Serializable { def tag: String }
  object Role {
    case object User      extends Role { val tag: String = "user"      }
    case object Assistant extends Role { val tag: String = "assistant" }
    case object System    extends Role { val tag: String = "system"    }
    case object Tool      extends Role { val tag: String = "tool"      }

    def fromTag(tag: String): Role =
      tag match {
        case "user"      => User
        case "assistant" => Assistant
        case "system"    => System
        case "tool"      => Tool
        case _           => User
      }
  }

  sealed trait ErrorCode extends Product with Serializable { def tag: String }
  object ErrorCode {
    case object InvalidRequest       extends ErrorCode { val tag: String = "invalid-request"       }
    case object AuthenticationFailed extends ErrorCode { val tag: String = "authentication-failed" }
    case object RateLimitExceeded    extends ErrorCode { val tag: String = "rate-limit-exceeded"   }
    case object InternalError        extends ErrorCode { val tag: String = "internal-error"        }
    case object Unsupported          extends ErrorCode { val tag: String = "unsupported"           }
    case object Unknown              extends ErrorCode { val tag: String = "unknown"               }

    def fromTag(tag: String): ErrorCode =
      tag match {
        case "invalid-request"       => InvalidRequest
        case "authentication-failed" => AuthenticationFailed
        case "rate-limit-exceeded"   => RateLimitExceeded
        case "internal-error"        => InternalError
        case "unsupported"           => Unsupported
        case "unknown"               => Unknown
        case _                       => Unknown
      }
  }

  sealed trait FinishReason extends Product with Serializable { def tag: String }
  object FinishReason {
    case object Stop          extends FinishReason { val tag: String = "stop"           }
    case object Length        extends FinishReason { val tag: String = "length"         }
    case object ToolCalls     extends FinishReason { val tag: String = "tool-calls"     }
    case object ContentFilter extends FinishReason { val tag: String = "content-filter" }
    case object Error         extends FinishReason { val tag: String = "error"          }
    case object Other         extends FinishReason { val tag: String = "other"          }

    def fromTag(tag: String): FinishReason =
      tag match {
        case "stop"           => Stop
        case "length"         => Length
        case "tool-calls"     => ToolCalls
        case "content-filter" => ContentFilter
        case "error"          => Error
        case _                => Other
      }
  }

  sealed trait ImageDetail extends Product with Serializable { def tag: String }
  object ImageDetail {
    case object Low  extends ImageDetail { val tag: String = "low"  }
    case object High extends ImageDetail { val tag: String = "high" }
    case object Auto extends ImageDetail { val tag: String = "auto" }

    def fromTag(tag: String): ImageDetail =
      tag match {
        case "low"  => Low
        case "high" => High
        case "auto" => Auto
        case _      => Auto
      }
  }

  final case class ImageUrl(url: String, detail: Option[ImageDetail])
  final case class ImageSource(data: Array[Byte], mimeType: String, detail: Option[ImageDetail])

  sealed trait ImageReference extends Product with Serializable
  object ImageReference {
    final case class Url(value: ImageUrl)       extends ImageReference
    final case class Inline(value: ImageSource) extends ImageReference
  }

  sealed trait ContentPart extends Product with Serializable
  object ContentPart {
    final case class Text(value: String)          extends ContentPart
    final case class Image(value: ImageReference) extends ContentPart
  }

  final case class Message(role: Role, name: Option[String], content: List[ContentPart])
  object Message {
    def system(text: String): Message                                 = Message(Role.System, None, List(ContentPart.Text(text)))
    def user(text: String): Message                                   = Message(Role.User, None, List(ContentPart.Text(text)))
    def assistant(text: String, name: Option[String] = None): Message =
      Message(Role.Assistant, name, List(ContentPart.Text(text)))
  }

  final case class ToolDefinition(name: String, description: Option[String], parametersSchema: String)
  final case class ToolCall(id: String, name: String, argumentsJson: String)
  final case class ToolSuccess(id: String, name: String, resultJson: String, executionTimeMs: Option[Int])
  final case class ToolFailure(id: String, name: String, errorMessage: String, errorCode: Option[String])

  sealed trait ToolResult extends Product with Serializable
  object ToolResult {
    final case class Success(value: ToolSuccess) extends ToolResult
    final case class Error(value: ToolFailure)   extends ToolResult
  }

  final case class Kv(key: String, value: String)

  final case class Config(
    model: String,
    temperature: Option[Float] = None,
    maxTokens: Option[Int] = None,
    stopSequences: Option[List[String]] = None,
    tools: Option[List[ToolDefinition]] = None,
    toolChoice: Option[String] = None,
    providerOptions: Option[List[Kv]] = None
  )

  final case class Usage(inputTokens: Option[Int], outputTokens: Option[Int], totalTokens: Option[Int])

  final case class ResponseMetadata(
    finishReason: Option[FinishReason],
    usage: Option[Usage],
    providerId: Option[String],
    timestamp: Option[String],
    providerMetadataJson: Option[String]
  )

  final case class Error(code: ErrorCode, message: String, providerErrorJson: Option[String])

  final case class Response(
    id: String,
    content: List[ContentPart],
    toolCalls: List[ToolCall],
    metadata: ResponseMetadata
  )

  sealed trait Event extends Product with Serializable
  object Event {
    final case class MessageEvent(message: Message)              extends Event
    final case class ResponseEvent(response: Response)           extends Event
    final case class ToolResultsEvent(results: List[ToolResult]) extends Event

    def message(m: Message): Event               = MessageEvent(m)
    def response(r: Response): Event             = ResponseEvent(r)
    def toolResults(rs: List[ToolResult]): Event = ToolResultsEvent(rs)
  }

  final case class StreamDelta(content: Option[List[ContentPart]], toolCalls: Option[List[ToolCall]])

  sealed trait StreamEvent extends Product with Serializable
  object StreamEvent {
    final case class Delta(value: StreamDelta)          extends StreamEvent
    final case class Finish(metadata: ResponseMetadata) extends StreamEvent
  }

  final class ChatStream private[golem] (private val underlying: js.Dynamic) {
    def pollNext(): Option[List[Either[Error, StreamEvent]]] =
      decodeStreamChunk(callStream("pollNext", "poll-next"))

    def getNext(): List[Either[Error, StreamEvent]] =
      decodeStreamChunk(callStream("getNext", "get-next")).getOrElse(Nil)

    private def callStream(primary: String, secondary: String): js.Dynamic = {
      val fn =
        if (!js.isUndefined(underlying.selectDynamic(primary))) underlying.selectDynamic(primary)
        else underlying.selectDynamic(secondary)
      fn.call(underlying).asInstanceOf[js.Dynamic]
    }
  }

  // ----- Public API -----------------------------------------------------------------------

  def sendResult(events: Vector[Event], config: Config): Either[Error, Response] = {
    ensureLlmEnvConfigured()
    val raw = LlmModule.send(toJsEvents(events), toJsConfig(config))
    decodeResult(raw)(fromJsResponse, fromJsError)
  }

  def send(events: Vector[Event], config: Config): Response =
    sendResult(events, config) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"LLM error ${err.code.tag}: ${err.message}")
    }

  def stream(events: Vector[Event], config: Config): ChatStream = {
    ensureLlmEnvConfigured()
    new ChatStream(LlmModule.stream(toJsEvents(events), toJsConfig(config)).asInstanceOf[js.Dynamic])
  }

  // ----- Private interop ------------------------------------------------------------------

  private type JObj = js.Dictionary[js.Any]

  private def toJsEvents(events: Vector[Event]): js.Array[JObj] =
    js.Array(events.map(toJsEvent): _*)

  private val llmProviderEnvKeys: List[String] =
    List(
      "ANTHROPIC_API_KEY",
      "OPENAI_API_KEY",
      "OPENROUTER_API_KEY",
      "XAI_API_KEY"
    )

  private val bedrockEnvKeys: List[String] =
    List("AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY", "AWS_REGION")

  private def ensureLlmEnvConfigured(): Unit = {
    val env        = Environment.getEnvironment()
    val hasSimple  = llmProviderEnvKeys.exists(env.contains)
    val hasBedrock = bedrockEnvKeys.forall(env.contains)
    val hasOllama  = env.contains("GOLEM_OLLAMA_BASE_URL")

    if (!hasSimple && !hasBedrock && !hasOllama) {
      val expected =
        (llmProviderEnvKeys ++ bedrockEnvKeys ++ List("GOLEM_OLLAMA_BASE_URL")).distinct.sorted.mkString(", ")
      throw new IllegalStateException(
        s"LLM provider not configured. Set one of: $expected"
      )
    }
  }

  private def toJsEvent(ev: Event): JObj =
    ev match {
      case Event.MessageEvent(m) =>
        js.Dictionary[js.Any]("tag" -> "message", "val" -> toJsMessage(m))
      case Event.ResponseEvent(r) =>
        js.Dictionary[js.Any]("tag" -> "response", "val" -> toJsResponse(r))
      case Event.ToolResultsEvent(results) =>
        js.Dictionary[js.Any]("tag" -> "tool-results", "val" -> js.Array(results.map(toJsToolResult): _*))
    }

  private def toJsMessage(m: Message): JObj =
    js.Dictionary[js.Any](
      "role"    -> m.role.tag,
      "name"    -> m.name.fold[js.Any](js.undefined)(identity),
      "content" -> js.Array(m.content.map(toJsContentPart): _*)
    )

  private def toJsContentPart(p: ContentPart): JObj =
    p match {
      case ContentPart.Text(t)    => js.Dictionary[js.Any]("tag" -> "text", "val" -> t)
      case ContentPart.Image(img) =>
        js.Dictionary[js.Any]("tag" -> "image", "val" -> toJsImageReference(img))
    }

  private def toJsImageReference(ref: ImageReference): JObj =
    ref match {
      case ImageReference.Url(value) =>
        js.Dictionary[js.Any]("tag" -> "url", "val" -> toJsImageUrl(value))
      case ImageReference.Inline(value) =>
        js.Dictionary[js.Any]("tag" -> "inline", "val" -> toJsImageSource(value))
    }

  private def toJsImageUrl(url: ImageUrl): JObj =
    js.Dictionary[js.Any](
      "url"    -> url.url,
      "detail" -> url.detail.fold[js.Any](js.undefined)(d => d.tag)
    )

  private def toJsImageSource(source: ImageSource): JObj =
    js.Dictionary[js.Any](
      "data"      -> toUint8Array(source.data),
      "mime-type" -> source.mimeType,
      "detail"    -> source.detail.fold[js.Any](js.undefined)(d => d.tag)
    )

  private def toJsToolDefinition(t: ToolDefinition): JObj =
    js.Dictionary[js.Any](
      "name"              -> t.name,
      "description"       -> t.description.fold[js.Any](js.undefined)(identity),
      "parameters-schema" -> t.parametersSchema
    )

  private def toJsToolCall(call: ToolCall): JObj =
    js.Dictionary[js.Any](
      "id"             -> call.id,
      "name"           -> call.name,
      "arguments-json" -> call.argumentsJson
    )

  private def toJsResponse(r: Response): JObj =
    js.Dictionary[js.Any](
      "id"         -> r.id,
      "content"    -> js.Array(r.content.map(toJsContentPart): _*),
      "tool-calls" -> js.Array(r.toolCalls.map(toJsToolCall): _*),
      "metadata"   -> toJsMetadata(r.metadata)
    )

  private def toJsToolResult(r: ToolResult): JObj =
    r match {
      case ToolResult.Success(value) =>
        js.Dictionary[js.Any]("tag" -> "success", "val" -> toJsToolSuccess(value))
      case ToolResult.Error(value) =>
        js.Dictionary[js.Any]("tag" -> "error", "val" -> toJsToolFailure(value))
    }

  private def toJsToolSuccess(value: ToolSuccess): JObj =
    js.Dictionary[js.Any](
      "id"                -> value.id,
      "name"              -> value.name,
      "result-json"       -> value.resultJson,
      "execution-time-ms" -> value.executionTimeMs.fold[js.Any](js.undefined)(identity)
    )

  private def toJsToolFailure(value: ToolFailure): JObj =
    js.Dictionary[js.Any](
      "id"            -> value.id,
      "name"          -> value.name,
      "error-message" -> value.errorMessage,
      "error-code"    -> value.errorCode.fold[js.Any](js.undefined)(identity)
    )

  private def toJsUsage(usage: Usage): JObj =
    js.Dictionary[js.Any](
      "input-tokens"  -> usage.inputTokens.fold[js.Any](js.undefined)(identity),
      "output-tokens" -> usage.outputTokens.fold[js.Any](js.undefined)(identity),
      "total-tokens"  -> usage.totalTokens.fold[js.Any](js.undefined)(identity)
    )

  private def toJsMetadata(meta: ResponseMetadata): JObj =
    js.Dictionary[js.Any](
      "finish-reason"          -> meta.finishReason.fold[js.Any](js.undefined)(_.tag),
      "usage"                  -> meta.usage.fold[js.Any](js.undefined)(toJsUsage),
      "provider-id"            -> meta.providerId.fold[js.Any](js.undefined)(identity),
      "timestamp"              -> meta.timestamp.fold[js.Any](js.undefined)(identity),
      "provider-metadata-json" -> meta.providerMetadataJson.fold[js.Any](js.undefined)(identity)
    )

  private def toJsConfig(config: Config): JObj =
    js.Dictionary[js.Any](
      "model"            -> config.model,
      "temperature"      -> config.temperature.fold[js.Any](js.undefined)(identity),
      "max-tokens"       -> config.maxTokens.fold[js.Any](js.undefined)(identity),
      "stop-sequences"   -> config.stopSequences.fold[js.Any](js.undefined)(ss => js.Array(ss: _*)),
      "tools"            -> config.tools.fold[js.Any](js.undefined)(t => js.Array(t.map(toJsToolDefinition): _*)),
      "tool-choice"      -> config.toolChoice.fold[js.Any](js.undefined)(identity),
      "provider-options" -> config.providerOptions.fold[js.Any](js.undefined)(opts => js.Array(opts.map(toJsKv): _*))
    )

  private def toJsKv(kv: Kv): JObj =
    js.Dictionary[js.Any]("key" -> kv.key, "value" -> kv.value)

  private def fromJsResponse(o: js.Dynamic): Response = {
    val obj      = o.asInstanceOf[JObj]
    val id       = obj.getOrElse("id", "").toString
    val content  = obj.get("content").map(asArray).getOrElse(js.Array()).toList.map(fromJsContentPart)
    val toolList = obj.get("tool-calls").map(asArray).getOrElse(js.Array()).toList.map(fromJsToolCall)
    val metadata = optionDynamic(obj.getOrElse("metadata", null))
      .map(fromJsMetadata)
      .getOrElse(
        ResponseMetadata(None, None, None, None, None)
      )
    Response(id, content, toolList, metadata)
  }

  private def fromJsContentPart(o: js.Dynamic): ContentPart = {
    val tag = tagOf(o)
    if (tag == "image") ContentPart.Image(fromJsImageReference(valOf(o)))
    else ContentPart.Text(String.valueOf(valOf(o)))
  }

  private def fromJsImageReference(o: js.Dynamic): ImageReference = {
    val tag = tagOf(o)
    if (tag == "inline") ImageReference.Inline(fromJsImageSource(valOf(o)))
    else ImageReference.Url(fromJsImageUrl(valOf(o)))
  }

  private def fromJsImageUrl(o: js.Dynamic): ImageUrl = {
    val obj    = o.asInstanceOf[JObj]
    val url    = obj.getOrElse("url", "").toString
    val detail = obj.get("detail").map(_.toString).map(ImageDetail.fromTag)
    ImageUrl(url, detail)
  }

  private def fromJsImageSource(o: js.Dynamic): ImageSource = {
    val obj    = o.asInstanceOf[JObj]
    val data   = toByteArray(obj.get("data"))
    val mime   = obj.getOrElse("mime-type", "").toString
    val detail = obj.get("detail").map(_.toString).map(ImageDetail.fromTag)
    ImageSource(data, mime, detail)
  }

  private def fromJsToolCall(o: js.Dynamic): ToolCall = {
    val obj = o.asInstanceOf[JObj]
    ToolCall(
      id = obj.getOrElse("id", "").toString,
      name = obj.getOrElse("name", "").toString,
      argumentsJson = obj.getOrElse("arguments-json", "").toString
    )
  }

  private def fromJsMetadata(o: js.Dynamic): ResponseMetadata = {
    val obj    = o.asInstanceOf[JObj]
    val finish = optionDynamic(obj.getOrElse("finish-reason", null)).map(_.toString).map(FinishReason.fromTag)
    val usage  = optionDynamic(obj.getOrElse("usage", null)).map(fromJsUsage)
    ResponseMetadata(
      finishReason = finish,
      usage = usage,
      providerId = optionDynamic(obj.getOrElse("provider-id", null)).map(_.toString),
      timestamp = optionDynamic(obj.getOrElse("timestamp", null)).map(_.toString),
      providerMetadataJson = optionDynamic(obj.getOrElse("provider-metadata-json", null)).map(_.toString)
    )
  }

  private def fromJsUsage(o: js.Dynamic): Usage = {
    val obj = o.asInstanceOf[JObj]
    Usage(
      inputTokens = optionDynamic(obj.getOrElse("input-tokens", null)).map(toInt),
      outputTokens = optionDynamic(obj.getOrElse("output-tokens", null)).map(toInt),
      totalTokens = optionDynamic(obj.getOrElse("total-tokens", null)).map(toInt)
    )
  }

  private def fromJsError(o: js.Dynamic): Error = {
    val obj = o.asInstanceOf[JObj]
    Error(
      code = ErrorCode.fromTag(obj.getOrElse("code", "unknown").toString),
      message = obj.getOrElse("message", "").toString,
      providerErrorJson = obj.get("provider-error-json").map(_.toString)
    )
  }

  private def fromJsStreamEvent(o: js.Dynamic): StreamEvent = {
    val tag = tagOf(o)
    if (tag == "finish") StreamEvent.Finish(fromJsMetadata(valOf(o)))
    else StreamEvent.Delta(fromJsStreamDelta(valOf(o)))
  }

  private def fromJsStreamDelta(o: js.Dynamic): StreamDelta = {
    val obj       = o.asInstanceOf[JObj]
    val content   = optionDynamic(obj.getOrElse("content", null)).map(asArray).map(_.toList.map(fromJsContentPart))
    val toolCalls = optionDynamic(obj.getOrElse("tool-calls", null)).map(asArray).map(_.toList.map(fromJsToolCall))
    StreamDelta(content, toolCalls)
  }

  private def decodeStreamChunk(raw: js.Dynamic): Option[List[Either[Error, StreamEvent]]] =
    optionDynamic(raw).map { value =>
      val arr = value.asInstanceOf[js.Array[js.Dynamic]]
      arr.toList.map { item =>
        decodeResult(item)(fromJsStreamEvent, fromJsError)
      }
    }

  private def decodeResult[A](raw: js.Dynamic)(ok: js.Dynamic => A, err: js.Dynamic => Error): Either[Error, A] = {
    val tag = tagOf(raw)
    tag match {
      case "ok" | "success" => Right(ok(valOf(raw)))
      case "err" | "error"  => Left(err(valOf(raw)))
      case _                => Right(ok(raw))
    }
  }

  private def tagOf(value: js.Dynamic): String = {
    val tag = value.asInstanceOf[js.Dynamic].selectDynamic("tag")
    if (js.isUndefined(tag) || tag == null) "" else tag.toString
  }

  private def valOf(value: js.Dynamic): js.Dynamic = {
    val dyn = value.asInstanceOf[js.Dynamic]
    if (!js.isUndefined(dyn.selectDynamic("val"))) dyn.selectDynamic("val").asInstanceOf[js.Dynamic]
    else if (!js.isUndefined(dyn.selectDynamic("value"))) dyn.selectDynamic("value").asInstanceOf[js.Dynamic]
    else dyn
  }

  private def asArray(value: js.Any): js.Array[js.Dynamic] =
    value.asInstanceOf[js.Array[js.Dynamic]]

  private def optionDynamic(value: js.Any): Option[js.Dynamic] =
    if (value == null || js.isUndefined(value)) None
    else {
      val dyn = value.asInstanceOf[js.Dynamic]
      val tag = dyn.selectDynamic("tag")
      if (!js.isUndefined(tag) && tag != null) {
        tag.toString match {
          case "some" => Some(dyn.selectDynamic("val").asInstanceOf[js.Dynamic])
          case "none" => None
          case _      => Some(dyn)
        }
      } else Some(dyn)
    }

  private def toInt(value: js.Any): Int =
    value.toString.toInt

  private def toByteArray(value: Option[js.Any]): Array[Byte] =
    value match {
      case None                   => Array.emptyByteArray
      case Some(arr: js.Array[_]) =>
        arr.map(_.toString.toInt.toByte).toArray
      case Some(typed: Uint8Array) =>
        val out = new Array[Byte](typed.length)
        var i   = 0
        while (i < typed.length) {
          out(i) = typed(i).toByte
          i += 1
        }
        out
      case Some(other) =>
        other.toString.getBytes("UTF-8")
    }

  private def toUint8Array(bytes: Array[Byte]): Uint8Array = {
    val array = new Uint8Array(bytes.length)
    var i     = 0
    while (i < bytes.length) {
      array(i) = (bytes(i) & 0xff).toShort
      i += 1
    }
    array
  }

  @js.native
  @JSImport("golem:llm/llm@1.0.0", JSImport.Namespace)
  private object LlmModule extends js.Object {
    def send(
      events: js.Array[js.Dictionary[js.Any]],
      config: js.Dictionary[js.Any]
    ): js.Dynamic = js.native
    @JSName("%stream")
    def stream(
      events: js.Array[js.Dictionary[js.Any]],
      config: js.Dictionary[js.Any]
    ): js.Dynamic = js.native
  }
}
