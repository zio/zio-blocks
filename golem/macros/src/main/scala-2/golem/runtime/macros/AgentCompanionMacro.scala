package golem.runtime.macros

import scala.reflect.macros.blackbox

/**
 * Scala 2 macro implementations backing `golem.AgentCompanion`.
 *
 * These expand at the call site and therefore work even when agent traits and
 * their companions are defined in the same compilation run.
 */
object AgentCompanionMacro {
  private def defaultTypeNameFromTrait(sym: scala.reflect.api.Universe#Symbol): String = {
    val raw = sym.name.decodedName.toString
    raw
      .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
      .toLowerCase
  }

  private def prefixTraitAndInput(c: blackbox.Context): (c.universe.Type, c.universe.Type) = {
    import c.universe._
    val prefix = c.prefix.actualType

    val baseSym = typeOf[_root_.golem.AgentCompanionBase[_]].typeSymbol
    val baseTpe = prefix.baseType(baseSym)
    if (baseTpe == NoType)
      c.abort(
        c.enclosingPosition,
        s"Expected companion to extend golem.AgentCompanionBase[AgentTrait], found: $prefix"
      )

    val traitTpe =
      baseTpe.typeArgs.headOption.getOrElse {
        c.abort(c.enclosingPosition, s"AgentCompanionBase is missing its agent trait type argument (found: $baseTpe)")
      }
    val inputTpe = agentInputType(c)(traitTpe)
    (traitTpe, inputTpe)
  }

  def typeNameImpl(c: blackbox.Context): c.Expr[String] = {
    import c.universe._
    val (traitTpe, _) = prefixTraitAndInput(c)
    val traitSym      = traitTpe.typeSymbol

    val agentDefinitionType = typeOf[_root_.golem.runtime.annotations.agentDefinition]
    val raw                 = traitSym.annotations.collectFirst {
      case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
    }.getOrElse("")

    val value = raw.trim
    if (value.nonEmpty) c.Expr[String](Literal(Constant(value)))
    else {
      val hasAnn = traitSym.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
      if (!hasAnn) c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
      c.Expr[String](Literal(Constant(defaultTypeNameFromTrait(traitSym))))
    }
  }

  def agentTypeImpl(c: blackbox.Context): c.Tree = {
    import c.universe._
    val (traitTpe, inTpe) = prefixTraitAndInput(c)
    q"""
       _root_.golem.runtime.rpc.AgentClient
         .agentType[$traitTpe]
         .asInstanceOf[_root_.golem.runtime.agenttype.AgentType[$traitTpe, $inTpe]]
     """
  }

  def getImpl(c: blackbox.Context)(input: c.Tree): c.Tree = {
    import c.universe._
    val (traitTpe, inTpe) = prefixTraitAndInput(c)
    val agentTypeExpr     = agentTypeImpl(c)
    q"""
       _root_.golem.runtime.rpc.AgentClientRuntime
         .resolve[$traitTpe, $inTpe](
           $agentTypeExpr.asInstanceOf[_root_.golem.runtime.agenttype.AgentType[$traitTpe, $inTpe]],
           $input.asInstanceOf[$inTpe]
         ) match {
           case _root_.scala.util.Left(err) =>
             throw _root_.scala.scalajs.js.JavaScriptException(err)
           case _root_.scala.util.Right(resolved) =>
             ${attachTriggerSchedule(c)(traitTpe, q"resolved")}
         }
     """
  }

  def getPhantomImpl(c: blackbox.Context)(input: c.Tree, phantom: c.Tree): c.Tree = {
    import c.universe._
    val (traitTpe, inTpe) = prefixTraitAndInput(c)
    val agentTypeExpr     = agentTypeImpl(c)
    q"""
       _root_.golem.runtime.rpc.AgentClientRuntime.resolveWithPhantom[$traitTpe, $inTpe](
         $agentTypeExpr.asInstanceOf[_root_.golem.runtime.agenttype.AgentType[$traitTpe, $inTpe]],
         $input.asInstanceOf[$inTpe],
         phantom = _root_.scala.Some($phantom.asInstanceOf[_root_.golem.Uuid])
       ) match {
         case _root_.scala.util.Left(err) =>
           throw _root_.scala.scalajs.js.JavaScriptException(err)
         case _root_.scala.util.Right(resolved) =>
           ${attachTriggerSchedule(c)(traitTpe, q"resolved")}
       }
     """
  }

