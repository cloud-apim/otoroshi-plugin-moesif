import Dependencies._

ThisBuild / scalaVersion     := "3.8.4"
ThisBuild / version          := "1.0.0-dev"
ThisBuild / organization     := "com.cloud-apim"
ThisBuild / organizationName := "Cloud-APIM"

lazy val root = (project in file("."))
  .settings(
    name := "otoroshi-plugin-moesif",
    resolvers += "jitpack" at "https://jitpack.io",
    libraryDependencies ++= Seq(
      // the wasm4s bundle ships a shaded scala 3 stdlib that clashes with the
      // one of the current scala version. this plugin does not use wasm at all
      // and otoroshi provides its own wasm4s at runtime
      "fr.maif" %% "otoroshi" % "18.0.0-preview6" % "provided" excludeAll (
        ExclusionRule(organization = "fr.maif", name = "wasm4s_3")
      ),
      munit % Test
    )
  )
