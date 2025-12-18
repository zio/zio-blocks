package zio.blocks.schema

import scala.quoted.*

/**
 * Common macro utilities trait providing path-dependent types and helper methods
 * for macro implementations. Both SchemaVersionSpecificImpl and IntoVersionSpecificImpl
 * extend this trait to share common infrastructure.
 */
private[schema] trait MacroUtils(using val quotes: Quotes) {
  import quotes.reflect.*

  // === Error handling ===
  
  protected def fail(msg: String): Nothing = CommonMacroOps.fail(msg)

  // === Type utilities ===

  protected def typeArgs(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.typeArgs(tpe)

  protected def isProductType(symbol: Symbol): Boolean = CommonMacroOps.isProductType(symbol)

  protected def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = 
    CommonMacroOps.isSealedTraitOrAbstractClass(tpe)

  protected def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = 
    CommonMacroOps.isNonAbstractScalaClass(tpe)

  protected def isEnumValue(tpe: TypeRepr): Boolean = CommonMacroOps.isEnumValue(tpe)

  protected def isEnumOrModuleValue(tpe: TypeRepr): Boolean = CommonMacroOps.isEnumOrModuleValue(tpe)

  protected def isOpaque(tpe: TypeRepr): Boolean = CommonMacroOps.isOpaque(tpe)

  protected def opaqueDealias(tpe: TypeRepr): TypeRepr = CommonMacroOps.opaqueDealias(tpe)

  protected def isZioPreludeNewtype(tpe: TypeRepr): Boolean = CommonMacroOps.isZioPreludeNewtype(tpe)

  protected def zioPreludeNewtypeDealias(tpe: TypeRepr): TypeRepr = 
    CommonMacroOps.zioPreludeNewtypeDealias(tpe)

  protected def isTypeRef(tpe: TypeRepr): Boolean = CommonMacroOps.isTypeRef(tpe)

  protected def typeRefDealias(tpe: TypeRepr): TypeRepr = CommonMacroOps.typeRefDealias(tpe)

  protected def dealiasOnDemand(tpe: TypeRepr): TypeRepr = CommonMacroOps.dealiasOnDemand(tpe)

  protected def isGenericTuple(tpe: TypeRepr): Boolean = CommonMacroOps.isGenericTuple(tpe)

  protected def genericTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] = 
    CommonMacroOps.genericTupleTypeArgs(tpe)

  protected def normalizeGenericTuple(typeArgs: List[TypeRepr]): TypeRepr = 
    CommonMacroOps.normalizeGenericTuple(typeArgs)

  protected def isUnion(tpe: TypeRepr): Boolean = CommonMacroOps.isUnion(tpe)

  protected def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.allUnionTypes(tpe)

  protected def directSubTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.directSubTypes(tpe)

  // === Path-dependent type info classes ===

  /** Information about a field in a product type */
  protected case class FieldInfo(
    name: String,
    tpe: TypeRepr,
    index: Int,
    getter: Symbol
  )

  /** 
   * Information about a product type (case class).
   * Provides field extraction, construction, and deconstruction utilities.
   */
  protected class ProductInfo[T: Type](val tpe: TypeRepr) {
    private val tpeClassSymbol              = tpe.classSymbol.get
    val primaryConstructor: Symbol          = tpeClassSymbol.primaryConstructor
    val tpeTypeArgs: List[TypeRepr]         = typeArgs(tpe)
    
    // Cached member lookups (same pattern as SchemaVersionSpecific)
    private var _fieldMembers: List[Symbol]  = null
    private var _methodMembers: List[Symbol] = null
    
    private def fieldMembers: List[Symbol] = {
      if (_fieldMembers eq null) _fieldMembers = tpeClassSymbol.fieldMembers
      _fieldMembers
    }
    
    private def methodMembers: List[Symbol] = {
      if (_methodMembers eq null) _methodMembers = tpeClassSymbol.methodMembers
      _methodMembers
    }

    /** All fields flattened from all parameter lists */
    val fields: List[FieldInfo] = {
      val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }
      val caseFields = tpeClassSymbol.caseFields
      var idx = 0
      
      tpeParams.flatten.map { symbol =>
        idx += 1
        var fTpe = tpe.memberType(symbol).dealias
        if (tpeTypeArgs.nonEmpty) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
        
        val name = symbol.name
        val getter = caseFields
          .find(_.name == name)
          .orElse(fieldMembers.find(_.name == name))
          .orElse(methodMembers.find(member => member.name == name && member.flags.is(Flags.FieldAccessor)))
          .getOrElse {
            fail(s"Field or getter '$name' of '${tpe.show}' should be defined as 'val' or 'var' in the primary constructor.")
          }
        
        if (getter.flags.is(Flags.PrivateLocal)) {
          fail(s"Field or getter '$name' of '${tpe.show}' should be defined as 'val' or 'var' in the primary constructor.")
        }
        
        FieldInfo(name, fTpe, idx - 1, getter)
      }
    }

    /** Construct an instance of T from a list of field value Terms (in field order) */
    def construct(args: List[Term]): Term = {
      val constructor = Select(New(Inferred(tpe)), primaryConstructor)
      val constructorWithTypes = 
        if (tpeTypeArgs.nonEmpty) constructor.appliedToTypes(tpeTypeArgs)
        else constructor
      Apply(constructorWithTypes, args)
    }

    /** Get the getter Term for reading a field from an instance */
    def fieldGetter(instance: Term, field: FieldInfo): Term = 
      Select(instance, field.getter)
  }
}

