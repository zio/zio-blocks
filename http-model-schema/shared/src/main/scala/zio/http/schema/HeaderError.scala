package zio.http.schema

sealed trait HeaderError extends Product with Serializable {
  def message: String
}

object HeaderError {

  final case class Missing(name: String) extends HeaderError {
    def message: String = s"Missing header: $name"
  }

  final case class Malformed(name: String, value: String, cause: String) extends HeaderError {
    def message: String = s"Malformed header '$name' value '$value': $cause"
  }
}
