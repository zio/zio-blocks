package typeclassderivation

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

import scala.reflect.ClassTag
import scala.util.Random

/**
 * A simple example of deriving a Gen (random value generator) type class.
 *
 * Gen[A] produces random values of type A - useful for property-based testing,
 * generating sample data, and fuzzing.
 *
 * This example demonstrates the core Deriver pattern with minimal complexity.
 * As the `Gen` type class is a generator, its derivation logic focuses on
 * producing random values of type `A` for various schema structures; which
 * means it requires constructing values rather than deconstructing them.
 */
object DeriveGenExample extends App {

  trait Gen[A] {
    def generate(random: Random): A
  }

  object Gen {
    def apply[A](implicit gen: Gen[A]): Gen[A] = gen
  }

  object DeriveGen extends Deriver[Gen] {

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Gen[A]] =
      Lazy {
        new Gen[A] {
          def generate(random: Random): A = primitiveType match {
            case _: PrimitiveType.String  => random.alphanumeric.take(random.nextInt(10) + 1).mkString.asInstanceOf[A]
            case _: PrimitiveType.Char    => random.alphanumeric.head.asInstanceOf[A]
            case _: PrimitiveType.Boolean => random.nextBoolean().asInstanceOf[A]
            case _: PrimitiveType.Int     => random.nextInt().asInstanceOf[A]
            case _: PrimitiveType.Long    => random.nextLong().asInstanceOf[A]
            case _: PrimitiveType.Double  => random.nextDouble().asInstanceOf[A]
            case PrimitiveType.Unit       => ().asInstanceOf[A]
            // For brevity, other primitives default to their zero/empty value
            // In a real implementation, you'd want to handle all primitives and possibly use modifiers for ranges, etc.
            case _ =>
              defaultValue.getOrElse {
                throw new IllegalArgumentException(
                  s"Gen derivation not implemented for primitive type $primitiveType " +
                    s"(typeId = $typeId) and no default value provided."
                )
              }
          }
        }
      }

