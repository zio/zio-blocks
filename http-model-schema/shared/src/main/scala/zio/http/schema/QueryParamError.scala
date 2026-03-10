package zio.http.schema

sealed trait QueryParamError extends Product with Serializable {
  def message: String
}

object QueryParamError {

  final case class Missing(key: String) extends QueryParamError {
    def message: String = s"Missing query parameter: $key"
  }

  final case class Malformed(key: String, value: String, cause: String) extends QueryParamError {
    def message: String = s"Malformed query parameter '$key' value '$value': $cause"
  }
}
