package golem.ai

import org.scalatest.funsuite.AnyFunSuite

final class EmbedCompileSpec extends AnyFunSuite {
  import Embed._

  test("TaskType — all 8 variants and fromTag roundtrip") {
    val types = List(
      TaskType.RetrievalQuery,
      TaskType.RetrievalDocument,
      TaskType.SemanticSimilarity,
      TaskType.Classification,
      TaskType.Clustering,
      TaskType.QuestionAnswering,
      TaskType.FactVerification,
      TaskType.CodeRetrieval
    )
    assert(types.size == 8)
    types.foreach(t => assert(TaskType.fromTag(t.tag) eq t))
  }

  test("OutputFormat — all 3 variants and fromTag roundtrip") {
    val formats = List(OutputFormat.FloatArray, OutputFormat.Binary, OutputFormat.Base64)
    assert(formats.size == 3)
    formats.foreach(f => assert(OutputFormat.fromTag(f.tag) eq f))
  }

  test("OutputDtype — all 5 variants and fromTag roundtrip") {
    val dtypes = List(
      OutputDtype.FloatArray,
      OutputDtype.Int8,
      OutputDtype.UInt8,
      OutputDtype.Binary,
      OutputDtype.UBinary
    )
    assert(dtypes.size == 5)
    dtypes.foreach(d => assert(OutputDtype.fromTag(d.tag) eq d))
  }

  test("ErrorCode — all 8 variants and fromTag roundtrip") {
    val codes = List(
      ErrorCode.InvalidRequest,
      ErrorCode.ModelNotFound,
      ErrorCode.Unsupported,
      ErrorCode.AuthenticationFailed,
      ErrorCode.ProviderError,
      ErrorCode.RateLimitExceeded,
      ErrorCode.InternalError,
      ErrorCode.Unknown
    )
    assert(codes.size == 8)
    codes.foreach(c => assert(ErrorCode.fromTag(c.tag) eq c))
  }

  test("ContentPart — Text and Image variants") {
    val text: ContentPart  = ContentPart.Text("hello")
    val image: ContentPart = ContentPart.Image(ImageUrl("http://img.png"))
    assert(text.isInstanceOf[ContentPart.Text])
    assert(image.isInstanceOf[ContentPart.Image])
  }

  test("Config construction with all optional fields") {
    val config = Config(
      model = Some("text-embedding-3-small"),
      taskType = Some(TaskType.RetrievalQuery),
      dimensions = Some(256),
      truncation = Some(true),
      outputFormat = Some(OutputFormat.FloatArray),
      outputDtype = Some(OutputDtype.FloatArray),
      user = Some("user-1"),
      providerOptions = List(Kv("k", "v"))
    )
    assert(config.model.contains("text-embedding-3-small"))
    assert(config.dimensions.contains(256))
  }

  test("Config construction with None defaults") {
    val config = Config()
    assert(config.model.isEmpty)
    assert(config.taskType.isEmpty)
    assert(config.providerOptions.isEmpty)
  }

  test("VectorData — all 6 variants") {
    val vectors: List[VectorData] = List(
      VectorData.FloatArray(Vector(1.0f, 2.0f)),
      VectorData.Int8(Vector(1.toByte, 2.toByte)),
      VectorData.UInt8(Vector(1, 2)),
      VectorData.Binary(Vector(0.toByte)),
      VectorData.UBinary(Vector(255)),
      VectorData.Base64("aGVsbG8=")
    )
    assert(vectors.size == 6)
  }

  test("Embedding and EmbeddingResponse construction") {
    val emb  = Embedding(0, VectorData.FloatArray(Vector(0.1f, 0.2f)))
    val resp = EmbeddingResponse(
      embeddings = List(emb),
      usage = Some(Usage(Some(5), Some(5))),
      model = "test-model",
      providerMetadataJson = None
    )
    assert(resp.embeddings.size == 1)
    assert(resp.model == "test-model")
  }

  test("RerankResult and RerankResponse construction") {
    val result = RerankResult(index = 0, relevanceScore = 0.95f, document = Some("doc"))
    val resp   = RerankResponse(
      results = List(result),
      usage = None,
      model = "rerank-model",
      providerMetadataJson = None
    )
    assert(resp.results.head.relevanceScore == 0.95f)
  }

  test("Error construction") {
    val err = Error(ErrorCode.RateLimitExceeded, "slow down", Some("{}"))
    assert(err.code == ErrorCode.RateLimitExceeded)
    assert(err.message == "slow down")
  }
}
