package org.multibot

import dispatch.classic.{url, :/}
import org.json4s.JsonAST._
import org.json4s.native.JsonMethods._

case class Interpreters(handler: HttpHandler, sendLines: (String, String) => Unit) {
  import handler._
  def serve(implicit msg: Msg): Unit = msg.message match {
    case Cmd(BOTMSG :: m :: Nil) if ADMINS contains msg.sender => m match {
      case Cmd("join" :: ch :: Nil) => joinChannel(ch)
      case Cmd("leave" :: ch :: Nil) => partChannel(ch)
      case Cmd("reply" :: ch :: Nil) => sendMessage(msg.channel, ch)
      case _ => sendMessage(msg.channel, "unknown command")
    }

    case "@bot" | "@bots" => sendMessage(msg.channel, ":)")
    case "@help" => sendMessage(msg.channel, "(!) scala (!reset|type|scalex), (i>) idris, (%) ruby (%reset), (,) clojure, (>>) haskell, (^) python, (&) javascript, (##) groovy, (<prefix>paste url), url: https://github.com/OlegYch/multibot")

    case Cmd("!" :: m :: Nil) => sendLines(msg.channel, scalaInterpreter(msg.channel) { (si, cout) =>
      import scala.tools.nsc.interpreter.Results._
      si interpret m match {
        case Success => cout.toString.replaceAll("(?m:^res[0-9]+: )", "") // + "\n" + iout.toString.replaceAll("(?m:^res[0-9]+: )", "")
        case Error => cout.toString.replaceAll("^<console>:[0-9]+: ", "")
        case Incomplete => "error: unexpected EOF found, incomplete expression"
      }
    })

    case Cmd("!type" :: m :: Nil) => sendMessage(msg.channel, scalaInterpreter(msg.channel)((si, cout) => si.typeOfExpression(m).directObjectString))
    case "!reset" => scalaInt invalidate msg.channel
    case "!reset-all" => scalaInt.invalidateAll()

    case Cmd("!scalex" :: m :: Nil) => respondJSON(:/("api.scalex.org") <<? Map("q" -> m)) {
      json =>
        Some((
          for {
            JObject(obj) <- json
            JField("results", JArray(arr)) <- obj
            JObject(res) <- arr
            JField("resultType", JString(rtype)) <- res

            JField("parent", JObject(parent)) <- res
            JField("name", JString(pname)) <- parent
            JField("typeParams", JString(ptparams)) <- parent

            JField("name", JString(name)) <- res
            JField("typeParams", JString(tparams)) <- res

            JField("comment", JObject(comment)) <- res
            JField("short", JObject(short)) <- comment
            JField("txt", JString(txt)) <- short

            JField("valueParams", JString(vparams)) <- res
          } yield pname + ptparams + " " + name + tparams + ": " + vparams + ": " + rtype + " '" + txt + "'").mkString("\n"))
    }

    case Cmd("!!" :: m :: Nil) => respond(:/("www.simplyscala.com") / "interp" <<? Map("bot" -> "irc", "code" -> m)) {
      case "warning: there were deprecation warnings; re-run with -deprecation for details" |
           "warning: there were unchecked warnings; re-run with -unchecked for details" |
           "New interpreter instance being created for you, this may take a few seconds." | "Please be patient." => None
      case line => Some(line.replaceAll("^res[0-9]+: ", ""))
    }

    case Cmd("," :: m :: Nil) => respondJSON(:/("try-clojure.org") / "eval.json" <<? Map("expr" -> m)) {
      case JObject(JField("expr", JString(_)) :: JField("result", JString(result)) :: Nil) => Some(result)
      case JObject(JField("error", JBool(true)) :: JField("message", JString(message)) :: Nil) => Some(message)
      case e => Some("unexpected: " + e)
    }

    case Cmd(">>" :: m :: Nil) => respondJSON(:/("tryhaskell.org") / "eval" <<? Map("exp" -> m)) {
      case JObject(
      JField("success",
      JObject(
      JField("expr", JString(_))
        :: JField("stdout", JArray(out))
        :: JField("value", JString(result))
        :: JField("files", _)
        :: JField("type", JString(xtype))
        :: Nil))
        :: Nil) => Some(s"$result :: $xtype " + out.collect { case JString(s) => s}.mkString("\n", "\n", ""))
      case JObject(JField("error", JString(error)) :: Nil) => Some(error)
      case e => Some("unexpected: " + e)
    }

    case Cmd("%%" :: m :: Nil) => respondJSON(:/("tryruby.org") / "/levels/1/challenges/0" <:<
      Map("Accept" -> "application/json, text/javascript, */*; q=0.01",
        "Content-Type" -> "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With" -> "XMLHttpRequest",
        "Connection" -> "keep-alive") <<< "cmd=" + java.net.URLEncoder.encode(m, "UTF-8")) {
      case JObject(JField("success", JBool(true)) :: JField("output", JString(output)) :: _) => Some(output)
      case JObject(JField("success", JBool(false)) :: _ :: JField("result", JString(output)) :: _) => Some(output)
      case e => Some("unexpected: " + e)
    }

    case "%reset" => jrubyInt invalidate msg.channel
    case "%reset-all" => jrubyInt.invalidateAll()

    case Cmd("%" :: m :: Nil) => sendLines(msg.channel, jrubyInterpreter(msg.channel) { (jr, sc, cout) =>
        try {
          val result = jr.evalScriptlet("# coding: utf-8\n" + m, sc).toString
          sendLines(msg.channel, cout.toString)
          result.toString
        } catch {
          case e: Exception => e.getMessage
        }
    })

    case Cmd("i>" :: m :: Nil) => respondJSON(:/("www.tryidris.org") / "interpret" << compact(render("expression", m))) {
      case JArray(List(JArray(List(JString(":return"), JArray(List(JString(_), JString(output), _*)), _*)), _*)) => Some(output)
      case e => Some("unexpected: " + e)
    }

    case Cmd("&" :: m :: Nil) =>
      val src = """
                var http = require('http');

                http.createServer(function (req, res) {
                  res.writeHead(200, {'Content-Type': 'text/plain'});
                  var a = (""" + m + """) + "";
                  res.end(a);
                }).listen();
                                     """

      respondJSON((:/("jsapp.us") / "ajax" << compact(render(("actions", List(("action", "test") ~("code", src) ~("randToken", "3901") ~("fileName", ""))) ~("user", "null") ~("token", "null"))))) {
        case JObject(JField("user", JNull) :: JField("data", JArray(JString(data) :: Nil)) :: Nil) => var s: String = "";
          createHttpClient(url(data) >- {
            source => s = source
          })
          Some(s)
        case e => Some("unexpected: " + e)
      }

    case Cmd("^" :: m :: Nil) => respondJSON2(:/("try-python.appspot.com") / "json" << compact(render(("method", "exec") ~("params", List(pythonSession, m)) ~ ("id" -> "null"))),
      :/("try-python.appspot.com") / "json" << compact(render(("method", "start_session") ~("params", List[String]()) ~ ("id" -> "null")))) {
      case JObject(JField("error", JNull) :: JField("id", JString("null")) :: JField("result", JObject(JField("text", JString(result)) :: _)) :: Nil) => Some(result)
      case e => Some("unexpected: " + e)
    } {
      case JObject(_ :: _ :: JField("result", JString(session)) :: Nil) => pythonSession = session; None
      case e => None
    }

    case Cmd("##" :: m :: Nil) => respondJSON(:/("groovyconsole.appspot.com") / "executor.groovy" <<? Map("script" -> m), true) {
      case JObject(JField("executionResult", JString(result)) :: JField("outputText", JString(output)) :: JField("stacktraceText", JString("")) :: Nil) => Some(result.trim + "\n" + output.trim)
      case JObject(JField("executionResult", JString("")) :: JField("outputText", JString("")) :: JField("stacktraceText", JString(err)) :: Nil) => Some(err)
      case e => Some("unexpected" + e)
    }

    case _ =>
  }
}
