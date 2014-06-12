/*
 * Copyright (C) 2014 Romain Reuillon
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

package fr.iscpif.gridscale.glite.services

import fr.iscpif.gridscale.libraries.wmsstub._
import scalaxb.HttpClients
import java.net.URI
import fr.iscpif.gridscale.globushttp.{ FixedAddressSocketCache, SimpleSocketFactory, GlobusHttpClient }
import fr.iscpif.gridscale.glite.GlobusAuthentication

object WMSService {

  def apply(uri: URI, _proxy: GlobusAuthentication.Proxy, _timeout: Int) =
    new WMSService {
      @transient lazy val httpClient: HttpClient = new HttpClient with GlobusHttpRequest with SimpleSocketFactory with FixedAddressSocketCache {
        val proxyBytes = _proxy.proxyBytes
        val timeout = _timeout
      }
      override def baseAddress = uri
    }.service

}

trait WMSService <: WMProxyBindings with scalaxb.Soap11Clients with HttpClients
