package org.multibot

import org.jibble.pircbot.{NickAlreadyInUseException, PircBot}

case class Msg(channel: String, sender: String, login: String, hostname: String, message: String)
object Cmd {
  def unapply(s: String) = if (s.contains(' ')) Some(s.split(" ", 2).toList) else None
}

object Multibottest extends PircBot {
  val PRODUCTION = Option(System getenv "multibot.production") exists (_ toBoolean)
  val BOTNAME = if (PRODUCTION) "multibot_" else "multibot__"
  val NUMLINES = 5
  val INNUMLINES = 8
  val LAMBDABOT = "lambdabot"
  val ADMINS = List("imeredith", "lopex", "tpolecat", "OlegYch")
  val httpHandler = HttpHandler(sendLines)
  val cache = InterpretersCache(List("#scala", "#scalaz"))
  val interpreters = InterpretersHandler(cache, httpHandler, sendLines)
  val admin = AdminHandler(getName + ":", ADMINS, joinChannel, partChannel, sendLines)

  def main(args: Array[String]) {
    setName(BOTNAME)
    setVerbose(true)
    setEncoding("UTF-8")
    tryConnect()
    scala.io.StdIn.readLine()
    sys.exit()
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

  override def handleLine(line: String): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.{Future, Promise}
    import scala.util.Success
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
      cache.scalaInt.cleanUp()
      println(s"scalas ${cache.scalaInt.size()} memory free ${Runtime.getRuntime.freeMemory() / 1024 / 1024} of ${Runtime.getRuntime.totalMemory() / 1024 / 1024}")
    } catch {
      case e: Exception => throw e
      case e: Throwable => e.printStackTrace(); sys.exit(-1)
    } finally {
      timeout.tryComplete(Success(false))
    }
  }

  override def onPrivateMessage(sender: String, login: String, hostname: String, message: String) = {
    onMessage(sender, sender, login, hostname, message)
  }

  override def onMessage(channel: String, sender: String, login: String, hostname: String, message: String) = {
    val msg = Msg(channel, sender, login, hostname, message)
    interpreters.serve(msg)
    admin.serve(msg)
  }

  def sendLines(channel: String, message: String) = {
    println(message)
    message split ("\n") filter (!_.isEmpty) take NUMLINES foreach (m => sendMessage(channel, " " + (if (!m.isEmpty && m.charAt(0) == 13) m.substring(1) else m)))
  }

}
