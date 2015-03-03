package org.multibot

object Multibottest extends App {
  val cache = InterpretersCache(List("#scala", "#scalaz"))
  val PRODUCTION = Option(System getenv "multibot.production") exists (_.toBoolean)
  val BOTNAME = if (PRODUCTION) "multibot_" else "multibot__"
  val channels: List[String] = if (PRODUCTION)
    List("#clojure.pl", "#scala.pl", "#jruby", "#ruby.pl", "#rubyonrails.pl", "#scala", "#scalaz", "#scala-fr", "#lift", "#playframework", "#bostonpython", "#fp-in-scala", "#CourseraProgfun", "#shapeless", "#akka", "#sbt", "#scala-monocle", "#scala-ru")
  else
    List("#multibottest", "#multibottest2")

  Multibot(cache, BOTNAME, channels).start()
  scala.io.StdIn.readLine()
  sys.exit()
}
