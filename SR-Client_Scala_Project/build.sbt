ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.12"

val akkaVersion = "2.8.0"
val akkaHttpVersion = "10.5.0"

ThisBuild / libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "org.scalafx" %% "scalafx" % "15.0.1-R21",
  "org.scalatest" %% "scalatest" % "3.2.15" % Test,
)

lazy val root = (project in file("."))
  .settings(
    name := "SR-Client_Scala_Project"
  )

fork := true

javaOptions ++= Seq(
  "--module-path", "C:\\javafx-sdk-21.0.2\\lib",
  "--add-modules", "javafx.controls,javafx.fxml"
)

lazy val osName = System.getProperty("os.name") match {
  case n if n.startsWith("Linux") => "linux"
  case n if n.startsWith("Mac") => "mac"
  case n if n.startsWith("Windows") => "win"
  case _ => throw new Exception("Unknown platform!")
}

// Add JavaFX dependencies
lazy val javaFXModules = Seq("base", "controls", "fxml", "graphics", "media", "swing", "web")
libraryDependencies ++= javaFXModules.map( m=>
  "org.openjfx" % s"javafx-$m" % "11" classifier osName
)

libraryDependencies += "org.openjfx" % "javafx" % "19.0.2.1" pomOnly()