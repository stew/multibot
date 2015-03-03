package org.multibot

import javax.net.ssl.SSLSocketFactory

object Multibottest extends App {
  val cache = InterpretersCache(List("#scala", "#scalaz", "#dev-ua/scala"))
  val PRODUCTION = Option(System getenv "multibot.production") exists (_.toBoolean)
  val gitterPass = Option(System getenv "multibot.gitter.pass").getOrElse("709182327498f5ee393dbb0bc6e440975fa316e5")
  Multibot(cache, if (PRODUCTION) "multibot_" else "multibot__",
    if (PRODUCTION)
      List("#clojure.pl", "#scala.pl", "#scala", "#scalaz", "#scala-fr", "#lift", "#playframework",
        "#bostonpython", "#fp-in-scala", "#CourseraProgfun", "#shapeless", "#akka", "#sbt", "#scala-monocle")
    else
      List("#multibottest", "#multibottest2")
  ).start()
  Multibot(cache,
    if (PRODUCTION) "multibot1" else "multibot2",
    if (PRODUCTION) List("#dev-ua/scala") else List("#OlegYch/multibot"),
  _.setServerHostname("irc.gitter.im").setServerPassword(gitterPass).
    setSocketFactory(SSLSocketFactory.getDefault)
  ).start()
  while (scala.io.StdIn.readLine() != "exit") Thread.sleep(1000)
  sys.exit()
}
