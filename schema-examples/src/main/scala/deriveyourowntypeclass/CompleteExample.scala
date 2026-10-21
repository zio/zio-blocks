package deriveyourowntypeclass

import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

/**
 * How to Derive Your Own Type Class from a Schema — The Complete Example
 *
 * The whole type class, deriver, bookstore model, a field masked through
 * withModifier and the Email override in one program.
 *
 * Run with: cd schema-examples && sbt "runMain
 * deriveyourowntypeclass.CompleteExample"
 */
object CompleteExample {

  sealed trait Format
  object Format {
    case object Hardcover                  extends Format
    case object Paperback                  extends Format
    final case class Ebook(sizeMb: Double) extends Format

    implicit val schema: Schema[Format] = Schema.derived[Format]
  }

  final case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], format: Format)
  object Book {
    implicit val schema: Schema[Book] = Schema.derived[Book]
  }

  final case class Email(value: String)
  object Email {
    implicit val schema: Schema[Email] = Schema[String].transform(Email(_), _.value)
  }

  final case class Customer(name: String, email: Email)
  object Customer {
    implicit val schema: Schema[Customer] = Schema.derived[Customer]
  }

  final case class Order(
    id: Long,
    customer: Customer,
    books: List[Book],
    @Modifier.config("log.redact", "true") paymentToken: String
  )
  object Order {
    implicit val schema: Schema[Order] = Schema.derived[Order]
  }

  val zionomicon         = Book("Zionomicon", "John A. De Goes", 2024, None, Format.Ebook(12.5))
  val programmingInScala = Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), Format.Hardcover)
  val fpInScala          =
    Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), Format.Paperback)

  val order = Order(1001L, Customer("Ada", Email("ada@example.com")), List(zionomicon), "tok_4242")

  trait Redact[A] {
    def redact(value: A): String
  }

  object Redact {
    val Masked = "***"

    def derived[A](implicit schema: Schema[A]): Redact[A] = schema.derive(RedactDeriver)
  }

  trait RedactStructure extends Deriver[Redact] {

    protected def isSensitive(modifiers: Seq[Modifier]): Boolean =
      modifiers.exists {
        case Modifier.config("log.redact", "true") => true
        case _                                     => false
      }

    override def derivePrimitive[A](
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      binding: Binding.Primitive[A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    ): Lazy[Redact[A]] =
      Lazy {
        primitiveType match {
          case _: PrimitiveType.String => (value: A) => "\"" + value + "\""
          case _                       => (value: A) => String.valueOf(value)
        }
      }

    override def deriveRecord[F[_, _], A](
      fields: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding.Record[A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[A]] = {
      val names     = fields.map(_.name)
      val sensitive = fields.map(field => isSensitive(field.modifiers))
      val reflect   = new Reflect.Record[Binding, A](
        fields.asInstanceOf[IndexedSeq[Term[Binding, A, ?]]],
        typeId,
        binding,
        doc,
        modifiers
      )
      Lazy {
        new Redact[A] {
          private lazy val children: IndexedSeq[Redact[Any]] =
            fields.map(field => D.instance(field.value.metadata).force.asInstanceOf[Redact[Any]])

          def redact(value: A): String = {
            val registers = Registers(reflect.usedRegisters)
            binding.deconstructor.deconstruct(registers, RegisterOffset.Zero, value)
            val rendered = fields.indices.map { i =>
              val shown =
                if (sensitive(i)) Redact.Masked
                else children(i).redact(reflect.registers(i).get(registers, RegisterOffset.Zero))
              s"${names(i)} = $shown"
            }
            if (fields.isEmpty) typeId.name else rendered.mkString(s"${typeId.name}(", ", ", ")")
          }
        }
      }
    }

    override def deriveVariant[F[_, _], A](
      cases: IndexedSeq[Term[F, A, ?]],
      typeId: TypeId[A],
      binding: Binding.Variant[A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[A]] =
      Lazy {
        new Redact[A] {
          private lazy val children: IndexedSeq[Redact[Any]] =
            cases.map(c => D.instance(c.value.metadata).force.asInstanceOf[Redact[Any]])

          def redact(value: A): String = {
            val i = binding.discriminator.discriminate(value)
            children(i).redact(binding.matchers(i).downcastOrNull(value))
          }
        }
      }
  }

  object RedactDeriver extends RedactStructure {

    override def deriveSequence[F[_, _], C[_], A](
      element: Reflect[F, A],
      typeId: TypeId[C[A]],
      binding: Binding.Seq[C, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[C[A]],
      examples: Seq[C[A]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[C[A]]] =
      D.instance(element.metadata).map { elem => (value: C[A]) =>
        binding.deconstructor.deconstruct(value).map(elem.redact).mkString("[", ", ", "]")
      }

    override def deriveMap[F[_, _], M[_, _], K, V](
      key: Reflect[F, K],
      value: Reflect[F, V],
      typeId: TypeId[M[K, V]],
      binding: Binding.Map[M, K, V],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[M[K, V]],
      examples: Seq[M[K, V]]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[M[K, V]]] =
      D.instance(key.metadata).zip(D.instance(value.metadata)).map { case (k, v) =>
        (m: M[K, V]) =>
          val d = binding.deconstructor
          d.deconstruct(m)
            .map(kv => s"${k.redact(d.getKey(kv))} -> ${v.redact(d.getValue(kv))}")
            .mkString("{", ", ", "}")
      }

    override def deriveWrapper[F[_, _], A, B](
      wrapped: Reflect[F, B],
      typeId: TypeId[A],
      binding: Binding.Wrapper[A, B],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[A],
      examples: Seq[A]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[A]] =
      D.instance(wrapped.metadata).map(inner => (value: A) => inner.redact(binding.unwrap(value)))

    override def deriveDynamic[F[_, _]](
      binding: Binding.Dynamic,
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      defaultValue: Option[DynamicValue],
      examples: Seq[DynamicValue]
    )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[Redact[DynamicValue]] =
      Lazy((_: DynamicValue) => Redact.Masked)
  }

  def main(args: Array[String]): Unit = {
    val redactBook: Redact[Book] = Book.schema.derive(RedactDeriver)
    List(zionomicon, programmingInScala, fpInScala).foreach(book => println(redactBook.redact(book)))

    println(Order.schema.derive(RedactDeriver).redact(order))

    val strictDeriver: Deriver[Redact] =
      RedactDeriver.withModifier(TypeId.of[Customer], "name", Modifier.config("log.redact", "true"))
    println(Order.schema.derive(strictDeriver).redact(order))

    val partialEmail: Redact[Email] = (email: Email) => {
      val at = email.value.indexOf('@')
      email.value.take(1) + Redact.Masked + email.value.drop(at)
    }
    val logDeriver: Deriver[Redact] = strictDeriver.withInstance[Email](partialEmail)
    println(Order.schema.derive(logDeriver).redact(order))
  }
}
