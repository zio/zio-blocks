package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import java.util.concurrent.atomic.AtomicReference

trait DerivedOptics[T] {
  val _opticsCache: AtomicReference[Any] = new AtomicReference(null)
  def optics(implicit schema: Schema[T]): Any = macro DerivedOpticsMacro.deriveOptics[T]
}

trait DerivedOptics_[T] {
  val _opticsCache: AtomicReference[Any] = new AtomicReference(null)
  def optics(implicit schema: Schema[T]): Any = macro DerivedOpticsMacro.deriveOpticsUnderscore[T]
}

object DerivedOpticsMacro {

  def deriveOptics[T: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[T]]): c.Expr[Any] =
    deriveImpl[T](c)(schema, useUnderscore = false)

  def deriveOpticsUnderscore[T: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[T]]): c.Expr[Any] =
    deriveImpl[T](c)(schema, useUnderscore = true)

  private def deriveImpl[T: c.WeakTypeTag](
    c: whitebox.Context
  )(schemaExpr: c.Expr[Schema[T]], useUnderscore: Boolean): c.Expr[Any] = {
    import c.universe._

    val tpe    = weakTypeOf[T]
    val sym    = tpe.typeSymbol
    val prefix = c.prefix

    def mkAccessorName(name: String): TermName =
      TermName(if (useUnderscore) "_" + name else name)

    def decapitalize(s: String): String =
      if (s.isEmpty) s else s.head.toLower + s.tail

    val isCaseClass   = sym.isClass && sym.asClass.isCaseClass
    val isSealedTrait = sym.isClass && sym.asClass.isSealed

    val tree: c.Tree = if (isCaseClass && !isSealedTrait) {

      val fields = tpe.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.toList

      if (fields.isEmpty) {
        c.abort(c.enclosingPosition, s"Case class $tpe has no fields")
      }

      val (methodDefs, methodDecls) = fields.map { field =>
        val fieldName    = field.name.decodedName.toString
        val accessorName = mkAccessorName(fieldName)
        val fieldType    = field.returnType.asSeenFrom(tpe, tpe.typeSymbol)
        val lensType     = appliedType(weakTypeOf[Lens[_, _]].typeConstructor, List(tpe, fieldType))

        // Definition (Implementation)
        val defn = q"""
          def $accessorName: $lensType = {
            val r = $schemaExpr.reflect
            if (r.isRecord) {
              val record = r.asInstanceOf[_root_.zio.blocks.schema.Reflect.Record.Bound[$tpe]]
              record.fields.find(_.name == ${fieldName}) match {
                case _root_.scala.Some(term) =>
                  _root_.zio.blocks.schema.Lens[$tpe, $fieldType](
                    record, 
                    term.asInstanceOf[_root_.zio.blocks.schema.Term.Bound[$tpe, $fieldType]]
                  )
                case _root_.scala.None =>
                  sys.error("Field '" + ${fieldName} + "' not found in schema")
              }
            } else {
               sys.error("Expected Record schema for " + ${tpe.toString})
            }
          }
        """

        // Declaration (Signature only)
        val decl = q"def $accessorName: $lensType"

        (defn, decl)
      }.unzip

      q"""
        {
          val cached = $prefix._opticsCache.get()
          if (cached != null) {
            cached.asInstanceOf[{ ..$methodDecls }]
          } else {
            val opticsInstance = new {
              ..$methodDefs
            }
            $prefix._opticsCache.compareAndSet(null, opticsInstance)
            $prefix._opticsCache.get().asInstanceOf[{ ..$methodDecls }]
          }
        }
      """

    } else if (isSealedTrait) {

      val sym      = tpe.typeSymbol.asClass
      val children = sym.knownDirectSubclasses.toList.sortBy(_.name.toString)

      if (children.isEmpty) {
        c.abort(
          c.enclosingPosition,
          s"Sealed trait $tpe has no known subclasses. Ensure all subclasses are defined in the same compilation unit."
        )
      }

      val (methodDefs, methodDecls) = children.map { child =>
        val childName    = child.name.decodedName.toString
        val accessorName = mkAccessorName(decapitalize(childName))
        val childType    = child.asType.toType
        val prismType    = appliedType(weakTypeOf[Prism[_, _]].typeConstructor, List(tpe, childType))

        // Definition
        val defn = q"""
          def $accessorName: $prismType = {
            val r = $schemaExpr.reflect
            if (r.isVariant) {
              val variant = r.asInstanceOf[_root_.zio.blocks.schema.Reflect.Variant.Bound[$tpe]]
              variant.cases.find(_.name == ${childName}) match {
                case _root_.scala.Some(term) =>
                  _root_.zio.blocks.schema.Prism[$tpe, $childType](
                    variant, 
                    term.asInstanceOf[_root_.zio.blocks.schema.Term.Bound[$tpe, $childType]]
                  )
                case _root_.scala.None =>
                  sys.error("Variant '" + ${childName} + "' not found in schema")
              }
            } else {
              sys.error("Expected Variant schema for " + ${tpe.toString})
            }
          }
        """

        // Declaration
        val decl = q"def $accessorName: $prismType"

        (defn, decl)
      }.unzip

      q"""
        {
          val cached = $prefix._opticsCache.get()
          if (cached != null) {
            cached.asInstanceOf[{ ..$methodDecls }]
          } else {
            val opticsInstance = new {
              ..$methodDefs
            }
            $prefix._opticsCache.compareAndSet(null, opticsInstance)
            $prefix._opticsCache.get().asInstanceOf[{ ..$methodDecls }]
          }
        }
      """

    } else {
      c.abort(c.enclosingPosition, s"DerivedOptics requires a case class or sealed trait. Got: $tpe")
    }

    c.Expr[Any](tree)
  }
}
// Trigger CI check


