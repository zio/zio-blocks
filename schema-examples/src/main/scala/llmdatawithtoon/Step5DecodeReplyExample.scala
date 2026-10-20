package llmdatawithtoon

import zio.blocks.schema._
import zio.blocks.schema.toon._

/**
 * How to Send Compact Data to an LLM with TOON and Parse the Reply — Decoding
 * the Model's Reply
 *
 * Decodes a well-formed reply, prints the error for each malformed reply, and
 * strips a code fence before decoding.
 *
 * Run with: cd schema-examples && sbt "runMain
 * llmdatawithtoon.Step5DecodeReplyExample"
 */
object Step5DecodeReplyExample extends App {

  case class Recommendation(title: String, reason: String)

  object Recommendation {
    implicit val schema: Schema[Recommendation] = Schema.derived
  }

  val replyCodec: ToonCodec[Recommendation] =
    Recommendation.schema.derive(ToonCodecDeriver.withRejectExtraFields(true))

  val reply =
    """title: Zionomicon
      |reason: It teaches ZIO, an effect system for Scala, from first steps to concurrency.""".stripMargin

  println(replyCodec.decode(reply))

  val malformedReplies = List(
    "missing field"     -> "title: Zionomicon",
    "renamed field"     -> "book: Zionomicon\nreason: It teaches ZIO.",
    "extra field"       -> "title: Zionomicon\nreason: It teaches ZIO.\nconfidence: 0.9",
    "prose before TOON" -> "Sure! Here is my pick:\ntitle: Zionomicon\nreason: It teaches ZIO."
  )

  for ((label, text) <- malformedReplies)
    replyCodec.decode(text) match {
      case Left(error)  => println(s"$label: ${error.message}")
      case Right(value) => println(s"$label: decoded $value")
    }

  val lenientCodec = Recommendation.schema.derive(ToonFormat)

  println(lenientCodec.decode("Sure! Here is my pick:\ntitle: Zionomicon\nreason: It teaches ZIO."))

  def extractToon(reply: String): String = {
    val fence = "```"
    val open  = reply.indexOf(fence)
    if (open < 0) reply.trim
    else {
      val bodyStart = reply.indexOf('\n', open) + 1
      val close     = reply.indexOf(fence, bodyStart)
      reply.substring(bodyStart, if (close < 0) reply.length else close).trim
    }
  }

  val chattyReply =
    """Happy to help! Here is my recommendation:
      |
      |```toon
      |title: Zionomicon
      |reason: It teaches ZIO, an effect system for Scala, from first steps to concurrency.
      |```
      |
      |Enjoy the book!""".stripMargin

  println(replyCodec.decode(chattyReply).left.map(_.message))

  println(replyCodec.decode(extractToon(chattyReply)))
}
