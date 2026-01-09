package golem.tools

import golem.runtime.AgentMetadata
import ujson._

object AgentTypeJsonEncoder {
  def encode(agentName: String, metadata: AgentMetadata): Value = {
    val methods = Arr(
      metadata.methods.map { method =>
        Obj(
          "name"        -> Str(method.name),
          "description" -> Str(method.description.getOrElse("")),
          "prompt"      -> method.prompt.map(Str(_)).getOrElse(Null),
          "input"       -> SchemaJsonEncoder.encode(method.input),
          "output"      -> SchemaJsonEncoder.encode(method.output)
        )
      }: _*
    )

    Obj(
      "name"        -> Str(agentName),
      "description" -> Str(metadata.description.getOrElse("")),
      "constructor" -> SchemaJsonEncoder.encode(metadata.constructor),
      "methods"     -> methods
    )
  }
}
