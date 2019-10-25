name := "parsers"

organization := "net.kurnevsky"

version := "0.1"

// scalaVersion := "2.13.1"
scalaVersion := "2.12.10"

resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
  "org.tpolecat" %% "atto-core" % "0.7.0",
  "org.parboiled" %% "parboiled" % "2.1.8",
  "com.codecommit" %% "gll-combinators" % "2.3",
  "com.lihaoyi" %% "fastparse" % "2.1.3",
)
