package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaVersionSpecificSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaVersionSpecificSpec")(
    suite("Reflect.Record")(
      test("derives schema using 'derives' keyword") {
        case class Record1(b: Byte, i: Int) derives Schema

        val schema = summon[Schema[Record1]]
        val record = schema.reflect.asInstanceOf[Reflect.Record[Binding, Record1]]
        val field1 = record.fields(0).asInstanceOf[Term.Bound[Record1, Byte]]
        val field2 = record.fields(1).asInstanceOf[Term.Bound[Record1, Int]]
        val lens1  = Lens(record, field1)
        val lens2  = Lens(record, field2)
        assert(field1.value.binding.defaultValue)(isNone) &&
        assert(field2.value.binding.defaultValue)(isNone) &&
        assert(record.constructor.usedRegisters)(equalTo(RegisterOffset(bytes = 1, ints = 1))) &&
        assert(record.deconstructor.usedRegisters)(equalTo(RegisterOffset(bytes = 1, ints = 1))) &&
        assert(lens1.get(Record1(1, 2)))(equalTo(1: Byte)) &&
        assert(lens2.get(Record1(1, 2)))(equalTo(2)) &&
        assert(lens1.replace(Record1(1, 2), 3: Byte))(equalTo(Record1(3, 2))) &&
        assert(lens2.replace(Record1(1, 2), 3))(equalTo(Record1(1, 3))) &&
        assert(schema)(
          equalTo(
            new Schema[Record1](
              reflect = Reflect.Record[Binding, Record1](
                fields = Seq(
                  Schema[Byte].reflect.asTerm("b"),
                  Schema[Int].reflect.asTerm("i")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Record1"
                ),
                recordBinding = null,
                doc = Doc.Empty,
                modifiers = Nil
              )
            )
          )
        )
      }
    )
  )
}
