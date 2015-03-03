package org.multibot

import org.jibble.pircbot.{NickAlreadyInUseException, PircBot}

case class Msg(channel: String, sender: String, login: String, hostname: String, message: String)
object Cmd {
  def unapply(s: String) = if (s.contains(' ')) Some(s.split(" ", 2).toList) else None
}

case class Multibot(cache: InterpretersCache, botname: String, channels: List[String]) {
  val NUMLINES = 5
  val LAMBDABOT = "lambdabot"
  val ADMINS = List("imeredith", "lopex", "tpolecat", "OlegYch")
  val httpHandler = HttpHandler(bot.sendLines)
  val interpreters = InterpretersHandler(cache, httpHandler, bot.sendLines)
  def admin = AdminHandler(bot.getName + ":", ADMINS, bot.joinChannel, bot.partChannel, bot.sendLines)
  def start() {
    bot.tryConnect()
  }

  private case object bot extends PircBot {
    setName(botname)
    def tryConnect(): Unit = {
      setVerbose(true)
      setEncoding("UTF-8")
      try connect()
      catch {
        case e: NickAlreadyInUseException =>
          setName(getName + "_")
          tryConnect()
        case e: Exception =>
          e.printStackTrace()
          sys.exit(-1)
      }
    }

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
}
