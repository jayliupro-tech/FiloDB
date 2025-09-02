import sbt._
import sbt.Keys._

publishTo := Some(Resolver.file("Unused repo", file("target/unusedrepo")))


// Global setting across all subprojects
ThisBuild / organization := "org.filodb"
ThisBuild / organizationName := "FiloDB"
ThisBuild / scalaVersion := "2.12.12"
ThisBuild / publishMavenStyle := true
ThisBuild / Test / publishArtifact := false
ThisBuild / IntegrationTest / publishArtifact := false
ThisBuild / licenses += ("Apache-2.0", url("http://choosealicense.com/licenses/apache/"))
ThisBuild / pomIncludeRepository := { x => false }

enablePlugins(ProtobufPlugin)

lazy val memory = Submodules.memory
lazy val core = Submodules.core
lazy val query = Submodules.query
lazy val prometheus = Submodules.prometheus
lazy val coordinator = Submodules.coordinator
lazy val cassandra = Submodules.cassandra
lazy val kafka = Submodules.kafka
lazy val cli = Submodules.cli
lazy val http = Submodules.http
lazy val gateway = Submodules.gateway
lazy val standalone = Submodules.standalone
lazy val bootstrapper = Submodules.bootstrapper
lazy val sparkJobs = Submodules.sparkJobs
lazy val jmh = Submodules.jmh
lazy val gatling = Submodules.gatling
lazy val grpc = Submodules.grpc

lazy val sonarScanIfLogin = taskKey[Unit]("Run sonarScan only if sonar.login is provided")
lazy val runAllTests = taskKey[Unit]("Run all tests")
lazy val root = (project in file("."))
  .aggregate(coordinator, core, memory, standalone, http, bootstrapper, sparkJobs, kafka, cli, cassandra, query, prometheus, grpc)
  .settings(
    name := "filodb-oss",
    sonarProperties := {
      val moduleFolders = new java.io.File(".").listFiles
        .filter(f => f.isDirectory && !f.getName.startsWith("."))
        .map(_.getName)
        .toList

      val sourcePaths = moduleFolders.map(f => s"$f/src/main/scala").filter(f => file(f).exists).mkString(",")
      val testPaths   = moduleFolders.map(f => s"$f/src/test/scala").filter(f => file(f).exists && file(f).list().nonEmpty).mkString(",")
      val javaBinaryPaths = moduleFolders.map(f => s"${baseDirectory.value}/$f/target/scala-${scalaBinaryVersion.value}/classes")
        .filter(p => file(p).exists)
        .mkString(",")

      Map(
        "sonar.sourceEncoding" -> "UTF-8",
        "sonar.sources" -> sourcePaths,
        "sonar.tests" -> testPaths,
        "sonar.java.binaries" -> javaBinaryPaths,
        "sonar.exclusions" -> "conf/**,scripts/**,resources/**,target/**,**/jmh/**,**/gatling/**,**/grpc/**,**/standalone/**",
        "sonar.test.exclusions" -> "test/resources/**,test/scripts/**,**/jmh/**,**/gatling/**,**/grpc/**,**/standalone/**",
        "sonar.scala.coverage.reportPaths" -> s"target/scala-${scalaBinaryVersion.value}/scoverage-report/scoverage.xml"
      )
    },
    runAllTests := {
      val log = streams.value.log
      log.info("Running all tests with coverage in all submodules...")

      val modules = Seq(
        coordinator, core, memory, standalone, http, bootstrapper, sparkJobs,
        kafka, cli, cassandra, query, prometheus, grpc
      )

//      // coverageOn for all submodules
//      modules.foreach { mod =>
//        log.info(s"--> Coverage On: ${mod.id}")
//        (mod / coverageOn).value
//      }

      // run tests for all submodules
      modules.foreach { mod =>
        log.info(s"--> Running tests: ${mod.id}")
        (mod / Test / test).value
      }

//      // coverage report & aggregate
//      modules.foreach { mod =>
//        log.info(s"--> Generating coverage report: ${mod.id}")
//        (mod / coverageReport).value
//      }
//      modules.foreach { mod =>
//        log.info(s"--> Aggregating coverage: ${mod.id}")
//        (mod / coverageAggregate).value
//      }

      log.info("All tests completed.")
    },
    sonarScanIfLogin := Def.taskDyn {
      val login = sys.env.getOrElse("SONAR_LOGIN", "")
      if (login.trim.isEmpty)
        Def.task { streams.value.log.warn("Skipping SonarQube scan: SONAR_LOGIN is empty") }
      else
        Def.task { sonarScan.value }
    }.value
  )
