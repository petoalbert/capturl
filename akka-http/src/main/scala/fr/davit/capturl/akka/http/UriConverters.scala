package fr.davit.capturl.akka.http

import java.net.IDN

import akka.http.scaladsl.model.Uri
import fr.davit.capturl.scaladsl.Authority.{Port, UserInfo}
import fr.davit.capturl.scaladsl._

trait UriConverters {

  implicit def schemeConverter[T <: Scheme](scheme: T): String = scheme.toString

  implicit def hostConverter[T <: Host](host: Host): Uri.Host = host match {
    case Host.Empty              => Uri.Host.Empty
    case Host.IPv4Host(bytes)    => Uri.IPv4Host(bytes.toArray)
    case Host.IPv6Host(bytes)    => Uri.IPv6Host(bytes)
    case Host.NamedHost(address) => Uri.NamedHost(IDN.toASCII(address))
  }

  implicit def userinfoConverter[T <: UserInfo](userInfo: T): String = userInfo match {
    case UserInfo.Empty              => "" // akka doesn't differentiate no user info from empty userinfo
    case UserInfo.Credentials(value) => value
  }

  implicit def portConverter[T <: Port](port: T): Int = port match {
    case Port.Empty         => 0 // akka doesn't differentiate no port and port 0
    case Port.Number(value) => value
  }

  implicit def authorityConverter(authority: Authority): Uri.Authority = {
    Uri.Authority(authority.host, authority.port, authority.userInfo)
  }

  implicit def pathConverter[T <: Path](path: T): Uri.Path = {
    path.foldRight(Uri.Path.Empty: Uri.Path) {
      case ("/", p)                        => Uri.Path.Slash(p)
      case (str, p: Uri.Path.SlashOrEmpty) => Uri.Path.Segment(str, p)
      case _                               => throw new Exception("Invalid path construction")
    }
  }

  implicit def queryConverter[T <: Query](query: T): Uri.Query = {
    val b = Uri.Query.newBuilder
    query.foreach { case (k, v) => b += k -> v.getOrElse("") } // akka doesn't differentiate no value from empty value
    b.result()
  }

  implicit def queryStringConverter[T <: Query](query: T): Option[String] = query match {
    case Query.Empty => None
    case _           => Some(query.toString)
  }

  implicit def fragmentConverter[T <: Fragment](fragment: T): Option[String] = fragment match {
    case Fragment.Empty             => None
    case Fragment.Identifier(value) => Some(value) // for empty identifier, akka will remove the fragment
  }

  implicit def iriConverter(iri: Iri): Uri = {
    Uri(iri.scheme, iri.authority, iri.path, iri.query, iri.fragment)
  }

}

object UriConverters extends UriConverters
