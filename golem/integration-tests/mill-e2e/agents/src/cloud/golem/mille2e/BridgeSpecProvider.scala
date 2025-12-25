package cloud.golem.mille2e

import java.util
import java.util.function.Supplier

/**
 * Provider-class BridgeSpec for the Mill e2e harness.
 *
 * Loaded on the JVM by tooling-core (via the Mill plugin) during `agents.golemWire`.
 * Uses reflection to build tooling-core bridge objects (so build.mill stays free of BridgeSpec construction).
 */
final class BridgeSpecProvider extends Supplier[AnyRef] {
  override def get(): AnyRef = {
    val cl = this.getClass.getClassLoader

    def cls(name: String): Class[?] =
      cl.loadClass(name)

    val cBridge = cls("cloud.golem.tooling.bridge.BridgeSpec")
    val cAgent  = cls("cloud.golem.tooling.bridge.AgentSpec")
    val cMethod = cls("cloud.golem.tooling.bridge.MethodSpec")
    val cParam  = cls("cloud.golem.tooling.bridge.ParamSpec")
    val cNoArg  = cls("cloud.golem.tooling.bridge.NoArgConstructorSpec")

    val noArgCtor = cNoArg.getField("INSTANCE").get(null)

    val param =
      cParam.getConstructor(classOf[String], classOf[String], classOf[String]).newInstance("input", "Name", "input").asInstanceOf[AnyRef]

    val method =
      cMethod
        .getConstructor(classOf[String], java.lang.Boolean.TYPE, classOf[String], classOf[java.util.List[?]], classOf[String])
        .newInstance("reverse", Boolean.box(true), "Name", util.Arrays.asList(param), "")
        .asInstanceOf[AnyRef]

    val nameTypeDecls = util.Arrays.asList("type Name = { value: string };")

    val nameAgent =
      cAgent
        .getConstructor(
          classOf[String],
          classOf[String],
          classOf[String],
          cls("cloud.golem.tooling.bridge.ConstructorSpec"),
          classOf[java.util.List[?]],
          classOf[java.util.List[?]]
        )
        .newInstance(
          "scala-name-agent",
          "ScalaNameAgent",
          "newNameAgent",
          noArgCtor,
          nameTypeDecls,
          util.Arrays.asList(method)
        )
        .asInstanceOf[AnyRef]

    cBridge
      .getConstructor(classOf[String], classOf[String], classOf[java.util.List[?]])
      .newInstance(
        "./scala-autowired.js",
        "(scalaExports as any).scalaAgents ?? (globalThis as any).scalaAgents",
        util.Arrays.asList(nameAgent)
      )
      .asInstanceOf[AnyRef]
  }
}