name := "akka-streams"

version := "1.0"

scalaVersion := "2.11.6"

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

val specs2Version = "3.6.1"
val akkaVersion = "2.3.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka"           %% "akka-actor"                 % akkaVersion,
  "com.typesafe.akka"           %% "akka-stream-experimental"   % "1.0-RC3",
  "com.typesafe.scala-logging"  %% "scala-logging"              % "3.1.0",
  "ch.qos.logback"              %  "logback-classic"            % "1.1.3",
  "org.specs2"                  %% "specs2-core"                % specs2Version   % "test",
  "org.specs2"                  %% "specs2-mock"                % specs2Version   % "test",
  "com.typesafe.akka"           %% "akka-testkit"               % akkaVersion     % "test"
)
