package cloud.golem.tooling.bridge;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class BridgeSpecManifestTest {

  @Test
  public void roundTripsThroughProperties() throws Exception {
    AgentSpec agent =
      new AgentSpec(
        "my-agent",
        "MyAgent",
        "newMyAgent",
        NoArgConstructorSpec.INSTANCE,
        Arrays.asList("type Name = { value: string };"),
        Arrays.asList(
          new MethodSpec(
            "reverse",
            true,
            "Name",
            Arrays.asList(new ParamSpec("input", "Name", "input")),
            ""
          )
        )
      );

    BridgeSpec spec = new BridgeSpec("./scala-autowired.js", "", Arrays.asList(agent));
    String props = BridgeSpecManifest.toProperties(spec);
    BridgeSpec parsed = BridgeSpecManifest.fromProperties(props);

    assertEquals("./scala-autowired.js", parsed.scalaBundleImport);
    assertNotNull(parsed.agents);
    assertEquals(1, parsed.agents.size());
    assertEquals("my-agent", parsed.agents.get(0).agentName);
    assertEquals("MyAgent", parsed.agents.get(0).className);
    assertEquals("newMyAgent", parsed.agents.get(0).scalaFactory);
    assertTrue(parsed.agents.get(0).constructor instanceof NoArgConstructorSpec);
    assertEquals(1, parsed.agents.get(0).methods.size());
    assertEquals("reverse", parsed.agents.get(0).methods.get(0).name);
  }

  @Test
  public void writeAndReadFile() throws Exception {
    BridgeSpec spec = new BridgeSpec("./scala.js", "", Collections.<AgentSpec>emptyList());
    Path file = Files.createTempDirectory("bridge-manifest-").resolve("bridge-spec.properties");

    BridgeSpecManifest.write(file, spec);
    assertTrue(Files.exists(file));
    String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    assertTrue(content.contains("scalaBundleImport="));

    BridgeSpec parsed = BridgeSpecManifest.read(file);
    assertEquals("./scala.js", parsed.scalaBundleImport);
  }
}