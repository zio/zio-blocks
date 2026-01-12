import sbt.*
import sbt.Keys.*

/**
 * sbt plugin for Golem-related build wiring.
 *
 * Currently this provides Scala.js agent auto-registration generation, so
 * user-land code never needs to write/maintain a `RegisterAgents` list.
 */
object GolemPlugin extends AutoPlugin {
  object autoImport {
    val golemAutoRegisterAgentsBasePackage: SettingKey[Option[String]] =
      settingKey[Option[String]](
        "Base package whose @agentImplementation classes should be auto-registered (Scala.js)."
      )
  }

  import autoImport.*

  override def requires: Plugins      = plugins.JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  override def projectSettings: Seq[Def.Setting[?]] =
    Seq(
      golemAutoRegisterAgentsBasePackage := None,
      Compile / sourceGenerators += Def.task {
        val basePackageOpt = golemAutoRegisterAgentsBasePackage.value
        basePackageOpt match {
          case None =>
            Nil
          case Some(basePackage) =>
            val log         = streams.value.log
            val managedRoot = (Compile / sourceManaged).value / "golem" / "generated" / "autoregister"
            // IMPORTANT: avoid `(Compile / sources)` here, because it includes `managedSources`,
            // which depend on `sourceGenerators`, which would create an sbt cycle.
            val scalaSources = (Compile / unmanagedSources).value.filter(f => f.getName.endsWith(".scala"))

            final case class AgentImpl(pkg: String, implClass: String, traitType: String, ctorTypes: List[String])

            def packageOf(source: String): String = {
              val Pkg = """(?m)^\s*package\s+([a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)*)\s*$""".r
              Pkg.findFirstMatchIn(source).map(_.group(1)).getOrElse("")
            }

            val Ann           = """@agentImplementation(?:\([^\)]*\))?""".r
            val ClassWithCtor =
              (Ann.regex + """\s*(?:final\s+)?class\s+([A-Za-z_]\w*)\s*\(([^)]*)\)\s*extends\s+([^\s\{]+)""").r
            val ClassNoCtor =
              (Ann.regex + """\s*(?:final\s+)?class\s+([A-Za-z_]\w*)\s*extends\s+([^\s\{]+)""").r

            def ctorTypes(params: String): List[String] = {
              val trimmed = params.trim
              if (trimmed.isEmpty) Nil
              else {
                // Very simple splitting; good enough for our examples/quickstart (no nested commas in types).
                trimmed
                  .split(",")
                  .toList
                  .map(_.trim)
                  .filter(_.nonEmpty)
                  .flatMap { p =>
                    val colonIdx = p.indexOf(':')
                    if (colonIdx < 0) Nil
                    else {
                      val afterColon = p.substring(colonIdx + 1).trim
                      val tpe        = afterColon.takeWhile(_ != '=').trim
                      if (tpe.isEmpty) Nil else List(tpe)
                    }
                  }
              }
            }

            def parseAgentImpls(source: String): List[AgentImpl] = {
              val pkg      = packageOf(source)
              val withCtor = ClassWithCtor
                .findAllMatchIn(source)
                .map { m =>
                  AgentImpl(
                    pkg = pkg,
                    implClass = m.group(1),
                    traitType = m.group(3),
                    ctorTypes = ctorTypes(m.group(2))
                  )
                }
                .toList

              // Avoid double-counting: the "no ctor" regex would also match ctor classes if we ran it blindly.
              val ctorClassNames = withCtor.map(_.implClass).toSet
              val noCtor         = ClassNoCtor
                .findAllMatchIn(source)
                .map(m => AgentImpl(pkg = pkg, implClass = m.group(1), traitType = m.group(2), ctorTypes = Nil))
                .filterNot(ai => ctorClassNames.contains(ai.implClass))
                .toList

              withCtor ++ noCtor
            }

            val impls: List[AgentImpl] =
              scalaSources
                .flatMap(f => parseAgentImpls(IO.read(f)))
                .toList
                .filter(_.pkg.nonEmpty)
                .distinct
                .sortBy(ai => (ai.pkg, ai.traitType, ai.implClass))

            if (impls.isEmpty) {
              log.info(
                s"[golem] No @agentImplementation classes found; skipping auto RegisterAgents generation for $basePackage."
              )
              Nil
            } else {
              // Generate into an internal golem-owned package so this glue isn't exposed in user land.
              val moduleSuffix =
                basePackage
                  .replaceAll("[^a-zA-Z0-9_]", "_")
                  .stripPrefix("_")
                  .stripSuffix("_") match {
                  case ""  => "app"
                  case out => out
                }
              val genBasePkg = s"golem.runtime.__generated.autoregister.$moduleSuffix"

              val byPkg: Map[String, List[AgentImpl]] =
                impls
                  .groupBy(_.pkg)
                  .map { case (pkg, pkgImpls) =>
                    pkg -> pkgImpls.sortBy(ai => (ai.traitType, ai.implClass))
                  }

              def fileFor(pkg: String, name: String): File =
                managedRoot / pkg.split('.').toList.mkString("/") / name

              def fqn(ownerPkg: String, tpeOrTerm: String): String =
                if (tpeOrTerm.contains(".")) tpeOrTerm
                else s"$ownerPkg.$tpeOrTerm"

              def registrationExpr(ai: AgentImpl): String = {
                // Heuristic: in our examples/quickstart, impl class and its agent trait live in the same package.
                val traitFqn = fqn(ai.pkg, ai.traitType)
                val implFqn  = fqn(ai.pkg, ai.implClass)

                ai.ctorTypes match {
                  case Nil =>
                    s"AgentImplementation.register[$traitFqn](new $implFqn())"
                  case tpe :: Nil =>
                    s"AgentImplementation.register[$traitFqn, $tpe]((in: $tpe) => new $implFqn(in))"
                  case many =>
                    val tupleTpe = many.mkString("(", ", ", ")")
                    val args     = many.indices.map(i => s"in._${i + 1}").mkString(", ")
                    s"AgentImplementation.register[$traitFqn, $tupleTpe]((in: $tupleTpe) => new $implFqn($args))"
                }
              }

              val perPkgFiles: Seq[File] =
                byPkg.toSeq.sortBy(_._1).map { case (pkg, pkgImpls) =>
                  val objSuffix = pkg.replaceAll("[^a-zA-Z0-9_]", "_")
                  val out       = fileFor(genBasePkg, s"__GolemAutoRegister_$objSuffix.scala")
                  val body      = pkgImpls.map(ai => s"    ${registrationExpr(ai)}").mkString("\n")
                  val content   =
                    s"""|package $genBasePkg
                        |
                        |import golem.runtime.autowire.AgentImplementation
                        |
                        |/** Generated. Do not edit. */
                        |private[golem] object __GolemAutoRegister_$objSuffix {
                        |  val register: Unit = {
                        |$body
                        |    ()
                        |  }
                        |}
                        |""".stripMargin
                  IO.write(out, content)
                  out
                }

              val baseOut = fileFor(genBasePkg, "RegisterAgents.scala")
              val touches =
                byPkg.keys.toSeq.sorted.map { pkg =>
                  val objSuffix = pkg.replaceAll("[^a-zA-Z0-9_]", "_")
                  s"    __GolemAutoRegister_$objSuffix.register"
                }.mkString("\n")
              val baseContent =
                s"""|package $genBasePkg
                    |
                    |import scala.scalajs.js.annotation.JSExportTopLevel
                    |
                    |/** Generated. Do not edit. */
                    |private[golem] object RegisterAgents {
                    |  @JSExportTopLevel("__golemRegisterAgents")
                    |  val __golemRegisterAgents: Unit = {
                    |$touches
                    |    ()
                    |  }
                    |}
                    |""".stripMargin
              IO.write(baseOut, baseContent)

              log.info(
                s"[golem] Generated Scala.js agent registration for $basePackage into $genBasePkg (${impls.length} impls, ${byPkg.size} pkgs)."
              )
              perPkgFiles :+ baseOut
            }
        }
      }.taskValue
    )
}
