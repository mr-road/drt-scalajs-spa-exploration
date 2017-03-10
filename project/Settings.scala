import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._

/**
  * Application settings. Configure the build for your application here.
  * You normally don't have to touch the actual build definition after this.
  */
object Settings {
  /** The name of your application */
  val name = "scalajs-spa"

  /** The version of your application */
  val version = sys.env.getOrElse("BUILD_ID", "dev")

  /** Options for the scala compiler */
  val scalacOptions = Seq(
    "-Xlint",
    "-unchecked",
    "-deprecation",
    "-feature"
  )

  /** Declare global dependency versions here to avoid mismatches in multi part dependencies */
  object versions {
    val scala = "2.11.8"
    val scalaDom = "0.9.1"
    val scalajsReact = "0.11.1"
    val scalajsReactComponents = "0.5.0"
    val scalaCSS = "0.4.1"
    val log4js = "1.4.10"
    val autowire = "0.2.5"
    val booPickle = "1.2.4"
    val diode = "1.0.0"
    val uTest = "0.4.3"

    val react = "15.1.0"
    val jQuery = "1.11.1"
    val bootstrap = "3.3.6"
    val chartjs = "2.1.3"

    val playScripts = "0.5.0"
    val sprayVersion: String = "1.3.3"
    val json4sVersion = "3.4.0"
  }

  /**
    * These dependencies are shared between JS and JVM projects
    * the special %%% function selects the correct version for each project
    */
  val sharedDependencies = Def.setting(Seq(
    "com.lihaoyi" %%% "autowire" % versions.autowire,
    "me.chrons" %%% "boopickle" % versions.booPickle
  ))

  /** Dependencies only used by the JVM project */
  val jvmDependencies = Def.setting(List(
    "io.spray" % "spray-caching_2.11" % "1.3.4",
    "org.specs2" %% "specs2-core" % "3.7" % Test,
    "uk.gov.homeoffice.borderforce" %% "chroma-live" % "1.0",
    "com.vmunier" %% "play-scalajs-scripts" % versions.playScripts,
    "org.webjars" % "font-awesome" % "4.3.0-1" % Provided,
    "org.webjars" % "bootstrap" % versions.bootstrap % Provided,
    "com.typesafe.akka" %% "akka-testkit" % "2.4.9" % "test",
    "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.9" % "test",
    "com.typesafe.akka" %% "akka-persistence" % "2.4.11",
    "org.iq80.leveldb" % "leveldb" % "0.7",
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
    "com.lihaoyi" %%% "utest" % versions.uTest % Test,
    "org.renjin" % "renjin-script-engine" % "0.8.2195",
    "joda-time" % "joda-time" % "2.9.4",
    "org.pac4j" % "pac4j-saml" % "2.0.0-RC1"
  ) :::
    List("io.spray" %% "spray-client" % versions.sprayVersion,
      "io.spray" %% "spray-routing" % versions.sprayVersion,
      "io.spray" %% "spray-json" % "1.3.2",
      "com.typesafe" % "config" % "1.3.0"
    ))


  /** Dependencies only used by the JS project (note the use of %%% instead of %%) */
  val scalajsDependencies = Def.setting(Seq(
    "com.github.japgolly.scalajs-react" %%% "core" % versions.scalajsReact,
    "com.github.japgolly.scalajs-react" %%% "extra" % versions.scalajsReact,
    "com.github.japgolly.scalacss" %%% "ext-react" % versions.scalaCSS,
    "com.github.chandu0101.scalajs-react-components" %%% "core" % versions.scalajsReactComponents,
    "me.chrons" %%% "diode" % versions.diode,
    "com.payalabs" %%% "scalajs-react-bridge" % "0.2.0-SNAPSHOT",
    "me.chrons" %%% "diode-react" % versions.diode,
    "org.scala-js" %%% "scalajs-dom" % versions.scalaDom,
    "com.lihaoyi" %%% "utest" % versions.uTest % Test
  ))

  /** Dependencies for external JS libs that are bundled into a single .js file according to dependency order */
  val jsDependencies = Def.setting(Seq(
    //    "org.webjars.bower" % "react" % versions.react / "react-with-addons.js" minified "react-with-addons.min.js" commonJSName "React",
    //    "org.webjars.bower" % "react" % versions.react / "react-dom.js" minified "react-dom.min.js" dependsOn "react-with-addons.js" commonJSName "ReactDOM",
    //    "org.webjars.npm" % "fixed-data-table" % "0.6.3" / "dist/fixed-data-table.js" minified "dist/fixed-data-table.min.js" dependsOn "react-with-addons.js" commonJSName "ReactFixedDataTable",
    "org.webjars" % "rgraph" % "3_2014-07-27-stable" / "RGraph.bar.js",
    "org.webjars" % "jquery" % versions.jQuery / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "bootstrap" % versions.bootstrap / "bootstrap.js" minified "bootstrap.min.js" dependsOn "jquery.js",
    "org.webjars" % "chartjs" % versions.chartjs / "Chart.js" minified "Chart.min.js",
    "org.webjars" % "log4javascript" % versions.log4js / "js/log4javascript_uncompressed.js" minified "js/log4javascript.js"
  ))
}
