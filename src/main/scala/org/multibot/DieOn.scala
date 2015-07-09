package org.multibot

import scala.concurrent.duration.Duration

//exit vm on unrecoverable condition, a supervisor (heroku) will restart it
object DieOn {
  def error[T](f: => T): T = {
    try f
    catch {
      case e: Error => e.printStackTrace(); sys.exit(-1)
    }
  }
  def timeout[T](time: Duration)(f: => T): T = {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent._
    val timeout = Promise[Boolean]()
    try {
      Future {
        blocking(Thread.sleep(time.toMillis))
        timeout.trySuccess(true)
      }
      timeout.future.foreach { timeout =>
        if (timeout) {
          new Throwable("timed out").printStackTrace()
          sys.exit(-1)
        }
      }
      f
    } finally {
      timeout.trySuccess(false)
    }
  }
}
