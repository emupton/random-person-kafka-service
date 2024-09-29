val Http4sVersion = "0.23.28"
val CirceVersion = "0.14.10"
val MunitVersion = "1.0.1"
val LogbackVersion = "1.5.8"
val MunitCatsEffectVersion = "2.0.0"
val KafkaClientsVersion = "3.8.0"
val Fs2Version = "3.11.0"

lazy val root = (project in file("."))
  .settings(
    organization := "com.example",
    name := "backend-service",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.14",
    libraryDependencies ++= Seq(
      "org.http4s"      %% "http4s-ember-server" % Http4sVersion,
      "org.http4s"      %% "http4s-ember-client" % Http4sVersion,
      "org.http4s"      %% "http4s-circe"        % Http4sVersion,
      "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
      "io.circe"        %% "circe-generic"       % CirceVersion,
      "io.circe"        %% "circe-parser"        % CirceVersion,
      "co.fs2"          %% "fs2-core"            % Fs2Version,
      "org.apache.kafka" % "kafka-clients"       % KafkaClientsVersion,
      "org.scalameta"   %% "munit"               % MunitVersion           % Test,
      "org.typelevel"   %% "munit-cats-effect"   % MunitCatsEffectVersion % Test,
      "ch.qos.logback"  %  "logback-classic"     % LogbackVersion         % Runtime,
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.13.3" cross CrossVersion.full),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case x => (assembly / assemblyMergeStrategy).value.apply(x)
    }
  )
