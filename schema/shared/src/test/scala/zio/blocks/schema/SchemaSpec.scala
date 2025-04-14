package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Reflect.Primitive
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding._
import zio.test.Assertion._
import zio.test._

object SchemaSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment with Scope, Any] = suite("SchemaSpec")(
    suite("Reflect.Primitive")(
      test("has consistent equals and hashCode") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.None),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]], // should be ignored in equals and hashCode
          typeName = TypeName.long,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val long2 = long1.copy(primitiveType = PrimitiveType.Long(Validation.Numeric.Positive))
        val long3 = long1.copy(typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Long1"))
        val long4 = long1.copy(doc = Doc("text"))
        val long5 = long1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema[Long])(equalTo(Schema[Long])) &&
        assert(Schema[Long].hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema(long1))(equalTo(Schema[Long])) &&
        assert(Schema(long1).hashCode)(equalTo(Schema[Long].hashCode)) &&
        assert(Schema(long2))(not(equalTo(Schema[Long]))) &&
        assert(Schema(long3))(not(equalTo(Schema[Long]))) &&
        assert(Schema(long4))(not(equalTo(Schema[Long]))) &&
        assert(Schema(long5))(not(equalTo(Schema[Long])))
      },
      test("has access to primitive documentation") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.None),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]],
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        assert(Schema(long1).doc)(equalTo(Doc("Long (positive)")))
      },
      test("has access to primitive examples") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = List(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        assert(Schema(long1).examples)(equalTo(List(1L, 2L, 3L)))
      }
    ),
    suite("Reflect.Record")(
      test("has consistent equals and hashCode") {
        val record1 = Reflect.Record(
          fields = List[Term.Bound[Record, _]](
            Term("b", Reflect.byte, Doc("Field b"), Nil),
            Term("i", Reflect.int, Doc("Field i"), Nil)
          ),
          typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record"),
          recordBinding = null.asInstanceOf[Binding.Record[Record]], // should be ignored in equals and hashCode
          doc = Doc("Record with 2 fields"),
          modifiers = Nil
        )
        val record2 = record1.copy(typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record2"))
        val record3 = record1.copy(fields = record1.fields.reverse)
        val record4 = record1.copy(doc = Doc("text"))
        val record5 = record1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Record.schema)(equalTo(Record.schema)) &&
        assert(Record.schema.hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Schema(record1))(equalTo(Record.schema)) &&
        assert(Schema(record1).hashCode)(equalTo(Record.schema.hashCode)) &&
        assert(Schema(record2))(not(equalTo(Record.schema))) &&
        assert(Schema(record3))(not(equalTo(Record.schema))) &&
        assert(Schema(record4))(not(equalTo(Record.schema))) &&
        assert(Schema(record5))(not(equalTo(Record.schema)))
      },
      test("has consistent fields, length, registers and usedRegisters") {
        val record1 = Record.schema.reflect.asInstanceOf[Reflect.Record.Bound[Record]]
        val record2 = Case1.schema.reflect.asInstanceOf[Reflect.Record.Bound[Case1]]
        val record3 = Case2.schema.reflect.asInstanceOf[Reflect.Record.Bound[Case2]]
        assert(record1.length)(equalTo(2)) &&
        assert(record1.fields.length)(equalTo(2)) &&
        assert(record1.registers.length)(equalTo(2)) &&
        assert(record1.fields(0).value.asInstanceOf[Primitive[Binding, Byte]].primitiveType)(
          equalTo(PrimitiveType.Byte(Validation.None))
        ) &&
        assert(record1.registers(0).usedRegisters)(equalTo(RegisterOffset(bytes = 1))) &&
        assert(record1.fields(1).value.asInstanceOf[Primitive[Binding, Int]].primitiveType)(
          equalTo(PrimitiveType.Int(Validation.None))
        ) &&
        assert(record1.registers(1).usedRegisters)(equalTo(RegisterOffset(ints = 1))) &&
        assert(record1.usedRegisters)(equalTo(record1.registers.foldLeft(0)(_ + _.usedRegisters))) &&
        assert(record2.length)(equalTo(1)) &&
        assert(record2.fields.length)(equalTo(1)) &&
        assert(record2.registers.length)(equalTo(1)) &&
        assert(record2.fields(0).value.asInstanceOf[Primitive[Binding, Double]].primitiveType)(
          equalTo(PrimitiveType.Double(Validation.None))
        ) &&
        assert(record2.registers(0).usedRegisters)(equalTo(RegisterOffset(doubles = 1))) &&
        assert(record2.usedRegisters)(equalTo(record2.registers.foldLeft(0)(_ + _.usedRegisters))) &&
        assert(record3.length)(equalTo(1)) &&
        assert(record3.fields.length)(equalTo(1)) &&
        assert(record3.registers.length)(equalTo(1)) &&
        assert(record3.fields(0).value.asInstanceOf[Primitive[Binding, String]].primitiveType)(
          equalTo(PrimitiveType.String(Validation.None))
        ) &&
        assert(record3.registers(0).usedRegisters)(equalTo(RegisterOffset(objects = 1))) &&
        assert(record3.usedRegisters)(equalTo(record3.registers.foldLeft(0)(_ + _.usedRegisters)))
      },
      test("has access to record documentation") {
        assert(Record.schema.doc)(equalTo(Doc("Record with 2 fields")))
      },
      test("has access to record term documentation using lens focus") {
        val record = Record.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record]]
        assert(Record.schema.doc(Lens(record, record.fields(0))): Doc)(equalTo(record.fields(0).value.doc))
      },
      test("has access to record examples") {
        assert(Record.schema.examples)(equalTo(Record(1, 1000) :: Nil))
      },
      test("has access to record term examples using lens focus") {
        val record = Record.schema.reflect.asInstanceOf[Reflect.Record[Binding, Record]]
        assert(Record.schema.examples(Lens(record, record.fields(0))): List[_])(
          equalTo(record.fields(0).value.binding.examples)
        )
      }
    ),
    suite("Reflect.Variant")(
      test("has consistent equals and hashCode") {
        val variant1 = Reflect.Variant[Binding, Variant](
          cases = List(
            Term("case1", Case1.schema.reflect, Doc("Case 1"), Nil),
            Term("case2", Case2.schema.reflect, Doc("Case 2"), Nil)
          ),
          typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
          variantBinding = null.asInstanceOf[Binding.Variant[Variant]], // should be ignored in equals and hashCode
          doc = Doc("Variant with 2 cases"),
          modifiers = Nil
        )
        val variant2 = variant1.copy(cases = variant1.cases.reverse)
        val variant3 = variant1.copy(typeName = TypeName(Namespace(List("zio", "blocks", "schema2"), Nil), "Variant"))
        val variant4 = variant1.copy(doc = Doc("text"))
        val variant5 = variant1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Variant.schema)(equalTo(Variant.schema)) &&
        assert(Variant.schema.hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Schema(variant1))(equalTo(Variant.schema)) &&
        assert(Schema(variant1).hashCode)(equalTo(Variant.schema.hashCode)) &&
        assert(Schema(variant2))(not(equalTo(Variant.schema))) &&
        assert(Schema(variant3))(not(equalTo(Variant.schema))) &&
        assert(Schema(variant4))(not(equalTo(Variant.schema))) &&
        assert(Schema(variant5))(not(equalTo(Variant.schema)))
      },
      test("has access to variant documentation") {
        assert(Variant.schema.doc)(equalTo(Doc("Variant with 2 cases")))
      },
      test("has access to variant case documentation using prism focus") {
        val variant = Variant.schema.reflect.asInstanceOf[Reflect.Variant[Binding, Variant]]
        assert(Variant.schema.doc(Prism(variant, variant.cases(0))): Doc)(equalTo(variant.cases(0).value.doc))
      },
      test("has access to record examples") {
        assert(Variant.schema.examples)(equalTo(Case1(1.0) :: Case2("WWW") :: Nil))
      },
      test("has access to variant case examples using prism focus") {
        val variant = Variant.schema.reflect.asInstanceOf[Reflect.Variant[Binding, Variant]]
        assert(Variant.schema.examples(Prism(variant, variant.cases(0))): List[_])(
          equalTo(variant.cases(0).value.binding.examples)
        )
      }
    ),
    suite("Reflect.Sequence")(
      test("has consistent equals and hashCode") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Double]], // should be ignored in equals and hashCode
          doc = Doc.Empty,
          modifiers = Nil
        )
        val sequence2 = sequence1.copy(element =
          Primitive(PrimitiveType.Double(Validation.None), Binding.Primitive.double, TypeName.double, Doc("text"), Nil)
        )
        val sequence3 = sequence1.copy(typeName = TypeName[List[Double]](Namespace("scala" :: Nil, Nil), "List2"))
        val sequence4 = sequence1.copy(doc = Doc("text"))
        val sequence5 = sequence1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema[List[Double]])(equalTo(Schema[List[Double]])) &&
        assert(Schema[List[Double]].hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema(sequence1))(equalTo(Schema[List[Double]])) &&
        assert(Schema(sequence1).hashCode)(equalTo(Schema[List[Double]].hashCode)) &&
        assert(Schema(sequence2))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema(sequence3))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema(sequence4))(not(equalTo(Schema[List[Double]]))) &&
        assert(Schema(sequence5))(not(equalTo(Schema[List[Double]])))
      },
      test("has access to sequence documentation") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Double]],
          doc = Doc("List of doubles"),
          modifiers = Nil
        )
        assert(Schema(sequence1).doc)(equalTo(Doc("List of doubles")))
      },
      test("has access to sequence value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = null.asInstanceOf[Binding.Primitive[Long]],
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        val sequence1 = Reflect.Sequence[Binding, Long, List](
          element = long1,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Long]],
          doc = Doc("List of positive longs"),
          modifiers = Nil
        )
        assert(Schema(sequence1).doc(Traversal.listValues(long1)): Doc)(equalTo(Doc("Long (positive)")))
      },
      test("has access to record examples") {
        val sequence1 = Reflect.Sequence[Binding, Double, List](
          element = Reflect.double,
          typeName = TypeName.list,
          seqBinding = Binding.Seq[List, Double](
            constructor = SeqConstructor.listConstructor,
            deconstructor = SeqDeconstructor.listDeconstructor,
            examples = List(0.1, 0.2, 0.3) :: Nil
          ),
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(sequence1).examples)(equalTo(List(0.1, 0.2, 0.3) :: Nil))
      },
      test("has access to sequence value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = List(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        val sequence1 = Reflect.Sequence[Binding, Long, List](
          element = long1,
          typeName = TypeName.list,
          seqBinding = null.asInstanceOf[Binding.Seq[List, Long]],
          doc = Doc("List of positive longs"),
          modifiers = Nil
        )
        assert(Schema(sequence1).examples(Traversal.listValues(long1)): List[_])(equalTo(List(1L, 2L, 3L)))
      }
    ),
    suite("Reflect.Map")(
      test("has consistent equals and hashCode") {
        val map1 = Reflect.Map[Binding, Short, Float, Map](
          key = Reflect.short,
          value = Reflect.float,
          typeName = TypeName.map[Short, Float],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Short, Float]], // should be ignored in equals and hashCode
          doc = Doc.Empty,
          modifiers = Nil
        )
        val map2 = map1.copy(key =
          Primitive(
            PrimitiveType.Short(Validation.Numeric.Positive),
            Binding.Primitive.short,
            TypeName.short,
            Doc.Empty,
            Nil
          )
        )
        val map3 = map1.copy(value =
          Primitive(PrimitiveType.Float(Validation.None), Binding.Primitive.float, TypeName.float, Doc("text"), Nil)
        )
        val map4 = map1.copy(typeName = TypeName[Map[Short, Float]](Namespace("scala" :: Nil, Nil), "Map2"))
        val map5 = map1.copy(doc = Doc("text"))
        val map6 = map1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema[Map[Short, Float]])(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema[Map[Short, Float]].hashCode)(equalTo(Schema[Map[Short, Float]].hashCode)) &&
        assert(Schema(map1))(equalTo(Schema[Map[Short, Float]])) &&
        assert(Schema(map1).hashCode)(equalTo(Schema[Map[Short, Float]].hashCode)) &&
        assert(Schema(map2))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map3))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map4))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map5))(not(equalTo(Schema[Map[Short, Float]]))) &&
        assert(Schema(map6))(not(equalTo(Schema[Map[Short, Float]])))
      },
      test("has access to map documentation") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]], // should be ignored in equals and hashCode
          doc = Doc("Map of Int to Long"),
          modifiers = Nil
        )
        assert(Schema(map1).doc)(equalTo(Doc("Map of Int to Long")))
      },
      test("has access to map key documentation using traversal focus") {
        val int1 = Primitive(
          primitiveType = PrimitiveType.Int(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Int](),
          typeName = TypeName.int,
          doc = Doc("Int (positive)"),
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = int1,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).doc(Traversal.mapKeys(map1)): Doc)(equalTo(Doc("Int (positive)")))
      },
      test("has access to map value documentation using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](),
          typeName = TypeName.long,
          doc = Doc("Long (positive)"),
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = long1,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).doc(Traversal.mapValues(map1)): Doc)(equalTo(Doc("Long (positive)")))
      },
      test("has access to map examples") {
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = Binding.Map[Map, Int, Long](
            constructor = MapConstructor.map,
            deconstructor = MapDeconstructor.map,
            examples = Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil
          ),
          doc = Doc("Map of Int to Long"),
          modifiers = Nil
        )
        assert(Schema(map1).examples)(equalTo(Map(1 -> 1L, 2 -> 2L, 3 -> 3L) :: Nil))
      },
      test("has access to sequence map value examples using traversal focus") {
        val long1 = Primitive(
          primitiveType = PrimitiveType.Long(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Long](examples = List(1L, 2L, 3L)),
          typeName = TypeName.long,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = Reflect.int,
          value = long1,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).examples(Traversal.mapValues(map1)): List[_])(equalTo(List(1L, 2L, 3L)))
      },
      test("has access to sequence map value examples using traversal focus") {
        val int1 = Primitive(
          primitiveType = PrimitiveType.Int(Validation.Numeric.Positive),
          primitiveBinding = Binding.Primitive[Int](examples = List(1, 2, 3)),
          typeName = TypeName.int,
          doc = Doc.Empty,
          modifiers = Nil
        )
        val map1 = Reflect.Map[Binding, Int, Long, Map](
          key = int1,
          value = Reflect.long,
          typeName = TypeName.map[Int, Long],
          mapBinding = null.asInstanceOf[Binding.Map[Map, Int, Long]],
          doc = Doc.Empty,
          modifiers = Nil
        )
        assert(Schema(map1).examples(Traversal.mapKeys(map1)): List[_])(equalTo(List(1, 2, 3)))
      }
    ),
    suite("Reflect.Dynamic")(
      test("has consistent equals and hashCode") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(),
          doc = Doc.Empty,
          modifiers = Nil
        )
        val dynamic2 = dynamic1.copy(dynamicBinding = null.asInstanceOf[Binding.Dynamic])
        val dynamic3 = dynamic1.copy(doc = Doc("text"))
        val dynamic4 = dynamic1.copy(modifiers = List(Modifier.config("key", "value")))
        assert(Schema(dynamic1))(equalTo(Schema(dynamic1))) &&
        assert(Schema(dynamic1).hashCode)(equalTo(Schema(dynamic1).hashCode)) &&
        assert(Schema(dynamic2))(equalTo(Schema(dynamic1))) &&
        assert(Schema(dynamic2).hashCode)(equalTo(Schema(dynamic1).hashCode)) &&
        assert(Schema(dynamic3))(not(equalTo(Schema(dynamic1)))) &&
        assert(Schema(dynamic4))(not(equalTo(Schema(dynamic1))))
      },
      test("has access to dynamic documentation") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(),
          doc = Doc("Dynamic"),
          modifiers = Nil
        )
        assert(Schema(dynamic1).doc)(equalTo(Doc("Dynamic")))
      },
      test("has access to dynamic examples") {
        val dynamic1 = Reflect.Dynamic[Binding](
          dynamicBinding = Binding.Dynamic(),
          doc = Doc("Dynamic"),
          modifiers = Nil
        )
        assert(Schema(dynamic1).examples)(equalTo(Nil))
      }
    ),
    suite("Reflect.Deferred")(
      test("has consistent equals and hashCode") {
        val deferred1 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred2 = Reflect.Deferred[Binding, Int](() => Reflect.int)
        val deferred3 = Reflect.int[Binding]
        val deferred4 =
          Primitive(PrimitiveType.Int(Validation.Numeric.Positive), Binding.Primitive.int, TypeName.int, Doc.Empty, Nil)
        val deferred5 = Reflect.Deferred[Binding, Int](() => deferred4)
        assert(Schema(deferred1))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred1).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred2))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred2).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred3))(equalTo(Schema(deferred1))) &&
        assert(Schema(deferred3).hashCode)(equalTo(Schema(deferred1).hashCode)) &&
        assert(Schema(deferred4))(not(equalTo(Schema(deferred1)))) &&
        assert(Schema(deferred5))(not(equalTo(Schema(deferred1))))
      },
      test("has access to deferred documentation") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive.int,
            TypeName.int,
            Doc("Int (positive)"),
            Nil
          )
        }
        assert(Schema(deferred1).doc)(equalTo(Doc("Int (positive)")))
      },
      test("has access to deferred examples") {
        val deferred1 = Reflect.Deferred[Binding, Int] { () =>
          Primitive(
            PrimitiveType.Int(Validation.Numeric.Positive),
            Binding.Primitive(examples = List(1, 2, 3)),
            TypeName.int,
            Doc.Empty,
            Nil
          )
        }
        assert(Schema(deferred1).examples)(equalTo(List(1, 2, 3)))
      }
    )
  )

  case class Record(b: Byte, i: Int)

  object Record {
    val schema: Schema[Record] = Schema(
      reflect = Reflect.Record[Binding, Record](
        fields = List(
          Term("b", Reflect.byte, Doc("Field b"), Nil),
          Term("i", Reflect.int, Doc("Field i"), Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Record"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Record] {
            def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1, ints = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Record =
              Record(in.getByte(baseOffset, 0), in.getInt(baseOffset, 0))
          },
          deconstructor = new Deconstructor[Record] {
            def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1, ints = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Record): Unit = {
              out.setByte(baseOffset, 0, in.b)
              out.setInt(baseOffset, 1, in.i)
            }
          },
          examples = Record(1, 1000) :: Nil
        ),
        doc = Doc("Record with 2 fields"),
        modifiers = Nil
      )
    )
  }

  sealed trait Variant

  object Variant {
    val schema: Schema[Variant] = Schema(
      reflect = Reflect.Variant[Binding, Variant](
        cases = List(
          Term("case1", Case1.schema.reflect, Doc("Case 1"), Nil),
          Term("case2", Case2.schema.reflect, Doc("Case 2"), Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Variant"),
        variantBinding = Binding.Variant(
          discriminator = new Discriminator[Variant] {
            def discriminate(a: Variant): Int = a match {
              case _: Case1 => 0
              case _: Case2 => 1
            }
          },
          matchers = Matchers(
            new Matcher[Case1] {
              def downcastOrNull(a: Any): Case1 = a match {
                case x: Case1 => x
                case _        => null
              }
            },
            new Matcher[Case2] {
              def downcastOrNull(a: Any): Case2 = a match {
                case x: Case2 => x
                case _        => null
              }
            }
          ),
          examples = Case1(1.0) :: Case2("WWW") :: Nil
        ),
        doc = Doc("Variant with 2 cases"),
        modifiers = Nil
      )
    )
  }

  case class Case1(d: Double) extends Variant

  object Case1 {
    val schema: Schema[Case1] = Schema(
      reflect = Reflect.Record[Binding, Case1](
        fields = List(
          Term("d", Reflect.double, Doc.Empty, Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case1"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Case1] {
            def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Case1 =
              Case1(in.getDouble(baseOffset, 0))
          },
          deconstructor = new Deconstructor[Case1] {
            def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case1): Unit =
              out.setDouble(baseOffset, 0, in.d)
          },
          examples = Case1(1.0) :: Nil
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }

  case class Case2(s: String) extends Variant

  object Case2 {
    val schema: Schema[Case2] = Schema(
      reflect = Reflect.Record[Binding, Case2](
        fields = List(
          Term("s", Reflect.string, Doc.Empty, Nil)
        ),
        typeName = TypeName(Namespace(List("zio", "blocks", "schema"), Nil), "Case2"),
        recordBinding = Binding.Record(
          constructor = new Constructor[Case2] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

            def construct(in: Registers, baseOffset: RegisterOffset): Case2 =
              Case2(in.getObject(baseOffset, 0).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[Case2] {
            def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

            def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Case2): Unit =
              out.setObject(baseOffset, 0, in.s)
          },
          examples = Case2("WWW") :: Nil
        ),
        doc = Doc.Empty,
        modifiers = Nil
      )
    )
  }
}
