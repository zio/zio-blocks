package zio.blocks.schema.binding

import scala.util.Try
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object HasBindingCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("HasBindingCoverageSpec")(
    test("exercises HasBinding helpers (happy paths)") {
      val hb = Binding.bindingHasBinding

      val prim  = hb.primitive(Binding.Primitive.int)
      val prim2 = hb.updatePrimitive[Int](Binding.Primitive.int, (p: Binding.Primitive[Int]) => p.examples(1, 2, 3))

      val rec  = hb.record(Binding.Record.someInt)
      val rec2 = hb.updateConstructor[Some[Int]](Binding.Record.someInt, (c: Constructor[Some[Int]]) => c)
      val rec3 = hb.updateDeconstructor[Some[Int]](Binding.Record.someInt, (d: Deconstructor[Some[Int]]) => d)

      val vari  = hb.variant(Binding.Variant.option[Int])
      val vari2 = hb.updateDiscriminator[Option[Int]](Binding.Variant.option[Int], (d: Discriminator[Option[Int]]) => d)
      val vari3 = hb.updateMatchers[Option[Int]](Binding.Variant.option[Int], (m: Matchers[Option[Int]]) => m)

      val seq  = hb.seq(Binding.Seq.vector[Int])
      val seqC = hb.seqConstructor(Binding.Seq.vector[Int])
      val seqD = hb.seqDeconstructor(Binding.Seq.vector[Int])
      val seq2 = hb.updateSeq[Vector, Int](Binding.Seq.vector[Int], (s: Binding.Seq[Vector, Int]) => s)

      val map  = hb.map(Binding.Map.map[String, Int])
      val mapC = hb.mapConstructor(Binding.Map.map[String, Int])
      val mapD = hb.mapDeconstructor(Binding.Map.map[String, Int])
      val map2 = hb.updateMap[Predef.Map, String, Int](
        Binding.Map.map[String, Int],
        (m: Binding.Map[Predef.Map, String, Int]) => m
      )

      val wrap = hb.wrapper(Binding.Wrapper[String, Int](i => Right(i.toString), _.length))

      assertTrue(
        (prim ne null) &&
          (prim2 ne null) &&
          (rec ne null) &&
          (rec2 ne null) &&
          (rec3 ne null) &&
          (vari ne null) &&
          (vari2 ne null) &&
          (vari3 ne null) &&
          (seq ne null) &&
          (seqC ne null) &&
          (seqD ne null) &&
          (seq2 ne null) &&
          (map ne null) &&
          (mapC ne null) &&
          (mapD ne null) &&
          (map2 ne null) &&
          (wrap ne null)
      )
    },
    test("exercises HasBinding helpers (error paths)") {
      val hb = Binding.bindingHasBinding

      val wrongPrim = Binding.Record.someInt.asInstanceOf[Binding[BindingType.Primitive, Some[Int]]]
      val wrongRec  = Binding.Primitive.int.asInstanceOf[Binding[BindingType.Record, Int]]
      val wrongVar  = Binding.Primitive.int.asInstanceOf[Binding[BindingType.Variant, Int]]
      val wrongMap  = Binding.Primitive.int.asInstanceOf[Binding[BindingType.Map[Predef.Map], Predef.Map[String, Int]]]
      val wrongSeq  = Binding.Primitive.int.asInstanceOf[Binding[BindingType.Seq[Vector], Vector[Int]]]
      val wrongWrap = Binding.Primitive.int.asInstanceOf[Binding[BindingType.Wrapper[String, Int], String]]

      assertTrue(
        Try(hb.primitive(wrongPrim)).isFailure &&
          Try(
            hb.updatePrimitive[Some[Int]](
              wrongPrim,
              (_: Binding.Primitive[Some[Int]]) => Binding.Primitive.unit.asInstanceOf[Binding.Primitive[Some[Int]]]
            )
          ).isFailure &&
          Try(hb.record(wrongRec)).isFailure &&
          Try(hb.variant(wrongVar)).isFailure &&
          Try(hb.map(wrongMap)).isFailure &&
          Try(hb.seq(wrongSeq)).isFailure &&
          Try(hb.wrapper(wrongWrap)).isFailure
      )
    }
  )
}
