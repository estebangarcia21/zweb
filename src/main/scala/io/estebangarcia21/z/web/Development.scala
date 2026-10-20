package io.estebangarcia21.z.web

import zio.*
import zio.http.*

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*

/** A scoped development monitor. One blocking scan serves all connected browser tabs; closing the
  * server scope interrupts the monitor. Failed scans retain the last successful snapshot.
  */
private[web] final class Development private (snapshot: Ref[String]):

  val routes: Routes[Any, Response] = Routes(
    Method.GET / "_zweb" / "live-reload" -> handler(
      snapshot.get.map(value => Development.noStore(Response.text(value)))
    ),
    Method.GET / "_zweb" / "live-reload.js" -> handler(Zweb.reloadScriptResponse)
  )

private[web] object Development:

  def make: ZIO[Scope, Throwable, Development] =
    for
      initial <- readVersion()
      snapshot <- Ref.make(initial)
      _ <- (snapshot.get
        .flatMap(previous => readVersion(previous.split(":")(1)))
        .flatMap(snapshot.set)
        .catchAll(error => ZIO.logWarning(s"Zweb development scan failed: ${error.getMessage}")))
        .repeat(Schedule.spaced(1.second))
        .forkScoped
    yield new Development(snapshot)

  def readVersion(previousCode: String = "0"): Task[String] = ZIO.attemptBlocking {
    val compiled = Zweb.reloadStampFile
      .filter(Files.isRegularFile(_))
      .map(path => Files.readString(path).trim)
      .filter(_.nonEmpty)
      .getOrElse("0")
    val code = Zweb.hotswapLogFile.fold(compiled)(path => acknowledgedVersion(path, previousCode))
    val (css, assets) = Zweb.developmentAssetDirectory.map(assetVersions).getOrElse(("0", "0"))

    s"${Zweb.serverInstance}:$code:$css:$assets"
  }

  // Hotswap Agent emits this DEBUG acknowledgement after Instrumentation.redefineClasses returns.
  // Its earlier RELOAD message is not readiness. Bound reads so a long development session stays cheap.
  private[web] def acknowledgedVersion(path: Path, previous: String): String =
    if !Files.isRegularFile(path) then previous
    else
      val channel = Files.newByteChannel(path)

      try
        val start = math.max(0L, channel.size() - 1024 * 1024)
        channel.position(start)
        val buffer = ByteBuffer.allocate((channel.size() - start).min(1024 * 1024).toInt)

        while buffer.hasRemaining && channel.read(buffer) > 0 do ()

        val text = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8)
        val marker = "(org.hotswap.agent.config.PluginManager) - ... reloaded classes "
        val index = text.lastIndexOf(marker)

        if index < 0 then previous
        else
          val lineStart = text.lastIndexOf('\n', index) + 1
          val lineEnd = text.indexOf('\n', index)

          if lineEnd < 0 then previous
          else
            val acknowledgement = s"${start + index}:${text.substring(lineStart, lineEnd)}"
            HexFormat
              .of()
              .formatHex(
                MessageDigest
                  .getInstance("SHA-256")
                  .digest(acknowledgement.getBytes(StandardCharsets.UTF_8))
              )
      finally channel.close()

  def noStore(response: Response): Response =
    response.removeHeader("Cache-Control").addHeader("Cache-Control", "no-store")

  // Separate CSS from other assets so stylesheet edits preserve Alpine state and unsaved forms.
  private[web] def assetVersions(directory: Path): (String, String) =
    if !Files.isDirectory(directory) then ("0", "0")
    else
      val files = Files.walk(directory)

      try
        val css = MessageDigest.getInstance("SHA-256")
        val other = MessageDigest.getInstance("SHA-256")

        files
          .iterator()
          .asScala
          .filter(Files.isRegularFile(_))
          .toVector
          .sortBy(_.toString)
          .foreach { path =>
            val entry = s"${directory.relativize(path)}:${Files.size(path)}:${Files
                .getLastModifiedTime(path)
                .to(TimeUnit.NANOSECONDS)}\n"
            val digest = if path.toString.endsWith(".css") then css else other

            digest.update(entry.getBytes(StandardCharsets.UTF_8))
          }

        (HexFormat.of().formatHex(css.digest()), HexFormat.of().formatHex(other.digest()))
      finally files.close()
