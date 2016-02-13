package org.multibot

case class AdminHandler(BOTMSG: String, ADMINS: List[String], joinChannel: String => Unit, partChannel: String => Unit, sendLines: (String, String) => Unit) {
  def serve(implicit msg: Msg): Unit = msg.message match {
    case Cmd(BOTMSG :: m :: Nil) if ADMINS contains msg.sender => m match {
      case Cmd("join" :: ch :: Nil) => joinChannel(ch)
      case Cmd("leave" :: ch :: Nil) => partChannel(ch)
      case Cmd("reply" :: ch :: Nil) => sendLines(msg.channel, ch)
      case _ => sendLines(msg.channel, "unknown command")
    }

    case "@bot" | "@bots" => sendLines(msg.channel, ":)")
    case "@help" => sendLines(msg.channel, "(!) scala (!reset|type|scalex), (i>) idris, (,) clojure, (>>) haskell, (^) python, (&) javascript, (##) groovy, (%) ruby url: https://github.com/stew/multibot")

    case _ =>
  }
}
