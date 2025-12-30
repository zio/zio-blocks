lazy val zioSbtVersion = "0.4.9"
addSbtPlugin("dev.zio" % "zio-sbt-website" % zioSbtVersion)

addSbtPlugin("com.timushev.sbt"   % "sbt-updates"                   % "0.6.4")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                       % "0.4.8")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"                  % "2.3.1")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"                 % "0.13.1")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"                % "1.11.2")
addSbtPlugin("com.typesafe"       % "sbt-mima-plugin"               % "1.1.4")
addSbtPlugin("com.github.sbt"     % "sbt-header"                    % "5.11.0")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.20.1")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.9")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.5.6")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"                 % "2.4.3")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                       % "0.4.7")
addDependencyTreePlugin

// Use the following command to find updates for dependencies and sbt-plugins:
// sbt ";dependencyUpdates; reload plugins; dependencyUpdates; reload return"
