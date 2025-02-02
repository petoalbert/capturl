package fr.davit.capturl.scaladsl

import java.util.Optional

import fr.davit.capturl.javadsl
import fr.davit.capturl.parsers.AuthorityParser
import fr.davit.capturl.scaladsl.Authority.{Port, UserInfo}
import fr.davit.capturl.scaladsl.OptionalPart.{DefinedPart, EmptyPart}

import scala.compat.java8.OptionConverters._
import scala.util.{Success, Try}

final case class Authority(host: Host = Host.empty, port: Port = Port.empty, userInfo: UserInfo = UserInfo.empty)
    extends javadsl.Authority {
  def isEmpty: Boolean  = host.isEmpty
  def nonEmpty: Boolean = !isEmpty

  def withHost(host: Host): Authority            = copy(host = host)
  override def withHost(host: String): Authority = withHost(Host(host))

  def withPort(port: Port): Authority         = copy(port = port)
  override def withPort(port: Int): Authority = withPort(Port.Number(port))

  def withUserInfo(userInfo: UserInfo): Authority        = copy(userInfo = userInfo)
  override def withUserInfo(userInfo: String): Authority = withUserInfo(UserInfo(userInfo))

  /** Java API */
  override def getHost: javadsl.Host         = host
  override def getPort: Optional[Integer]    = port.toOption.map(p => p: Integer).asJava
  override def getUserInfo: Optional[String] = userInfo.toOption.asJava
  override def asScala(): Authority          = this

  override def toString: String = {
    val b = new StringBuilder()
    if (userInfo.nonEmpty) b.append(s"$userInfo@")
    if (host.nonEmpty) b.append(host.address)
    if (port.nonEmpty) b.append(s":$port")
    b.toString
  }
}

object Authority {

  val empty: Authority = Authority()

  def apply(authority: String): Authority = parse(authority).get

  def parse(authority: String): Try[Authority] = {
    AuthorityParser(authority).phrase(_.iauthority)
  }

  trait Port extends OptionalPart[Int]

  object Port {
    val MaxPortNumber = 65535

    val empty: Port = Empty

    def apply(port: Int): Port = port match {
      case 0 => Empty
      case _ => Number(port)
    }

    case object Empty extends Port with EmptyPart
    final case class Number(value: Int) extends Port with DefinedPart[Int] {
      require(0 < value && value < MaxPortNumber, s"Invalid port number '$value'")
    }
  }

  trait UserInfo extends OptionalPart[String]

  object UserInfo {
    val empty: UserInfo = Empty

    def apply(userInfo: String): UserInfo = parse(userInfo).get

    def parse(userInfo: String): Try[UserInfo] = {
      if (userInfo.isEmpty) {
        Success(UserInfo.Empty)
      } else {
        AuthorityParser(userInfo).phrase(_.iuserinfo)
      }
    }

    case object Empty extends UserInfo with EmptyPart
    final case class Credentials(value: String) extends UserInfo with DefinedPart[String]
  }
}
