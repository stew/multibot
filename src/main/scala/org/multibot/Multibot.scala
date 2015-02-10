package org.multibot

import org.jibble.pircbot.{NickAlreadyInUseException, PircBot}
import dispatch.classic._
import org.json4s.native.JsonMethods._
import org.json4s.native._
import org.json4s.JsonAST._
import org.json4s.JsonDSL._

import java.io.{PrintStream, ByteArrayOutputStream}
import com.google.common.cache.{RemovalNotification, RemovalListener, CacheLoader, CacheBuilder}
import java.util.concurrent.TimeUnit

object Multibottest extends PircBot {
  val PRODUCTION = Option(System getenv "multibot.production") exists (_ toBoolean)
  val BOTNAME = if (PRODUCTION) "multibot_" else "multibot__"
  val BOTMSG = BOTNAME + ":"
  val NUMLINES = 5
  val INNUMLINES = 8
  val LAMBDABOT = "lambdabot"
  val LAMBDABOTIGNORE = Set("#scala", "#scalaz")
  val ADMINS = List("imeredith", "lopex", "tpolecat", "OlegYch")

  def main(args: Array[String]) {
    setName(BOTNAME)
    setVerbose(true)
    setEncoding("UTF-8")
    scalaInt.get("#scala")
    scalaInt.get("#scalaz")
    tryConnect()
  }

  private def tryConnect(): Unit = try connect()
  catch {
    case e: NickAlreadyInUseException =>
      setName(getName + "_")
      tryConnect()
    case e: Exception =>
      e.printStackTrace()
      sys.exit(-1)
  }

  val channels = if (PRODUCTION)
    List("#clojure.pl", "#scala.pl", "#jruby", "#ruby.pl", "#rubyonrails.pl", "#scala", "#scalaz", "#scala-fr", "#lift", "#playframework", "#bostonpython", "#fp-in-scala", "#CourseraProgfun", "#shapeless", "#akka", "#sbt", "#scala-monocle")
  else
    List("#multibottest", "#multibottest2")

  def connect() {
    connect("irc.freenode.net")

    channels foreach joinChannel
  }

  override def onDisconnect(): Unit = while (true)
    try {
      tryConnect()
      return
    } catch {
      case e: Exception =>
        e.printStackTrace()
        Thread sleep 10000
    }

  var lastChannel: Option[String] = None


  override def handleLine(line: String): Unit = {
    import scala.concurrent.{Promise, Future}
    import scala.util.Success
    import scala.concurrent.ExecutionContext.Implicits.global
    val timeout = Promise[Boolean]()
    try {
      Future {
        scala.concurrent.blocking(Thread.sleep(1000 * 60))
        timeout.tryComplete(Success(true))
      }
      timeout.future.foreach { timeout =>
        if (timeout) {
          println(s"!!!!!!!!! timed out evaluating $line")
          sys.exit(-1)
        }
      }
      super.handleLine(line)
      scalaInt.cleanUp()
      jrubyInt.cleanUp()
      println(s"scalas ${scalaInt.size()} rubys ${jrubyInt.size()} memory free ${Runtime.getRuntime.freeMemory() / 1024 / 1024} of ${Runtime.getRuntime.totalMemory() / 1024 / 1024}")
    } catch {
      case e: Exception => throw e
      case e: Throwable => e.printStackTrace(); sys.exit(-1)
    } finally {
      timeout.tryComplete(Success(false))
    }
  }

  override def onPrivateMessage(sender: String, login: String, hostname: String, message: String) = sender match {
    case LAMBDABOT => lastChannel foreach (sendMessage(_, message))
    case _ => onMessage(sender, sender, login, hostname, message)
  }

  override def onNotice(sender: String, login: String, hostname: String, target: String, notice: String) = sender match {
    case LAMBDABOT => lastChannel foreach (sendNotice(_, notice))
    case _ =>
  }

  override def onAction(sender: String, login: String, hostname: String, target: String, action: String) = sender match {
    case LAMBDABOT => lastChannel foreach (sendAction(_, action))
    case _ =>
  }

