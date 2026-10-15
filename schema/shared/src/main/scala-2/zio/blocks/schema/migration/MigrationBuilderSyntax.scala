/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import scala.reflect.macros.whitebox
import zio.blocks.schema.SchemaExpr

object MigrationBuilderMacros {

  private def extractFieldPathFromSelector(c: whitebox.Context)(selector: c.Tree): String = {
    import c.universe._

    def extractFromBody(body: c.Tree): List[String] = body match {
      case Select(parent, fieldName) => extractFromBody(parent) :+ fieldName.decodedName.toString
      case Ident(_)                  => Nil
      case _                         => c.abort(c.enclosingPosition, s"Cannot extract field path from selector: ${showRaw(body)}")
    }

    selector match {
      case q"($_) => $body" =>
        val segments = extractFromBody(body)
        if (segments.isEmpty)
          c.abort(c.enclosingPosition, "Selector must access at least one field")
        else
          segments.mkString(".")
      case _ => c.abort(c.enclosingPosition, s"Expected a lambda expression, got: ${showRaw(selector)}")
    }
  }

  private def createRefinedType(c: whitebox.Context)(currentType: c.Type, fieldName: String): c.Type = {
    import c.universe._

    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    val fieldNameTpe     = appliedType(typeOf[Changeset.FieldName[_]].typeConstructor, fieldNameLiteral)
    c.internal.refinedType(List(currentType, fieldNameTpe), c.internal.newScopeWith())
  }

  private def addToChangeset(c: whitebox.Context)(currentType: c.Type, opType: c.Type): c.Type =
    c.internal.refinedType(List(currentType, opType), c.internal.newScopeWith())

  private def checkCollisionAtCallSite(c: whitebox.Context)(
    opLabel: String,
    handledNames: List[String],
    providedNames: List[String],
    checkAutoMapped: Boolean,
    aType: c.Type,
    bType: c.Type,
    csType: c.Type
  ): Unit = {
    val (priorHandled, priorProvided) = extractHandledAndProvidedCounts(c)(csType)

    def incremented(prior: Map[String, Int], names: List[String]): Map[String, Int] =
      names.foldLeft(prior)((counts, name) => counts.updated(name, counts.getOrElse(name, 0) + 1))

    val newHandled  = incremented(priorHandled, handledNames)
    val newProvided = incremented(priorProvided, providedNames)

    def countOf(counts: Map[String, Int], name: String): Int = counts.getOrElse(name, 0)

    (providedNames ++ newProvided.keySet).distinct.collectFirst {
      case n if countOf(newProvided, n) > 1 && countOf(newHandled, n) != 1 => n
    }.foreach { n =>
      c.abort(
        c.enclosingPosition,
        s"$opLabel failed: field '$n' is already provided by a previous operation. " +
          s"Each target field must be provided exactly once."
      )
    }

    (handledNames ++ newHandled.keySet).distinct.collectFirst {
      case n if countOf(newHandled, n) > 1 && countOf(newProvided, n) != 1 => n
    }.foreach { n =>
      c.abort(
        c.enclosingPosition,
        s"$opLabel failed: field '$n' is already handled/consumed by a previous operation. " +
          s"Each source field must be handled exactly once."
      )
    }

    if (checkAutoMapped) {
      val autoMapped = computeAutoMappedFields(c)(aType, bType)
      providedNames.collectFirst { case n if autoMapped.contains(n) && countOf(newHandled, n) != 1 => n }.foreach { n =>
        c.abort(
          c.enclosingPosition,
          s"$opLabel failed: field '$n' collides with an auto-mapped field that is already provided. " +
            s"Rename targets must not collide with existing fields."
        )
      }
    }
  }

