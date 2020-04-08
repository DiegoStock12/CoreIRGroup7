name := "ir_scala"

version := "0.1"

scalaVersion := "2.12.10"

libraryDependencies ++= Seq(
  "org.deeplearning4j" % "deeplearning4j-nlp" % "1.0.0-beta6",
  "org.nd4j" % "nd4j-native" % "1.0.0-beta6" % Test,
  "org.nd4j" % "nd4j-native-platform" % "1.0.0-beta6"
)