  override def onMessage(channel: String, sender: String, login: String, hostname: String, message: String) =
    serve(Msg(channel, sender, login, hostname, message))

  object Cmd {
    def unapply(s: String) = if (s.contains(' ')) Some(s.split(" ", 2).toList) else None
  }

  object PasteCmd {
    def unapply(s: String) = if (s.indexOf("paste") == 1 && s.length > "?paste ".length) Some((s.substring(0, 1), s.substring("?paste ".length))) else None
  }

  case class Msg(channel: String, sender: String, login: String, hostname: String, message: String)

  val stdOut = System.out
  val stdErr = System.err
  val conOut = new ByteArrayOutputStream
  val conOutStream = new PrintStream(conOut)
  val conStdOut = Console.out
  val conStdErr = Console.err

  def captureOutput[T](block: => T): T = try {
    System setOut conOutStream
    System setErr conOutStream
    (Console withOut conOutStream) {
      (Console withErr conOutStream) {
        block
      }
    }
  } finally {
    System setOut stdOut
    System setErr stdErr
    conOut.flush()
    conOut.reset()
  }

  import scala.tools.nsc.interpreter.IMain

  val scalaInt = interpreterCache(new CacheLoader[String, IMain] {
    override def load(key: String) = {
      val settings = new scala.tools.nsc.Settings(null)
      val classpath = sys.props("java.class.path").split(java.io.File.pathSeparatorChar).toList
      val plugins = classpath.map(jar => s"-Xplugin:$jar")
      val pluginsOptions = plugins //++ List("-P:wartremover:only-warn-traverser:org.brianmckenna.wartremover.warts.Unsafe")
      settings.processArguments(pluginsOptions, true)
      settings.usejavacp.value = true
      settings.deprecation.value = true
      settings.feature.value = false
      val si = new IMain(settings)

      val imports = List("scalaz._", "Scalaz._", "reflect.runtime.universe.reify", "org.scalacheck.Prop._", "monocle.syntax._", "monocle.macros._")
      si.beQuietDuring {
        imports.foreach(i => si.interpret(s"import $i"))
      }
      si
    }
  })

  def scalaInterpreter(channel: String)(f: (IMain, ByteArrayOutputStream) => String) = this.synchronized {
    val si = scalaInt.get(channel)
    ScriptSecurityManager.hardenPermissions(captureOutput {
      f(si, conOut)
    })
  }

  import org.jruby.{RubyInstanceConfig, Ruby}
  import org.jruby.runtime.scope.ManyVarsDynamicScope

  val jrubyInt = interpreterCache(new CacheLoader[String, (Ruby, ManyVarsDynamicScope)] {
    override def load(key: String) = {
      val config = new RubyInstanceConfig
      config setOutput conOutStream
      config setError conOutStream
      config setInternalEncoding "utf-8"
      config setExternalEncoding "utf-8"

      val jruby = Ruby.newInstance(config)
      val scope = new ManyVarsDynamicScope(jruby.getStaticScopeFactory.newEvalScope(jruby.getCurrentContext.getCurrentScope.getStaticScope), jruby.getCurrentContext.getCurrentScope)
      (jruby, scope)
    }
  })

  def interpreterCache[K <: AnyRef, V <: AnyRef](loader: CacheLoader[K, V]) = {
    CacheBuilder.newBuilder().softValues().maximumSize(2).removalListener(new RemovalListener[K, V] {
      override def onRemoval(notification: RemovalNotification[K, V]) = println(s"expired $notification")
    }).build(loader)
  }
  def jrubyInterpreter(channel: String)(f: (Ruby, ManyVarsDynamicScope, ByteArrayOutputStream) => String) = this.synchronized {
    val (jr, sc) = jrubyInt.get(channel)
    ScriptSecurityManager.hardenPermissions(captureOutput {
      f(jr, sc, conOut)
    })
  }

  var pythonSession = ""

