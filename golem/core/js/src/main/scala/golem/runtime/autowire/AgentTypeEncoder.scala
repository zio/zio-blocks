package golem.runtime.autowire

import golem.data.StructuredSchema
import golem.host.js._
import golem.runtime.AgentMetadata

import scala.scalajs.js

object AgentTypeEncoder {
  def from[Instance](definition: AgentDefinition[Instance]): JsAgentType = {
    val constructorMeta =
      Option(definition.constructor)
        .map(_.info)
        .getOrElse(ConstructorMetadata(name = None, description = definition.typeName, promptHint = None))

    val constructorSchema: JsDataSchema =
      Option(definition.constructor)
        .map(_.schema)
        .getOrElse(JsDataSchema.tuple(new js.Array[js.Tuple2[String, JsElementSchema]]()))

    val constructorInfo = JsAgentConstructorDef(
      description = constructorMeta.description,
      inputSchema = constructorSchema,
      name = Option(constructorMeta.name).flatten.fold[js.UndefOr[String]](js.undefined)(n => n),
      promptHint = Option(constructorMeta.promptHint).flatten.fold[js.UndefOr[String]](js.undefined)(p => p)
    )

    val methodsArray   = new js.Array[JsAgentMethod]()
    val methodBindings = Option(definition.methodMetadata).getOrElse(Nil)
    methodBindings.foreach { binding =>
      if (binding != null) {
        val metadata          = binding.metadata
        val methodDescription = Option(metadata.description).flatten.getOrElse(metadata.name)
        val methodInfo = JsAgentMethod(
          name = metadata.name,
          description = methodDescription,
          httpEndpoint = new js.Array[JsHttpEndpointDetails](),
          inputSchema = binding.inputSchema,
          outputSchema = binding.outputSchema,
          promptHint = Option(metadata.prompt).flatten.fold[js.UndefOr[String]](js.undefined)(p => p)
        )
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

    JsAgentType(
      typeName = definition.typeName,
      description = typeDescription,
      sourceLanguage = "scala",
      constructor = constructorInfo,
      methods = methodsArray,
      dependencies = new js.Array[JsAgentDependency](),
      mode = definition.mode.value,
      snapshotting = JsSnapshotting.disabled,
      config = new js.Array[JsAgentConfigDeclaration]()
    )
  }
}
