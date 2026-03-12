package zio.blocks.schema.yaml

import zio.blocks.schema.json.Json

object YamlJsonInterop {

  def yamlToJson(yaml: Yaml): Json = yaml match {
    case Yaml.Mapping(entries) =>
      val fields = entries.map {
        case (Yaml.Scalar(key, _), value) => (key, yamlToJson(value))
        case (key, value)                 => (key.print, yamlToJson(value))
      }
      new Json.Object(fields)
    case Yaml.Sequence(elements) =>
      new Json.Array(elements.map(yamlToJson))
    case Yaml.Scalar(value, _) =>
      value match {
        case "true" | "True" | "TRUE"       => Json.True
        case "false" | "False" | "FALSE"    => Json.False
        case "null" | "~" | "Null" | "NULL" => Json.Null
        case _                              =>
          try Json.Number(BigDecimal(value))
          catch { case _: NumberFormatException => new Json.String(value) }
      }
    case _: Yaml.NullValue.type => Json.Null
  }

  def jsonToYaml(json: Json): Yaml = json match {
    case obj: Json.Object =>
      Yaml.Mapping(obj.value.map { case (key, value) => (Yaml.Scalar(key): Yaml, jsonToYaml(value)) })
    case arr: Json.Array =>
      Yaml.Sequence(arr.value.map(jsonToYaml))
    case str: Json.String =>
      Yaml.Scalar(str.value)
    case num: Json.Number =>
      val tag = if (num.value.isWhole) Some(YamlTag.Int) else Some(YamlTag.Float)
      Yaml.Scalar(num.value.toString, tag = tag)
    case bool: Json.Boolean =>
      Yaml.Scalar(bool.value.toString, tag = Some(YamlTag.Bool))
    case _: Json.Null.type =>
      Yaml.NullValue
  }
}
