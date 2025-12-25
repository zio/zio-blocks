package cloud.golem.tooling.bridge;

import java.util.List;

/** Constructor with a single record input, e.g. {@code constructor(input: { shardId: number })}. */
public final class RecordConstructorSpec implements ConstructorSpec {
  public final String inputTypeName;
  public final List<FieldSpec> fields;
  /** Scala factory arguments as TypeScript expressions, usually derived from {@code input.<field>}. */
  public final List<String> scalaFactoryArgs;

  public RecordConstructorSpec(String inputTypeName, List<FieldSpec> fields, List<String> scalaFactoryArgs) {
    this.inputTypeName = inputTypeName;
    this.fields = fields;
    this.scalaFactoryArgs = scalaFactoryArgs;
  }
}