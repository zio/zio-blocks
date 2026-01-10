package zio.blocks.schema

import scala.quoted._
import zio.blocks.schema.binding._

/**
 * Macros for deriving schemas for structural types.
 */
object StructuralSchemaMacros {

  def structuralImpl[T: Type](using Quotes): Expr[Schema[T]] = {
    val deriver = new StructuralDeriver(using summon[Quotes])
    deriver.deriveStructural[T]
  }
}

private class StructuralDeriver(using val quotes: Quotes) {
  import quotes.reflect._

  case class StructuralFieldInfo(name: String, tpe: TypeRepr)

  def deriveStructural[T: Type]: Expr[Schema[T]] = {
    val tpe        = TypeRepr.of[T]
    val fieldInfos = collectFullFields(tpe)

    val fieldExprs = fieldInfos.map { info =>
      info.tpe.asType match {
        case '[ft] =>
          val schema = findSchema[ft]
          val name   = Expr(info.name)
          '{ $schema.reflect.asTerm[T]($name) }
      }
    }

    val fieldSchemas = Varargs(fieldExprs)
    val tpeName      = Expr(tpe.show)
    val numFields    = fieldInfos.size

    '{
      val fieldsVector: _root_.scala.collection.immutable.IndexedSeq[
        _root_.zio.blocks.schema.Term[_root_.zio.blocks.schema.binding.Binding, T, ?]
      ] =
        _root_.scala.collection.immutable
          .Vector($fieldSchemas*)
          .asInstanceOf[_root_.scala.collection.immutable.IndexedSeq[
            _root_.zio.blocks.schema.Term[_root_.zio.blocks.schema.binding.Binding, T, ?]
          ]]

      val fieldRegisters: _root_.scala.Array[_root_.zio.blocks.schema.binding.Register[Any]] =
        _root_.zio.blocks.schema.Reflect.Record.registers(
          fieldsVector
            .map(_.value.asInstanceOf[_root_.zio.blocks.schema.Reflect[_root_.zio.blocks.schema.binding.Binding, Any]])
            .toArray
        )
      val usedRegs = _root_.zio.blocks.schema.Reflect.Record.usedRegisters(fieldRegisters)

      new _root_.zio.blocks.schema.Schema[T](
        reflect = new _root_.zio.blocks.schema.Reflect.Record[_root_.zio.blocks.schema.binding.Binding, T](
          fields = fieldsVector,
          typeName =
            new _root_.zio.blocks.schema.TypeName(new _root_.zio.blocks.schema.Namespace(Nil, Nil), $tpeName, Nil),
          recordBinding = new _root_.zio.blocks.schema.binding.Binding.Record(
            constructor = new _root_.zio.blocks.schema.binding.Constructor[T] {
              def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = usedRegs
              def construct(
                in: _root_.zio.blocks.schema.binding.Registers,
                offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset
              ): T = {
                val mapBuilder = _root_.scala.collection.immutable.Map.newBuilder[String, Any]
                var i          = 0
                while (i < ${ Expr(numFields) }) {
                  val register = fieldRegisters(i)
                  mapBuilder += (fieldsVector(i).name -> register.get(in, offset))
                  i += 1
                }
                new _root_.zio.blocks.schema.MapSelectable(mapBuilder.result()).asInstanceOf[T]
              }
            },
            deconstructor = new _root_.zio.blocks.schema.binding.Deconstructor[T] {
              def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = usedRegs
              def deconstruct(
                out: _root_.zio.blocks.schema.binding.Registers,
                offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset,
                in: T
              ): Unit = {
                val selectable = in.asInstanceOf[_root_.zio.blocks.schema.StructuralInstance]
                var i          = 0
                while (i < ${ Expr(numFields) }) {
                  val name     = fieldsVector(i).name
                  val register = fieldRegisters(i)
                  register
                    .asInstanceOf[_root_.zio.blocks.schema.binding.Register[Any]]
                    .set(out, offset, selectable.selectDynamic(name))
                  i += 1
                }
              }
            }
          )
        )
      )
    }
  }

  private def collectFullFields(tpe: TypeRepr): List[StructuralFieldInfo] = {
    def loop(current: TypeRepr): List[StructuralFieldInfo] = current.dealias match {
      case Refinement(parent, name, info) =>
        StructuralFieldInfo(name, info) :: loop(parent)
      case t if t.classSymbol.isDefined =>
        val symb    = t.classSymbol.get
        val members = if (symb.flags.is(Flags.Case)) {
          symb.caseFields
        } else {
          symb.fieldMembers.filter(m => !m.flags.is(Flags.Private) && !m.flags.is(Flags.Protected))
        }
        members.map { f =>
          StructuralFieldInfo(f.name, t.memberType(f))
        }
      case AppliedType(tycon, _) =>
        loop(tycon)
      case _ => Nil
    }

    loop(tpe).distinctBy(_.name).reverse
  }

  private def findSchema[ft: Type]: Expr[Schema[ft]] =
    Expr.summon[_root_.zio.blocks.schema.Schema[ft]].getOrElse {
      // If we can't find an implicit schema, we try to derive it structurally.
      // This allows for recursive structural types.
      StructuralSchemaMacros.structuralImpl[ft]
    }
}

/**
 * A trait for structural instances that provide explicit field access.
 */
trait StructuralInstance extends _root_.scala.Selectable {
  def selectDynamic(name: String): Any
}

/**
 * A Selectable implementation backed by a Map. Used at runtime for structural
 * type instances created by the migration system.
 */
class MapSelectable(val fields: Map[String, Any]) extends StructuralInstance {
  def selectDynamic(name: String): Any                                   = fields(name)
  def applyDynamic(name: String, paramTypes: Class[_]*)(args: Any*): Any =
    throw new UnsupportedOperationException(s"Method $name not supported on MapSelectable")
}
