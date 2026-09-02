package typeid

import zio.blocks.typeid._

object HttpStatus {
  val OK       = 200
  val NotFound = 404
}

object SingletonTypeTest extends App {
  val okTypeId       = TypeId.of[HttpStatus.OK.type]
  val notFoundTypeId = TypeId.of[HttpStatus.NotFound.type]

  println("OK TypeId name: " + okTypeId.name)
  println("NotFound TypeId name: " + notFoundTypeId.name)
  println("OK TypeId fullName: " + okTypeId.fullName)
  println("NotFound TypeId fullName: " + notFoundTypeId.fullName)
  println("Are they equal? " + (okTypeId == notFoundTypeId))

  // Try to dispatch on them
  def dispatch(typeId: TypeId[_]): String =
    if (typeId == okTypeId) "Handling OK"
    else if (typeId == notFoundTypeId) "Handling NotFound"
    else "Unknown"

  println("Dispatch OK: " + dispatch(okTypeId))
  println("Dispatch NotFound: " + dispatch(notFoundTypeId))
}
