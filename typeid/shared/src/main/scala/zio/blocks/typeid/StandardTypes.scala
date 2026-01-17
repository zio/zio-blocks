package zio.blocks.typeid

/**
 * Predefined TypeId instances for common Scala and Java types.
 */
object StandardTypes {

  // ========== Primitives ==========

  val IntId: TypeId[Int]         = TypeId.Int
  val LongId: TypeId[Long]       = TypeId.Long
  val DoubleId: TypeId[Double]   = TypeId.Double
  val FloatId: TypeId[Float]     = TypeId.Float
  val BooleanId: TypeId[Boolean] = TypeId.Boolean
  val CharId: TypeId[Char]       = TypeId.Char
  val ByteId: TypeId[Byte]       = TypeId.Byte
  val ShortId: TypeId[Short]     = TypeId.Short
  val UnitId: TypeId[Unit]       = TypeId.Unit

  // ========== Reference Types ==========

  val StringId: TypeId[String]   = TypeId.String
  val AnyId: TypeId[Any]         = TypeId.Any
  val AnyRefId: TypeId[AnyRef]   = TypeId.AnyRef
  val AnyValId: TypeId[AnyVal]   = TypeId.AnyVal
  val NothingId: TypeId[Nothing] = TypeId.Nothing
  val NullId: TypeId[Null]       = TypeId.Null

  // ========== Collection Type Constructors ==========

  val ListId: TypeId[List[Nothing]]        = TypeId.ListTypeId.asInstanceOf[TypeId[List[Nothing]]]
  val VectorId: TypeId[Vector[Nothing]]    = TypeId.VectorTypeId.asInstanceOf[TypeId[Vector[Nothing]]]
  val SetId: TypeId[Set[Nothing]]          = TypeId.SetTypeId.asInstanceOf[TypeId[Set[Nothing]]]
  val MapId: TypeId[Map[Nothing, Nothing]] = TypeId.MapTypeId.asInstanceOf[TypeId[Map[Nothing, Nothing]]]

  val OptionId: TypeId[Option[Nothing]]          = TypeId.OptionTypeId.asInstanceOf[TypeId[Option[Nothing]]]
  val EitherId: TypeId[Either[Nothing, Nothing]] = TypeId.EitherTypeId.asInstanceOf[TypeId[Either[Nothing, Nothing]]]

  // ========== Tuple Type Constructors ==========

  val EmptyTupleId: TypeId[EmptyTuple] = TypeId.Tuple.Empty
  val TupleConsId: TypeId[Nothing]     = TypeId.Tuple.TupleConsTypeId

  // ========== ZIO Blocks Specific ==========
  val DynamicValueId: TypeId[Any] = TypeId.DynamicValue

  // ========== Convenience TypeRepr Builders ==========

  def int: TypeRepr     = TypeRepr.Ref(IntId)
  def long: TypeRepr    = TypeRepr.Ref(LongId)
  def double: TypeRepr  = TypeRepr.Ref(DoubleId)
  def float: TypeRepr   = TypeRepr.Ref(FloatId)
  def boolean: TypeRepr = TypeRepr.Ref(BooleanId)
  def string: TypeRepr  = TypeRepr.Ref(StringId)
  def unit: TypeRepr    = TypeRepr.UnitType
  def any: TypeRepr     = TypeRepr.AnyType
  def nothing: TypeRepr = TypeRepr.NothingType

  def list(elem: TypeRepr): TypeRepr                    = TypeRepr.Applied(TypeRepr.Ref(ListId), List(elem))
  def vector(elem: TypeRepr): TypeRepr                  = TypeRepr.Applied(TypeRepr.Ref(VectorId), List(elem))
  def set(elem: TypeRepr): TypeRepr                     = TypeRepr.Applied(TypeRepr.Ref(SetId), List(elem))
  def map(key: TypeRepr, value: TypeRepr): TypeRepr     = TypeRepr.Applied(TypeRepr.Ref(MapId), List(key, value))
  def option(elem: TypeRepr): TypeRepr                  = TypeRepr.Applied(TypeRepr.Ref(OptionId), List(elem))
  def either(left: TypeRepr, right: TypeRepr): TypeRepr = TypeRepr.Applied(TypeRepr.Ref(EitherId), List(left, right))
}
