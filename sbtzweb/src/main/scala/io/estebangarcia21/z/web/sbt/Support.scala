package io.estebangarcia21.z.web.sbt

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.util.control.NonFatal

private[sbt] trait Downloader {
  def get(url: String): Either[String, Array[Byte]]
}

private[sbt] object HttpDownloader extends Downloader {
  private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

  override def get(url: String): Either[String, Array[Byte]] =
    try {
      val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())

      if (response.statusCode() / 100 == 2) Right(response.body())
      else Left(s"Could not download $url (HTTP ${response.statusCode()}).")
    } catch {
      case NonFatal(error) =>
        Left(s"Could not download $url: ${Support.safeMessage(error)}")
    }

}

private[sbt] object Support {

  def attempt[A](value: => A): Either[String, A] =
    try Right(value)
    catch {
      case NonFatal(error) => Left(safeMessage(error))
    }

  def either(condition: Boolean, message: => String): Either[String, Unit] =
    if (condition) Right(()) else Left(message)

  def safeMessage(error: Throwable): String =
    Option(error.getMessage).filter(_.nonEmpty).getOrElse(error.getClass.getSimpleName)

  def hash(bytes: Array[Byte]): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(bytes)
      .map("%02x".format(_))
      .mkString

  def write(path: Path, bytes: Array[Byte]): Either[String, Unit] =
    attempt {
      Files.createDirectories(path.getParent)
      val temporary = Files.createTempFile(path.getParent, s".${path.getFileName}", ".tmp")

      try {
        Files.write(temporary, bytes)

        try {
          Files.move(
            temporary,
            path,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
          )
        } catch {
          case _: AtomicMoveNotSupportedException =>
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
      } finally Files.deleteIfExists(temporary)

      ()
    }

}
