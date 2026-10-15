package io.estebangarcia21.z.web.sbt

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

class ZwebConfigSuite extends munit.FunSuite {

  test("defaults remain project-local and do not invent an icon package") {
    withProject { root =>
      val config = load(root)

      assertEquals(config.scalaPackage, None)
      assertEquals(config.iconsOutput, None)
      assertEquals(
        config.assetsDirectory.toPath,
        root.resolve("src/main/resources/assets")
      )
      assertEquals(config.developmentPort, 8080)
      assertEquals(config.developmentAutoRefresh, true)
    }
  }

  test("zweb.conf controls generated sources, assets, and development") {
    withProject { root =>
      write(
        root.resolve("zweb.conf"),
        """zweb {
          |  scala-package = "example.ui"
          |  icons-output = "src/main/scala/example/generated/Icons.scala"
          |  assets-directory = "src/main/resources/public"
          |  development.port = 9191
          |  development.auto-refresh = false
          |}
          |""".stripMargin
      )

      val config = load(root)

      assertEquals(config.scalaPackage, Some("example.ui"))
      assertEquals(
        config.iconsOutput.map(_.toPath),
        Some(root.resolve("src/main/scala/example/generated/Icons.scala"))
      )
      assertEquals(
        config.assetsDirectory.toPath,
        root.resolve("src/main/resources/public")
      )
      assertEquals(config.developmentPort, 9191)
      assertEquals(config.developmentAutoRefresh, false)
    }
  }

  test("configured paths cannot escape the consuming project") {
    withProject { root =>
      write(
        root.resolve("zweb.conf"),
        "zweb.assets-directory = \"../shared\""
      )

      val result = ZwebConfig.load(root.toFile, root.resolve("zweb.conf").toFile)

      assert(result.isLeft)
      assert(result.left.getOrElse("").contains("must stay inside the project"))
    }
  }

  private def load(root: Path): ZwebConfig =
    ZwebConfig
      .load(root.toFile, root.resolve("zweb.conf").toFile)
      .fold(fail(_), identity)

  private def withProject(test: Path => Unit): Unit = {
    val root = Files.createTempDirectory("zweb-config-test")

    try test(root)
    finally deleteRecursively(root)
  }

  private def write(path: Path, contents: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, contents.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def deleteRecursively(root: Path): Unit = {
    val paths = Files.walk(root)

    try
      paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
    finally paths.close()
  }

}
