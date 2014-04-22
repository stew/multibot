import sbtassembly.Plugin.AssemblyKeys

name := "multibot"

version := "1.0"

scalaVersion := "2.10.4"

libraryDependencies ++= {
  val scalazVersion = "7.1.0-SNAPSHOT"
  val scalazContribVersion = "0.2-SNAPSHOT"
  val shapelessContribVersion = "0.3-SNAPSHOT"
  Seq(
    "org.typelevel" %% "scalaz-contrib-210" % scalazContribVersion,
    "org.typelevel" %% "scalaz-contrib-validation" % scalazContribVersion,
    "org.typelevel" %% "scalaz-contrib-undo" % scalazContribVersion,
    "org.typelevel" %% "scalaz-lift" % scalazContribVersion,
    "org.typelevel" %% "scalaz-nscala-time" % scalazContribVersion,
    "org.typelevel" %% "scalaz-spire" % scalazContribVersion,
    "org.typelevel" %% "shapeless-scalacheck" % shapelessContribVersion,
    "org.typelevel" %% "shapeless-spire" % shapelessContribVersion,
    "org.typelevel" %% "shapeless-scalaz" % shapelessContribVersion,
    "org.scalaz" %% "scalaz-iteratee" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion,
    "org.scalaz" %% "scalaz-typelevel" % scalazVersion,
    "org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion,
    "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
    "pircbot" % "pircbot" % "1.5.0",
    "org.scala-lang" % "scala-compiler" % "2.10.4-RC3",
    "net.databinder" %% "dispatch-http" % "0.8.8",
    "org.json4s" %% "json4s-native" % "3.1.0",
    "org.jruby" % "jruby-complete" % "1.7.10"
  )
}


// autoCompilerPlugins := true

AssemblyKeys.assembleArtifact in packageBin := false

seq(sbtassembly.Plugin.assemblySettings: _*)

scalacOptions ++= Seq("-feature", "-language:_", "-deprecation", "-Xexperimental")

// mergeStrategy in assembly := (e => MergeStrategy.first)

//mergeStrategy in assembly := {
//    case s if s.startsWith("org/joda") => MergeStrategy.discard
//    case _ => MergeStrategy.deduplicate
//}

resolvers += "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

com.typesafe.sbt.SbtStartScript.startScriptForClassesSettings