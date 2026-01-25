package zio.blocks.schema.json

/**
 * A sealed trait representing the type of a JSON value.
 *
 * This is used by [[MergeStrategy]] to make decisions about recursion without
 * needing access to the actual JSON values.
 */
sealed trait JsonType {

  /**
   * Returns the type index for ordering: Null=0, Boolean=1, Number=2, String=3,
   * Array=4, Object=5
   */
  def typeIndex: Int
}

object JsonType {
  case object Null    extends JsonType { val typeIndex = 0 }
  case object Boolean extends JsonType { val typeIndex = 1 }
  case object Number  extends JsonType { val typeIndex = 2 }
  case object String  extends JsonType { val typeIndex = 3 }
  case object Array   extends JsonType { val typeIndex = 4 }
  case object Object  extends JsonType { val typeIndex = 5 }
}
