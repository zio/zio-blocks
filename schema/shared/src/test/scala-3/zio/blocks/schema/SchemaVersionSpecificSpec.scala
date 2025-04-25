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
    ),
    suite("Reflect.Variant")(
      test("derives schema using 'derives' keyword") {
        sealed trait Variant1 derives Schema

        case class Case1(d: Double) extends Variant1 derives Schema

        case class Case2(f: Float) extends Variant1 derives Schema

        val schema  = Schema.derived[Variant1]
        val variant = schema.reflect.asInstanceOf[Reflect.Variant[Binding, Variant1]]
        val case1   = variant.cases(0).asInstanceOf[Term.Bound[Variant1, Case1]]
        val case2   = variant.cases(1).asInstanceOf[Term.Bound[Variant1, Case2]]
        val prism1  = Prism(variant, case1)
        val prism2  = Prism(variant, case2)
        assert(prism1.getOption(Case1(0.1)))(isSome(equalTo(Case1(0.1)))) &&
        assert(prism2.getOption(Case2(0.2f)))(isSome(equalTo(Case2(0.2f)))) &&
        assert(prism1.replace(Case1(0.1), Case1(0.2)))(equalTo(Case1(0.2))) &&
        assert(prism2.replace(Case2(0.2f), Case2(0.3f)))(equalTo(Case2(0.3f))) &&
        assert(schema)(
          equalTo(
            new Schema[Variant1](
              reflect = Reflect.Variant[Binding, Variant1](
                cases = Seq(
                  Schema[Case1].reflect.asTerm("case0"),
                  Schema[Case2].reflect.asTerm("case1")
                ),
                typeName = TypeName(
                  namespace = Namespace(
                    packages = Seq("zio", "blocks", "schema"),
                    values = Seq("SchemaVersionSpecificSpec", "spec")
                  ),
                  name = "Variant1"
                ),
                variantBinding = null,
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
