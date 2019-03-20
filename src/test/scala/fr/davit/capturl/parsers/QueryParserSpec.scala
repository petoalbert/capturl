package fr.davit.capturl.parsers

import fr.davit.capturl.parsers.ParserFixture.TestParser
import fr.davit.capturl.scaladsl.Query
import org.parboiled2.ParserInput
import org.scalatest.{FlatSpec, Matchers}

class QueryParserSpec extends FlatSpec with Matchers {

  trait Fixture extends ParserFixture[Query] {
    override def createParser(input: ParserInput) = new TestParser[Query](input) with QueryParser {
      override def rule = iquery
    }
  }

  "QueryParser" should "parse query" in new Fixture {
    parse("key1=val1?key2=val2#fragment") shouldBe Query("key1=val1?key2=val2") -> "#fragment"
  }

}
