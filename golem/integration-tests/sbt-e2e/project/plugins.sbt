// External-style fixture: resolve the sbt plugin from Ivy local.
addSbtPlugin("dev.zio" % "zio-golem-sbt-plugin" % "0.0.0-SNAPSHOT")

// Required because the Golem sbt plugin requires Scala.js.
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")
