package zio.blocks.typeid

sealed trait Constant {
  type Value
  def value: Value
}

object Constant {
  final case class IntConst(value: Int)         extends Constant { type Value = Int                                 }
  final case class LongConst(value: Long)       extends Constant { type Value = Long                                }
  final case class FloatConst(value: Float)     extends Constant { type Value = Float                               }
  final case class DoubleConst(value: Double)   extends Constant { type Value = Double                              }
  final case class BooleanConst(value: Boolean) extends Constant { type Value = Boolean                             }
  final case class CharConst(value: Char)       extends Constant { type Value = Char                                }
  final case class StringConst(value: String)   extends Constant { type Value = String                              }
  final case class NullConst()                  extends Constant { type Value = Null; def value: Null = null        }
  final case class UnitConst()                  extends Constant { type Value = Unit; def value: Unit = ()          }
  final case class ClassOfConst(tpe: TypeRepr)  extends Constant { type Value = TypeRepr; def value: TypeRepr = tpe }
}
