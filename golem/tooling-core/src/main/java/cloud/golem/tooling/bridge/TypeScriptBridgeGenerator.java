package cloud.golem.tooling.bridge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a TypeScript bridge (`src/main.ts`) compatible with the `component new ts` shape, but driven by a
 * user-provided {@link BridgeSpec} (no hard-coded example agents).
 */
public final class TypeScriptBridgeGenerator {

  private TypeScriptBridgeGenerator() {}

  public static String generate(BridgeSpec spec) {
    if (spec == null) throw new IllegalArgumentException("BridgeSpec cannot be null");
    if (spec.scalaBundleImport == null || spec.scalaBundleImport.trim().isEmpty())
      throw new IllegalArgumentException("BridgeSpec.scalaBundleImport must be set (e.g. ./scala.js)");

    String scalaAgentsExpr = spec.scalaAgentsExpr;
    if (scalaAgentsExpr == null || scalaAgentsExpr.trim().isEmpty()) {
      scalaAgentsExpr = "(scalaExports as any).scalaAgents ?? (globalThis as any).scalaAgents";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("// @ts-ignore Scala.js bundle does not ship TypeScript declarations\n");
    sb.append("import * as scalaExports from \"").append(escapeString(spec.scalaBundleImport)).append("\";\n\n");
    sb.append("import { BaseAgent, agent } from \"@golemcloud/golem-ts-sdk\";\n\n");
    // Optional user extension point. The deterministic scaffold creates src/user.ts by default.
    sb.append("import \"./user\";\n\n");
    sb.append("const scalaAgents: any = ").append(scalaAgentsExpr).append(";\n\n");

    // Helpers for mapping between JS optional/undefined and WIT option/null conventions.
    // These are intentionally tiny and are provided as building blocks for user-supplied implArgExprs.
    sb.append("const __golemOpt = <T>(v: T | undefined | null): T | null => (v === undefined ? null : v);\n");
    sb.append("const __golemUndef = <T>(v: T | null | undefined): T | undefined => (v === null ? undefined : v);\n\n");

    // Validate uniqueness across agents.
    Set<String> agentNames = new HashSet<String>();
    Set<String> classNames = new HashSet<String>();
    for (AgentSpec a : spec.agents) {
      if (a == null) continue;
      if (!agentNames.add(a.agentName))
        throw new IllegalArgumentException("Duplicate agent name in BridgeSpec: " + a.agentName);
      if (!classNames.add(a.className))
        throw new IllegalArgumentException("Duplicate class name in BridgeSpec: " + a.className);
    }

    for (AgentSpec agent : spec.agents) {
      emitAgent(sb, agent);
      sb.append("\n");
    }

    return sb.toString();
  }

  private static void emitAgent(StringBuilder sb, AgentSpec agent) {
    if (agent == null) throw new IllegalArgumentException("AgentSpec cannot be null");
    if (isBlank(agent.agentName)) throw new IllegalArgumentException("AgentSpec.agentName cannot be blank");
    if (isBlank(agent.className)) throw new IllegalArgumentException("AgentSpec.className cannot be blank");
    if (isBlank(agent.scalaFactory)) throw new IllegalArgumentException("AgentSpec.scalaFactory cannot be blank");
    if (agent.constructor == null) throw new IllegalArgumentException("AgentSpec.constructor cannot be null");

    // Ensure the record constructor input type exists.
    boolean emittedAutoCtorType = false;
    if (agent.constructor instanceof RecordConstructorSpec) {
      RecordConstructorSpec rc = (RecordConstructorSpec) agent.constructor;
      if (isBlank(rc.inputTypeName)) throw new IllegalArgumentException("RecordConstructorSpec.inputTypeName cannot be blank");
      if (rc.fields != null && !rc.fields.isEmpty()) {
        // Validate fields
        for (FieldSpec f : rc.fields) {
          if (f == null) continue;
          if (isBlank(f.name)) throw new IllegalArgumentException("RecordConstructorSpec.fields.name cannot be blank");
        }
        boolean hasDecl = hasTypeDeclaration(agent.typeDeclarations, rc.inputTypeName);
        if (!hasDecl) {
          sb.append("type ").append(rc.inputTypeName).append(" = { ");
          for (int i = 0; i < rc.fields.size(); i++) {
            FieldSpec f = rc.fields.get(i);
            if (f == null) continue;
            if (i > 0) sb.append("; ");
            String t = isBlank(f.tsType) ? "any" : f.tsType;
            sb.append(f.name).append(": ").append(t);
          }
          sb.append(" };\n\n");
          emittedAutoCtorType = true;
        }
      }
    }

    if (!emittedAutoCtorType && agent.typeDeclarations != null) {
      for (String decl : agent.typeDeclarations) {
        if (decl == null) continue;
        String d = decl.trim();
        if (d.isEmpty()) continue;
        sb.append(d).append("\n");
      }
      if (!agent.typeDeclarations.isEmpty()) sb.append("\n");
    }

    sb.append("@agent({ name: \"").append(escapeString(agent.agentName)).append("\" })\n");
    sb.append("class ").append(agent.className).append(" extends BaseAgent {\n");
    sb.append("  private readonly impl: any;\n\n");

    emitConstructor(sb, agent);
    sb.append("\n");

    // Validate method name uniqueness per agent.
    if (agent.methods != null) {
      Set<String> names = new HashSet<String>();
      for (MethodSpec m : agent.methods) {
        if (m == null) continue;
        if (!names.add(m.name))
          throw new IllegalArgumentException("Duplicate method name '" + m.name + "' on agent " + agent.className);
      }
    }

    if (agent.methods != null) {
      for (MethodSpec m : agent.methods) {
        emitMethod(sb, m);
        sb.append("\n");
      }
    }

    sb.append("}\n");
  }

  private static void emitConstructor(StringBuilder sb, AgentSpec agent) {
    ConstructorSpec ctor = agent.constructor;
    if (ctor instanceof NoArgConstructorSpec) {
      sb.append("  constructor() {\n");
      sb.append("    super();\n");
      sb.append("    this.impl = scalaAgents.").append(agent.scalaFactory).append("();\n");
      sb.append("  }\n");
      return;
    }

    if (ctor instanceof ScalarConstructorSpec) {
      ScalarConstructorSpec sc = (ScalarConstructorSpec) ctor;
      String tsType = isBlank(sc.tsType) ? "any" : sc.tsType;
      sb.append("  constructor(input: ").append(tsType).append(") {\n");
      sb.append("    super();\n");
      sb.append("    this.impl = scalaAgents.").append(agent.scalaFactory).append("(");
      if (sc.scalaFactoryArgs != null && !sc.scalaFactoryArgs.isEmpty()) sb.append(String.join(", ", sc.scalaFactoryArgs));
      else sb.append("input");
      sb.append(");\n");
      sb.append("  }\n");
      return;
    }

    if (ctor instanceof PositionalConstructorSpec) {
      PositionalConstructorSpec pc = (PositionalConstructorSpec) ctor;
      sb.append("  constructor(");
      if (pc.params != null && !pc.params.isEmpty()) {
        for (int i = 0; i < pc.params.size(); i++) {
          FieldSpec f = pc.params.get(i);
          if (f == null) continue;
          if (i > 0) sb.append(", ");
          String name = isBlank(f.name) ? ("arg" + i) : f.name;
          String t = isBlank(f.tsType) ? "any" : f.tsType;
          sb.append(name).append(": ").append(t);
        }
      }
      sb.append(") {\n");
      sb.append("    super();\n");
      sb.append("    this.impl = scalaAgents.").append(agent.scalaFactory).append("(");
      if (pc.scalaFactoryArgs != null && !pc.scalaFactoryArgs.isEmpty()) sb.append(String.join(", ", pc.scalaFactoryArgs));
      else if (pc.params != null && !pc.params.isEmpty()) {
        for (int i = 0; i < pc.params.size(); i++) {
          FieldSpec f = pc.params.get(i);
          if (f == null) continue;
          if (i > 0) sb.append(", ");
          String name = isBlank(f.name) ? ("arg" + i) : f.name;
          sb.append(name);
        }
      }
      sb.append(");\n");
      sb.append("  }\n");
      return;
    }

    if (ctor instanceof RecordConstructorSpec) {
      RecordConstructorSpec rc = (RecordConstructorSpec) ctor;
      if (isBlank(rc.inputTypeName)) throw new IllegalArgumentException("RecordConstructorSpec.inputTypeName cannot be blank");
      sb.append("  constructor(input: ").append(rc.inputTypeName).append(") {\n");
      sb.append("    super();\n");
      sb.append("    this.impl = scalaAgents.").append(agent.scalaFactory).append("(");
      if (rc.scalaFactoryArgs != null && !rc.scalaFactoryArgs.isEmpty()) sb.append(String.join(", ", rc.scalaFactoryArgs));
      else if (rc.fields != null && !rc.fields.isEmpty()) {
        // Convenience default: pass input.<field> for each field, if no explicit args were provided.
        for (int i = 0; i < rc.fields.size(); i++) {
          FieldSpec f = rc.fields.get(i);
          if (f == null) continue;
          if (i > 0) sb.append(", ");
          sb.append("input.").append(f.name);
        }
      }
      sb.append(");\n");
      sb.append("  }\n");

      return;
    }

    throw new IllegalArgumentException("Unknown ConstructorSpec: " + ctor.getClass().getName());
  }

  private static void emitMethod(StringBuilder sb, MethodSpec m) {
    if (m == null) return;
    if (isBlank(m.name)) throw new IllegalArgumentException("MethodSpec.name cannot be blank");
    String implName = isBlank(m.implMethodName) ? m.name : m.implMethodName;
    String retType = isBlank(m.tsReturnType) ? "any" : m.tsReturnType;

    String paramsSig = "";
    String argsCall = "";
    if (m.params != null && !m.params.isEmpty()) {
      paramsSig = joinParamsSig(m.params);
      argsCall = joinArgsExpr(m.params);
    }

    boolean methodIsIdent = isValidTsIdentifier(m.name);
    boolean implIsIdent = isValidTsIdentifier(implName);
    String methodNameTs = methodIsIdent ? m.name : ("\"" + escapeString(m.name) + "\"");
    String implAccess = implIsIdent ? ("this.impl." + implName) : ("this.impl[\"" + escapeString(implName) + "\"]");

    if (m.isAsync) {
      sb.append("  async ").append(methodNameTs).append("(").append(paramsSig).append("): Promise<").append(retType).append("> {\n");
      sb.append("    return await ").append(implAccess).append("(").append(argsCall).append(");\n");
      sb.append("  }\n");
    } else {
      sb.append("  ").append(methodNameTs).append("(").append(paramsSig).append("): ").append(retType).append(" {\n");
      if ("void".equals(retType)) {
        sb.append("    ").append(implAccess).append("(").append(argsCall).append(");\n");
      } else {
        sb.append("    return ").append(implAccess).append("(").append(argsCall).append(");\n");
      }
      sb.append("  }\n");
    }
  }

  private static String joinParamsSig(List<ParamSpec> params) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < params.size(); i++) {
      ParamSpec p = params.get(i);
      if (p == null) continue;
      if (i > 0) sb.append(", ");
      String tsType = isBlank(p.tsType) ? "any" : p.tsType;
      sb.append(p.name).append(": ").append(tsType);
    }
    return sb.toString();
  }

  private static String joinArgsExpr(List<ParamSpec> params) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < params.size(); i++) {
      ParamSpec p = params.get(i);
      if (p == null) continue;
      if (i > 0) sb.append(", ");
      String expr = isBlank(p.implArgExpr) ? p.name : p.implArgExpr;
      sb.append(expr);
    }
    return sb.toString();
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }

  private static boolean hasTypeDeclaration(List<String> declarations, String typeName) {
    if (declarations == null || declarations.isEmpty()) return false;
    String t = typeName.trim();
    if (t.isEmpty()) return false;
    String needleType = "type " + t + " ";
    String needleInterface = "interface " + t + " ";
    for (String decl : declarations) {
      if (decl == null) continue;
      String d = decl.trim();
      if (d.startsWith(needleType) || d.startsWith(needleInterface)) return true;
    }
    return false;
  }

  private static boolean isValidTsIdentifier(String s) {
    if (isBlank(s)) return false;
    // Good-enough check for our generator: JS identifiers usable in method names + property access.
    // (Avoids emitting invalid `async foo-bar()` syntax.)
    return s.matches("^[A-Za-z_$][A-Za-z0-9_$]*$");
  }

  private static String escapeString(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}