  private def extractHandledAndProvidedCounts(c: whitebox.Context)(
    tpe: c.universe.Type
  ): (Map[String, Int], Map[String, Int]) = {
    import c.universe._

    var handled  = Map.empty[String, Int]
    var provided = Map.empty[String, Int]

    def count(m: Map[String, Int], n: String): Map[String, Int] =
      m.updated(n, m.getOrElse(n, 0) + 1)

    def extractString(t: Type): Option[String] = t.dealias match {
      case ConstantType(Constant(s: String)) => Some(s)
      case _                                 => None
    }

    def extract(t: Type): Unit = t.dealias match {
      case RefinedType(parents, _) =>
        parents.foreach(extract)
      case t if t =:= typeOf[Any] => ()
      case TypeRef(_, sym, args)  =>
        sym.name.toString match {
          case "AddField" =>
            args.headOption.flatMap(extractString).foreach(n => provided = count(provided, n))
          case "DropField" =>
            args.headOption.flatMap(extractString).foreach(n => handled = count(handled, n))
          case "RenameField" =>
            args.headOption.flatMap(extractString).foreach(n => handled = count(handled, n))
            args.lift(1).flatMap(extractString).foreach(n => provided = count(provided, n))
          case "TransformField" =>
            args.headOption.flatMap(extractString).foreach(n => handled = count(handled, n))
            args.lift(1).flatMap(extractString).foreach(n => provided = count(provided, n))
          case "MandateField" =>
            args.headOption.flatMap(extractString).foreach(n => handled = count(handled, n))
            args.lift(1).flatMap(extractString).foreach(n => provided = count(provided, n))
          case "OptionalizeField" =>
            args.headOption.flatMap(extractString).foreach(n => handled = count(handled, n))
            args.lift(1).flatMap(extractString).foreach(n => provided = count(provided, n))
          case "ChangeFieldType" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled = count(handled, n)
              provided = count(provided, n)
            }
          case "MigrateField" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled = count(handled, n)
              provided = count(provided, n)
            }
          case "FieldName" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled = count(handled, n)
              provided = count(provided, n)
            }
          case "RenameCase" | "TransformCase" | "TransformElements" | "TransformKeys" | "TransformValues" =>
            ()
          case _ => ()
        }
      case _ => ()
    }

    extract(tpe)
    (handled, provided)
  }

  private def computeAutoMappedFields(c: whitebox.Context)(
    aType: c.universe.Type,
    bType: c.universe.Type
  ): Set[String] = {
    import c.universe._

    val aSym = aType.typeSymbol
    val bSym = bType.typeSymbol

    if (aSym.isClass && aSym.asClass.isCaseClass && bSym.isClass && bSym.asClass.isCaseClass) {
      val aCtor = aType.decl(termNames.CONSTRUCTOR).asMethod
      val bCtor = bType.decl(termNames.CONSTRUCTOR).asMethod

      val aFields: Map[String, Type] = aCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(aType, aSym))
        .toMap

      val bFields: Map[String, Type] = bCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(bType, bSym))
        .toMap

      aFields.keySet
        .intersect(bFields.keySet)
        .flatMap { fieldName =>
          (aFields.get(fieldName), bFields.get(fieldName)) match {
            case (Some(srcType), Some(tgtType)) if srcType =:= tgtType =>
              Set(fieldName) ++ computeAutoMappedNested(c)(srcType, tgtType, fieldName)
            case (Some(srcType), Some(tgtType)) if srcType <:< tgtType || tgtType <:< srcType =>
              Set(fieldName)
            case _ => Set.empty[String]
          }
        }
        .toSet
    } else {
      Set.empty
    }
  }

  private def computeAutoMappedNested(c: whitebox.Context)(
    srcType: c.universe.Type,
    tgtType: c.universe.Type,
    prefix: String
  ): Set[String] = {
    import c.universe._

    val srcSym = srcType.typeSymbol
    val tgtSym = tgtType.typeSymbol

    if (srcSym.isClass && srcSym.asClass.isCaseClass && tgtSym.isClass && tgtSym.asClass.isCaseClass) {
      val srcCtor = srcType.decl(termNames.CONSTRUCTOR).asMethod
      val tgtCtor = tgtType.decl(termNames.CONSTRUCTOR).asMethod

      val srcFields: Map[String, Type] = srcCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(srcType, srcSym))
        .toMap

      val tgtFields: Map[String, Type] = tgtCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(tgtType, tgtSym))
        .toMap

      srcFields.keySet
        .intersect(tgtFields.keySet)
        .flatMap { fieldName =>
          val fullFieldName = s"$prefix.$fieldName"
          (srcFields.get(fieldName), tgtFields.get(fieldName)) match {
            case (Some(srcFieldType), Some(tgtFieldType)) if srcFieldType =:= tgtFieldType =>
              Set(fullFieldName) ++ computeAutoMappedNested(c)(srcFieldType, tgtFieldType, fullFieldName)
            case (Some(srcFieldType), Some(tgtFieldType))
                if srcFieldType <:< tgtFieldType || tgtFieldType <:< srcFieldType =>
              Set(fullFieldName)
            case _ => Set.empty[String]
          }
        }
        .toSet
    } else {
      Set.empty
    }
  }

  private def createAddFieldType(c: whitebox.Context)(fieldName: String): c.Type = {
    import c.universe._
    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    appliedType(typeOf[Changeset.AddField[_]].typeConstructor, fieldNameLiteral)
  }

  private def createDropFieldType(c: whitebox.Context)(fieldName: String): c.Type = {
    import c.universe._
    val fieldNameLiteral = c.internal.constantType(Constant(fieldName))
    appliedType(typeOf[Changeset.DropField[_]].typeConstructor, fieldNameLiteral)
  }

  private def createRenameFieldType(c: whitebox.Context)(from: String, to: String): c.Type = {
    import c.universe._
    val fromLiteral = c.internal.constantType(Constant(from))
    val toLiteral   = c.internal.constantType(Constant(to))
    appliedType(typeOf[Changeset.RenameField[_, _]].typeConstructor, fromLiteral, toLiteral)
  }

  private def createTransformFieldType(c: whitebox.Context)(from: String, to: String): c.Type = {
    import c.universe._
    val fromLiteral = c.internal.constantType(Constant(from))
    val toLiteral   = c.internal.constantType(Constant(to))
    appliedType(typeOf[Changeset.TransformField[_, _]].typeConstructor, fromLiteral, toLiteral)
  }

  private def createMandateFieldType(c: whitebox.Context)(source: String, target: String): c.Type = {
    import c.universe._
    val sourceLiteral = c.internal.constantType(Constant(source))
    val targetLiteral = c.internal.constantType(Constant(target))
    appliedType(typeOf[Changeset.MandateField[_, _]].typeConstructor, sourceLiteral, targetLiteral)
  }

  private def createOptionalizeFieldType(c: whitebox.Context)(source: String, target: String): c.Type = {
    import c.universe._
    val sourceLiteral = c.internal.constantType(Constant(source))
    val targetLiteral = c.internal.constantType(Constant(target))
    appliedType(typeOf[Changeset.OptionalizeField[_, _]].typeConstructor, sourceLiteral, targetLiteral)
  }

  private def createChangeFieldTypeType(c: whitebox.Context)(name: String): c.Type = {
    import c.universe._
    val nameLiteral = c.internal.constantType(Constant(name))
    appliedType(typeOf[Changeset.ChangeFieldType[_]].typeConstructor, nameLiteral)
  }

  private def createMigrateFieldType(c: whitebox.Context)(name: String): c.Type = {
    import c.universe._
    val nameLiteral = c.internal.constantType(Constant(name))
    appliedType(typeOf[Changeset.MigrateField[_]].typeConstructor, nameLiteral)
  }

  def addFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fieldName = extractFieldPathFromSelector(c)(target.tree)
    checkCollisionAtCallSite(c)("addField", Nil, List(fieldName), false, aType, bType, csType)
    val addFieldType = createAddFieldType(c)(fieldName)
    val newCSType    = addToChangeset(c)(csType, addFieldType)

    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val targetPath = $targetPath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.AddField(
          targetPath,
          $default.toDynamic
        )
      )
    }"""
  }

  def dropFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Any],
    defaultForReverse: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fieldName = extractFieldPathFromSelector(c)(source.tree)
    checkCollisionAtCallSite(c)("dropField", List(fieldName), Nil, false, aType, bType, csType)
    val dropFieldType = createDropFieldType(c)(fieldName)
    val newCSType     = addToChangeset(c)(csType, dropFieldType)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.DropField(
          sourcePath,
          $defaultForReverse.toDynamic
        )
      )
    }"""
  }

  def renameFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fromFieldName = extractFieldPathFromSelector(c)(from.tree)
    val toFieldName   = extractFieldPathFromSelector(c)(to.tree)
    checkCollisionAtCallSite(c)("renameField", List(fromFieldName), List(toFieldName), true, aType, bType, csType)
    val renameFieldType = createRenameFieldType(c)(fromFieldName, toFieldName)
    val newCSType       = addToChangeset(c)(csType, renameFieldType)

    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      val toName = toPath.nodes.lastOption match {
        case _root_.scala.Some(_root_.zio.blocks.schema.DynamicOptic.Node.Field(name)) => name
        case _ => throw new IllegalArgumentException("Target selector must end with a field access")
      }
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.RenameField(fromPath, toName)
      )
    }"""
  }

  def transformFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    from: c.Expr[A => Any],
    to: c.Expr[B => Any],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val fromFieldName = extractFieldPathFromSelector(c)(from.tree)
    val toFieldName   = extractFieldPathFromSelector(c)(to.tree)
    if (fromFieldName != toFieldName)
      c.abort(
        c.enclosingPosition,
        s"transformField requires the same field name in source and target, " +
          s"got '$fromFieldName' and '$toFieldName'. " +
          s"Use renameField followed by transformField for rename-and-transform."
      )
    checkCollisionAtCallSite(c)("transformField", List(fromFieldName), List(toFieldName), false, aType, bType, csType)
    val transformFieldType = createTransformFieldType(c)(fromFieldName, toFieldName)
    val newCSType          = addToChangeset(c)(csType, transformFieldType)

    val fromPath = SelectorMacros.toPathImpl[A, Any](c)(from.asInstanceOf[c.Expr[A => Any]])
    val toPath   = SelectorMacros.toPathImpl[B, Any](c)(to.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val fromPath = $fromPath
      val toPath = $toPath
      locally(toPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformField(
          fromPath,
          $transform.toDynamic
        )
      )
    }"""
  }

  def mandateFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    source: c.Expr[A => Option[_]],
    target: c.Expr[B => Any],
    default: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val sourceFieldName = extractFieldPathFromSelector(c)(source.tree)
    val targetFieldName = extractFieldPathFromSelector(c)(target.tree)
    if (sourceFieldName != targetFieldName)
      c.abort(
        c.enclosingPosition,
        s"mandateField requires the same field name in source and target, " +
          s"got '$sourceFieldName' and '$targetFieldName'. " +
          s"Use renameField followed by mandateField for rename-and-mandate."
      )
    checkCollisionAtCallSite(c)(
      "mandateField",
      List(sourceFieldName),
      List(targetFieldName),
      false,
      aType,
      bType,
      csType
    )
    val mandateFieldType = createMandateFieldType(c)(sourceFieldName, targetFieldName)
    val newCSType        = addToChangeset(c)(csType, mandateFieldType)

    val sourcePath = SelectorMacros.toPathImpl[A, Option[_]](c)(source.asInstanceOf[c.Expr[A => Option[_]]])
    val targetPath = SelectorMacros.toPathImpl[B, Any](c)(target.asInstanceOf[c.Expr[B => Any]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.MandateField(
          sourcePath,
          $default.toDynamic
        )
      )
    }"""
  }

  def optionalizeFieldImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    target: c.Expr[B => Option[_]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val sourceFieldName = extractFieldPathFromSelector(c)(source.tree)
    val targetFieldName = extractFieldPathFromSelector(c)(target.tree)
    if (sourceFieldName != targetFieldName)
      c.abort(
        c.enclosingPosition,
        s"optionalizeField requires the same field name in source and target, " +
          s"got '$sourceFieldName' and '$targetFieldName'. " +
          s"Use renameField followed by optionalizeField for rename-and-optionalize."
      )
    // Source-side check: reject already-Option source (Option -> Option would double-wrap at runtime).
    // Unresolvable member (e.g. structural selector) skips the check.
    val segments: List[String] = sourceFieldName.split('.').toList
    var current: Type          = aType.dealias
    var resolvable             = true
    var si                     = 0
    while (si < segments.length && resolvable) {
      val sym = current.typeSymbol
      if (sym.isClass && sym.asClass.isCaseClass) {
        val ctor   = current.decl(termNames.CONSTRUCTOR).asMethod
        val params = ctor.paramLists.headOption.getOrElse(Nil)
        params.find(_.name.decodedName.toString == segments(si)) match {
          case Some(param) => current = param.typeSignature.asSeenFrom(current, sym).dealias
          case None        => resolvable = false
        }
      } else {
        resolvable = false
      }
      si += 1
    }
    if (resolvable && (current.dealias <:< typeOf[Option[Any]]))
      c.abort(
        c.enclosingPosition,
        s"optionalizeField requires source field '$sourceFieldName' to be a non-Option type, " +
          s"but found $current. " +
          s"optionalizeField wraps the value in Option at runtime."
      )
    checkCollisionAtCallSite(c)(
      "optionalizeField",
      List(sourceFieldName),
      List(targetFieldName),
      false,
      aType,
      bType,
      csType
    )
    val optionalizeFieldType = createOptionalizeFieldType(c)(sourceFieldName, targetFieldName)
    val newCSType            = addToChangeset(c)(csType, optionalizeFieldType)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])
    val targetPath = SelectorMacros.toPathImpl[B, Option[_]](c)(target.asInstanceOf[c.Expr[B => Option[_]]])

    q"""{
      val sourcePath = $sourcePath
      val targetPath = $targetPath
      locally(targetPath)
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.OptionalizeField(sourcePath)
      )
    }"""
  }

  def changeFieldTypeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    source: c.Expr[A => Any],
    converter: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val sourceFieldName = extractFieldPathFromSelector(c)(source.tree)
    checkCollisionAtCallSite(c)(
      "changeFieldType",
      List(sourceFieldName),
      List(sourceFieldName),
      false,
      aType,
      bType,
      csType
    )
    val changeFieldTypeType = createChangeFieldTypeType(c)(sourceFieldName)
    val newCSType           = addToChangeset(c)(csType, changeFieldTypeType)

    val sourcePath = SelectorMacros.toPathImpl[A, Any](c)(source.asInstanceOf[c.Expr[A => Any]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.ChangeFieldType(
          sourcePath,
          $converter.toDynamic
        )
      )
    }"""
  }

  def transformElementsImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Iterable[_]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val path = SelectorMacros.toPathImpl[A, Iterable[_]](c)(at.asInstanceOf[c.Expr[A => Iterable[_]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $csType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformElements(
          path,
          $transform.toDynamic
        )
      )
    }"""
  }

  def transformKeysImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](c: whitebox.Context)(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $csType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformKeys(
          path,
          $transform.toDynamic
        )
      )
    }"""
  }

  def transformValuesImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  )(
    at: c.Expr[A => Map[_, _]],
    transform: c.Expr[SchemaExpr[_, _]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val csType = weakTypeOf[CS]

    val path = SelectorMacros.toPathImpl[A, Map[_, _]](c)(at.asInstanceOf[c.Expr[A => Map[_, _]]])

    q"""{
      val path = $path
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $csType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.TransformValues(
          path,
          $transform.toDynamic
        )
      )
    }"""
  }

  def migrateFieldExplicitImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    F1: c.WeakTypeTag,
    F2: c.WeakTypeTag,
    CS: c.WeakTypeTag
  ](c: whitebox.Context)(
    selector: c.Expr[A => F1],
    migration: c.Expr[Migration[F1, F2]]
  ): c.Tree = {
    import c.universe._

    val aType  = weakTypeOf[A]
    val bType  = weakTypeOf[B]
    val f1Type = weakTypeOf[F1]
    val f2Type = weakTypeOf[F2]
    val csType = weakTypeOf[CS]

    val sourceFieldName = extractFieldPathFromSelector(c)(selector.tree)

    val nestedSourceFields = extractCaseClassFields(c)(f1Type)
    val nestedTargetFields = extractCaseClassFields(c)(f2Type)

    val migrateFieldType = createMigrateFieldType(c)(sourceFieldName)
    var newCSType        = addToChangeset(c)(csType, migrateFieldType)

    for (nestedField <- nestedSourceFields) {
      val dotPath = s"$sourceFieldName.$nestedField"
      newCSType = createRefinedType(c)(newCSType, dotPath)
    }

    // Resolve the target prefix against B by walking B's fields for each selector
    // segment; abort if any segment is absent in B. The emitted MigrateField keeps
    // the source path (the runtime navigates the source value).
    val targetFieldPrefix = resolveTargetPrefix(c)(bType, sourceFieldName)

    checkCollisionAtCallSite(c)(
      "migrateField",
      sourceFieldName :: nestedSourceFields.map(nestedField => s"$sourceFieldName.$nestedField"),
      targetFieldPrefix :: nestedTargetFields.map(nestedField => s"$targetFieldPrefix.$nestedField"),
      false,
      aType,
      bType,
      csType
    )

    for (nestedField <- nestedTargetFields) {
      val dotPath = s"$targetFieldPrefix.$nestedField"
      newCSType = createRefinedType(c)(newCSType, dotPath)
    }

    val sourcePath = SelectorMacros.toPathImpl[A, F1](c)(selector.asInstanceOf[c.Expr[A => F1]])

    q"""{
      val sourcePath = $sourcePath
      new _root_.zio.blocks.schema.migration.MigrationBuilder[$aType, $bType, $newCSType](
        ${c.prefix}.sourceSchema,
        ${c.prefix}.targetSchema,
        ${c.prefix}.actions :+ _root_.zio.blocks.schema.migration.MigrationAction.MigrateField(
          sourcePath,
          $migration.dynamicMigration
        )
      )
    }"""
  }

  def migrateFieldImplicitImpl[
    A: c.WeakTypeTag,
    B: c.WeakTypeTag,
    F1: c.WeakTypeTag,
    F2: c.WeakTypeTag,
    CS: c.WeakTypeTag
  ](c: whitebox.Context)(
    selector: c.Expr[A => F1]
  )(
    migration: c.Expr[Migration[F1, F2]]
  ): c.Tree =
    migrateFieldExplicitImpl[A, B, F1, F2, CS](c)(selector, migration)

  // Walks B's case-class fields for each selector segment. When any segment cannot be
  // resolved (structural/refinement B types, which Scala 2 cannot enumerate), falls back
  // to the legacy source-prefix behavior instead of aborting.
  private def resolveTargetPrefix(c: whitebox.Context)(bType: c.universe.Type, sourceFieldPath: String): String = {
    import c.universe._

    val resolved =
      sourceFieldPath.split('.').toList.foldLeft((bType, List.empty[String])) { case ((current, prefix), segment) =>
        val sym  = current.typeSymbol
        val next =
          if (sym.isClass && sym.asClass.isCaseClass) {
            val ctor = current.decl(termNames.CONSTRUCTOR).asMethod
            ctor.paramLists.headOption
              .getOrElse(Nil)
              .find(_.name.decodedName.toString == segment)
              .map(param => param.typeSignature.asSeenFrom(current, sym))
          } else None
        next match {
          case Some(fieldType) => (fieldType.dealias, prefix :+ segment)
          case None            => return sourceFieldPath
        }
      }
    resolved._2.mkString(".")
  }

  private def extractCaseClassFields(c: whitebox.Context)(tpe: c.universe.Type): List[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption.getOrElse(Nil).map(_.name.decodedName.toString)
    } else {
      Nil
    }
  }
}

