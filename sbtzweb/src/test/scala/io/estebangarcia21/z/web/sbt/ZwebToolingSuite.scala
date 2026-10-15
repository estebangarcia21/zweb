package io.estebangarcia21.z.web.sbt

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._

class ZwebToolingSuite extends munit.FunSuite {

  test("hot development requires an installed Hotswap Agent") {
    withProject { root =>
      val result = ZwebTooling.hotSwapJvmOptions(root, autoRefresh = true)

      assert(result.isLeft)
      assert(result.left.getOrElse("").contains("hotswap-agent.jar"))
    }
  }

  test("hot development starts with enhanced redefinition and automatic class watching") {
    withProject { root =>
      val agent = root.resolve("lib/hotswap/hotswap-agent.jar")
      write(agent, "agent placeholder")

      val options =
        ZwebTooling.hotSwapJvmOptions(root, autoRefresh = true).fold(fail(_), identity)

      assertEquals(
        options,
        Seq(
          "-Dzweb.liveReload=true",
          "-XX:+AllowEnhancedClassRedefinition",
          "-XX:HotswapAgent=external",
          s"-javaagent:$agent=autoHotswap=true,disablePlugin=AnonymousClassPatch"
        )
      )
    }
  }

  test("hot development can disable browser refresh without disabling class redefinition") {
    withProject { root =>
      val agent = root.resolve("lib/hotswap/hotswap-agent.jar")
      write(agent, "agent placeholder")

      val options =
        ZwebTooling.hotSwapJvmOptions(root, autoRefresh = false).fold(fail(_), identity)

      assertEquals(options.head, "-Dzweb.liveReload=false")
      assert(options.contains("-XX:+AllowEnhancedClassRedefinition"))
      assert(options.exists(_.startsWith("-javaagent:")))
    }
  }

  test("reload version advances only when compiled classes change") {
    withProject { root =>
      val classes = root.resolve("classes")
      val source = classes.resolve("example/Page.class")
      write(source, "first")

      val initial = ZwebTooling.classTreeVersion(classes)

      assertEquals(ZwebTooling.classTreeVersion(classes), initial)

      write(source, "second")

      assertNotEquals(ZwebTooling.classTreeVersion(classes), initial)
    }
  }

  test("init creates the only project configuration needed by the tooling") {
    withProject { root =>
      val initial = load(root)
      val messages = ZwebTooling
        .run(Seq("init", "example.app"), initial, rejectingDownloader)
        .fold(fail(_), identity)

      assertEquals(messages, Seq("Created zweb.conf for example.app."))

      val configured = load(root)
      assertEquals(configured.scalaPackage, Some("example.app"))
      assertEquals(
        configured.iconsOutput.map(_.toPath),
        Some(root.resolve("src/main/scala/example/app/Icons.scala"))
      )
    }
  }

  test("icon commands create app-owned source, cache, and lock files") {
    withConfiguredProject { (root, config) =>
      val svg =
        """<!-- license --><svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" class="lucide lucide-star"><path d="M1 2" /></svg>"""
      val downloader = fixedDownloader(svg)

      val added = ZwebTooling
        .run(Seq("icon", "add", "star"), config, downloader)
        .fold(fail(_), identity)

      assertEquals(added, Seq("Added Icons.star from Lucide 1.37.0."))
      assert(Files.isRegularFile(root.resolve(".zweb/icons.lock")))
      assert(Files.isRegularFile(root.resolve(".zweb/icons/star.svg")))

      val source = read(root.resolve("src/main/scala/example/app/Icons.scala"))
      assert(source.contains("package example.app"))
      assert(source.contains("def `star`: Frag"))
      assert(source.contains("aria-hidden=\\\"true\\\""))
      assert(!source.contains("width=\\\"24\\\""))

      val listed = ZwebTooling
        .run(Seq("icon", "list"), config, rejectingDownloader)
        .fold(fail(_), identity)
      assertEquals(listed, Seq("star 1.37.0"))

      ZwebTooling
        .run(Seq("icon", "remove", "star"), config, rejectingDownloader)
        .fold(fail(_), identity)

      assert(!Files.exists(root.resolve(".zweb/icons/star.svg")))
      assert(!read(root.resolve("src/main/scala/example/app/Icons.scala")).contains("def `star`"))
    }
  }

  test("JavaScript commands pin exact content in the application assets") {
    withConfiguredProject { (root, config) =>
      val javascript = "window.htmx = { version: 'test' };"
      val downloader = fixedDownloader(javascript)

      val added = ZwebTooling
        .run(Seq("js", "add", "htmx", "2.0.10"), config, downloader)
        .fold(fail(_), identity)

      assertEquals(
        added,
        Seq("Pinned htmx 2.0.10 at assets/js/vendor/htmx.min.js.")
      )
      assertEquals(
        read(root.resolve("src/main/resources/assets/js/vendor/htmx.min.js")),
        javascript
      )
      assert(
        read(root.resolve("src/main/resources/assets/js/vendor/jsDeps.lock"))
          .contains("htmx.org\t2.0.10")
      )

      val listed = ZwebTooling
        .run(Seq("js", "list"), config, rejectingDownloader)
        .fold(fail(_), identity)
      assertEquals(listed, Seq("htmx 2.0.10"))
    }
  }

  private val rejectingDownloader = new Downloader {
    override def get(url: String): Either[String, Array[Byte]] =
      Left(s"Unexpected download: $url")
  }

  private def fixedDownloader(contents: String): Downloader = new Downloader {
    override def get(url: String): Either[String, Array[Byte]] =
      Right(contents.getBytes(StandardCharsets.UTF_8))
  }

  private def withConfiguredProject(test: (Path, ZwebConfig) => Unit): Unit =
    withProject { root =>
      write(
        root.resolve("zweb.conf"),
        "zweb.scala-package = \"example.app\""
      )

      test(root, load(root))
    }

  private def load(root: Path): ZwebConfig =
    ZwebConfig
      .load(root.toFile, root.resolve("zweb.conf").toFile)
      .fold(fail(_), identity)

  private def withProject(test: Path => Unit): Unit = {
    val root = Files.createTempDirectory("zweb-tooling-test")

    try test(root)
    finally deleteRecursively(root)
  }

  private def read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

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
