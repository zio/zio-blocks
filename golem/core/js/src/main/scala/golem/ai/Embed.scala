package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js wrapper for `golem:embed/embed@1.0.0`.
 *
 * Public API avoids `scala.scalajs.js.*` types.
 */
object Embed {
  sealed trait TaskType extends Product with Serializable { def tag: String }
  object TaskType {
    case object RetrievalQuery     extends TaskType { val tag: String = "retrieval-query"     }
    case object RetrievalDocument  extends TaskType { val tag: String = "retrieval-document"  }
    case object SemanticSimilarity extends TaskType { val tag: String = "semantic-similarity" }
    case object Classification     extends TaskType { val tag: String = "classification"      }
    case object Clustering         extends TaskType { val tag: String = "clustering"          }
    case object QuestionAnswering  extends TaskType { val tag: String = "question-answering"  }
    case object FactVerification   extends TaskType { val tag: String = "fact-verification"   }
    case object CodeRetrieval      extends TaskType { val tag: String = "code-retrieval"      }

    def fromTag(tag: String): TaskType =
      tag match {
        case "retrieval-query"     => RetrievalQuery
        case "retrieval-document"  => RetrievalDocument
        case "semantic-similarity" => SemanticSimilarity
        case "classification"      => Classification
        case "clustering"          => Clustering
        case "question-answering"  => QuestionAnswering
        case "fact-verification"   => FactVerification
        case "code-retrieval"      => CodeRetrieval
        case _                     => RetrievalQuery
      }
  }

  sealed trait OutputFormat extends Product with Serializable { def tag: String }
  object OutputFormat {
    case object FloatArray extends OutputFormat { val tag: String = "float-array" }
    case object Binary     extends OutputFormat { val tag: String = "binary"      }
    case object Base64     extends OutputFormat { val tag: String = "base64"      }

    def fromTag(tag: String): OutputFormat =
      tag match {
        case "float-array" => FloatArray
        case "binary"      => Binary
        case "base64"      => Base64
        case _             => FloatArray
      }
  }

  sealed trait OutputDtype extends Product with Serializable { def tag: String }
  object OutputDtype {
    case object FloatArray extends OutputDtype { val tag: String = "float-array" }
    case object Int8       extends OutputDtype { val tag: String = "int8"        }
    case object UInt8      extends OutputDtype { val tag: String = "uint8"       }
    case object Binary     extends OutputDtype { val tag: String = "binary"      }
    case object UBinary    extends OutputDtype { val tag: String = "ubinary"     }

    def fromTag(tag: String): OutputDtype =
      tag match {
        case "float-array" => FloatArray
        case "int8"        => Int8
        case "uint8"       => UInt8
        case "binary"      => Binary
        case "ubinary"     => UBinary
        case _             => FloatArray
      }
  }

  sealed trait ErrorCode extends Product with Serializable { def tag: String }
  object ErrorCode {
    case object InvalidRequest       extends ErrorCode { val tag: String = "invalid-request"       }
    case object ModelNotFound        extends ErrorCode { val tag: String = "model-not-found"       }
    case object Unsupported          extends ErrorCode { val tag: String = "unsupported"           }
    case object AuthenticationFailed extends ErrorCode { val tag: String = "authentication-failed" }
    case object ProviderError        extends ErrorCode { val tag: String = "provider-error"        }
    case object RateLimitExceeded    extends ErrorCode { val tag: String = "rate-limit-exceeded"   }
    case object InternalError        extends ErrorCode { val tag: String = "internal-error"        }
    case object Unknown              extends ErrorCode { val tag: String = "unknown"               }

    def fromTag(tag: String): ErrorCode =
      tag match {
        case "invalid-request"       => InvalidRequest
        case "model-not-found"       => ModelNotFound
        case "unsupported"           => Unsupported
        case "authentication-failed" => AuthenticationFailed
        case "provider-error"        => ProviderError
        case "rate-limit-exceeded"   => RateLimitExceeded
        case "internal-error"        => InternalError
        case "unknown"               => Unknown
        case _                       => Unknown
      }
  }

