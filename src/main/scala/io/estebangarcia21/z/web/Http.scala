package io.estebangarcia21.z.web

import scalatags.Text.Frag
import zio.http.{Body, Response, Status}

/** Encodes a pure ScalaTags document as UTF-8 HTML. */
def html(fragment: Frag, status: Status = Status.Ok): Response =
  Response(status = status, body = Body.fromString(fragment.render))
    .addHeader("Content-Type", "text/html; charset=utf-8")
