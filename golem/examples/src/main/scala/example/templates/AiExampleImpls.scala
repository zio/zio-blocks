package example.templates

import golem.ai.{Llm, WebSearch}
import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future

@agentImplementation()
final class ChatAgentImpl(private val chatName: String) extends ChatAgent {
  private var events: Vector[Llm.Event] =
    Vector(Llm.Event.message(Llm.Message.system(s"You are a helpful assistant for $chatName.")))

  override def ask(question: String): Future[String] =
    Future.successful {
      events = events :+ Llm.Event.message(Llm.Message.user(question))

      val response = Llm.send(events, Llm.Config(model = "gpt-oss:20b"))

      events = events :+ Llm.Event.response(response)

      response.content.collect { case Llm.ContentPart.Text(t) => t }.mkString("\n")
    }

  override def history(): Future[List[String]] =
    Future.successful {
      events.toList.map {
        case _: Llm.Event.MessageEvent     => "message"
        case _: Llm.Event.ResponseEvent    => "response"
        case _: Llm.Event.ToolResultsEvent => "tool-results"
      }
    }
}

@agentImplementation()
final class ResearchAgentImpl() extends ResearchAgent {
  override def research(topic: String): Future[String] =
    Future.successful {
      val results     = searchWebForTopic(topic)
      val resultsText =
        results
          .map(r => s"- ${r.title} (${r.url})\n  ${r.snippet}")
          .mkString("\n")

      val prompt =
        s"""I'm writing a report on the topic "$topic".
           |Your job is to provide an initial overview based on your own knowledge and the search results below.
           |
           |Search results:
           |$resultsText
           |""".stripMargin

      val response =
        Llm.send(
          Vector(Llm.Event.message(Llm.Message.assistant(prompt, name = Some("research-agent")))),
          Llm.Config(model = "gpt-oss:20b")
        )

      val textResult = response.content.collect { case Llm.ContentPart.Text(t) => t }.mkString("\n")

      s"Finished research for topic $topic:\n$textResult"
    }

  private def searchWebForTopic(topic: String): List[WebSearch.SearchResult] = {
    val pagesToRetrieve = 3
    val session         =
      WebSearch.startSearch(
        WebSearch.SearchParams(
          query = topic,
          language = Some("lang_en"),
          safeSearch = Some(WebSearch.SafeSearchLevel.Off),
          maxResults = Some(10),
          advancedAnswer = Some(true)
        )
      )

    val out = scala.collection.mutable.ListBuffer.empty[WebSearch.SearchResult]
    (0 until pagesToRetrieve).foreach { _ =>
      session.nextPage().foreach(out.addOne)
    }
    out.toList
  }
}
