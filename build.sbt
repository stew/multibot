import sbt.Keys._
import sbt._
import sbtbuildinfo.Plugin._

name := "multibot"

version := "1.0"

scalaVersion := "2.11.2"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

libraryDependencies ++= {
  val scalazVersion = "7.1.0"
  val scalazStreamVersion = "0.5a"
  val shapelessVersion = "2.0.0"
  val monocleVersion = "0.5.0"
  val spireVersion = "0.8.2"
  Seq(
    "org.scalaz" %% "scalaz-iteratee" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion,
    "org.scalaz" %% "scalaz-typelevel" % scalazVersion,
    "org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion,
    "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
    "com.chuusai" %% "shapeless" % shapelessVersion,
    "com.github.julien-truffaut" %% "monocle-generic" % monocleVersion,
    "com.github.julien-truffaut" %% "monocle-law" % monocleVersion,
    "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion,
    "org.spire-math" %% "spire" % spireVersion,
    "org.scalaz.stream" %% "scalaz-stream" % scalazStreamVersion
  )
}

libraryDependencies ++= Seq(
  "pircbot" % "pircbot" % "1.5.0",
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "net.databinder" %% "dispatch-http" % "0.8.10",
  "org.json4s" %% "json4s-native" % "3.2.10",
  "org.jruby" % "jruby-complete" % "1.7.10",
  "com.google.guava" % "guava" % "17.0"
)

autoCompilerPlugins := true

scalacOptions ++= Seq("-feature:false", "-language:_", "-deprecation", "-Xexperimental")

resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

resolvers += "linter" at "http://hairyfotr.github.io/linteRepo/releases"

addCompilerPlugin("com.foursquare.lint" %% "linter" % "0.1.3")

libraryDependencies += "com.foursquare.lint" %% "linter" % "0.1.3"

//scalacOptions += "-P:linter:disable:OLOLOUseHypot+CloseSourceFile+OptionOfOption"

addCompilerPlugin("org.brianmckenna" %% "wartremover" % "0.10")

libraryDependencies += "org.brianmckenna" %% "wartremover" % "0.10"

//scalacOptions += "-P:wartremover:traverser:OLOLOorg.brianmckenna.wartremover.warts.Unsafe"
scalacOptions += "-P:wartremover:only-warn-traverser:org.brianmckenna.wartremover.warts.Unsafe"

com.typesafe.sbt.SbtStartScript.startScriptForClassesSettings

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, scalacOptions in(Compile, compile), libraryDependencies in(Compile, compile))

buildInfoPackage := "org.multibot"