  final case class ImageUrl(url: String)

  sealed trait ContentPart extends Product with Serializable
  object ContentPart {
    final case class Text(value: String)    extends ContentPart
    final case class Image(value: ImageUrl) extends ContentPart
  }

  final case class Kv(key: String, value: String)

  final case class Config(
    model: Option[String] = None,
    taskType: Option[TaskType] = None,
    dimensions: Option[Int] = None,
    truncation: Option[Boolean] = None,
    outputFormat: Option[OutputFormat] = None,
    outputDtype: Option[OutputDtype] = None,
    user: Option[String] = None,
    providerOptions: List[Kv] = Nil
  )

  final case class Usage(inputTokens: Option[Int], totalTokens: Option[Int])

  sealed trait VectorData extends Product with Serializable
  object VectorData {
    final case class FloatArray(value: Vector[Float]) extends VectorData
    final case class Int8(value: Vector[Byte])        extends VectorData
    final case class UInt8(value: Vector[Int])        extends VectorData
    final case class Binary(value: Vector[Byte])      extends VectorData
    final case class UBinary(value: Vector[Int])      extends VectorData
    final case class Base64(value: String)            extends VectorData
  }

  final case class Embedding(index: Int, vector: VectorData)

  final case class EmbeddingResponse(
    embeddings: List[Embedding],
    usage: Option[Usage],
    model: String,
    providerMetadataJson: Option[String]
  )

  final case class RerankResult(index: Int, relevanceScore: Float, document: Option[String])
  final case class RerankResponse(
    results: List[RerankResult],
    usage: Option[Usage],
    model: String,
    providerMetadataJson: Option[String]
  )

  final case class Error(code: ErrorCode, message: String, providerErrorJson: Option[String])

  def generateResult(inputs: Vector[ContentPart], config: Config): Either[Error, EmbeddingResponse] = {
    val raw = EmbedModule.generate(toJsInputs(inputs), toJsConfig(config))
    decodeResult(raw)(fromJsEmbeddingResponse, fromJsError)
  }

