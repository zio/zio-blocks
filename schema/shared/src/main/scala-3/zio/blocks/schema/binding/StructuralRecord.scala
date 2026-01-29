package zio.blocks.schema.binding

object StructuralRecord {
  def create[A](fieldNames: Array[String], fieldValues: Array[Any]): A = {
    val values = new java.util.HashMap[String, Any]()
    var i      = 0
    while (i < fieldNames.length) {
      values.put(fieldNames(i), fieldValues(i))
      i += 1
    }
    (new scala.Selectable {
      @scala.annotation.nowarn("msg=unused private member")
      def selectDynamic(name: String): Any = values.get(name)
    }).asInstanceOf[A]
  }
}
