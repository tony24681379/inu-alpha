package frontend

import common.ImplicitPrint._
import org.elasticsearch.action.search.SearchResponse
import spray.http.Uri
import scala.language.implicitConversions
import scalaz._, Scalaz._
import scalaz.Ordering

object Pagination {

  implicit def extractHitsOfTotal(r: SearchResponse): Long = r.getHits.totalHits()
}

case class Pagination(size: Int, from: Int, totals: Long = 0)(implicit uri: spray.http.Uri) {

  import UriImplicitConversions._

  private val next: Long = from + size
  private val previous = from - size

  lazy val linkOfNext  = next ?|? totals match {
    case scalaz.Ordering.LT => Some(s"""{"prompt" : "Next", "rel" : "next", "href" : "${uri.withExistQuery(("from", s"$next"), ("size", s"$size"))}", "render" : "link"}""")
    case _ => None
  }

  lazy val linkOfPrevious = previous ?|? 0 match {
    case Ordering.GT | Ordering.EQ => Some(s"""{"prompt" : "Previous", "rel" : "previous", "href" : "${uri.withExistQuery(("from", s"$previous"), ("size", s"$size"))}", "render" : "link"}""")
    case _ => None
  }

  lazy val links: Seq[String] = Seq(linkOfNext, linkOfPrevious).flatten
}


object UriImplicitConversions {

  implicit class Uri0(uri: Uri) {
    def appendToValueOfKey(key: String)(value: String): Uri = {
      s"${uri.query.get(key).getOrElse("")} $value".trim match {
        case "" => uri
        case appended =>
          uri.withQuery(uri.query.toMap + (key -> appended))
      }
    }

    def withExistQuery(kvp: (String, String)*): Uri = {
      uri.withQuery(uri.query.toMap ++ kvp)
    }

    def drop(keys: String*) = {
      uri.withQuery(keys.foldLeft(uri.query.toMap)(_ - _))
    }
  }
}