object MigrationValidationMacros {

  def validateMigrationImpl[A: c.WeakTypeTag, B: c.WeakTypeTag, CS: c.WeakTypeTag](
    c: whitebox.Context
  ): c.Tree = {
    import c.universe._

    val sourceType = weakTypeOf[A]
    val targetType = weakTypeOf[B]
    val csType     = weakTypeOf[CS]

    val sourceFields = extractCaseClassFieldsWithNested(c)(sourceType, "")
    val targetFields = extractCaseClassFieldsWithNested(c)(targetType, "")

    val (handledFields, providedFields) = extractHandledAndProvided(c)(csType)

    val autoMapped = computeAutoMappedWithNested(c)(sourceType, targetType, "")

    val allExplicitlyHandled = handledFields ++ providedFields
    val crossTypeAutoMapped  = computeCrossTypeAutoMapped(c)(sourceType, targetType, allExplicitlyHandled, "")

    var coveredSource = handledFields ++ autoMapped ++ crossTypeAutoMapped
    var coveredTarget = providedFields ++ autoMapped ++ crossTypeAutoMapped

    coveredSource = inferParentCoverage(sourceFields, coveredSource)
    coveredTarget = inferParentCoverage(targetFields, coveredTarget)

    val missingTarget   = targetFields -- coveredTarget
    val unhandledSource = sourceFields -- coveredSource

    if (missingTarget.nonEmpty || unhandledSource.nonEmpty) {
      val errors = new StringBuilder("Migration incomplete:\n")

      if (missingTarget.nonEmpty) {
        errors.append(s"\n  Target fields not provided: ${missingTarget.mkString(", ")}\n")
        errors.append("  Use addField, renameField, transformField, or migrateField to provide these fields.\n")
      }

      if (unhandledSource.nonEmpty) {
        errors.append(s"\n  Source fields not handled: ${unhandledSource.mkString(", ")}\n")
        errors.append("  Use dropField, renameField, transformField, or migrateField to handle these fields.\n")
      }

      c.abort(c.enclosingPosition, errors.toString)
    }

    q"_root_.zio.blocks.schema.migration.MigrationComplete.unsafeCreate[$sourceType, $targetType, $csType]"
  }

