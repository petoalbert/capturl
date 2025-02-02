package fr.davit.capturl.akka.http

import java.net.IDN

import akka.http.scaladsl.model.Uri
import fr.davit.capturl.scaladsl.Authority.{Port, UserInfo}
import fr.davit.capturl.scaladsl.Path.{Empty, Segment, Slash, SlashOrEmpty}
import fr.davit.capturl.scaladsl._

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

trait UriConverters {

  implicit def fromAkkaScheme(scheme: String): Scheme = scheme match {
    case "" => Scheme.Empty
    case _  => Scheme.Protocol(scheme)
  }

  implicit def toAkkaScheme[T <: Scheme](scheme: T): String = scheme.toString

  implicit def fromAkkaHost(host: Uri.Host): Host = host match {
    case Uri.Host.Empty         => Host.Empty
    case Uri.IPv4Host(bytes, _) => Host.IPv4Host(bytes)
    case Uri.IPv6Host(bytes, _) => Host.IPv6Host(bytes)
    case Uri.NamedHost(address) => Host.NamedHost(IDN.toUnicode(address))
  }

  implicit def toAkkaHost[T <: Host](host: Host): Uri.Host = host match {
    case Host.Empty              => Uri.Host.Empty
    case Host.IPv4Host(bytes)    => Uri.IPv4Host(bytes.toArray)
    case Host.IPv6Host(bytes)    => Uri.IPv6Host(bytes)
    case Host.NamedHost(address) => Uri.NamedHost(IDN.toASCII(address))
  }

  implicit def fromAkkaUserInfo(userInfo: String): UserInfo = userInfo match {
    case "" => UserInfo.Empty
    case _  => UserInfo.Credentials(userInfo)
  }

  implicit def toAkkaUserInfo[T <: UserInfo](userInfo: T): String = userInfo match {
    case UserInfo.Empty              => ""
    case UserInfo.Credentials(value) => value
  }

  implicit def fromAkkaPort(port: Int): Port = port match {
    case 0 => Port.Empty
    case _ => Port.Number(port)
  }

  implicit def toAkkaPort[T <: Port](port: T): Int = port match {
    case Port.Empty         => 0
    case Port.Number(value) => value
  }

  implicit def fromAkkaAuthority(authority: Uri.Authority): Authority = {
    Authority(authority.host, authority.port, authority.userinfo)
  }

  implicit def toAkkaAuthority(authority: Authority): Uri.Authority = {
    Uri.Authority(authority.host, authority.port, authority.userInfo)
  }

  implicit def fromAkkaPath(path: Uri.Path): Path = {
    @tailrec def pathBuilder(akkaPath: Uri.Path, p: SlashOrEmpty): Path = akkaPath match {
      case Uri.Path.Empty                                  => p
      case Uri.Path.Slash(tail)                            => pathBuilder(tail, Slash(p))
      case Uri.Path.Segment(segment, Uri.Path.Empty)       => Segment(segment, p)
      case Uri.Path.Segment(segment, Uri.Path.Slash(tail)) => pathBuilder(tail, Slash(Segment(segment, p)))
    }

    pathBuilder(path.reverse, Path.Empty)
  }

  implicit def toAkkaPath(path: Path): Uri.Path = {
    @tailrec def akkaPathBuilder(p: Path, akkaPath: Uri.Path.SlashOrEmpty): Uri.Path = p match {
      case Empty                         => akkaPath
      case Slash(tail)                   => akkaPathBuilder(tail, Uri.Path.Slash(akkaPath))
      case Segment("", tail)             => akkaPathBuilder(tail, akkaPath)
      case Segment(segment, Empty)       => Uri.Path.Segment(segment, akkaPath)
      case Segment(segment, Slash(tail)) => akkaPathBuilder(tail, Uri.Path.Slash(Uri.Path.Segment(segment, akkaPath)))
    }
    akkaPathBuilder(path.reverse, Uri.Path.Empty)
  }

  implicit def fromAkkaQuery(query: Uri.Query): Query = {
    val b = Query.newBuilder
    query.foreach { case (k, v) => b.+=((k, if (v.isEmpty) None else Some(v))) }
    b.result()
  }

  implicit def toAkkaQuery[T <: Query](query: T): Uri.Query = {
    val b = Uri.Query.newBuilder
    query.foreach { case (k, v) => b += k -> v.getOrElse(Uri.Query.EmptyValue) }
    b.result()
  }

  implicit def toAkkaQueryString[T <: Query](query: T): Option[String] = query match {
    case Query.Empty => None
    case _           => Some(toAkkaQuery(query).toString) // force conversion to Uri.Query for the encoding
  }

  implicit def fromAkkaFragment(fragment: Option[String]): Fragment = fragment match {
    case None           => Fragment.Empty
    case Some(fragment) => Fragment.Identifier(fragment)
  }

  implicit def toAkkaFragment[T <: Fragment](fragment: T): Option[String] = fragment match {
    case Fragment.Empty             => None
    case Fragment.Identifier(value) => Some(value)
  }

  implicit def fromAkkaUri(uri: Uri): Iri = {
    Try(uri.query()) match {
      case Success(q) =>
        StrictIri(uri.scheme, uri.authority, uri.path, q, uri.fragment)
      case Failure(_) =>
        LazyIri(
          Iri.rawString(uri.scheme),
          Iri.rawString(uri.authority.toString),
          Iri.rawString(uri.path.toString),
          uri.rawQueryString,
          uri.fragment
        )
    }

  }

  implicit def toAkkaUri(iri: Iri): Uri = iri match {
    case StrictIri(s, a, p, q, f) => Uri(s, a, p, q, f)
    case lazyIri @ LazyIri(_, _, _, rq, rf) =>
      val uriResult = for {
        s <- lazyIri.schemeResult
        a <- lazyIri.authorityResult
        p <- lazyIri.pathResult
      } yield {
        // query and fragment are 'lazyly' parsed in akka Uri
        // in case of non RFC compliant, create the Uri with the raw string
        val q = rq.map(lazyIri.queryResult.toOption.flatMap(toAkkaQueryString).getOrElse(_))
        val f = rf.map(lazyIri.fragmentResult.toOption.flatMap(toAkkaFragment).getOrElse(_))
        Uri(s, a, p, q, f)
      }
      uriResult.get
  }

}

object UriConverters extends UriConverters