  def sendLines(channel: String, message: String) = {
    println(message)
    message split ("\n") filter (!_.isEmpty) take NUMLINES foreach (m => sendMessage(channel, " " + (if (!m.isEmpty && m.charAt(0) == 13) m.substring(1) else m)))
  }

  def serve(implicit msg: Msg): Unit = msg.message match {
    case Cmd(BOTMSG :: m :: Nil) if ADMINS contains msg.sender => m match {
      case Cmd("join" :: ch :: Nil) => joinChannel(ch)
      case Cmd("leave" :: ch :: Nil) => partChannel(ch)
      case Cmd("reply" :: ch :: Nil) => sendMessage(msg.channel, ch)
      case Cmd("cookies" :: "" :: Nil) => sendMessage(msg.channel, cookies.map {
        case (k, v) => k + " -> " + v
      }.mkString(" - ")) //cookies foreach {case(k, v) => sendMessage(msg.channel, k + " -> " + v)}
      case _ => sendMessage(msg.channel, "unknown command")
    }

    // case "@listchans" => sendMessage(msg.channel, getChannels mkString " ")

    case "@bot" | "@bots" => sendMessage(msg.channel, ":)")
    case "@help" => sendMessage(msg.channel, "(!) scala (!reset|type|scalex), (i>) idris, (%) ruby (%reset), (,) clojure, (>>) haskell, (^) python, (&) javascript, (##) groovy, (<prefix>paste url), lambdabot relay (" + !LAMBDABOTIGNORE.contains(msg.channel) + "), url: https://github.com/OlegYch/multibot")

    case Cmd("!" :: m :: Nil) => sendLines(msg.channel, scalaInterpreter(msg.channel) { (si, cout) =>
      import scala.tools.nsc.interpreter.Results._
      si interpret m match {
        case Success => cout.toString.replaceAll("(?m:^res[0-9]+: )", "") // + "\n" + iout.toString.replaceAll("(?m:^res[0-9]+: )", "")
        case Error => cout.toString.replaceAll("^<console>:[0-9]+: ", "")
        case Incomplete => "error: unexpected EOF found, incomplete expression"
      }
    })

    case PasteCmd(cmd, m) => // Http(url(m) >- {source => serve(msg.copy(message = "! " + source))})
      val conOut = new ByteArrayOutputStream
      createHttpClient(url(m) >>> new PrintStream(conOut))
      serve(msg.copy(message = cmd + " " + conOut))

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
          });
          Some(s)
        case e => Some("unexpected: " + e)
      }


    //case Cmd("^" :: m :: Nil) => jythonInterpreter(msg.channel){(jy, cout) =>
    //    try {
    //        //val result = jr.evalScriptlet(m, sc).toString
    //        jy.exec(m)
    //        sendLines(msg.channel, cout.toString)
    //        //sendLines(msg.channel, result.toString)
    //    } catch {
    //        case e: Exception => sendMessage(msg.channel, e.getMessage)
    //    }
    //}

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

    case m if (m.startsWith("@") || m.startsWith(">") || m.startsWith("?")) && m.trim.length > 1 && !LAMBDABOTIGNORE.contains(msg.channel) =>
      lastChannel = Some(msg.channel)
      sendMessage(LAMBDABOT, m)

    case _ =>
  }

  val cookies = scala.collection.mutable.Map[String, String]()

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
    //         val request = cookies.get(channel + host) map (c => req <:< Map("Cookie" -> c)) getOrElse {
    //             val req2 = req >:> { headers => println(headers.get("Set-Cookie"))}
    //             (new Http)(req2)
    //             req
    //         }

    val handler = request >+> {
      r =>
        r >:> {
          headers =>
            headers.get("Set-Cookie").foreach(h => h.foreach(c => cookies(channel + host) = c.split(";").head))
            r >~ {
              source =>
                val lines = source.getLines.take(NUMLINES)
                (if (join) List(lines.mkString) else lines).foreach(line => response(line).foreach(l => l.split("\n").take(INNUMLINES).foreach(ml => sendMessage(channel, ml))))
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
