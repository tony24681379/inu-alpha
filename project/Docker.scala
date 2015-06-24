import sbt.Keys._
import sbt._
import sbtdocker.DockerPlugin.autoImport._
import sbtdocker.mutable.Dockerfile

object Docker {
  val settings = List(
    docker <<= docker.dependsOn(sbt.Keys.`package`.in(Compile, packageBin)),
    // Define a Dockerfile
    dockerfile in docker := {
      val jarFile = artifactPath.in(Compile, packageBin).value
      val classpath = (managedClasspath in Compile).value
      val mainclass = mainClass.in(Compile, packageBin).value.getOrElse(sys.error("Expected exactly one main class"))
      val libs = "/app/libs"
      val jarTarget = "/app/" + jarFile.name

      new Dockerfile {
        // Use a base image that contain Java
        from("java")
        // Expose port 8080
        expose(7879)

        // Copy all dependencies to 'libs' in the staging directory
        classpath.files.foreach { depFile =>
          val target = file(libs) / depFile.name
          stageFile(depFile, target)
        }
        // Add the libs dir from the
        addRaw(libs, libs)

        // Add the generated jar file
        add(jarFile, jarTarget)
        // The classpath is the 'libs' dir and the produced jar file
        val classpathString = s"$libs/*:$jarTarget"
        // Set the entry point to start the application using the main class
        cmd("java", "-cp", classpathString, mainclass)
      }
    },
    imageNames in docker := Seq(
      ImageName("jaohaohsuan/inu-alpha:0.0.3"),
      ImageName(namespace = Some(organization.value),
        repository = name.value,
        tag = Some("v" + version.value)))
  )
}
