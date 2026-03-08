package zio.blocks.schema.yaml

sealed trait YamlTag
object YamlTag {
  case object Str                      extends YamlTag
  case object Bool                     extends YamlTag
  case object Int                      extends YamlTag
  case object Float                    extends YamlTag
  case object Null                     extends YamlTag
  case object Seq                      extends YamlTag
  case object Map                      extends YamlTag
  case object Timestamp                extends YamlTag
  final case class Custom(uri: String) extends YamlTag

  val str: YamlTag       = Str
  val bool: YamlTag      = Bool
  val int: YamlTag       = Int
  val float: YamlTag     = Float
  val `null`: YamlTag    = Null
  val seq: YamlTag       = Seq
  val map: YamlTag       = Map
  val timestamp: YamlTag = Timestamp

  def fromString(tag: String): YamlTag = tag match {
    case "!!str"       => Str
    case "!!bool"      => Bool
    case "!!int"       => Int
    case "!!float"     => Float
    case "!!null"      => Null
    case "!!seq"       => Seq
    case "!!map"       => Map
    case "!!timestamp" => Timestamp
    case other         => Custom(other)
  }

  def toTagString(tag: YamlTag): String = tag match {
    case Str         => "!!str"
    case Bool        => "!!bool"
    case Int         => "!!int"
    case Float       => "!!float"
    case Null        => "!!null"
    case Seq         => "!!seq"
    case Map         => "!!map"
    case Timestamp   => "!!timestamp"
    case Custom(uri) => uri
  }
}
