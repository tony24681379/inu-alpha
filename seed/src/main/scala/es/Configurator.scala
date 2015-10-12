package es

import akka.actor.Status.Failure
import akka.actor.{Props, ActorLogging, Actor}
import elastic.ImplicitConversions._
import es.indices._
import akka.pattern._
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingResponse
import org.elasticsearch.action.admin.indices.template.put.PutIndexTemplateResponse
import org.elasticsearch.client.Client

case object IndexScan
case class StoredQueryMappingResponse(responses: Seq[PutMappingResponse])

object Configurator {
  def props(implicit client: Client) = Props(classOf[es.Configurator], client)
}

/* node.client().admin().cluster().prepareClusterStats().execute().asFuture.onComplete {
       case Success(x) => system.log.info(s"data-node status: ${x.getStatus}")
       case Failure(e) => system.log.error(e, s"Unable to run elasticsearch data-node")
     }*/
/**
 * Created by henry on 9/24/15.
 */
class Configurator(implicit val client: Client) extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case IndexScan =>
      storedQuery.exists.execute().asFuture.map(_.isExists).flatMap {
        case false => storedQuery.create.asFuture
        case true =>
          log.info(s"${storedQuery.index} exists")
          for {
            r1 <- storedQuery.mapping.asFuture
            r2 <- storedQuery.putSourceMapping("ytx").asFuture
          } yield StoredQueryMappingResponse(Seq(r1,r2))

      } pipeTo self

      logs.putIndexTemplate.asFuture pipeTo self

    case r: CreateIndexResponse if r.isAcknowledged =>
      log.info(s"index created")
      sender ! IndexScan

    case r: StoredQueryMappingResponse =>
      log.info(s"$r")

    case r: PutMappingResponse if r.isAcknowledged =>
      log.info(s"mapping updated")

    case r: PutIndexTemplateResponse if r.isAcknowledged =>
      log.info(s"indexTemplate updated")
      
    case Failure(ex) =>
      log.error(ex ,s"elasticsearch checkup error")
    case unknown =>
      println(unknown.getClass.getName)

  }

}
