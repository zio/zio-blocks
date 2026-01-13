package golem.examples.templates

import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

@agentImplementation()
final class ChatAgentImpl(private val chatName: String) extends ChatAgent {
  private var events: Vector[js.Dynamic] =
    Vector(
      js.Dynamic.literal(
        "tag" -> "message",
        "val" -> js.Dynamic.literal(
          "role"    -> "system",
          "name"    -> (js.undefined: js.UndefOr[String]),
          "content" -> js.Array(
            js.Dynamic.literal("tag" -> "text", "val" -> s"You are a helpful assistant for $chatName.")
          )
        )
      )
    )

  override def ask(question: String): Future[String] =
    Future.successful {
      events = events :+ js.Dynamic.literal(
        "tag" -> "message",
        "val" -> js.Dynamic.literal(
          "role"    -> "user",
          "name"    -> (js.undefined: js.UndefOr[String]),
          "content" -> js.Array(js.Dynamic.literal("tag" -> "text", "val" -> question))
        )
      )

      val response = LlmApi.send(events.toJSArray, js.Dynamic.literal("model" -> "gpt-oss:20b"))

      events = events :+ js.Dynamic.literal("tag" -> "response", "val" -> response)

      val content = response.selectDynamic("content").asInstanceOf[js.Array[js.Dynamic]]
      content.toVector
        .filter(_.selectDynamic("tag").asInstanceOf[String] == "text")
        .map(_.selectDynamic("val").asInstanceOf[String])
        .mkString("\n")
    }

  override def history(): Future[List[String]] =
    Future.successful {
      events.toList.map(_.selectDynamic("tag").asInstanceOf[String])
    }
}

@agentImplementation()
final class ResearchAgentImpl() extends ResearchAgent {
  override def research(topic: String): Future[String] =
    Future.successful {
      val results = searchWebForTopic(topic)
      val prompt  =
        s"""I'm writing a report on the topic "$topic".
           |Your job is to provide an initial overview based on your own knowledge and the search results below.
           |
           |Search results: ${js.JSON.stringify(results.asInstanceOf[js.Any])}
           |""".stripMargin

      val response =
        LlmApi.send(
          js.Array(
            js.Dynamic.literal(
              "tag" -> "message",
              "val" -> js.Dynamic.literal(
                "role"    -> "assistant",
                "name"    -> "research-agent",
                "content" -> js.Array(js.Dynamic.literal("tag" -> "text", "val" -> prompt))
              )
            )
          ),
          js.Dynamic.literal("model" -> "gpt-oss:20b")
        )

      val content    = response.selectDynamic("content").asInstanceOf[js.Array[js.Dynamic]]
      val textResult =
        content.toVector
          .filter(_.selectDynamic("tag").asInstanceOf[String] == "text")
          .map(_.selectDynamic("val").asInstanceOf[String])
          .mkString("\n")

      s"Finished research for topic $topic:\n$textResult"
    }

  private def searchWebForTopic(topic: String): js.Array[js.Dynamic] = {
    val pagesToRetrieve = 3
    val session         =
      WebSearchApi.startSearch(
        js.Dynamic.literal(
          "query"          -> topic,
          "language"       -> "lang_en",
          "safeSearch"     -> "off",
          "maxResults"     -> 10,
          "advancedAnswer" -> true
        )
      )

    val out = js.Array[js.Dynamic]()
    (0 until pagesToRetrieve).foreach { _ =>
      val page = session.nextPage().asInstanceOf[js.Array[js.Dynamic]]
      page.foreach { item =>
        out.push(
          js.Dynamic.literal(
            "url"     -> item.selectDynamic("url").asInstanceOf[String],
            "title"   -> item.selectDynamic("title").asInstanceOf[String],
            "snippet" -> item.selectDynamic("snippet").asInstanceOf[String]
          )
        )
      }
    }
    out
  }
}

private[golem] object LlmApi {
  def send(events: js.Array[js.Dynamic], config: js.Dynamic): js.Dynamic =
    ModuleLoader
      .require("golem:llm/llm@1.0.0")
      .selectDynamic("send")
      .asInstanceOf[js.Function2[js.Array[js.Dynamic], js.Dynamic, js.Dynamic]]
      .apply(events, config)
}

private[golem] object WebSearchApi {
  def startSearch(params: js.Dynamic): js.Dynamic =
    ModuleLoader
      .require("golem:web-search/web-search@1.0.0")
      .selectDynamic("startSearch")
      .asInstanceOf[js.Function1[js.Dynamic, js.Dynamic]]
      .apply(params)
}

private[golem] object ModuleLoader {
  def require(moduleName: String): js.Dynamic = {
    val req = js.Dynamic.global.selectDynamic("require")
    if (js.isUndefined(req) || req == null)
      throw new IllegalStateException(s"Module loader 'require' not available (needed for $moduleName)")
    req.asInstanceOf[js.Function1[String, js.Dynamic]].apply(moduleName)
  }
}
