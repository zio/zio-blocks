package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js wrapper for `golem:llm/llm@1.0.0`.
 *
 * Public API intentionally avoids `scala.scalajs.js.*` types.
 */
object Llm {
  // ----- Public Scala model types -----------------------------------------------------------

  final case class Config(model: String)

  sealed trait ContentPart extends Product with Serializable
  object ContentPart {
    final case class Text(value: String) extends ContentPart
  }

  final case class Message(role: String, name: Option[String], content: List[ContentPart])
  object Message {
    def system(text: String): Message = Message("system", None, List(ContentPart.Text(text)))
    def user(text: String): Message   = Message("user", None, List(ContentPart.Text(text)))
    def assistant(text: String, name: Option[String] = None): Message =
      Message("assistant", name, List(ContentPart.Text(text)))
  }

  sealed trait Event extends Product with Serializable {
    def tag: String
  }
  object Event {
    final case class MessageEvent(message: Message) extends Event { override val tag: String = "message" }
    final case class ResponseEvent(response: Response) extends Event { override val tag: String = "response" }
    def message(m: Message): Event   = MessageEvent(m)
    def response(r: Response): Event = ResponseEvent(r)
  }

  final case class Response(content: List[ContentPart])

  // ----- Public API -----------------------------------------------------------------------

  /**
   * Send a single LLM request.
   *
   * Note: if the underlying host library reports an error, this throws an `IllegalStateException`.
   * (We can switch this to `Either` if you prefer, but this keeps user code very small.)
   */
  def send(events: Vector[Event], config: Config): Response = {
    val jsEvents = js.Array(events.map(toJsEvent): _*)
    val jsConfig = js.Dictionary[js.Any]("model" -> config.model)
    val raw      = LlmModule.send(jsEvents, jsConfig)
    fromJsResponse(raw)
  }

  // ----- Private interop ------------------------------------------------------------------

  private type JObj = js.Dictionary[js.Any]

  private def toJsEvent(ev: Event): JObj =
    ev match {
      case Event.MessageEvent(m) =>
        js.Dictionary[js.Any](
          "tag" -> "message",
          "val" -> toJsMessage(m)
        )
      case Event.ResponseEvent(r) =>
        js.Dictionary[js.Any](
          "tag" -> "response",
          "val" -> toJsResponse(r)
        )
    }

  private def toJsMessage(m: Message): JObj =
    js.Dictionary[js.Any](
      "role"    -> m.role,
      "name"    -> m.name.fold[js.Any](js.undefined)(identity),
      "content" -> js.Array(m.content.map(toJsContentPart): _*)
    )

  private def toJsContentPart(p: ContentPart): JObj =
    p match {
      case ContentPart.Text(t) => js.Dictionary[js.Any]("tag" -> "text", "val" -> t)
    }

  private def fromJsResponse(o: JObj): Response = {
    val parts = o("content").asInstanceOf[js.Array[JObj]].toList.map(fromJsContentPart)
    Response(parts)
  }

  private def fromJsContentPart(o: JObj): ContentPart =
    o.get("tag").map(_.toString).fold[ContentPart](ContentPart.Text(o.toString)) {
      case "text" => ContentPart.Text(o.getOrElse("val", "").toString)
      case other  => ContentPart.Text(s"[$other] ${o.getOrElse("val", "").toString}")
    }

  private def toJsResponse(r: Response): JObj =
    js.Dictionary[js.Any](
      "content" -> js.Array(r.content.map(toJsContentPart): _*)
    )

  @js.native
  @JSImport("golem:llm/llm@1.0.0", JSImport.Namespace)
  private object LlmModule extends js.Object {
    def send(
      events: js.Array[js.Dictionary[js.Any]],
      config: js.Dictionary[js.Any]
    ): js.Dictionary[js.Any] = js.native
  }
}

