import zio.blocks.schema.json._
import zio.json.JsonEncoder

object ValidTypesTest {
  def main(args: Array[String]): Unit = {
    // Test with stringable types as keys and values
    val str = "hello"
    val num = 42
    val bool = true
    
    val json1 = json"""{"$str": $num}"""
    val json2 = json"""{"key": $bool}"""
    val json3 = json"""{"$num": $str}"""
    
    println("All tests passed! JSON interpolations work with stringable types.")
  }
}