  def generate(inputs: Vector[ContentPart], config: Config): EmbeddingResponse =
    generateResult(inputs, config) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Embed error ${err.code.tag}: ${err.message}")
    }

  def rerankResult(query: String, documents: Vector[String], config: Config): Either[Error, RerankResponse] = {
    val raw = EmbedModule.rerank(query, js.Array(documents: _*), toJsConfig(config))
    decodeResult(raw)(fromJsRerankResponse, fromJsError)
  }

  def rerank(query: String, documents: Vector[String], config: Config): RerankResponse =
    rerankResult(query, documents, config) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(s"Embed error ${err.code.tag}: ${err.message}")
    }

  private type JObj = js.Dictionary[js.Any]

  private def toJsInputs(inputs: Vector[ContentPart]): js.Array[JObj] =
    js.Array(inputs.map(toJsContentPart): _*)

  private def toJsContentPart(part: ContentPart): JObj =
    part match {
      case ContentPart.Text(value) =>
        js.Dictionary[js.Any]("tag" -> "text", "val" -> value)
      case ContentPart.Image(value) =>
        js.Dictionary[js.Any]("tag" -> "image", "val" -> js.Dictionary[js.Any]("url" -> value.url))
    }

  private def toJsConfig(config: Config): JObj =
    js.Dictionary[js.Any](
      "model"            -> config.model.fold[js.Any](js.undefined)(identity),
      "task-type"        -> config.taskType.fold[js.Any](js.undefined)(_.tag),
      "dimensions"       -> config.dimensions.fold[js.Any](js.undefined)(identity),
      "truncation"       -> config.truncation.fold[js.Any](js.undefined)(identity),
      "output-format"    -> config.outputFormat.fold[js.Any](js.undefined)(_.tag),
      "output-dtype"     -> config.outputDtype.fold[js.Any](js.undefined)(_.tag),
      "user"             -> config.user.fold[js.Any](js.undefined)(identity),
      "provider-options" -> js.Array(config.providerOptions.map(toJsKv): _*)
    )

  private def toJsKv(kv: Kv): JObj =
    js.Dictionary[js.Any]("key" -> kv.key, "value" -> kv.value)

  private def fromJsEmbeddingResponse(raw: js.Dynamic): EmbeddingResponse = {
    val obj   = raw.asInstanceOf[JObj]
    val items = obj.get("embeddings").map(asArray).getOrElse(js.Array()).toList.map(fromJsEmbedding)
    EmbeddingResponse(
      embeddings = items,
      usage = optionDynamic(obj.getOrElse("usage", null)).map(fromJsUsage),
      model = obj.getOrElse("model", "").toString,
      providerMetadataJson = obj.get("provider-metadata-json").map(_.toString)
    )
  }

  private def fromJsEmbedding(raw: js.Dynamic): Embedding = {
    val obj = raw.asInstanceOf[JObj]
    Embedding(
      index = obj.getOrElse("index", 0).toString.toInt,
      vector = fromJsVectorData(obj.getOrElse("vector", js.Dictionary()).asInstanceOf[js.Dynamic])
    )
  }

  private def fromJsVectorData(raw: js.Dynamic): VectorData = {
    val tag = tagOf(raw)
    val v   = valOf(raw)
    tag match {
      case "float" =>
        VectorData.FloatArray(asArray(v).map(_.toString.toFloat).toVector)
      case "int8" =>
        VectorData.Int8(asArray(v).map(_.toString.toInt.toByte).toVector)
      case "uint8" =>
        VectorData.UInt8(asArray(v).map(_.toString.toInt).toVector)
      case "binary" =>
        VectorData.Binary(asArray(v).map(_.toString.toInt.toByte).toVector)
      case "ubinary" =>
        VectorData.UBinary(asArray(v).map(_.toString.toInt).toVector)
      case "base64" =>
        VectorData.Base64(v.toString)
      case _ =>
        VectorData.FloatArray(Vector.empty)
    }
  }

  private def fromJsRerankResponse(raw: js.Dynamic): RerankResponse = {
    val obj   = raw.asInstanceOf[JObj]
    val items = obj.get("results").map(asArray).getOrElse(js.Array()).toList.map(fromJsRerankResult)
    RerankResponse(
      results = items,
      usage = optionDynamic(obj.getOrElse("usage", null)).map(fromJsUsage),
      model = obj.getOrElse("model", "").toString,
      providerMetadataJson = obj.get("provider-metadata-json").map(_.toString)
    )
  }

  private def fromJsRerankResult(raw: js.Dynamic): RerankResult = {
    val obj = raw.asInstanceOf[JObj]
    RerankResult(
      index = obj.getOrElse("index", 0).toString.toInt,
      relevanceScore = obj.getOrElse("relevance-score", 0.0).toString.toFloat,
      document = obj.get("document").map(_.toString)
    )
  }

  private def fromJsUsage(raw: js.Dynamic): Usage = {
    val obj = raw.asInstanceOf[JObj]
    Usage(
      inputTokens = optionDynamic(obj.getOrElse("input-tokens", null)).map(toInt),
      totalTokens = optionDynamic(obj.getOrElse("total-tokens", null)).map(toInt)
    )
  }

  private def fromJsError(raw: js.Dynamic): Error = {
    val obj = raw.asInstanceOf[JObj]
    Error(
      code = ErrorCode.fromTag(obj.getOrElse("code", "unknown").toString),
      message = obj.getOrElse("message", "").toString,
      providerErrorJson = obj.get("provider-error-json").map(_.toString)
    )
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

  @js.native
  @JSImport("golem:embed/embed@1.0.0", JSImport.Namespace)
  private object EmbedModule extends js.Object {
    def generate(
      inputs: js.Array[js.Dictionary[js.Any]],
      config: js.Dictionary[js.Any]
    ): js.Dynamic = js.native

    def rerank(
      query: String,
      documents: js.Array[String],
      config: js.Dictionary[js.Any]
    ): js.Dynamic = js.native
  }
}
