package deriveyourowntypeclass

import zio.blocks.docs.Doc
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.derive.Deriver
import zio.blocks.typeid.TypeId

// The Redact type class, its structure deriver and RedactDeriver (library file, not runnable).
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
        d.deconstruct(m).map(kv => s"${k.redact(d.getKey(kv))} -> ${v.redact(d.getValue(kv))}").mkString("{", ", ", "}")
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
