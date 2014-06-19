/*
 * Copyright (C) 2012 Romain Reuillon
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.iscpif.gridscale.ssh

import java.util.UUID
import fr.iscpif.gridscale.tools.shell._
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.common.IOUtils
import fr.iscpif.gridscale._
import tools._
import jobservice._

object SSHJobService {

  val rootDir = ".gridscale/ssh"

  def file(jobId: String, suffix: String) = rootDir + "/" + jobId + "." + suffix
  def pidFile(jobId: String) = file(jobId, "pid")
  def endCodeFile(jobId: String) = file(jobId, "end")
  def outFile(jobId: String) = file(jobId, "out")
  def errFile(jobId: String) = file(jobId, "err")

  val PROCESS_CANCELED = 143
  val COMMAND_NOT_FOUND = 127

  /*def exec (connection: Connection, cde: String): Unit = {
    val session = connection.openSession
    try {
      exec(session, cde) 
      if(session.getExitStatus != 0) throw new RuntimeException("Return code was no 0 but " + session.getExitStatus)
    } finally session.close
  } */

  def withSession[T](c: SSHClient)(f: Session ⇒ T): T = {
    val session = c.startSession
    try f(session)
    finally session.close
  }

  def execReturnCode(session: Session, cde: Command) = {
    val cmd = session.exec(cde.toString)
    try {
      cmd.join
      cmd.getExitStatus
    } finally cmd.close
  }

  def execReturnCodeOutput(session: Session, cde: Command) = {
    val cmd = session.exec(cde.toString)
    try {
      cmd.join
      (cmd.getExitStatus, IOUtils.readFully(cmd.getInputStream).toString, IOUtils.readFully(cmd.getErrorStream).toString)
    } finally cmd.close
  }

  def exec(session: Session, cde: Command) = {
    val retCode = execReturnCode(session, cde)
    if (retCode != 0) throw new RuntimeException("Return code was no 0 but " + retCode)
  }

  def exception(ret: Int, command: String, output: String, error: String) = new RuntimeException(s"Unexpected return code $ret, when running $command (stdout=$output, stderr=$error")

}

import SSHJobService._

trait SSHJobService extends JobService with SSHHost with SSHStorage with BashShell { js ⇒
  type J = String
  type D = SSHJobDescription

  def bufferSize = 65535

  def submit(description: D): J = {
    val jobId = UUID.randomUUID.toString
    val command = new ScriptBuffer

    def absolute(path: String) = "$HOME/" + path

    command += "mkdir -p " + absolute(rootDir)
    command += "mkdir -p " + description.workDirectory
    command += "cd " + description.workDirectory

    val executable = description.executable + " " + description.arguments

    val jobDir =
      command += "((" +
        executable +
        " > " + absolute(outFile(jobId)) + " 2> " + absolute(errFile(jobId)) + " ; " +
        " echo $? > " + absolute(endCodeFile(jobId)) + ") & " +
        "echo $! > " + absolute(pidFile(jobId)) + " )"

    withConnection(withSession(_) { exec(_, command.toString) })
    jobId
  }

  def state(job: J): JobState =
    if (exists(endCodeFile(job))) {
      val is = openInputStream(endCodeFile(job))
      val content =
        try getBytes(is, bufferSize, timeout)
        finally is.close

      translateState(new String(content).takeWhile(_.isDigit).toInt)
    } else Running

  def cancel(jobId: J) = withConnection(withSession(_) {
    s ⇒
      val cde = s"kill `cat ${pidFile(jobId)}`;"
      exec(s, cde)
  })

  def purge(jobId: String) = withConnection(withSession(_) {
    s ⇒
      val cde = s"rm -rf $rootDir/$jobId*"
      exec(s, cde)
  })

  private def translateState(retCode: Int) =
    if (retCode >= 0)
      if (retCode == PROCESS_CANCELED) Failed
      else if (retCode == COMMAND_NOT_FOUND) Failed
      else Done
    else Failed

}
