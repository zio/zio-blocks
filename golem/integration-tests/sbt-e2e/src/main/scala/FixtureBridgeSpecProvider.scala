import java.util.Arrays
import java.util.Collections
import java.util.function.Supplier

import cloud.golem.tooling.bridge._

/**
 * External-style fixture provider for metadata-driven bridge generation:
 * sbt plugin loads this class from the project classpath and calls get().
 */
final class FixtureBridgeSpecProvider extends Supplier[BridgeSpec] {
  override def get(): BridgeSpec = {
    val typeDecls = Arrays.asList("type Name = { value: string };")
    val agent =
      new AgentSpec(
        "my-agent",
        "MyAgent",
        "newMyAgent",
        NoArgConstructorSpec.INSTANCE,
        typeDecls,
        Arrays.asList(
          new MethodSpec("reverse", true, "Name", Arrays.asList(new ParamSpec("input", "Name", "input")), "")
        )
      )
    new BridgeSpec("./scala-autowired.js", "", Arrays.asList(agent))
  }
}