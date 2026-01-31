import zio.blocks.schema.binding._
import zio.blocks.schema._
import zio.blocks.schema.derive.Deriver

object DeriveShowExperimental extends App {
  trait Show[A] {
    def show(value: A): String
  }

  object DeriveShow extends Deriver[Show] {

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      binding: Binding[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Show[A]] = Lazy {
      new Show[A] {
        def show(value: A): String =
          primitiveType match {
            case _: PrimitiveType.String => "\"" + value + "\""
            case _: PrimitiveType.Char   => "'" + value + "'"
            case _                       => String.valueOf(value)
          }
      }
    }

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, _]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = Lazy {
      // Cast to Binding.Record to access constructor/deconstructor
      val recordBinding = binding.asInstanceOf[Binding.Record[A]]
      
      // Build a Reflect.Record to get access to the computed registers for each field
      val recordReflect: Reflect.Record[Binding, A] = new Reflect.Record[Binding, A](
        fields.asInstanceOf[IndexedSeq[Term[Binding, A, _]]],
        typeName,
        recordBinding,
        doc,
        modifiers
      )

      // Get Show instances for all fields eagerly (force Lazy evaluation)
      val fieldShowInstances: IndexedSeq[(String, Show[Any])] = fields.map { field =>
        val fieldName    = field.name
        val showInstance = D.instance(field.value.metadata).force.asInstanceOf[Show[Any]]
        (fieldName, showInstance)
      }

      new Show[A] {
        def show(value: A): String = {
          // Create registers and deconstruct the value into them
          val regs = Registers(recordReflect.usedRegisters)
          recordBinding.deconstructor.deconstruct(regs, RegisterOffset.Zero, value)

          // Read each field from its computed register position
          val fieldStrings = fields.indices.map { i =>
            val (fieldName, showInstance) = fieldShowInstances(i)
            val fieldValue                = recordReflect.registers(i).get(regs, RegisterOffset.Zero)
            s"$fieldName = ${showInstance.show(fieldValue)}"
          }

          s"${typeName.name}(${fieldStrings.mkString(", ")})"
        }
      }

    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, _]],
      typeName: TypeName[A],
      binding: Binding[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = ???

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeName: TypeName[C[A]],
      binding: Binding[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[C[A]]] = ???

    override def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeName: TypeName[M[K, V]],
      binding: Binding[BindingType.Map[M], M[K, V]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[M[K, V]],
      examples: Seq[M[K, V]]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[M[K, V]]] = ???

    override def deriveDynamic[F[_, _]](
      binding: Binding[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[DynamicValue]] = ???

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeName: TypeName[A],
      wrapperPrimitiveType: Option[PrimitiveType[A]],
      binding: Binding[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: DeriveShow.HasInstance[F]): Lazy[Show[A]] = ???
  }

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived[Person]
    implicit val show: Show[Person]     = schema.derive(DeriveShow)
  }

  val person = Person("Alice", 30)
  println(Person.show.show(person)) // Expected output: Person(name="Alice", age=30

//  val person = Person("Alice", 30, bestFriend = Some(Person("Bob", 25, bestFriend = None)))
//  println(Person.show.show(person)) // Expected output: Person(name="Alice", age=30)
}
