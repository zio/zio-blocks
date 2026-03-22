/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