  def getUnitImpl(c: blackbox.Context)(): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    if (!(inTpe =:= typeOf[Unit]))
      c.abort(c.enclosingPosition, s"get() requires: BaseAgent[Unit] (found: $inTpe)")
    getImpl(c)(q"()")
  }

  def getPhantomUnitImpl(c: blackbox.Context)(phantom: c.Tree): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    if (!(inTpe =:= typeOf[Unit]))
      c.abort(c.enclosingPosition, s"getPhantom(phantom) requires: BaseAgent[Unit] (found: $inTpe)")
    getPhantomImpl(c)(q"()", phantom)
  }

  def getTuple2Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   = appliedType(typeOf[Tuple2[_, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2]))
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"get(a1,a2) requires: BaseAgent[($expected)] (found: $inTpe)")
    getImpl(c)(q"_root_.scala.Tuple2($a1, $a2)")
  }

  def getPhantomTuple2Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    phantom: c.Expr[_root_.golem.Uuid]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   = appliedType(typeOf[Tuple2[_, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2]))
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"getPhantom(a1,a2,phantom) requires: BaseAgent[($expected)] (found: $inTpe)")
    getPhantomImpl(c)(q"_root_.scala.Tuple2($a1, $a2)", phantom.tree)
  }

  def getTuple3Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag, A3: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    a3: c.Expr[A3]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(typeOf[Tuple3[_, _, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3]))
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"get(a1,a2,a3) requires: BaseAgent[($expected)] (found: $inTpe)")
    getImpl(c)(q"_root_.scala.Tuple3($a1, $a2, $a3)")
  }

  def getPhantomTuple3Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag, A3: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    a3: c.Expr[A3],
    phantom: c.Expr[_root_.golem.Uuid]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(typeOf[Tuple3[_, _, _]].typeConstructor, List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3]))
    if (!(inTpe =:= expected))
      c.abort(
        c.enclosingPosition,
        s"getPhantom(a1,a2,a3,phantom) requires: BaseAgent[($expected)] (found: $inTpe)"
      )
    getPhantomImpl(c)(q"_root_.scala.Tuple3($a1, $a2, $a3)", phantom.tree)
  }

  def getTuple4Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag, A3: c.WeakTypeTag, A4: c.WeakTypeTag](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    a3: c.Expr[A3],
    a4: c.Expr[A4]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(
        typeOf[Tuple4[_, _, _, _]].typeConstructor,
        List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3], weakTypeOf[A4])
      )
    if (!(inTpe =:= expected))
      c.abort(c.enclosingPosition, s"get(a1,a2,a3,a4) requires: BaseAgent[($expected)] (found: $inTpe)")
    getImpl(c)(q"_root_.scala.Tuple4($a1, $a2, $a3, $a4)")
  }

  def getPhantomTuple4Impl[A1: c.WeakTypeTag, A2: c.WeakTypeTag, A3: c.WeakTypeTag, A4: c.WeakTypeTag](
    c: blackbox.Context
  )(a1: c.Expr[A1], a2: c.Expr[A2], a3: c.Expr[A3], a4: c.Expr[A4], phantom: c.Expr[_root_.golem.Uuid]): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(
        typeOf[Tuple4[_, _, _, _]].typeConstructor,
        List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3], weakTypeOf[A4])
      )
    if (!(inTpe =:= expected))
      c.abort(
        c.enclosingPosition,
        s"getPhantom(a1,a2,a3,a4,phantom) requires: BaseAgent[($expected)] (found: $inTpe)"
      )
    getPhantomImpl(c)(q"_root_.scala.Tuple4($a1, $a2, $a3, $a4)", phantom.tree)
  }

  def getTuple5Impl[
    A1: c.WeakTypeTag,
    A2: c.WeakTypeTag,
    A3: c.WeakTypeTag,
    A4: c.WeakTypeTag,
    A5: c.WeakTypeTag
  ](c: blackbox.Context)(a1: c.Expr[A1], a2: c.Expr[A2], a3: c.Expr[A3], a4: c.Expr[A4], a5: c.Expr[A5]): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(
        typeOf[Tuple5[_, _, _, _, _]].typeConstructor,
        List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3], weakTypeOf[A4], weakTypeOf[A5])
      )
    if (!(inTpe =:= expected))
      c.abort(
        c.enclosingPosition,
        s"get(a1,a2,a3,a4,a5) requires: BaseAgent[($expected)] (found: $inTpe)"
      )
    getImpl(c)(q"_root_.scala.Tuple5($a1, $a2, $a3, $a4, $a5)")
  }

  def getPhantomTuple5Impl[
    A1: c.WeakTypeTag,
    A2: c.WeakTypeTag,
    A3: c.WeakTypeTag,
    A4: c.WeakTypeTag,
    A5: c.WeakTypeTag
  ](c: blackbox.Context)(
    a1: c.Expr[A1],
    a2: c.Expr[A2],
    a3: c.Expr[A3],
    a4: c.Expr[A4],
    a5: c.Expr[A5],
    phantom: c.Expr[_root_.golem.Uuid]
  ): c.Tree = {
    import c.universe._
    val (_, inTpe) = prefixTraitAndInput(c)
    val expected   =
      appliedType(
        typeOf[Tuple5[_, _, _, _, _]].typeConstructor,
        List(weakTypeOf[A1], weakTypeOf[A2], weakTypeOf[A3], weakTypeOf[A4], weakTypeOf[A5])
      )
    if (!(inTpe =:= expected))
      c.abort(
        c.enclosingPosition,
        s"getPhantom(a1,a2,a3,a4,a5,phantom) requires: BaseAgent[($expected)] (found: $inTpe)"
      )
    getPhantomImpl(c)(q"_root_.scala.Tuple5($a1, $a2, $a3, $a4, $a5)", phantom.tree)
  }

  private def agentInputType(c: blackbox.Context)(traitType: c.universe.Type): c.universe.Type = {
    import c.universe._
    val baseSymOpt = traitType.baseClasses.find(_.fullName == "golem.BaseAgent")
    val baseArgs   = baseSymOpt.toList.flatMap(sym => traitType.baseType(sym).typeArgs)
    baseArgs.headOption.getOrElse(typeOf[Unit]).dealias
  }

  private def attachTriggerSchedule(c: blackbox.Context)(traitTpe: c.universe.Type, resolvedTree: c.Tree): c.Tree = {
    import c.universe._

    val methods: List[MethodSymbol] =
      traitTpe.members.collect {
        case m: MethodSymbol if m.isAbstract && m.isPublic && m.isMethod && !m.isConstructor =>
          m
      }.toList

    def paramsFor(m: MethodSymbol): List[(TermName, Type)] =
      m.paramLists.headOption.getOrElse(Nil).map { sym =>
        (sym.name.toTermName, sym.typeSignatureIn(traitTpe).dealias.widen)
      }

    def inputTypeFor(params: List[(TermName, Type)]): Type =
      params match {
        case Nil      => typeOf[Unit]
        case _ :: Nil => params.head._2
        case _        => appliedType(typeOf[Vector[_]].typeConstructor, List(typeOf[Any]))
      }

    def buildInputValueExpr(argNames: List[TermName], accessMode: Int): Tree =
      accessMode match {
        case 0 => q"()"
        case 1 => q"${argNames.head}"
        case _ => q"_root_.scala.Vector[Any](..${argNames.map(n => q"$n.asInstanceOf[Any]")})"
      }

    def findMethodExpr(inputTpe: Type, methodName: String): Tree =
      q"""
         $resolvedTree.agentType.methods.collectFirst {
           case mm if mm.metadata.name == $methodName =>
             mm.asInstanceOf[_root_.golem.runtime.agenttype.AgentMethod[$traitTpe, $inputTpe, _root_.scala.Any]]
         }.getOrElse(throw new _root_.java.lang.IllegalStateException("Method definition for " + $methodName + " not found"))
       """

    val baseName     = TermName(c.freshName("base"))
    val triggerName  = TermName(c.freshName("trigger"))
    val scheduleName = TermName(c.freshName("schedule"))

    val triggerUpdates: List[Tree] = methods.map { m =>
      val params     = paramsFor(m)
      val accessMode = if (params.isEmpty) 0 else if (params.length == 1) 1 else 2
      val inputTpe   = inputTypeFor(params)
      val plainName  = m.name.decodedName.toString

      val argNames   = params.zipWithIndex.map { case ((n, _), idx) => TermName(s"${n.decodedName.toString}_$idx") }
      val args       = params.zip(argNames).map { case ((_, tpe), nm) => q"val $nm: $tpe" }
      val inputVal   = buildInputValueExpr(argNames, accessMode)
      val methodExpr = findMethodExpr(inputTpe, plainName)
      val body       = q"$resolvedTree.trigger[$inputTpe]($methodExpr, $inputVal)"

      val fn   = q"(..$args) => $body"
      val jsFn = params.length match {
        case 0 => q"_root_.scala.scalajs.js.Any.fromFunction0($fn)"
        case 1 => q"_root_.scala.scalajs.js.Any.fromFunction1($fn)"
        case 2 => q"_root_.scala.scalajs.js.Any.fromFunction2($fn)"
        case 3 => q"_root_.scala.scalajs.js.Any.fromFunction3($fn)"
        case n =>
          c.abort(c.enclosingPosition, s"Unsupported agent method arity for trigger.${m.name}: $n (supported: 0-3)")
      }
      q"$triggerName.updateDynamic($plainName)($jsFn)"
    }

    val scheduleUpdates: List[Tree] = methods.map { m =>
      val params     = paramsFor(m)
      val accessMode = if (params.isEmpty) 0 else if (params.length == 1) 1 else 2
      val inputTpe   = inputTypeFor(params)
      val plainName  = m.name.decodedName.toString

      val dtName   = TermName("datetime")
      val argNames = params.zipWithIndex.map { case ((n, _), idx) => TermName(s"${n.decodedName.toString}_$idx") }
      val args     =
        (q"val $dtName: _root_.golem.Datetime") +: params.zip(argNames).map { case ((_, tpe), nm) => q"val $nm: $tpe" }
      val inputVal   = buildInputValueExpr(argNames, accessMode)
      val methodExpr = findMethodExpr(inputTpe, plainName)
      val body       = q"$resolvedTree.schedule[$inputTpe]($methodExpr, $dtName, $inputVal)"

      val fn   = q"(..$args) => $body"
      val jsFn = (params.length + 1) match {
        case 1 => q"_root_.scala.scalajs.js.Any.fromFunction1($fn)"
        case 2 => q"_root_.scala.scalajs.js.Any.fromFunction2($fn)"
        case 3 => q"_root_.scala.scalajs.js.Any.fromFunction3($fn)"
        case 4 => q"_root_.scala.scalajs.js.Any.fromFunction4($fn)"
        case n =>
          c.abort(
            c.enclosingPosition,
            s"Unsupported agent method arity for schedule.${m.name}: $n (supported: 1-4 including datetime)"
          )
      }
      q"$scheduleName.updateDynamic($plainName)($jsFn)"
    }

    val refinedType = tq"$traitTpe with _root_.golem.TriggerSchedule"

    q"""
       val $baseName = _root_.golem.runtime.rpc.AgentClient.bind[$traitTpe]($resolvedTree)
       val $triggerName = _root_.scala.scalajs.js.Dynamic.literal()
       val $scheduleName = _root_.scala.scalajs.js.Dynamic.literal()
       ..$triggerUpdates
       ..$scheduleUpdates
       $baseName.asInstanceOf[_root_.scala.scalajs.js.Dynamic].updateDynamic("trigger")($triggerName)
       $baseName.asInstanceOf[_root_.scala.scalajs.js.Dynamic].updateDynamic("schedule")($scheduleName)
       $baseName.asInstanceOf[$refinedType]
     """
  }
}
