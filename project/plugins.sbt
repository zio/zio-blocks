addSbtPlugin("dev.zio" % "zio-sbt-website" % "0.7.2")

addSbtPlugin("dev.zio"            % "zio-sbt-ci"               % "0.7.2")
addSbtPlugin("com.timushev.sbt"   % "sbt-updates"              % "0.7.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                  % "0.4.8")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"             % "2.5.0")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"            % "0.13.1")
addSbtPlugin("com.github.sbt"     % "sbt-ci-release"           % "1.12.1")
addSbtPlugin("com.typesafe"       % "sbt-mima-plugin"          % "1.1.6")
addSbtPlugin("com.github.sbt"     % "sbt-header"               % "5.11.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.22.0")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"             % "2.6.2")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"            % "2.4.4")
addSbtPlugin("com.eed3si9n"       % "sbt-salad-days"           % "0.2.0")

// `build.sbt` calls `SbtGit.useReadableConsoleGit` so sbt can start inside a git worktree (#1139).
// sbt-git used to arrive transitively via sbt-ci-release, which dropped that dependency in 1.12.0.
addSbtPlugin("com.github.sbt" % "sbt-git" % "2.1.0")

addDependencyTreePlugin

// Use the following command to find updates for dependencies and sbt-plugins:
// sbt ";dependencyUpdates; reload plugins; dependencyUpdates; reload return"
