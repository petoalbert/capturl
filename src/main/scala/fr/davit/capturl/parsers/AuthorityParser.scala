package fr.davit.capturl.parsers

import fr.davit.capturl.scaladsl.Authority.{Port, UserInfo}
import fr.davit.capturl.scaladsl.{Authority, Host}
import org.parboiled2.CharPredicate._
import org.parboiled2.Rule1

import scala.util.{Success, Try}

object AuthorityParser {

  def apply(userInfo: String): StringParser with AuthorityParser = {
    new StringParser(userInfo) with AuthorityParser
  }
}

trait AuthorityParser extends HostParser { this: StringParser =>

  def port: Rule1[Port] = rule {
    atomic {
      capture(Digit.+) ~> { s: String =>
        Try(s.toInt) match {
          case Success(value) if value < Port.MaxPortNumber => push(Port.Number(value))
          case _                                            => failX("port")
        }
      }
    }
  }

  def iuserinfo: Rule1[UserInfo] = rule {
    atomic {
      clearSB() ~ (iunreserved | `pct-encoded` | `sub-delims` | ':' ~ appendSB()).* ~
        push(UserInfo.Credentials(sb.toString))
    }
  }

  def iauthority: Rule1[Authority] = rule {
    ((iuserinfo ~ '@').? ~ ihost ~ (':' ~ port).? ~> {
      (u: Option[UserInfo], h: Host, p: Option[Port]) => Authority(h, p.getOrElse(Port.empty), u.getOrElse(UserInfo.empty))
    })
  }
}
