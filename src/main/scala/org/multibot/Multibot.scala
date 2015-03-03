package org.multibot

import java.nio.charset.Charset

import org.pircbotx.Configuration.Builder
import org.pircbotx._
import org.pircbotx.hooks.ListenerAdapter
import org.pircbotx.hooks.events.{MessageEvent, PrivateMessageEvent}
import org.pircbotx.hooks.types.GenericMessageEvent

case class Msg(channel: String, sender: String, message: String)
object Cmd {
  def unapply(s: String) = if (s.contains(' ')) Some(s.split(" ", 2).toList) else None
}

case class Multibot(cache: InterpretersCache, botname: String, channels: List[String]) {
  val NUMLINES = 5
  val LAMBDABOT = "lambdabot"
  val ADMINS = List("imeredith", "lopex", "tpolecat", "OlegYch")
  val httpHandler = HttpHandler()
  def start() {
    bot.startBot()
  }

  private def timeout[T](line: String)(f: String => T): T = {
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
      f(line)
    } catch {
      case e: Exception => throw e
      case e: Throwable => e.printStackTrace(); sys.exit(-1)
    } finally {
      cache.scalaInt.cleanUp()
      println(s"scalas ${cache.scalaInt.size()} memory free ${Runtime.getRuntime.freeMemory() / 1024 / 1024} of ${Runtime.getRuntime.totalMemory() / 1024 / 1024}")
      timeout.tryComplete(Success(false))
    }
  }
  private val builder = new Builder[PircBotX].
    setName(botname).setEncoding(Charset.forName("UTF-8")).setAutoNickChange(true).setAutoReconnect(true)
    .setServerHostname("irc.freenode.net")
    .setShutdownHookEnabled(false)
    .setAutoSplitMessage(true)
    .addListener(new ListenerAdapter[PircBotX] {
    def handle(_e: (String, String, GenericMessageEvent[PircBotX])) = {
      val (channel, sender, e) = _e
      def sendLines(channel: String, message: String) = {
        println(message)

        message split "\n" filter (!_.isEmpty) take NUMLINES foreach {m =>
          val message = " " + (if (!m.isEmpty && m.charAt(0) == 13) m.substring(1) else m)
          if (channel == sender) e.respond(message)
          else e.getBot.getUserChannelDao.getChannel(channel).send().message(message)
        }
      }
      def interpreters = InterpretersHandler(cache, httpHandler, sendLines)
      def admin = AdminHandler(e.getBot.getNick + ":", ADMINS, _ => (), _ => (), sendLines) //todo
      timeout(e.getMessage) { message =>
        val msg = Msg(channel, sender, message)
        interpreters.serve(msg)
        admin.serve(msg)
      }
    }
    override def onPrivateMessage(event: PrivateMessageEvent[PircBotX]): Unit = {
      super.onPrivateMessage(event)
      handle(event.getUser.getNick, event.getUser.getNick, event)
    }
    override def onMessage(event: MessageEvent[PircBotX]): Unit = {
      super.onMessage(event)
      handle(event.getChannel.getName, event.getUser.getNick, event)
    }
  })
  channels.foreach(builder.addAutoJoinChannel(_))
  private case object bot extends org.pircbotx.PircBotX(builder.buildConfiguration())
}
