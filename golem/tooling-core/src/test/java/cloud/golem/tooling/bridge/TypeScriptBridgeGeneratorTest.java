package cloud.golem.tooling.bridge;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

import static org.junit.Assert.*;

public class TypeScriptBridgeGeneratorTest {

  @Test
  public void generatesMinimalBridge() {
    BridgeSpec spec = BridgeSpec.empty();
    String ts = TypeScriptBridgeGenerator.generate(spec);
    assertTrue(ts.contains("import { BaseAgent, agent } from \"@golemcloud/golem-ts-sdk\";"));
    assertTrue(ts.contains("const scalaAgents: any"));
  }

  @Test
  public void minimalBridgeMatchesGolden() throws Exception {
    BridgeSpec spec = BridgeSpec.empty();
    String ts = TypeScriptBridgeGenerator.generate(spec);

    String expected = readResourceUtf8("cloud/golem/tooling/bridge/golden-minimal.ts");
    assertEquals(expected, ts);
  }

  @Test
  public void generatesAgentWithNoArgCtorAndAsyncMethod() {
    AgentSpec a =
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

    BridgeSpec spec = new BridgeSpec("./scala.js", "", Arrays.asList(a));
    String ts = TypeScriptBridgeGenerator.generate(spec);

    assertTrue(ts.contains("@agent({ name: \"my-agent\" })"));
    assertTrue(ts.contains("class MyAgent extends BaseAgent"));
    assertTrue(ts.contains("constructor()"));
    assertTrue(ts.contains("this.impl = scalaAgents.newMyAgent();"));
    assertTrue(ts.contains("async reverse(input: Name): Promise<Name>"));
  }

  @Test
  public void autoEmitsRecordConstructorInputTypeAndDefaultArgs() {
    RecordConstructorSpec ctor =
      new RecordConstructorSpec(
        "Input",
        Arrays.asList(new FieldSpec("tableName", "string"), new FieldSpec("shardId", "number")),
        Collections.<String>emptyList()
      );

    AgentSpec a =
      new AgentSpec(
        "shard",
        "ShardAgent",
        "newShard",
        ctor,
        Collections.<String>emptyList(),
        Collections.<MethodSpec>emptyList()
      );

    BridgeSpec spec = new BridgeSpec("./scala.js", "", Arrays.asList(a));
    String ts = TypeScriptBridgeGenerator.generate(spec);

    assertTrue(ts.contains("type Input = { tableName: string; shardId: number };"));
    assertTrue(ts.contains("constructor(input: Input)"));
    assertTrue(ts.contains("this.impl = scalaAgents.newShard(input.tableName, input.shardId);"));
  }

  @Test
  public void emitsScalarConstructor() {
    ScalarConstructorSpec ctor = new ScalarConstructorSpec("string", Collections.<String>emptyList());
    AgentSpec a =
      new AgentSpec(
        "counter-agent",
        "CounterAgent",
        "newCounterAgent",
        ctor,
        Collections.<String>emptyList(),
        Collections.<MethodSpec>emptyList()
      );

    String ts = TypeScriptBridgeGenerator.generate(new BridgeSpec("./scala.js", "", Arrays.asList(a)));
    assertTrue(ts.contains("constructor(input: string)"));
    assertTrue(ts.contains("this.impl = scalaAgents.newCounterAgent(input);"));
  }

  @Test
  public void emitsPositionalConstructor() {
    PositionalConstructorSpec ctor =
      new PositionalConstructorSpec(
        Arrays.asList(new FieldSpec("tableName", "string"), new FieldSpec("shardId", "number")),
        Collections.<String>emptyList()
      );
    AgentSpec a =
      new AgentSpec(
        "shard",
        "ShardAgent",
        "newShard",
        ctor,
        Collections.<String>emptyList(),
        Collections.<MethodSpec>emptyList()
      );

    String ts = TypeScriptBridgeGenerator.generate(new BridgeSpec("./scala.js", "", Arrays.asList(a)));
    assertTrue(ts.contains("constructor(tableName: string, shardId: number)"));
    assertTrue(ts.contains("this.impl = scalaAgents.newShard(tableName, shardId);"));
  }

  @Test
  public void recordCtorAutoEmitsInputTypeWhenMissingFromDeclarations() {
    RecordConstructorSpec ctor =
      new RecordConstructorSpec(
        "MyInput",
        Arrays.asList(new FieldSpec("tableName", "string"), new FieldSpec("shardId", "number")),
        Collections.<String>emptyList()
      );

    AgentSpec a =
      new AgentSpec(
        "my-agent",
        "MyAgent",
        "newMyAgent",
        ctor,
        // Declarations exist but do not define MyInput -> generator should auto-emit MyInput anyway
        Arrays.asList("type Other = { x: string };"),
        Arrays.asList(new MethodSpec("ping", true, "string", Collections.<ParamSpec>emptyList(), ""))
      );

    String ts = TypeScriptBridgeGenerator.generate(new BridgeSpec("./scala.js", "", Arrays.asList(a)));
    assertTrue(ts.contains("type MyInput = { tableName: string; shardId: number };"));
  }

  @Test
  public void emitsQuotedMethodNameForKebabCaseAndUsesBracketImplAccess() {
    AgentSpec a =
      new AgentSpec(
        "my-agent",
        "MyAgent",
        "newMyAgent",
        NoArgConstructorSpec.INSTANCE,
        Collections.<String>emptyList(),
        Arrays.asList(
          new MethodSpec(
            "run-tests",
            true,
            "string",
            Collections.<ParamSpec>emptyList(),
            "run-tests"
          )
        )
      );

    String ts = TypeScriptBridgeGenerator.generate(new BridgeSpec("./scala.js", "", Arrays.asList(a)));
    assertTrue(ts.contains("async \"run-tests\"("));
    assertTrue(ts.contains("this.impl[\"run-tests\"]("));
  }

  @Test
  public void rejectsDuplicateAgentNames() {
    AgentSpec a1 =
      new AgentSpec(
        "dup",
        "A1",
        "f1",
        NoArgConstructorSpec.INSTANCE,
        Collections.<String>emptyList(),
        Collections.<MethodSpec>emptyList()
      );
    AgentSpec a2 =
      new AgentSpec(
        "dup",
        "A2",
        "f2",
        NoArgConstructorSpec.INSTANCE,
        Collections.<String>emptyList(),
        Collections.<MethodSpec>emptyList()
      );

    try {
      TypeScriptBridgeGenerator.generate(new BridgeSpec("./scala.js", "", Arrays.asList(a1, a2)));
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Duplicate agent name"));
    }
  }

  private static String readResourceUtf8(String path) throws Exception {
    InputStream is = TypeScriptBridgeGeneratorTest.class.getClassLoader().getResourceAsStream(path);
    if (is == null) throw new RuntimeException("Missing test resource: " + path);
    try {
      byte[] bytes = is.readAllBytes();
      return new String(bytes, StandardCharsets.UTF_8);
    } finally {
      is.close();
    }
  }
}



