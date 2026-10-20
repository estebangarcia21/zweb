#!/usr/bin/env -S scala-cli shebang --server=false
//> using scala "3.8.4"
//> using dep "com.microsoft.playwright:playwright:1.62.0"

import com.microsoft.playwright.{BrowserType, Playwright}
import java.net.{ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import scala.util.Try

// Run after publishLocal. This isolated consumer exercises the real sbt watcher and browser client.
val hot = args.toList == List("hot")
require(args.isEmpty || hot, "Usage: ./scripts/testDevelopment.sc [hot]")

val root = Files.createTempDirectory("zweb-development-")
val socket = new ServerSocket(0)
val port = try socket.getLocalPort finally socket.close()
val origin = s"http://127.0.0.1:$port"
val http = HttpClient.newHttpClient()

def write(relative: String, text: String): Unit =
  val path = root.resolve(relative)

  Files.createDirectories(path.getParent)
  Files.writeString(path, text)
  ()

def get(path: String): String =
  val request = HttpRequest.newBuilder(URI.create(origin + path))
    .timeout(Duration.ofSeconds(2)).GET().build()

  http.send(request, HttpResponse.BodyHandlers.ofString()).body()

def eventually(description: String)(condition: => Boolean): Unit =
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)

  while !Try(condition).getOrElse(false) && System.nanoTime() < deadline do
    Thread.sleep(200)

  assert(Try(condition).getOrElse(false), s"Timed out: $description; logs: $root/development.log")

def application(message: String, extraRoute: Boolean = false): String =
  val extra = if extraRoute then
    """, Method.GET / "added" -> handler(Response.text("added route"))"""
  else ""

  s"""import io.estebangarcia21.z.web.*
     |import scalatags.Text.all.*
     |import zio.*
     |import zio.http.*
     |import java.nio.file.Files
     |
     |/** A consumer-owned service used to verify native route environment composition. */
     |final case class Greeting(value: String)
     |
     |object Main extends ZIOAppDefault:
     |  def message: String = "$message"
     |
     |  def response =
     |    val view = page[Unit](_ => PageMeta("Development", Assets(List("/assets/app.css"), List("/assets/app.js"))))(
     |      _ => div(h1(message), input(id := "draft"), span(id := "pid", ProcessHandle.current().pid().toString))
     |    )
     |    io.estebangarcia21.z.web.html(view(()))
     |      .addHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'")
     |
     |  def run = Zweb.serve(Routes(
     |    Method.GET / "" -> handler(ZIO.serviceWith[Greeting](greeting =>
     |      response.addHeader("X-Greeting", greeting.value)
     |    )),
     |    Method.GET / "assets" / string("file") -> handler { (file: String, _: Request) =>
     |      ZIO.attemptBlocking(Files.readString(Zweb.developmentAssetDirectory.get.resolve(file)))
     |        .map(body => Response.text(body).addHeader("Content-Type", if file.endsWith("css") then "text/css" else "text/javascript"))
     |        .orElseSucceed(Response.notFound)
     |    }$extra
     |  )).provide(ZLayer.succeed(Greeting("provided")))
     |""".stripMargin

write("project/build.properties", "sbt.version=1.13.0\n")
write("project/plugins.sbt", "addSbtPlugin(\"io.estebangarcia21.z\" % \"sbt-zweb\" % \"0.1.0\")\n")
write("build.sbt", "scalaVersion := \"3.8.4\"\nenablePlugins(ZwebPlugin)\n")
write("zweb.conf", s"zweb.development.port = $port\n")
write("src/main/scala/Main.scala", application("first"))
write("src/main/resources/assets/app.css", "body { color: rgb(10, 20, 30); }\n")
write("src/main/resources/assets/app.js", "window.assetVersion = 'first';\n")

val command = if hot then "zweb dev hot" else "zweb dev"
val process = new ProcessBuilder("sbt", "-Dsbt.supershell=false", command)
  .directory(root.toFile).redirectErrorStream(true)
  .redirectOutput(root.resolve("development.log").toFile).start()