  private def extractHandledAndProvided(c: whitebox.Context)(
    tpe: c.universe.Type
  ): (Set[String], Set[String]) = {
    import c.universe._

    var handled  = Set.empty[String]
    var provided = Set.empty[String]

    def extractString(t: Type): Option[String] = t.dealias match {
      case ConstantType(Constant(s: String)) => Some(s)
      case _                                 => None
    }

    def extract(t: Type): Unit = t.dealias match {
      case RefinedType(parents, _) =>
        parents.foreach(extract)
      case t if t =:= typeOf[Any] => ()
      case TypeRef(_, sym, args)  =>
        sym.name.toString match {
          case "AddField" =>
            args.headOption.flatMap(extractString).foreach(n => provided += n)
          case "DropField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
          case "RenameField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "TransformField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "MandateField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "OptionalizeField" =>
            args.headOption.flatMap(extractString).foreach(n => handled += n)
            args.lift(1).flatMap(extractString).foreach(n => provided += n)
          case "ChangeFieldType" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled += n
              provided += n
            }
          case "MigrateField" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled += n
              provided += n
            }
          case "FieldName" =>
            args.headOption.flatMap(extractString).foreach { n =>
              handled += n
              provided += n
            }
          case "RenameCase" | "TransformCase" | "TransformElements" | "TransformKeys" | "TransformValues" =>
            ()
          case _ => ()
        }
      case _ => ()
    }

    extract(tpe)
    (handled, provided)
  }

  private def extractCaseClassFieldsWithNested(
    c: whitebox.Context
  )(tpe: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption
        .getOrElse(Nil)
        .flatMap { param =>
          val fieldName =
            if (prefix.isEmpty) param.name.decodedName.toString else s"$prefix.${param.name.decodedName.toString}"
          val fieldType = param.typeSignature.asSeenFrom(tpe, sym)
          val fieldSym  = fieldType.typeSymbol

          if (fieldSym.isClass && fieldSym.asClass.isCaseClass) {
            Set(fieldName) ++ extractNestedFieldNames(c)(fieldType, fieldName)
          } else {
            Set(fieldName)
          }
        }
        .toSet
    } else {
      Set.empty
    }
  }

  private def extractNestedFieldNames(c: whitebox.Context)(tpe: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val sym = tpe.typeSymbol
    if (sym.isClass && sym.asClass.isCaseClass) {
      val ctor = tpe.decl(termNames.CONSTRUCTOR).asMethod
      ctor.paramLists.headOption
        .getOrElse(Nil)
        .flatMap { param =>
          val fieldName = s"$prefix.${param.name.decodedName.toString}"
          val fieldType = param.typeSignature.asSeenFrom(tpe, sym)
          val fieldSym  = fieldType.typeSymbol

          if (fieldSym.isClass && fieldSym.asClass.isCaseClass) {
            Set(fieldName) ++ extractNestedFieldNames(c)(fieldType, fieldName)
          } else {
            Set(fieldName)
          }
        }
        .toSet
    } else {
      Set.empty
    }
  }

  private def computeAutoMappedWithNested(
    c: whitebox.Context
  )(sourceType: c.universe.Type, targetType: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val sourceSym = sourceType.typeSymbol
    val targetSym = targetType.typeSymbol

    if (sourceSym.isClass && sourceSym.asClass.isCaseClass && targetSym.isClass && targetSym.asClass.isCaseClass) {
      val sourceCtor = sourceType.decl(termNames.CONSTRUCTOR).asMethod
      val targetCtor = targetType.decl(termNames.CONSTRUCTOR).asMethod

      val sourceFieldTypes: Map[String, Type] = sourceCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(sourceType, sourceSym))
        .toMap

      val targetFieldTypes: Map[String, Type] = targetCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(targetType, targetSym))
        .toMap

      val commonFields = sourceFieldTypes.keySet.intersect(targetFieldTypes.keySet)

      commonFields.flatMap { fieldName =>
        val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
        (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
          case (Some(srcType), Some(tgtType)) if srcType =:= tgtType =>
            Set(fullFieldName) ++ computeAutoMappedNested(c)(srcType, tgtType, fullFieldName)
          case (Some(srcType), Some(tgtType)) if srcType <:< tgtType || tgtType <:< srcType =>
            Set(fullFieldName)
          case _ => Set.empty[String]
        }
      }
    } else {
      Set.empty
    }
  }

  private def computeAutoMappedNested(
    c: whitebox.Context
  )(srcType: c.universe.Type, tgtType: c.universe.Type, prefix: String): Set[String] = {
    import c.universe._

    val srcSym = srcType.typeSymbol
    val tgtSym = tgtType.typeSymbol

    if (srcSym.isClass && srcSym.asClass.isCaseClass && tgtSym.isClass && tgtSym.asClass.isCaseClass) {
      val srcCtor = srcType.decl(termNames.CONSTRUCTOR).asMethod
      val tgtCtor = tgtType.decl(termNames.CONSTRUCTOR).asMethod

      val srcFields: Map[String, Type] = srcCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(srcType, srcSym))
        .toMap

      val tgtFields: Map[String, Type] = tgtCtor.paramLists.headOption
        .getOrElse(Nil)
        .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(tgtType, tgtSym))
        .toMap

      val commonFields = srcFields.keySet.intersect(tgtFields.keySet)

      commonFields.flatMap { fieldName =>
        val fullFieldName = s"$prefix.$fieldName"
        (srcFields.get(fieldName), tgtFields.get(fieldName)) match {
          case (Some(srcFieldType), Some(tgtFieldType)) if srcFieldType =:= tgtFieldType =>
            Set(fullFieldName) ++ computeAutoMappedNested(c)(srcFieldType, tgtFieldType, fullFieldName)
          case (Some(srcFieldType), Some(tgtFieldType))
              if srcFieldType <:< tgtFieldType || tgtFieldType <:< srcFieldType =>
            Set(fullFieldName)
          case _ => Set.empty[String]
        }
      }
    } else {
      Set.empty
    }
  }

  private def computeCrossTypeAutoMapped(
    c: whitebox.Context
  )(
    sourceType: c.universe.Type,
    targetType: c.universe.Type,
    explicitlyHandled: Set[String],
    prefix: String
  ): Set[String] = {
    import c.universe._

    val sourceSym = sourceType.typeSymbol
    val targetSym = targetType.typeSymbol

    val sourceFieldTypes: Map[String, Type] =
      if (sourceSym.isClass && sourceSym.asClass.isCaseClass) {
        val ctor = sourceType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(sourceType, sourceSym))
          .toMap
      } else Map.empty

    val targetFieldTypes: Map[String, Type] =
      if (targetSym.isClass && targetSym.asClass.isCaseClass) {
        val ctor = targetType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(targetType, targetSym))
          .toMap
      } else Map.empty

    val commonFields = sourceFieldTypes.keySet.intersect(targetFieldTypes.keySet)

    commonFields.flatMap { fieldName =>
      val fullFieldName = if (prefix.isEmpty) fieldName else s"$prefix.$fieldName"
      (sourceFieldTypes.get(fieldName), targetFieldTypes.get(fieldName)) match {
        case (Some(srcType), Some(tgtType)) if !(srcType =:= tgtType) =>
          val hasExplicitChild = explicitlyHandled.exists(_.startsWith(s"$fullFieldName."))
          if (hasExplicitChild)
            crossTypeAutoMapLeaves(c)(srcType, tgtType, explicitlyHandled, fullFieldName)
          else Set.empty[String]
        case _ => Set.empty[String]
      }
    }
  }

  private def crossTypeAutoMapLeaves(
    c: whitebox.Context
  )(srcType: c.universe.Type, tgtType: c.universe.Type, explicitlyHandled: Set[String], prefix: String): Set[String] = {
    import c.universe._

    val srcSym = srcType.typeSymbol
    val tgtSym = tgtType.typeSymbol

    val srcFields: Map[String, Type] =
      if (srcSym.isClass && srcSym.asClass.isCaseClass) {
        val ctor = srcType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(srcType, srcSym))
          .toMap
      } else Map.empty

    val tgtFields: Map[String, Type] =
      if (tgtSym.isClass && tgtSym.asClass.isCaseClass) {
        val ctor = tgtType.decl(termNames.CONSTRUCTOR).asMethod
        ctor.paramLists.headOption
          .getOrElse(Nil)
          .map(p => p.name.decodedName.toString -> p.typeSignature.asSeenFrom(tgtType, tgtSym))
          .toMap
      } else Map.empty

    val common = srcFields.keySet.intersect(tgtFields.keySet)

    common.flatMap { fieldName =>
      val fullName = s"$prefix.$fieldName"
      (srcFields(fieldName), tgtFields(fieldName)) match {
        case (s, t) if s =:= t => Set(fullName)
        case (s, t)            =>
          val hasExplicitChild = explicitlyHandled.exists(_.startsWith(s"$fullName."))
          if (hasExplicitChild)
            crossTypeAutoMapLeaves(c)(s, t, explicitlyHandled, fullName)
          else Set.empty[String]
      }
    }
  }

  private def inferParentCoverage(allFields: Set[String], covered: Set[String]): Set[String] = {
    val parents = allFields.flatMap { f =>
      val parts = f.split('.')
      if (parts.length > 1) Some(parts.init.mkString(".")) else None
    }
    var result  = covered
    var changed = true
    while (changed) {
      changed = false
      for (parent <- parents) {
        if (!result.contains(parent)) {
          val children = allFields.filter(_.startsWith(s"$parent."))
          if (children.nonEmpty && children.forall(result.contains)) {
            result = result + parent
            changed = true
          }
        }
      }
    }
    result
  }
}
