package cloud.golem.tooling;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TsAppScaffoldTest {

  @Test
  public void createsRootGolemYamlAndIsIdempotent() throws Exception {
    Path root = Files.createTempDirectory("golem-app-root-");

    Path appDir1 = GolemTooling.ensureTsAppScaffold(root, "my-app", "org:component", null);
    assertTrue(Files.isDirectory(appDir1));
    assertTrue(Files.isDirectory(appDir1.resolve("components-ts")));
    Path yaml = appDir1.resolve("golem.yaml");
    assertTrue(Files.exists(yaml));

    // Idempotence: do not overwrite existing file.
    Files.write(yaml, "custom\n".getBytes(StandardCharsets.UTF_8));
    Path appDir2 = GolemTooling.ensureTsAppScaffold(root, "my-app", "org:component", null);
    assertEquals(appDir1.toRealPath(), appDir2.toRealPath());

    String out = new String(Files.readAllBytes(yaml), StandardCharsets.UTF_8);
    assertEquals("custom\n", out);
  }
}