package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import java.util.concurrent.atomic.AtomicReference

trait DerivedOptics[T] {
  protected val _opticsCache: AtomicReference[Any] = new AtomicReference(null)
  def optics(implicit schema: Schema[T]): Any = macro DerivedOpticsMacro.deriveOptics[T]
}

trait DerivedOptics_[T] {
  protected val _opticsCache: AtomicReference[Any] = new AtomicReference(null)
  def optics(implicit schema: Schema[T]): Any = macro DerivedOpticsMacro.deriveOpticsUnderscore[T]
}

object DerivedOpticsMacro {

  def deriveOptics[T: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[T]]): c.Tree = {
    new DerivedOpticsMacroImpl(c).derive[T](schema, useUnderscore = false)
  }

  def deriveOpticsUnderscore[T: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[T]]): c.Tree = {
    new DerivedOpticsMacroImpl(c).derive[T](schema, useUnderscore = true)
  }

  private class DerivedOpticsMacroImpl(val c: whitebox.Context) {
    import c.universe._

    def derive[T: c.WeakTypeTag](schemaExpr: c.Expr[Schema[T]], useUnderscore: Boolean): c.Tree = {
      val tpe = weakTypeOf[T]
      val sym = tpe.typeSymbol

      def mkAccessorName(name: String): TermName = 
        TermName(if (useUnderscore) "_" + name else name)

      def decapitalize(s: String): String = 
        if (s.isEmpty) s else s.head.toLower + s.tail

      val isCaseClass = sym.isClass && sym.asClass.isCaseClass
      val isSealedTrait = sym.isClass && sym.asClass.isSealed

      if (isCaseClass && !isSealedTrait) {
        generateRecordOptics[T](schemaExpr, mkAccessorName, tpe)
      } else if (isSealedTrait) {
        generateVariantOptics[T](schemaExpr, s => mkAccessorName(decapitalize(s)), tpe)
      } else {
        c.abort(c.enclosingPosition, 
          s"DerivedOptics requires a case class or sealed trait. Got: $tpe")
      }
    }

    private def generateRecordOptics[T: c.WeakTypeTag](
      schemaExpr: c.Expr[Schema[T]], 
      mkName: String => TermName,
      tpe: c.Type
    ): c.Tree = {
      
      val fields = tpe.decls.collect {
        case m: MethodSymbol if m.isCaseAccessor => m
      }.toList

      if (fields.isEmpty) {
        c.abort(c.enclosingPosition, s"Case class $tpe has no fields")
      }

      val methodDefs = fields.map { field =>
        val fieldName = field.name.decodedName.toString
        val accessorName = mkName(fieldName)
        val fieldType = field.returnType.asSeenFrom(tpe, tpe.typeSymbol)
        
        val lensType = appliedType(typeOf[Lens[_, _]].typeConstructor, List(tpe, fieldType))
        
        // Note: Using Record[_, $tpe] to match arity, and .fields/.name logic
        q"""
          def $accessorName: $lensType = {
            val record = $schemaExpr.reflect match {
              case r: _root_.zio.blocks.schema.Reflect.Record[_, $tpe] => r
              case other => sys.error("Expected Record, got: " + other.getClass.getName)
            }
            record.fields.find(_.name == ${fieldName}) match {
              case _root_.scala.Some(term) =>
                _root_.zio.blocks.schema.Lens[$tpe, $fieldType](
                  record.asInstanceOf[_root_.zio.blocks.schema.Reflect.Record.Bound[$tpe]], 
                  term.asInstanceOf[_root_.zio.blocks.schema.Term.Bound[$tpe, $fieldType]]
                )
              case _root_.scala.None =>
                sys.error("Field '" + ${fieldName} + "' not found in schema")
            }
          }
        """
      }

      val refinementParts = fields.map { field =>
        val fieldName = field.name.decodedName.toString
        val accessorName = if (mkName(fieldName).toString.startsWith("_")) "_" + fieldName else fieldName
        val fieldType = field.returnType.asSeenFrom(tpe, tpe.typeSymbol)
        val lensType = appliedType(typeOf[Lens[_, _]].typeConstructor, List(tpe, fieldType))
        s"def $accessorName: $lensType"
      }
      
      val refinedTypeStr = refinementParts.mkString("{ ", "; ", " }")
      val refinedType = c.typecheck(c.parse(s"null.asInstanceOf[$refinedTypeStr]"), c.TYPEmode).tpe

      q"""
        {
          val cached = _opticsCache.get()
          if (cached != null) {
            cached.asInstanceOf[$refinedType]
          } else {
            val opticsInstance = new {
              ..$methodDefs
            }
            val result = opticsInstance.asInstanceOf[$refinedType]
            _opticsCache.compareAndSet(null, result)
            _opticsCache.get().asInstanceOf[$refinedType]
          }
        }
      """
    }

    private def generateVariantOptics[T: c.WeakTypeTag](
      schemaExpr: c.Expr[Schema[T]], 
      mkName: String => TermName,
      tpe: c.Type
    ): c.Tree = {
      
      val sym = tpe.typeSymbol.asClass
      val children = sym.knownDirectSubclasses.toList.sortBy(_.name.toString)

      if (children.isEmpty) {
        c.abort(c.enclosingPosition, 
          s"Sealed trait $tpe has no known subclasses. Ensure all subclasses are defined in the same compilation unit.")
      }

      val methodDefs = children.map { child =>
        val childName = child.name.decodedName.toString
        val accessorName = mkName(childName)
        val childType = child.asType.toType
        
        val prismType = appliedType(typeOf[Prism[_, _]].typeConstructor, List(tpe, childType))
        
        // Note: Using Variant[_, $tpe] and .cases/.name logic
        q"""
          def $accessorName: $prismType = {
            val variant = $schemaExpr.reflect match {
              case v: _root_.zio.blocks.schema.Reflect.Variant[_, $tpe] => v
              case other => sys.error("Expected Variant, got: " + other.getClass.getName)
            }
            variant.cases.find(_.name == ${childName}) match {
              case _root_.scala.Some(term) =>
                _root_.zio.blocks.schema.Prism[$tpe, $childType](
                  variant.asInstanceOf[_root_.zio.blocks.schema.Reflect.Variant.Bound[$tpe]], 
                  term.asInstanceOf[_root_.zio.blocks.schema.Term.Bound[$tpe, $childType]]
                )
              case _root_.scala.None =>
                sys.error("Variant '" + ${childName} + "' not found in schema")
            }
          }
        """
      }

      val refinementParts = children.map { child =>
        val childName = child.name.decodedName.toString
        val accessorNameStr = mkName(childName).toString
        val childType = child.asType.toType
        val prismType = appliedType(typeOf[Prism[_, _]].typeConstructor, List(tpe, childType))
        s"def $accessorNameStr: $prismType"
      }
      
      val refinedTypeStr = refinementParts.mkString("{ ", "; ", " }")
      val refinedType = c.typecheck(c.parse(s"null.asInstanceOf[$refinedTypeStr]"), c.TYPEmode).tpe

      q"""
        {
          val cached = _opticsCache.get()
          if (cached != null) {
            cached.asInstanceOf[$refinedType]
          } else {
            val opticsInstance = new {
              ..$methodDefs
            }
            val result = opticsInstance.asInstanceOf[$refinedType]
            _opticsCache.compareAndSet(null, result)
            _opticsCache.get().asInstanceOf[$refinedType]
          }
        }
      """
    }
  }
}