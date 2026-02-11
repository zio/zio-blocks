package golem.runtime.autowire

import golem.data.StructuredSchema
import golem.runtime.AgentMetadata

import scala.scalajs.js

object AgentTypeEncoder {
  def from[Instance](definition: AgentDefinition[Instance]): js.Dynamic = {
    val constructorMeta =
      Option(definition.constructor)
        .map(_.info)
        .getOrElse(ConstructorMetadata(name = None, description = definition.typeName, promptHint = None))

    val constructorSchema =
      Option(definition.constructor)
        .map(_.schema)
        .getOrElse(js.Dynamic.literal())

    val constructorInfo = js.Dynamic.literal(
      "description" -> constructorMeta.description,
      "inputSchema" -> constructorSchema
    )

    Option(constructorMeta.name).flatten
      .foreach(name => constructorInfo.updateDynamic("name")(name))
    Option(constructorMeta.promptHint).flatten
      .foreach(prompt => constructorInfo.updateDynamic("promptHint")(prompt))

    val methodsArray   = new js.Array[js.Dynamic]()
    val methodBindings = Option(definition.methodMetadata).getOrElse(Nil)
    methodBindings.foreach { binding =>
      if (binding != null) {
        val metadata          = binding.metadata
        val methodDescription = Option(metadata.description).flatten.getOrElse(metadata.name)
        val methodInfo        = js.Dynamic.literal(
          "name"         -> metadata.name,
          "description"  -> methodDescription,
          "inputSchema"  -> binding.inputSchema,
          "outputSchema" -> binding.outputSchema
        )
        Option(metadata.prompt).flatten
          .foreach(prompt => methodInfo.updateDynamic("promptHint")(prompt))
        methodsArray.push(methodInfo)
      }
    }

    val metadataInfo =
      Option(definition.metadata)
        .getOrElse(
          AgentMetadata(definition.typeName, None, Some(definition.mode.value), Nil, StructuredSchema.Tuple(Nil))
        )

    val typeDescription =
      Option(metadataInfo.description).flatten.getOrElse(definition.typeName)

    js.Dynamic.literal(
      "typeName"     -> definition.typeName,
      "description"  -> typeDescription,
      "constructor"  -> constructorInfo,
      "methods"      -> methodsArray,
      "dependencies" -> new js.Array[js.Dynamic](),
      "mode"         -> definition.mode.value
    )
  }
}
