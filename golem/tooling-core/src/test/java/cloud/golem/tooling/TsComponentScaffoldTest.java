package cloud.golem.tooling;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class TsComponentScaffoldTest {

  @Test
  public void createsExpectedFilesAndIsIdempotent() throws Exception {
    Path appDir = Files.createTempDirectory("golem-app-");
    String component = "org:component";

    Path componentDir1 = GolemTooling.ensureTsComponentScaffold(appDir, component, null);
    assertTrue(Files.isDirectory(componentDir1));
    assertTrue(Files.isDirectory(componentDir1.resolve("src")));
    assertTrue(Files.exists(componentDir1.resolve("golem.yaml")));
    assertTrue(Files.exists(componentDir1.resolve("package.json")));
    assertTrue(Files.exists(componentDir1.resolve("tsconfig.json")));
    assertTrue(Files.exists(componentDir1.resolve("src").resolve("main.ts")));
    assertTrue(Files.exists(componentDir1.resolve("src").resolve("user.ts")));

    // Idempotence: does not overwrite existing content.
    Path mainTs = componentDir1.resolve("src").resolve("main.ts");
    Files.write(mainTs, "// user content\n".getBytes(StandardCharsets.UTF_8));

    Path componentDir2 = GolemTooling.ensureTsComponentScaffold(appDir, component, null);
    assertEquals(componentDir1.toRealPath(), componentDir2.toRealPath());

    String out = new String(Files.readAllBytes(mainTs), StandardCharsets.UTF_8);
    assertEquals("// user content\n", out);
  }

  @Test
  public void wiringOverwritesMainTsAndCopiesBundleButDoesNotTouchUserTs() throws Exception {
    Path appDir = Files.createTempDirectory("golem-app-");
    String component = "org:component";
    Path componentDir = GolemTooling.ensureTsComponentScaffold(appDir, component, null);

    // create a dummy bundle
    Path bundle = Files.createTempFile("scala", ".js");
    Files.write(bundle, "console.log('hello');\n".getBytes(StandardCharsets.UTF_8));

    // user.ts should not be touched
    Path userTs = componentDir.resolve("src").resolve("user.ts");
    Files.write(userTs, "// keep\n".getBytes(StandardCharsets.UTF_8));

    GolemTooling.wireTsComponent(componentDir, bundle, "scala.js", "export const x = 1;", null);

    String mainOut = new String(Files.readAllBytes(componentDir.resolve("src").resolve("main.ts")), StandardCharsets.UTF_8);
    assertTrue(mainOut.contains("export const x = 1;"));

    String bundleOut = new String(Files.readAllBytes(componentDir.resolve("src").resolve("scala.js")), StandardCharsets.UTF_8);
    assertTrue(bundleOut.contains("console.log('hello')"));

    String userOut = new String(Files.readAllBytes(userTs), StandardCharsets.UTF_8);
    assertEquals("// keep\n", userOut);
  }
}