    /**
     * Strategy:
     *   1. Get Gen type class instances for each field
     *   2. Generate random values for each field
     *   3. Use the constructor to build the record
     */
    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, _]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] =
      Lazy {
        // Get Gen instances for each field
        val fieldGens: IndexedSeq[Lazy[Gen[Any]]] = fields.map { field =>
          D.instance(field.value.metadata).asInstanceOf[Lazy[Gen[Any]]]
        }

        // Build Reflect.Record to access registers and constructor
        val recordFields  = fields.asInstanceOf[IndexedSeq[Term[Binding, A, _]]]
        val recordBinding = binding.asInstanceOf[Binding.Record[A]]
        val recordReflect = new Reflect.Record[Binding, A](recordFields, typeId, recordBinding, doc, modifiers)

        new Gen[A] {
          def generate(random: Random): A = {
            // Create registers to hold field values
            val registers = Registers(recordReflect.usedRegisters)

            // Generate each field and store in registers
            fields.indices.foreach { i =>
              val value = fieldGens(i).force.generate(random)
              recordReflect.registers(i).set(registers, RegisterOffset.Zero, value)
            }

            // Construct the record from registers
            recordBinding.constructor.construct(registers, RegisterOffset.Zero)
          }
        }
      }

    /**
     * Strategy:
     *   1. Get Gen type class instances for all cases
     *   2. Randomly pick a case
     *   3. Generate a value for that case
     */
    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, _]],
      typeId: TypeId[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] = Lazy {
      // Get Gen instances for all cases
      val caseGens: IndexedSeq[Lazy[Gen[A]]] = cases.map { c =>
        D.instance(c.value.metadata).asInstanceOf[Lazy[Gen[A]]]
      }

      new Gen[A] {
        def generate(random: Random): A = {
          // Pick a random case and generate its value
          val caseIndex = random.nextInt(cases.length)
          caseGens(caseIndex).force.generate(random)
        }
      }
    }

    /**
     * Strategy:
     *   1. Get Gen type class instances for the element type
     *   2. Generate 0-5 elements
     *   3. Build the collection using the constructor
     */
    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeId: TypeId[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[C[A]]] = Lazy {
      val elementGen   = D.instance(element.metadata)
      val seqBinding   = binding.asInstanceOf[Binding.Seq[C, A]]
      val constructor  = seqBinding.constructor
      val elemClassTag = element.typeId.classTag.asInstanceOf[ClassTag[A]]

      new Gen[C[A]] {
        def generate(random: Random): C[A] = {
          val length                   = random.nextInt(6) // 0 to 5 elements
          implicit val ct: ClassTag[A] = ClassTag.Any.asInstanceOf[ClassTag[A]]

          if (length == 0) {
            constructor.empty[A]
          } else {
            val builder = constructor.newBuilder[A](length)(elemClassTag)
            (0 until length).foreach { _ =>
              constructor.add(builder, elementGen.force.generate(random))
            }
            constructor.result(builder)
          }
        }
      }
    }

    /**
     * Strategy:
     *   1. Get Gen type class instances for key and value types
     *   2. Generate 0-5 key-value pairs
     *   3. Build the map using the constructor
     */
    override def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeId: TypeId[M[K, V]],
      binding: Binding[BindingType.Map[M], M[K, V]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[M[K, V]],
      examples: Seq[M[K, V]]
    )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[M[K, V]]] = Lazy {
      val keyGen      = D.instance(key.metadata)
      val valueGen    = D.instance(value.metadata)
      val mapBinding  = binding.asInstanceOf[Binding.Map[M, K, V]]
      val constructor = mapBinding.constructor

      new Gen[M[K, V]] {
        def generate(random: Random): M[K, V] = {
          val size = random.nextInt(6) // 0 to 5 entries

          if (size == 0) {
            constructor.emptyObject[K, V]
          } else {
            val builder = constructor.newObjectBuilder[K, V](size)
            (0 until size).foreach { _ =>
              constructor.addObject(builder, keyGen.force.generate(random), valueGen.force.generate(random))
            }
            constructor.resultObject(builder)
          }
        }
      }
    }

    /**
     * Since DynamicValue can represent any schema type, we generate random
     * dynamic values by randomly choosing a variant and generating appropriate
     * content.
     */
    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[DynamicValue]] = Lazy {
      new Gen[DynamicValue] {
        // Helper to generate a random primitive value
        private def randomPrimitive(random: Random): DynamicValue.Primitive = {
          val primitiveType = random.nextInt(5)
          primitiveType match {
            case 0 => DynamicValue.Primitive(PrimitiveValue.Int(random.nextInt()))
            case 1 => DynamicValue.Primitive(PrimitiveValue.String(random.alphanumeric.take(10).mkString))
            case 2 => DynamicValue.Primitive(PrimitiveValue.Boolean(random.nextBoolean()))
            case 3 => DynamicValue.Primitive(PrimitiveValue.Double(random.nextDouble()))
            case 4 => DynamicValue.Primitive(PrimitiveValue.Long(random.nextLong()))
          }
        }

        def generate(random: Random): DynamicValue = {
          // Randomly choose what kind of DynamicValue to generate
          // Weight towards primitives and simpler structures to avoid deep nesting
          val valueType = random.nextInt(10)
          valueType match {
            case 0 | 1 | 2 | 3 | 4 =>
              // 50% chance: generate a primitive
              randomPrimitive(random)

            case 5 | 6 =>
              // 20% chance: generate a record with 1-3 fields
              val numFields = random.nextInt(3) + 1
              val fields    = (0 until numFields).map { i =>
                val fieldName  = s"field$i"
                val fieldValue = randomPrimitive(random)
                (fieldName, fieldValue: DynamicValue)
              }
              DynamicValue.Record(Chunk.from(fields))

            case 7 | 8 =>
              // 20% chance: generate a sequence of 0-3 primitives
              val numElements = random.nextInt(4)
              val elements    = (0 until numElements).map(_ => randomPrimitive(random): DynamicValue)
              DynamicValue.Sequence(Chunk.from(elements))

            case 9 =>
              // 10% chance: generate null
              DynamicValue.Null
          }
        }
      }
    }

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeId: TypeId[A],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveGen.HasInstance[F]): Lazy[Gen[A]] = Lazy {
      val wrappedGen     = D.instance(wrapped.metadata)
      val wrapperBinding = binding.asInstanceOf[Binding.Wrapper[A, B]]

      new Gen[A] {
        def generate(random: Random): A =
          wrapperBinding.wrap(wrappedGen.force.generate(random))
      }
    }
  }

  // ===========================================
  // Examples
  // ===========================================

  val random = new Random(42) // Seeded for reproducible output

  // Example 1: Simple Record
  println("=== Example 1: Record (case class) ===")
  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val gen: Gen[Person]       = schema.derive(DeriveGen)
  }
  (1 to 3).foreach(i => println(s"  $i: ${Person.gen.generate(random)}"))

  // Example 2: Variant (sealed trait)
  println("\n=== Example 2: Variant (sealed trait) ===")
  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape
  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived[Shape]
    implicit val gen: Gen[Shape]       = schema.derive(DeriveGen)
  }
  (1 to 4).foreach(i => println(s"  $i: ${Shape.gen.generate(random)}"))

  // Example 3: Sequence (List)
  println("\n=== Example 3: Sequence (List) ===")
  case class Team(members: List[String])
  object Team {
    implicit val schema: Schema[Team] = Schema.derived[Team]
    implicit val gen: Gen[Team]       = schema.derive(DeriveGen)
  }
  (1 to 3).foreach(i => println(s"  $i: ${Team.gen.generate(random)}"))

  // Example 4: Recursive type
  println("\n=== Example 4: Recursive type ===")
  case class Tree(value: Int, children: List[Tree])
  object Tree {
    implicit val schema: Schema[Tree] = Schema.derived[Tree]
    implicit val gen: Gen[Tree]       = schema.derive(DeriveGen)
  }
  println(s"  ${Tree.gen.generate(random)}")

  // Example 5: DynamicValue
  println("\n=== Example 5: DynamicValue ===")
  implicit val dynamicGen: Gen[DynamicValue] = Schema.dynamic.derive(DeriveGen)
  (1 to 3).foreach(i => println(s"  $i: ${dynamicGen.generate(random)}"))
}
