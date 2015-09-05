import sbt.Keys._
import sbt._

object Version {
  val akka  = "2.4.0-RC2"
  val scala = "2.11.7"
  val spray = "1.3.+"
}

object Library {
  val akkaActor                = "com.typesafe.akka"      %% "akka-actor"                    % Version.akka
  val akkaSlf4j                = "com.typesafe.akka"      %% "akka-slf4j"                    % Version.akka
  val akkaPersistence          = "com.typesafe.akka"      %% "akka-persistence" % Version.akka
  val akkaCluster              = "com.typesafe.akka"      %% "akka-cluster"                  % Version.akka
  val akkaClusterMetrics       = "com.typesafe.akka"      %% "akka-cluster-metrics"          % Version.akka
  val akkaContrib              = "com.typesafe.akka"      %% "akka-contrib"                  % Version.akka
  val spray                    = "io.spray"               %% "spray-can"                     % Version.spray
  val sprayRouting             = "io.spray"               %% "spray-routing"                 % Version.spray
  val sprayTestkit             = "io.spray"               %% "spray-testkit"                 % Version.spray % "test"
  val logbackClassic           = "ch.qos.logback"         %  "logback-classic"               % "1.1.3"
  val sigar                    = "org.fusesource"         %  "sigar"                         % "1.6.4" classifier("native") classifier("")
  val json4sNative             = "org.json4s"             %% "json4s-native"                 % "3.2.10"
  val scalaJsonCollection      = "net.hamnaberg.rest"     %% "scala-json-collection"         % "2.3"
  val elastic4s                = "com.sksamuel.elastic4s"    %% "elastic4s-core"                % "1.7.+"
  val hashids                  = "com.timesprint"            %% "hashids-scala"                 % "1.0.0"
  val log4j                    = "log4j"                     %  "log4j"                         % "1.2.17"
  val nscalaTime               = "com.github.nscala-time"    %% "nscala-time"                   % "2.0.0"
  val scopt                    = "com.github.scopt"          %% "scopt"                         % "3.3.0"
  val akkaPersistenceCassandra = "com.github.krasserm"       %% "akka-persistence-cassandra"    % "0.3.9"
  val leveldb                  = "org.iq80.leveldb"          % "leveldb"                        % "0.7"
  val leveldbjniAll            = "org.fusesource.leveldbjni" % "leveldbjni-all"              % "1.8"
}

object Dependencies {
  
  import Library._

  val resolvers = Seq(
    "Spray Repository"    at "http://repo.spray.io/",
    "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"
  )

  val projectTemplate = List(
    akkaActor,
    akkaPersistence,
    akkaCluster,
    akkaContrib,
    akkaSlf4j,
    spray,
    sprayRouting,
    sigar,
    json4sNative,
    scalaJsonCollection,
    elastic4s,
    logbackClassic,
    hashids,
    log4j,
    nscalaTime,
    scopt,
    akkaPersistenceCassandra,
    sprayTestkit
  )
}
