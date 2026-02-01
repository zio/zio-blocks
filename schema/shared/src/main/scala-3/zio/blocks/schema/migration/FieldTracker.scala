package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Compile-time field tracking utilities for schema migrations.
 *
 * Provides compile-time type manipulation and validation capabilities that
 * allow the migration system to track which fields have been handled and verify
 * coverage completeness.
 */
object FieldTracker {

  /**
   * Type-level representation of migration field coverage.
   *
   * At compile time, this tracks which fields have been handled by migration
   * actions, enabling the build method to verify completeness.
   */
  sealed trait Coverage

  object Coverage {

    /** All fields are covered by migration actions. */
    sealed trait Complete extends Coverage

    /** Some fields are missing coverage. */
    sealed trait Partial extends Coverage
  }

  /**
   * Evidence that a field has been provided in the target schema.
   */
  trait Provided[Name <: String]

  /**
   * Evidence that a field has been handled from the source schema.
   */
  trait Handled[Name <: String]

  /**
   * Type-level set of field names.
   */
  sealed trait FieldSet

  object FieldSet {
    sealed trait Empty                                      extends FieldSet
    sealed trait NonEmpty[Head <: String, Tail <: FieldSet] extends FieldSet
  }

  /**
   * Type-level proof that one field set contains another.
   */
  trait Contains[Field <: String, Set <: FieldSet]

  object Contains {
    given headContains[F <: String, T <: FieldSet]: Contains[F, FieldSet.NonEmpty[F, T]] with {}

    given tailContains[F <: String, H <: String, T <: FieldSet](using
      ev: Contains[F, T]
    ): Contains[F, FieldSet.NonEmpty[H, T]] with {}
  }

  /**
   * Macro to extract field names from a type at compile time.
   */
  inline def fieldsOf[A]: List[String] = ${ fieldsOfImpl[A] }

  private def fieldsOfImpl[A: Type](using Quotes): Expr[List[String]] = {
    import quotes.reflect.*

    val tpe    = TypeRepr.of[A]
    val fields = extractAllFields(tpe)
    Expr(fields)
  }

  private def extractAllFields(using Quotes)(tpe: quotes.reflect.TypeRepr): List[String] = {
    import quotes.reflect.*

    tpe.dealias match {
      case ref: Refinement =>
        extractRefinementFields(ref)

      case tpe if tpe.typeSymbol.flags.is(Flags.Case) =>
        tpe.typeSymbol.caseFields.map(_.name)

      case tpe if tpe.typeSymbol.isClassDef =>
        tpe.typeSymbol.fieldMembers
          .filter(_.flags.is(Flags.ParamAccessor))
          .map(_.name)

      case _ => Nil
    }
  }

  private def extractRefinementFields(using
    Quotes
  )(
    refinement: quotes.reflect.Refinement
  ): List[String] = {
    import quotes.reflect.*

    def loop(tpe: TypeRepr, acc: List[String]): List[String] = tpe match {
      case Refinement(parent, name, _) if name != "Tag" =>
        loop(parent, name :: acc)
      case Refinement(parent, _, _) =>
        loop(parent, acc)
      case _ => acc
    }

    loop(refinement, Nil)
  }

  /**
   * Compile-time check if a type is a structural type (refinement).
   */
  inline def isStructural[A]: Boolean = ${ isStructuralImpl[A] }

  private def isStructuralImpl[A: Type](using Quotes): Expr[Boolean] = {
    import quotes.reflect.*

    val tpe          = TypeRepr.of[A]
    val isStructural = tpe.dealias match {
      case _: Refinement => true
      case _             => false
    }

    Expr(isStructural)
  }

  /**
   * Get the type name at compile time.
   */
  inline def nameOf[A]: String = ${ nameOfImpl[A] }

  private def nameOfImpl[A: Type](using Quotes): Expr[String] = {
    import quotes.reflect.*
    Expr(TypeRepr.of[A].show)
  }

  /**
   * Runtime field tracking for migration coverage analysis.
   */
  case class TrackedFields(
    handledFromSource: Set[String],
    providedToTarget: Set[String],
    renamedFields: Map[String, String],
    droppedFields: Set[String],
    addedFields: Set[String]
  ) {
    def handleField(name: String): TrackedFields =
      copy(handledFromSource = handledFromSource + name)

    def provideField(name: String): TrackedFields =
      copy(providedToTarget = providedToTarget + name)

    def renameField(from: String, to: String): TrackedFields =
      copy(
        renamedFields = renamedFields + (from -> to),
        handledFromSource = handledFromSource + from,
        providedToTarget = providedToTarget + to
      )

    def dropField(name: String): TrackedFields =
      copy(
        droppedFields = droppedFields + name,
        handledFromSource = handledFromSource + name
      )

    def addField(name: String): TrackedFields =
      copy(
        addedFields = addedFields + name,
        providedToTarget = providedToTarget + name
      )

    def isComplete(sourceFields: Set[String], targetFields: Set[String]): Boolean =
      sourceFields.subsetOf(handledFromSource) && targetFields.subsetOf(providedToTarget)

    def missingFromSource(sourceFields: Set[String]): Set[String] =
      sourceFields.diff(handledFromSource)

    def missingFromTarget(targetFields: Set[String]): Set[String] =
      targetFields.diff(providedToTarget)
  }

  object TrackedFields {
    val empty: TrackedFields = TrackedFields(
      handledFromSource = Set.empty,
      providedToTarget = Set.empty,
      renamedFields = Map.empty,
      droppedFields = Set.empty,
      addedFields = Set.empty
    )
  }
}
