package org.multibot

import java.io.{ByteArrayOutputStream, PrintStream}

import com.google.common.cache.{CacheBuilder, CacheLoader, RemovalListener, RemovalNotification}

case class InterpretersCache(preload: List[String]) {
  private val stdOut = System.out
  private val stdErr = System.err
  private val conOut = new ByteArrayOutputStream
  private val conOutStream = new PrintStream(conOut)

  private def captureOutput[T](block: => T): T = try {
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

      val imports = List("scalaz._", "Scalaz._", "reflect.runtime.universe.reify", "org.scalacheck.Prop._", "monocle.syntax._", "monocle.macros._", "effectful._")
      si.beQuietDuring {
        imports.foreach(i => si.interpret(s"import $i"))
      }
      si
    }
  })
  new Thread() {
    override def run(): Unit = preload.foreach(scalaInt.get)
    start()
  }

  def scalaInterpreter(channel: String)(f: (IMain, ByteArrayOutputStream) => String) = this.synchronized {
    import scala.concurrent.duration._
    val si = scalaInt.get(channel)
    val r = DieOn.timeout(1.minute) {
      ScriptSecurityManager.hardenPermissions(captureOutput {
        f(si, conOut)
      })
    }
    scalaInt.cleanUp()
    println(s"scalas ${scalaInt.size()} memory free ${Runtime.getRuntime.freeMemory() / 1024 / 1024} of ${Runtime.getRuntime.totalMemory() / 1024 / 1024}")
    r
  }

  private def interpreterCache[K <: AnyRef, V <: AnyRef](loader: CacheLoader[K, V]) = {
    CacheBuilder.newBuilder().softValues().maximumSize(preload.size + 1).removalListener(new RemovalListener[K, V] {
      override def onRemoval(notification: RemovalNotification[K, V]) = println(s"expired $notification")
    }).build(loader)
  }
}
