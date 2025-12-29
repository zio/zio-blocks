package zio.blocks.schema.shared

import java.time.Instant
import scala.util.Try
// 注意：若專案使用 zio-json 或其他庫，請確認 import 是否需調整，此為通用補丁
import sttp.tapir.Codec
import sttp.tapir.DecodeResult

object GlobalCodecs {
  // 讓編譯器看得懂 Instant
  given instantCodec: Codec[String, Instant, sttp.tapir.CodecFormat.TextPlain] = Codec.string.mapDecode(str => 
    Try(Instant.parse(str)).fold(
      e => DecodeResult.Error(str, e),
      DecodeResult.Value(_)
    )
  )(_.toString)
}
