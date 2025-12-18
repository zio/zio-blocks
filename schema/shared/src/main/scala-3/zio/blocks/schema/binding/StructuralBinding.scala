package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import scala.language.dynamics

/**
 * Describes a field in a structural type for use with StructuralConstructor and
 * StructuralDeconstructor.
 *
 * @param name
 *   The field name
 * @param offset
 *   The register offset where this field's value is stored
 * @param primitiveType
 *   The primitive type code (0=object, 1=boolean, 2=byte, 3=short, 4=int,
 *   5=long, 6=float, 7=double, 8=char)
 */
final case class StructuralFieldInfo(
  name: String,
  offset: RegisterOffset,
  primitiveType: Int
)

object StructuralFieldInfo {
  val Object: Int  = 0
  val Boolean: Int = 1
  val Byte: Int    = 2
  val Short: Int   = 3
  val Int: Int     = 4
  val Long: Int    = 5
  val Float: Int   = 6
  val Double: Int  = 7
  val Char: Int    = 8
}

/**
 * A constructor that creates Selectable instances from register values.
 *
 * @param fields
 *   Metadata about each field in the structural type
 * @param totalRegisters
 *   Total register space needed for all fields
 */
class StructuralConstructor[A](
  fields: IndexedSeq[StructuralFieldInfo],
  val usedRegisters: RegisterOffset
) extends Constructor[A] {

  def construct(in: Registers, baseOffset: RegisterOffset): A = {
    val fieldValues = new scala.collection.mutable.HashMap[String, Any]()

    var i = 0
    while (i < fields.length) {
      val field      = fields(i)
      val value: Any = field.primitiveType match {
        case StructuralFieldInfo.Object  => in.getObject(baseOffset, RegisterOffset.getObjects(field.offset))
        case StructuralFieldInfo.Boolean => in.getBoolean(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Byte    => in.getByte(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Short   => in.getShort(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Int     => in.getInt(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Long    => in.getLong(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Float   => in.getFloat(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Double  => in.getDouble(baseOffset, RegisterOffset.getBytes(field.offset))
        case StructuralFieldInfo.Char    => in.getChar(baseOffset, RegisterOffset.getBytes(field.offset))
        case _                           => throw new IllegalStateException(s"Unknown primitive type: ${field.primitiveType}")
      }
      fieldValues.put(field.name, value)
      i += 1
    }

    new StructuralValue(fieldValues.toMap).asInstanceOf[A]
  }
}

/**
 * A deconstructor that extracts field values from Selectable instances into
 * registers.
 *
 * @param fields
 *   Metadata about each field in the structural type
 * @param totalRegisters
 *   Total register space needed for all fields
 */
class StructuralDeconstructor[A](
  fields: IndexedSeq[StructuralFieldInfo],
  val usedRegisters: RegisterOffset
) extends Deconstructor[A] {

  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit = {
    val dynamic = in.asInstanceOf[StructuralValue]

    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      val value = dynamic.selectDynamic(field.name)

      field.primitiveType match {
        case StructuralFieldInfo.Object =>
          out.setObject(baseOffset, RegisterOffset.getObjects(field.offset), value.asInstanceOf[AnyRef])
        case StructuralFieldInfo.Boolean =>
          out.setBoolean(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Boolean])
        case StructuralFieldInfo.Byte =>
          out.setByte(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Byte])
        case StructuralFieldInfo.Short =>
          out.setShort(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Short])
        case StructuralFieldInfo.Int =>
          out.setInt(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Int])
        case StructuralFieldInfo.Long =>
          out.setLong(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Long])
        case StructuralFieldInfo.Float =>
          out.setFloat(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Float])
        case StructuralFieldInfo.Double =>
          out.setDouble(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Double])
        case StructuralFieldInfo.Char =>
          out.setChar(baseOffset, RegisterOffset.getBytes(field.offset), value.asInstanceOf[Char])
        case _ =>
          throw new IllegalStateException(s"Unknown primitive type: ${field.primitiveType}")
      }
      i += 1
    }
  }
}

/**
 * A Dynamic implementation that stores field values in a Map. Extends
 * scala.Selectable to support selectDynamic calls for structural types.
 */
class StructuralValue(values: Map[String, Any]) extends scala.Selectable {
  def selectDynamic(name: String): Any = values(name)
}
