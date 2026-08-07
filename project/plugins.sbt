addSbtPlugin("dev.zio" % "zio-sbt-website" % "0.6.3")

// zio-sbt-ci is tracked as a snapshot until the features it gained for this repository's workflow
// land in a release. Snapshots are published from zio-sbt's main branch on every push, so the
// version below is a specific commit and is expected to move.
resolvers += "central-snapshots" at "https://central.sonatype.com/repository/maven-snapshots/"
addSbtPlugin("dev.zio"            % "zio-sbt-ci"               % "0.6.3+11-d8c08c0f-SNAPSHOT")
addSbtPlugin("com.timushev.sbt"   % "sbt-updates"              % "0.7.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                  % "0.4.8")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"             % "2.4.1")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"            % "0.13.1")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"           % "1.11.2")
addSbtPlugin("com.typesafe"       % "sbt-mima-plugin"          % "1.1.6")
addSbtPlugin("com.github.sbt"     % "sbt-header"               % "5.11.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.22.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.6.2")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "2.4.4")
addSbtPlugin("com.eed3si9n"       % "sbt-salad-days"           % "0.2.0")

addDependencyTreePlugin

// Use the following command to find updates for dependencies and sbt-plugins:
// sbt ";dependencyUpdates; reload plugins; dependencyUpdates; reload return"