try
  eventually("server listening on configured port") { get("/").contains("first") }

  val playwright = Playwright.create(new Playwright.CreateOptions().setEnv(
    Map("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" -> "1").asJava
  ))
  val browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))

  try
    val page = browser.newPage()
    val navigation = page.navigate(origin)
    assert(navigation.headerValue("X-Greeting") == "provided")
    assert(navigation.headerValue("Cache-Control") == "no-store")
    eventually("initial JavaScript asset") { page.evaluate("() => window.assetVersion") == "first" }
    val pid = page.locator("#pid").textContent()
    page.locator("#draft").fill("unsaved input")
    page.evaluate("window.documentIdentity = 'preserved'")
    // Let the initial polling snapshot arrive before editing assets.
    page.waitForTimeout(1500)

    write("src/main/resources/assets/app.css", "body { color: rgb(40, 50, 60); }\n")
    eventually("stylesheet update") {
      page.evaluate("() => getComputedStyle(document.body).color") == "rgb(40, 50, 60)"
    }
    assert(page.locator("#draft").inputValue() == "unsaved input")
    assert(page.evaluate("window.documentIdentity") == "preserved")
    assert(page.locator("#pid").textContent() == pid)
    println("PASS: CSS refresh preserves document, form state, and JVM")

    write("src/main/resources/assets/app.js", "window.assetVersion = 'second';\n")
    eventually("JavaScript refresh") { page.evaluate("() => window.assetVersion") == "second" }
    assert(page.locator("#draft").inputValue().isEmpty)
    println("PASS: JavaScript changes reload the document")

    write("src/main/scala/Main.scala", application("second", extraRoute = !hot))
    eventually("Scala update") { page.locator("h1").textContent() == "second" }
    if hot then
      assert(page.locator("#pid").textContent() == pid, "Hot edit unexpectedly restarted JVM")
    else
      assert(page.locator("#pid").textContent() != pid, "Code edit did not restart JVM")
      assert(get("/added") == "added route")

    println("PASS: Scala edit reaches browser" + (if hot then " without JVM restart" else "; added route is live"))

    write("src/main/scala/Main.scala", "this is intentionally invalid Scala\n")
    eventually("compiler reports failure") {
      val log = Files.readString(root.resolve("development.log"))
      val failed = log.lastIndexOf("Compilation failed")

      failed >= 0 && log.lastIndexOf("Monitoring source files") > failed
    }
    assert(get("/").contains("second"))
    println("PASS: compilation failure keeps previous application serving")

    // Respect sbt's watch anti-entropy window before the next save.
    page.waitForTimeout(1000)
    write("src/main/scala/Main.scala", application("recovered"))
    eventually("watch recovery") { page.locator("h1").textContent() == "recovered" }
    println("PASS: watch recovers after compilation failure")

    if !hot then
      write("zweb.conf", s"zweb.development.port = $port\nzweb.development.auto-refresh = false\n")
      eventually("auto-refresh configuration restarts the application") {
        !get("/").contains("data-zweb-version")
      }
      val request = HttpRequest.newBuilder(URI.create(origin + "/_zweb/live-reload")).GET().build()
      val response = http.send(request, HttpResponse.BodyHandlers.ofString())
      assert(response.statusCode() == 404)
      println("PASS: disabling auto-refresh removes the browser client and reload endpoint")
  finally
    browser.close()
    playwright.close()
finally
  // Only terminate this test's process tree; never inspect or kill arbitrary port listeners.
  val children = process.descendants().toList.asScala.reverse

  children.foreach(_.destroy())
  process.destroy()
  if !process.waitFor(10, TimeUnit.SECONDS) then
    process.destroyForcibly()
  children.filter(_.isAlive).foreach(_.destroyForcibly())
  println(s"Development test artifacts: $root")
