package org.multibot

import java.security.{SecurityPermission, Permission}
import java.io.{File, FilePermission}
import java.util.PropertyPermission

/**
  */
object ScriptSecurityManager extends SecurityManager {
  System.setProperty("actors.enableForkJoin", false + "")

  private val lock = new Object
  @volatile private var sm: Option[SecurityManager] = None
  @volatile private var activated = false

  def hardenPermissions[T](f: => T): T = lock.synchronized {
    try {
      activate
      f
    }
    catch {
      case e: Exception => throw e
      case e: Throwable => e.printStackTrace(); sys.exit(-1)
    } finally {
      deactivate
    }
  }

  override def checkPermission(perm: Permission) {
    if (activated) try {
      deactivate
      doChecks(perm)
    } finally activate
    else {
      //don't use closures here to avoid SOE
      if (sm.isDefined && sm.get != this) {
        sm.get.checkPermission(perm)
      }
    }
  }

  private def doChecks(perm: Permission) {
    val read = perm.getActions == "read"
    val readWrite = perm.getActions == "read,write"
    val allowedMethods = Seq(
      "accessDeclaredMembers", "suppressAccessChecks", "createClassLoader",
      "accessClassInPackage.sun.reflect", "getStackTrace", "getClassLoader",
      "setIO", "getProtectionDomain", "setContextClassLoader", "getClassLoader", "accessClassInPackage.sun.misc"
    ).contains(perm.getName)
    val getenv = perm.getName.startsWith("getenv")
    val file = perm.isInstanceOf[FilePermission]
    val property = perm.isInstanceOf[PropertyPermission]
    val security = perm.isInstanceOf[SecurityPermission]

    val notExistingFile = !new File(perm.getName).exists()

    val allowedFiles =
      Seq( """.*\.class""", """.*\.jar""", """.*classes.*""", """.*library\.properties""",
        """.*src/main/scala.*""", """.*/?target""", ".")
    val isClass = allowedFiles.exists(allowed => perm.getName.replaceAll( """\""" + """\""", "/").matches(allowed) || new File(perm.getName).getAbsolutePath == new File(allowed).getAbsolutePath)

    val readClass = file && isClass && read
    val readMissingFile = file && notExistingFile && read
    def allowedClass(trace: Array[StackTraceElement]) = trace.exists { element =>
      val name = element.getFileName
      //todo apply more robust checks
      List("BytecodeWriters.scala", "Settings.scala", "PathResolver.scala", "JavaMirrors.scala", "ForkJoinPool.java", "Using.scala")
        .contains(name)
    }

    val allow = readMissingFile || readClass || (read && !file) || allowedMethods || getenv ||
      (property && readWrite) || (security && perm.getName.startsWith("getProperty.")) || allowedClass(new Throwable().getStackTrace)
    if (!allow) {
      val exception = new SecurityException(perm.toString)
      exception.printStackTrace()
      throw exception
    }
  }

  private def deactivate {
    activated = false
    if (System.getSecurityManager == this) sm.foreach(System.setSecurityManager)
  }

  private def activate {
    val manager = System.getSecurityManager
    if (manager != this) {
      sm = Option(manager)
      System.setSecurityManager(this)
    }
    activated = true
  }
}
