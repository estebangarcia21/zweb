package io.estebangarcia21.z.web.sbt

import java.io.InputStream
import java.util.Properties

private[sbt] object ZwebVersion {

  lazy val current: String = {
    val properties = new Properties()
    val stream: InputStream = Option(getClass.getResourceAsStream("/zweb-version.properties"))
      .getOrElse(sys.error("Zweb version resource is missing"))

    try properties.load(stream)
    finally stream.close()

    Option(properties.getProperty("version"))
      .filter(_.nonEmpty)
      .getOrElse(sys.error("Zweb version resource contains no version"))
  }

}
