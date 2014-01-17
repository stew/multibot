package org.multibot

import java.io.FilePermission
import java.security.Permission

/**
  */
object ScriptSecurityManager extends SecurityManager {
  System.setProperty("actors.enableForkJoin", false + "")
  private val sm = System.getSecurityManager
  private var activated = false

  override def checkPermission(perm: Permission) {
    if (activated) {
      val read = perm.getActions == ("read")
      val allowedMethods = Seq("accessDeclaredMembers", "suppressAccessChecks", "createClassLoader", "setContextClassLoader", "getClassLoader",
        "accessClassInPackage.sun.reflect", "accessClassInPackage.sun.misc", "setIO", "getProtectionDomain").contains(perm.getName)
      val file = perm.isInstanceOf[FilePermission]
      val readClass = file && (perm.getName.endsWith(".class") || perm.getName.endsWith(".jar") || perm
        .getName.endsWith("library.properties")) && read
      val allow = readClass || (read && !file) || allowedMethods
      if (!allow) {
        println(perm)
        throw new SecurityException(perm.toString)
      }
    } else {
      if (sm != null) {
        sm.checkPermission(perm)
      }
    }

  }

  def deactivate {
    activated = false
    System.setSecurityManager(sm)
  }

  def activate {
    System.setSecurityManager(this)
    activated = true
  }

  def hardenPermissions[T](f: => T): T = this.synchronized {
    try {
      this.activate
      f
    } finally {
      this.deactivate
    }
  }
}
