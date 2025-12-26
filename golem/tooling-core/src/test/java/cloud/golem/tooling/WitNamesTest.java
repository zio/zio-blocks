package cloud.golem.tooling;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WitNamesTest {

  @Test
  public void fullyQualifiedMethodFormatsCorrectly() {
    assertEquals(
      "scala:name-agent/scala-name-agent.{reverse}",
      WitNames.fullyQualifiedMethod("scala:name-agent", "scala-name-agent", "reverse")
    );
  }

  @Test
  public void agentIdFormatsWithEmptyCtorArgs() {
    assertEquals(
      "scala:name-agent/scala-name-agent()",
      WitNames.agentId("scala:name-agent", "scala-name-agent", "")
    );
    assertEquals(
      "scala:name-agent/scala-name-agent()",
      WitNames.agentId("scala:name-agent", "scala-name-agent", "   ")
    );
    assertEquals(
      "scala:name-agent/scala-name-agent()",
      WitNames.agentId("scala:name-agent", "scala-name-agent", null)
    );
  }

  @Test
  public void agentIdFormatsWithCtorArgs() {
    assertEquals(
      "scala:name-agent/scala-complex-shard({ table-name: \"demo\", shard-id: 1 })",
      WitNames.agentId("scala:name-agent", "scala-complex-shard", "{ table-name: \"demo\", shard-id: 1 }")
    );
  }
}


