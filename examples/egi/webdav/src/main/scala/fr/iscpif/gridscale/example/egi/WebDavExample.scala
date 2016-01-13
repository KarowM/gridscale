/*
 * Copyright (C) 2015 Romain Reuillon
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
package fr.iscpif.gridscale.example.egi

import java.io.{ FileOutputStream, File }

import fr.iscpif.gridscale.authentication._
import fr.iscpif.gridscale.http._
import fr.iscpif.gridscale.egi._

import scala.concurrent.duration._
import scala.io.Source
import scala.util.{ Success, Failure, Try }

object WebDavExample extends App {

  val location = new BDII("ldap://topbdii.grif.fr:2170").queryWebDAVLocations("vo.complex-systems.eu", 1 minute).find(_.host.contains("lal")).get

  VOMSAuthentication.setCARepository(new File("/home/reuillon/.openmole/simplet/CACertificates"))

  val p12 = P12Authentication(new File("/home/reuillon/.globus/certificate.p12"), "password")
  val authentication = P12VOMSAuthentication(p12, 24 hours, "voms://voms.hellasgrid.gr:15160/C=GR/O=HellasGrid/OU=hellasgrid.gr/CN=voms.hellasgrid.gr", "vo.complex-systems.eu")

  val dav = DPMWebDAVStorage(location)(authentication)

  def dir = s"testDirectory"
  println(Try(dav.rmDir(dir)))
  dav.makeDir(dir)

  for (i ← (0 to 9999).par) {
    val testFile = s"$dir/testdav$i.txt"

    Try {
      val out = dav.openOutputStream(testFile)
      try out.write("Life is great\n".getBytes)
      finally out.close

      val in = dav.openInputStream(testFile)
      try assert(Source.fromInputStream(in).mkString == "Life is great\n", "File content is not right")
      finally in.close

    } match {
      case Failure(e) ⇒ println(s"Failed $testFile $e")
      case Success(_) ⇒ println(s"Written $testFile")
    }
  }

}
