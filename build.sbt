import sbt.Resolver

organization := "uk.ac.wellcome"

name := "sierra-streams-source"

version := "0.1"

scalaVersion := "2.11.8"


val versions = new {
  val logback = "1.1.8"
  val scalatest = "3.0.1"
  val circeVersion = "0.8.0"
}

val circeDependencies = Seq(
  "io.circe" %% "circe-core" % versions.circeVersion,
  "io.circe" %% "circe-generic"% versions.circeVersion,
  "io.circe" %% "circe-parser"% versions.circeVersion,
  "io.circe" %% "circe-optics" % versions.circeVersion
)

libraryDependencies := Seq(
  "com.github.tomakehurst" % "wiremock" % "2.11.0" % Test,
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.scalatest" %% "scalatest" % versions.scalatest % Test,
  "ch.qos.logback" % "logback-classic" % versions.logback,
  "org.slf4j" % "slf4j-api" % "1.7.25",
  "com.typesafe.akka" %% "akka-stream" % "2.5.6",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.6" % Test
) ++ circeDependencies

resolvers += Resolver.sonatypeRepo("releases")

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding",
  "UTF-8",
  "-Xlint",
  "-Yclosure-elim",
  "-Yinline",
  "-Xverify",
  "-feature",
  "-language:postfixOps"
)

parallelExecution in Test := false