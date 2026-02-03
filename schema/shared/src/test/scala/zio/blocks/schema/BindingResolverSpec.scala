package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.schema.json.JsonBinaryCodecDeriver
import zio.blocks.typeid.TypeId
import zio.test._

object BindingResolverSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
  }

  case class SimpleRecord(x: Int, y: String)

  case class AllPrimitives(
    b: Boolean,
    by: Byte,
    c: Char,
    s: Short,
    i: Int,
    l: Long,
    f: Float,
    d: Double,
    str: String
  )

  sealed trait Animal
  case class Dog(name: String) extends Animal
  case class Cat(name: String) extends Animal
  object Dog {
    implicit val schema: Schema[Dog] = Schema.derived[Dog]
  }
  object Cat {
    implicit val schema: Schema[Cat] = Schema.derived[Cat]
  }
  object Animal {
    implicit val schema: Schema[Animal] = Schema.derived[Animal]
  }

  case class UserId(value: Long)
  object UserId {
    implicit val schema: Schema[UserId] = Schema[Long].transform((l: Long) => UserId(l), (u: UserId) => u.value)
  }

  def spec: Spec[TestEnvironment, Any] = suite("BindingResolverSpec")(
    suite("BindingResolver.empty")(
      test("creates an empty registry") {
        val reg = BindingResolver.empty
        assertTrue(
          reg.isEmpty,
          reg.size == 0
        )
      }
    ),
    suite("BindingResolver.defaults")(
      test("contains primitive bindings") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolvePrimitive[Int].isDefined,
          reg.resolvePrimitive[String].isDefined,
          reg.resolvePrimitive[Boolean].isDefined
        )
      },
      test("contains sequence bindings") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolveSeq[List[Int]].isDefined,
          reg.resolveSeq[Vector[String]].isDefined
        )
      },
      test("contains map bindings") {
        val reg = BindingResolver.defaults
        assertTrue(reg.resolveMap[Map[String, Int]].isDefined)
      },
      test("contains dynamic binding") {
        val reg = BindingResolver.defaults
        assertTrue(reg.resolveDynamic.isDefined)
      }
    ),
    suite("BindingResolver.++")(
      test("left resolver takes precedence") {
        val leftBinding  = Binding.Primitive.int
        val left         = BindingResolver.empty.bind(leftBinding)
        val rightBinding = Binding.Primitive.int
        val right        = BindingResolver.empty.bind(rightBinding)
        val combined     = left ++ right

        val resolved = combined.resolvePrimitive[Int]
        assertTrue(
          resolved.isDefined,
          resolved.get eq leftBinding
        )
      },
      test("falls back to right resolver when left has no binding") {
        val left  = BindingResolver.empty
        val right = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(combined.resolvePrimitive[Int].isDefined)
      },
      test("combines record bindings") {
        val personBinding = Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]]
        val left          = BindingResolver.empty.bind(personBinding)
        val right         = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(
          combined.resolveRecord[Person].isDefined,
          combined.resolvePrimitive[Int].isDefined
        )
      },
      test("combines variant bindings") {
        val animalBinding = Schema[Animal].reflect.binding.asInstanceOf[Binding.Variant[Animal]]
        val left          = BindingResolver.empty.bind(animalBinding)
        val right         = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(
          combined.resolveVariant[Animal].isDefined,
          combined.resolvePrimitive[Int].isDefined
        )
      },
      test("combines wrapper bindings") {
        val wrapperBinding = Binding.Wrapper[UserId, Long](
          wrap = l => UserId(l),
          unwrap = u => u.value
        )
        val left  = BindingResolver.empty.bind(wrapperBinding)
        val right = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(
          combined.resolveWrapper[UserId].isDefined,
          combined.resolvePrimitive[Long].isDefined
        )
      },
      test("combines sequence bindings") {
        val left  = BindingResolver.empty.bind[List](Binding.Seq.list[Nothing])
        val right = BindingResolver.empty.bind[Vector](Binding.Seq.vector[Nothing])

        val combined = left ++ right
        assertTrue(
          combined.resolveSeq[List[Int]].isDefined,
          combined.resolveSeq[Vector[String]].isDefined
        )
      },
      test("combines map bindings") {
        val left  = BindingResolver.empty.bind[Map](Binding.Map.map[Nothing, Nothing])
        val right = BindingResolver.defaults

        val combined = left ++ right
        assertTrue(combined.resolveMap[Map[String, Int]].isDefined)
      },
      test("combines dynamic bindings") {
        val left  = BindingResolver.empty.bind(Binding.Dynamic())
        val right = BindingResolver.empty

        val combined = left ++ right
        assertTrue(combined.resolveDynamic.isDefined)
      },
      test("toString shows combination") {
        val left     = BindingResolver.empty
        val right    = BindingResolver.defaults
        val combined = left ++ right
        assertTrue(combined.toString.contains("++"))
      },
      test("is associative for resolution") {
        val a = BindingResolver.empty.bind(Binding.Primitive.int)
        val b = BindingResolver.empty.bind(Binding.Primitive.string)
        val c = BindingResolver.defaults

        val leftAssoc  = (a ++ b) ++ c
        val rightAssoc = a ++ (b ++ c)

        assertTrue(
          leftAssoc.resolvePrimitive[Int].isDefined,
          rightAssoc.resolvePrimitive[Int].isDefined,
          leftAssoc.resolvePrimitive[String].isDefined,
          rightAssoc.resolvePrimitive[String].isDefined
        )
      }
    ),
    suite("BindingResolver.reflection")(
      test("returns None for primitives") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolvePrimitive[Int].isEmpty)
      },
      test("returns None for variants") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveVariant[Animal].isEmpty)
      },
      test("returns None for sequences") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveSeq[List[Int]].isEmpty)
      },
      test("returns None for maps") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveMap[Map[String, Int]].isEmpty)
      },
      test("returns None for wrappers") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveWrapper[UserId].isEmpty)
      },
      test("returns None for dynamic") {
        val resolver = BindingResolver.reflection
        assertTrue(resolver.resolveDynamic.isEmpty)
      },
      test("toString shows Reflection") {
        assertTrue(BindingResolver.reflection.toString.startsWith("BindingResolver.Reflection"))
      },
      test("derives record binding for case class with String and Int") {
        val resolver      = BindingResolver.reflection ++ BindingResolver.defaults
        val dynamicSchema = Schema[Person].toDynamicSchema
        val reboundSchema = dynamicSchema.rebind[Person](resolver)

        val person  = Person("Alice", 30)
        val dynamic = reboundSchema.toDynamicValue(person)
        val result  = reboundSchema.fromDynamicValue(dynamic)

        assertTrue(result == Right(person))
      } @@ TestAspect.jvmOnly,
      test("derives record binding with all primitive types and uses correct registers") {
        implicit val allPrimitivesSchema: Schema[AllPrimitives] = Schema.derived[AllPrimitives]
        val resolver                                            = BindingResolver.reflection ++ BindingResolver.defaults
        val dynamicSchema                                       = allPrimitivesSchema.toDynamicSchema
        val reboundSchema                                       = dynamicSchema.rebind[AllPrimitives](resolver)

        val value = AllPrimitives(
          b = true,
          by = 42.toByte,
          c = 'X',
          s = 1000.toShort,
          i = 123456,
          l = 9876543210L,
          f = 3.14f,
          d = 2.71828,
          str = "hello"
        )
        val dynamic = reboundSchema.toDynamicValue(value)
        val result  = reboundSchema.fromDynamicValue(dynamic)

        assertTrue(result == Right(value))
      } @@ TestAspect.jvmOnly,
      test("reflective binding works with JSON codec roundtrip for Person") {
        val resolver      = BindingResolver.reflection ++ BindingResolver.defaults
        val dynamicSchema = Schema[Person].toDynamicSchema
        val reboundSchema = dynamicSchema.rebind[Person](resolver)

        val codec   = reboundSchema.derive(JsonBinaryCodecDeriver)
        val person  = Person("Bob", 25)
        val encoded = codec.encodeToString(person)
        val decoded = codec.decode(encoded)

        assertTrue(decoded == Right(person))
      } @@ TestAspect.jvmOnly,
      test("reflective binding works with JSON codec roundtrip for AllPrimitives") {
        implicit val allPrimitivesSchema: Schema[AllPrimitives] = Schema.derived[AllPrimitives]
        val resolver                                            = BindingResolver.reflection ++ BindingResolver.defaults
        val dynamicSchema                                       = allPrimitivesSchema.toDynamicSchema
        val reboundSchema                                       = dynamicSchema.rebind[AllPrimitives](resolver)

        val codec = reboundSchema.derive(JsonBinaryCodecDeriver)
        val value = AllPrimitives(
          b = false,
          by = -100.toByte,
          c = 'Z',
          s = -5000.toShort,
          i = -999999,
          l = -1234567890123L,
          f = -1.5f,
          d = 99.99,
          str = "world"
        )
        val encoded = codec.encodeToString(value)
        val decoded = codec.decode(encoded)

        assertTrue(decoded == Right(value))
      } @@ TestAspect.jvmOnly
    ),
    suite("combined resolver usage")(
      test("custom registry overrides defaults") {
        val customInt = Binding.Primitive[Int]()
        val custom    = BindingResolver.empty.bind(customInt)
        val combined  = custom ++ BindingResolver.defaults

        val resolved = combined.resolvePrimitive[Int]
        assertTrue(
          resolved.isDefined,
          resolved.get eq customInt
        )
      },
      test("reflection with defaults for rebind") {
        val resolver = BindingResolver.reflection ++ BindingResolver.defaults

        assertTrue(
          resolver.resolvePrimitive[Int].isDefined,
          resolver.resolveSeq[List[Int]].isDefined,
          resolver.resolveMap[Map[String, Int]].isDefined
        )
      }
    ),
    suite("Registry")(
      test("bind returns new registry instance") {
        val reg1 = BindingResolver.empty
        val reg2 = reg1.bind(Binding.Primitive.int)
        assertTrue(
          reg1 ne reg2,
          reg1.isEmpty,
          !reg2.isEmpty
        )
      },
      test("size reflects number of bindings") {
        val reg = BindingResolver.empty
          .bind(Binding.Primitive.int)
          .bind(Binding.Primitive.string)
          .bind(Binding.Primitive.boolean)
        assertTrue(reg.size == 3)
      },
      test("nonEmpty is true when registry has bindings") {
        val reg = BindingResolver.empty.bind(Binding.Primitive.int)
        assertTrue(reg.nonEmpty)
      },
      test("isEmpty is true for empty registry") {
        assertTrue(BindingResolver.empty.isEmpty)
      },
      test("contains all primitive bindings in defaults") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolvePrimitive[Int].isDefined,
          reg.resolvePrimitive[String].isDefined,
          reg.resolvePrimitive[Boolean].isDefined,
          reg.resolvePrimitive[Long].isDefined,
          reg.resolvePrimitive[Double].isDefined,
          reg.resolvePrimitive[Float].isDefined,
          reg.resolvePrimitive[Short].isDefined,
          reg.resolvePrimitive[Byte].isDefined,
          reg.resolvePrimitive[Char].isDefined,
          reg.resolvePrimitive[Unit].isDefined,
          reg.resolvePrimitive[BigInt].isDefined,
          reg.resolvePrimitive[BigDecimal].isDefined
        )
      },
      test("contains java.time primitive bindings in defaults") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolvePrimitive[java.time.DayOfWeek].isDefined,
          reg.resolvePrimitive[java.time.Duration].isDefined,
          reg.resolvePrimitive[java.time.Instant].isDefined,
          reg.resolvePrimitive[java.time.LocalDate].isDefined,
          reg.resolvePrimitive[java.time.LocalDateTime].isDefined,
          reg.resolvePrimitive[java.time.LocalTime].isDefined,
          reg.resolvePrimitive[java.time.Month].isDefined,
          reg.resolvePrimitive[java.time.MonthDay].isDefined,
          reg.resolvePrimitive[java.time.OffsetDateTime].isDefined,
          reg.resolvePrimitive[java.time.OffsetTime].isDefined,
          reg.resolvePrimitive[java.time.Period].isDefined,
          reg.resolvePrimitive[java.time.Year].isDefined,
          reg.resolvePrimitive[java.time.YearMonth].isDefined,
          reg.resolvePrimitive[java.time.ZoneId].isDefined,
          reg.resolvePrimitive[java.time.ZoneOffset].isDefined,
          reg.resolvePrimitive[java.time.ZonedDateTime].isDefined
        )
      },
      test("contains java.util primitive bindings in defaults") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolvePrimitive[java.util.Currency].isDefined,
          reg.resolvePrimitive[java.util.UUID].isDefined
        )
      },
      test("contains all sequence bindings in defaults") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolveSeq[List[Int]].isDefined,
          reg.resolveSeq[Vector[String]].isDefined,
          reg.resolveSeq[Set[Long]].isDefined,
          reg.resolveSeq[IndexedSeq[Double]].isDefined,
          reg.resolveSeq[Seq[Boolean]].isDefined,
          reg.resolveSeq[Chunk[Int]].isDefined
        )
      },
      test("contains map bindings in defaults") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolveMap[Map[String, Int]].isDefined,
          reg.resolveMap[Map[Int, String]].isDefined
        )
      },
      test("binds record types") {
        val reg = BindingResolver.defaults
          .bind(Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]])
        assertTrue(reg.resolveRecord[Person].isDefined)
      },
      test("binds variant types") {
        val reg = BindingResolver.defaults
          .bind(Schema[Animal].reflect.binding.asInstanceOf[Binding.Variant[Animal]])
        assertTrue(reg.resolveVariant[Animal].isDefined)
      },
      test("binds wrapper types") {
        val reg = BindingResolver.defaults
          .bind(Schema[UserId].reflect.binding.asInstanceOf[Binding.Wrapper[UserId, Long]])
        assertTrue(reg.resolveWrapper[UserId].isDefined)
      },
      test("returns None for unbound types") {
        val reg = BindingResolver.empty
        assertTrue(
          reg.resolveRecord[Person].isEmpty,
          reg.resolveVariant[Animal].isEmpty,
          reg.resolvePrimitive[Int].isEmpty,
          reg.resolveWrapper[UserId].isEmpty,
          reg.resolveDynamic.isEmpty
        )
      },
      test("resolveSeq works with different element types") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolveSeq[List[Int]].isDefined,
          reg.resolveSeq[List[String]].isDefined,
          reg.resolveSeq[List[Person]].isDefined
        )
      },
      test("resolveMap works with different key/value types") {
        val reg = BindingResolver.defaults
        assertTrue(
          reg.resolveMap[Map[String, Int]].isDefined,
          reg.resolveMap[Map[Int, Person]].isDefined
        )
      },
      test("contains returns true for bound types") {
        val reg = BindingResolver.defaults
          .bind(Schema[Person].reflect.binding.asInstanceOf[Binding.Record[Person]])
        assertTrue(reg.contains[Person])
      },
      test("contains returns false for unbound types") {
        val reg = BindingResolver.empty
        assertTrue(!reg.contains[Person])
      },
      test("containsSeq returns true for bound sequence types") {
        val reg = BindingResolver.defaults
        assertTrue(reg.containsSeq[List[Int]])
      },
      test("containsSeq returns false for unbound sequence types") {
        val reg = BindingResolver.empty
        assertTrue(!reg.containsSeq[List[Int]])
      },
      test("containsMap returns true for bound map types") {
        val reg = BindingResolver.defaults
        assertTrue(reg.containsMap[Map[String, Int]])
      },
      test("containsMap returns false for unbound map types") {
        val reg = BindingResolver.empty
        assertTrue(!reg.containsMap[Map[String, Int]])
      },
      test("equal registries are equal") {
        val reg1 = BindingResolver.empty.bind(Binding.Primitive.int)
        val reg2 = BindingResolver.empty.bind(Binding.Primitive.int)
        assertTrue(reg1 == reg2)
      },
      test("different registries are not equal") {
        val reg1 = BindingResolver.empty.bind(Binding.Primitive.int)
        val reg2 = BindingResolver.empty.bind(Binding.Primitive.string)
        assertTrue(reg1 != reg2)
      },
      test("equal registries have same hashCode") {
        val reg1 = BindingResolver.empty.bind(Binding.Primitive.int)
        val reg2 = BindingResolver.empty.bind(Binding.Primitive.int)
        assertTrue(reg1.hashCode == reg2.hashCode)
      },
      test("not equal to other types") {
        val reg = BindingResolver.empty
        assertTrue(!reg.equals("not a registry"))
      },
      test("toString includes entry count") {
        val reg = BindingResolver.empty.bind(Binding.Primitive.int).bind(Binding.Primitive.string)
        assertTrue(reg.toString.contains("2 entries"))
      },
      test("throws for Seq binding via unified bind") {
        val binding = Binding.Seq.list[Nothing]
        val result  = try {
          BindingResolver.empty.bind(binding.asInstanceOf[Binding[Nothing, List[Nothing]]])
          false
        } catch {
          case _: IllegalArgumentException => true
        }
        assertTrue(result)
      },
      test("throws for Map binding via unified bind") {
        val binding = Binding.Map.map[Nothing, Nothing]
        val result  = try {
          BindingResolver.empty.bind(binding.asInstanceOf[Binding[Nothing, Map[Nothing, Nothing]]])
          false
        } catch {
          case _: IllegalArgumentException => true
        }
        assertTrue(result)
      }
    ),
    suite("TypeId.unapplied")(
      test("returns id with no typeArgs for proper type") {
        val id        = TypeId.of[Int]
        val unapplied = TypeId.unapplied(id)
        assertTrue(
          unapplied.typeArgs.isEmpty,
          unapplied.fullName == id.fullName
        )
      },
      test("strips type args from applied type") {
        val applied   = TypeId.of[List[Int]]
        val unapplied = TypeId.unapplied(applied)
        assertTrue(
          unapplied.typeArgs.isEmpty,
          unapplied.name == "List"
        )
      },
      test("different applied types unapply to same constructor") {
        val listInt         = TypeId.of[List[Int]]
        val listString      = TypeId.of[List[String]]
        val unappliedInt    = TypeId.unapplied(listInt)
        val unappliedString = TypeId.unapplied(listString)
        assertTrue(
          unappliedInt.fullName == unappliedString.fullName,
          unappliedInt.typeArgs.isEmpty,
          unappliedString.typeArgs.isEmpty
        )
      },
      test("strips wildcards from type constructor") {
        val listWildcard = TypeId.of[List[_]]
        val unapplied    = TypeId.unapplied(listWildcard)
        assertTrue(
          unapplied.typeArgs.isEmpty,
          unapplied.name == "List"
        )
      }
    ),
    suite("Binding.of and unified bind")(
      test("accepts Binding.of result for Record") {
        val binding  = Binding.of[Person]
        val registry = BindingResolver.defaults.bind(binding)
        assertTrue(registry.resolveRecord[Person].isDefined)
      },
      test("accepts Binding.of result for Variant") {
        val binding  = Binding.of[Animal]
        val registry = BindingResolver.defaults.bind(binding)
        assertTrue(registry.resolveVariant[Animal].isDefined)
      },
      test("accepts Binding.of result for Primitive") {
        val binding  = Binding.of[Int]
        val registry = BindingResolver.empty.bind(binding)
        assertTrue(registry.resolvePrimitive[Int].isDefined)
      },
      test("Binding.of[List] returns Seq binding") {
        val binding = Binding.of[List]
        assertTrue(binding.isInstanceOf[Binding.Seq[List, Nothing]])
      },
      test("Binding.of[Map] returns Map binding") {
        val binding = Binding.of[Map]
        assertTrue(binding.isInstanceOf[Binding.Map[Map, Nothing, Nothing]])
      },
      test("bind[C[_]] works with Binding.of[List]") {
        val binding  = Binding.of[List]
        val registry = BindingResolver.empty.bind[List](binding)
        assertTrue(registry.resolveSeq[List[Int]].isDefined)
      },
      test("bind[M[_, _]] works with Binding.of[Map]") {
        val binding  = Binding.of[Map]
        val registry = BindingResolver.empty.bind[Map](binding)
        assertTrue(registry.resolveMap[Map[String, Int]].isDefined)
      }
    ),
    suite("UnapplySeq")(
      test("UnapplySeq.listInstance provides evidence for List") {
        val u = implicitly[UnapplySeq[List[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.vectorInstance provides evidence for Vector") {
        val u = implicitly[UnapplySeq[Vector[String]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.setInstance provides evidence for Set") {
        val u = implicitly[UnapplySeq[Set[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.indexedSeqInstance provides evidence for IndexedSeq") {
        val u = implicitly[UnapplySeq[IndexedSeq[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.seqInstance provides evidence for Seq") {
        val u = implicitly[UnapplySeq[Seq[Int]]]
        assertTrue(u != null)
      },
      test("UnapplySeq.chunkInstance provides evidence for Chunk") {
        val u = implicitly[UnapplySeq[Chunk[Int]]]
        assertTrue(u != null)
      }
    ),
    suite("UnapplyMap")(
      test("UnapplyMap.mapInstance provides evidence for Map") {
        val u = implicitly[UnapplyMap[Map[String, Int]]]
        assertTrue(u != null)
      }
    )
  )
}
