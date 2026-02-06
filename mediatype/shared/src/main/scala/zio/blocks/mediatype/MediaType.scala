package zio.blocks.mediatype

final case class MediaType(
  mainType: String,
  subType: String,
  compressible: Boolean = false,
  binary: Boolean = false,
  fileExtensions: List[String] = Nil,
  extensions: Map[String, String] = Map.empty,
  parameters: Map[String, String] = Map.empty
) {
  val fullType: String = s"$mainType/$subType"

  def matches(other: MediaType, ignoreParameters: Boolean = false): Boolean = {
    val mainTypeMatches = mainType == "*" || other.mainType == "*" ||
      mainType.equalsIgnoreCase(other.mainType)
    val subTypeMatches = subType == "*" || other.subType == "*" ||
      subType.equalsIgnoreCase(other.subType)
    val parametersMatch = ignoreParameters ||
      parameters.forall { case (key, value) =>
        other.parameters.get(key).exists(_.equalsIgnoreCase(value))
      }

    mainTypeMatches && subTypeMatches && parametersMatch
  }
}
