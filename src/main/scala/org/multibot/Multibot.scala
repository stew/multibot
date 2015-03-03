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

case class Msg(channel: String, sender: String, login: String, hostname: String, message: String)
object Cmd {
  def unapply(s: String) = if (s.contains(' ')) Some(s.split(" ", 2).toList) else None
}

object Multibottest extends PircBot {
  val PRODUCTION = Option(System getenv "multibot.production") exists (_ toBoolean)
  val BOTNAME = if (PRODUCTION) "multibot_" else "multibot__"
  val BOTMSG = BOTNAME + ":"
  val NUMLINES = 5
  val INNUMLINES = 8
  val LAMBDABOT = "lambdabot"
  val ADMINS = List("imeredith", "lopex", "tpolecat", "OlegYch")
  val httpHandler = HttpHandler(sendLines)
  val interpreters = Interpreters(httpHandler, sendLines)

  def main(args: Array[String]) {
    setName(BOTNAME)
    setVerbose(true)
    setEncoding("UTF-8")
    scalaInt.get("#scala")
    scalaInt.get("#scalaz")
    scalaInt.get("#scala-ru")
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
    List("#clojure.pl", "#scala.pl", "#jruby", "#ruby.pl", "#rubyonrails.pl", "#scala", "#scalaz", "#scala-fr", "#lift", "#playframework", "#bostonpython", "#fp-in-scala", "#CourseraProgfun", "#shapeless", "#akka", "#sbt", "#scala-monocle", "#scala-ru")
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
    interpreters.serve(Msg(channel, sender, login, hostname, message))


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

}
