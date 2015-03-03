package org.multibot

import dispatch.classic.{ConfiguredHttpClient, Http, NoLogging, Request}
import org.json4s.JsonAST.JValue
import org.json4s.native.JsonParser

case class HttpHandler(sendMessage: (String, String) => Unit) {
  private val NUMLINES = 5
  private val INNUMLINES = 8
  private val cookies = scala.collection.mutable.Map[String, String]()

  def respondJSON(req: Request, join: Boolean = false)(response: JValue => Option[String])(implicit msg: Msg) = respond(req, join) {
    line => response(JsonParser.parse(line))
  }

  def respondJSON2(req: Request, init: Request)(response: JValue => Option[String])(initResponse: JValue => Option[String])(implicit msg: Msg) = try {
    respond(req) {
      line => response(JsonParser.parse(line))
    }
  } catch {
    case t: Throwable =>
      respond(init) {
        line => initResponse(JsonParser.parse(line))
      }
      respond(req) {
        line => response(JsonParser.parse(line))
      }
  }

  def respond(req: Request, join: Boolean = false)(response: String => Option[String])(implicit msg: Msg) = {
    val Msg(channel, sender, login, hostname, message) = msg
    val host = req.host

    val request = cookies.get(channel + host) map (c => req <:< Map("Cookie" -> c)) getOrElse req

    val handler = request >+> {
      r =>
        r >:> {
          headers =>
            headers.get("Set-Cookie").foreach(h => h.foreach(c => cookies(channel + host) = c.split(";").head))
            r >~ {
              source =>
                val lines = source.getLines.take(NUMLINES)
                (if (join) List(lines.mkString) else lines).foreach { line =>
                  println(line)
                  response(line).foreach(l => l.split("\n").take(INNUMLINES).foreach(ml => sendMessage(channel, ml)))
                }
            }
        }
    } // non empty lines

    createHttpClient(handler)
  }

  def createHttpClient = new Http with NoLogging {
    override def make_client = new ConfiguredHttpClient(credentials) {
      override def createHttpParams() = {
        val params = super.createHttpParams()
        import org.apache.http.params.HttpConnectionParams

import scala.concurrent.duration._
        val timeout = 10.seconds.toMillis.toInt
        HttpConnectionParams.setConnectionTimeout(params, timeout)
        HttpConnectionParams.setSoTimeout(params, timeout)
        params
      }
    }
  }

}
