package org.multibot

object Multibottest {
  def main(args: Array[String]): Unit = {
    val cache = InterpretersCache(List("#typelevel/cats", "#stew/dogs", "#typelevel/shapeless"))

    val gitterPass = Option(System getenv "MULTIBOT_GITTER_PASS").getOrElse("this isn't a password")

    Multibot(cache = cache,
      botname = "catsbot",
      channels = List("#stew/dogs", "#typelevel/cats", "#milessabin/shapeless"),
      settings = _.setServerHostname("irc.gitter.im").setServerPassword(gitterPass).
        setSocketFactory(javax.net.ssl.SSLSocketFactory.getDefault)
    ).start()
    while (scala.io.StdIn.readLine() != "exit") Thread.sleep(1000)
    sys.exit()
  }
}
