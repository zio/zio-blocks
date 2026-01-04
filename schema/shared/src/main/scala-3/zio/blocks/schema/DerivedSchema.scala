package zio.blocks.schema

import java.util.concurrent.ConcurrentHashMap
import scala.quoted.*

/**
 * A trait that can be extended by companion objects to automatically derive a
 * Schema for the type.
 *
 * Usage:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person extends DerivedOptics[Person] with DerivedSchema[Person]
 * }}}
 *
 * The schema is cached to avoid recreation on every access.
 */
trait DerivedSchema[A] {

  /**
   * Derives a Schema for the type A. The schema is cached to ensure it is only
   * constructed once.
   */
  inline implicit def schema: Schema[A] = ${ DerivedSchemaMacros.schemaImpl[A] }
}

private[schema] object DerivedSchemaMacros {
  // Global cache to store derived schemas
  private val cache = new ConcurrentHashMap[String, Any]()

  private[schema] def getOrCreate[T](key: String, create: => T): T = {
    var result = cache.get(key)
    if (result == null) {
      result = create
      val existing = cache.putIfAbsent(key, result)
      if (existing != null) result = existing
    }
    result.asInstanceOf[T]
  }

  def schemaImpl[A: Type](using q: Quotes): Expr[Schema[A]] = {
    import q.reflect.*

    val tpe     = TypeRepr.of[A]
    val typeKey = tpe.dealias.show

    '{
      DerivedSchemaMacros.getOrCreate[Schema[A]](
        ${ Expr(typeKey) },
        Schema.derived[A]
      )
    }
  }
}
