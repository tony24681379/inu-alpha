package domain

import akka.actor._
import akka.contrib.pattern.ClusterReceptionistExtension
import akka.persistence._
import akka.util.Timeout
import algorithm.TopologicalSort._
import com.sksamuel.elastic4s.ElasticClient
import scala.concurrent.duration._
import util.ImplicitActorLogging

import scala.util.{Try, Success, Failure}


object StoredQueryAggregateRoot {

  val storedQueryAggregateRootSingleton = "/user/stored-query-aggregate-root/active"

  sealed trait Event

  sealed trait State

  sealed trait Command

  case class ClauseAddedAck(clauseId: String)

  case object UpdatedAck

  case object ClausesRemovedAck

  case class ItemCreated(entity: StoredQuery, dependencies: Map[(String, String), Int]) extends Event

  case class ItemsChanged(items: Map[String, StoredQuery], changes: List[String], dependencies: Map[(String, String), Int]) extends Event

  case class ChangesRegistered(records: Set[(String, Int)]) extends Event

  sealed trait BoolClause {
    val occurrence: String
  }

  sealed trait UnalliedBoolClause extends BoolClause

  case class NamedBoolClause(storedQueryId: String,
                             title: String,
                             occurrence: String,
                             clauses: Map[Int, BoolClause] = Map.empty) extends BoolClause

  case class MatchBoolClause(query: String, operator: String, occurrence: String) extends UnalliedBoolClause

  case class SpanNearBoolClause(terms: List[String],
                                slop: Option[Int],
                                inOrder: Boolean, occurrence: String) extends UnalliedBoolClause

  case class AddClause(storedQueryId: String, clause: BoolClause) extends Command

  case class UpdateTags(storedQueryId: String, tags: Set[String])

  case class RemoveClauses(storedQueryId: String, specified: List[Int]) extends Command

  val temporaryId: String = "temporary"

  case class CreateNewStoredQuery(title: String, referredId: String) extends Command

  case class UpdateStoredQuery(storedQueryId: String, title: String, tags: Option[String]) extends Command


  case class StoredQuery(id: String = "", title: String = "", clauses: Map[Int, BoolClause] = Map.empty, tags: Set[String] = Set.empty)

  case object CycleInDirectedGraphError

  case class Active(items: Map[String, StoredQuery] = Map.empty,
                    clausesDependencies: Map[(String, String), Int] = Map.empty,
                    changes: Map[String, Int] = Map.empty) extends State {

    import algorithm.TopologicalSort._

    def CreateAcyclicClauseDependencies(consumer: String, provider: String, clauseId: Int) = {
      val source = clausesDependencies + ((consumer, provider) -> clauseId)
      Try(sort(toPredecessor(source.keys))) match {
        case Success(_) => Some(source)
        case Failure(_) => None
      }
    }

    def generateNewItemId: String = {
      val id = scala.math.abs(scala.util.Random.nextInt()).toString
      if (items.keys.exists(_ == id)) generateNewItemId else id
    }

    def generateNewClauseId(item: StoredQuery): Int = {
      val id = scala.math.abs(scala.util.Random.nextInt())
      if (item.clauses.keys.exists(_ == id)) generateNewClauseId(item) else id
    }

    def getItem(id: String): Option[StoredQuery] = items.get(id)

    def update(event: Event): Active = {

      event match {

        case ItemCreated(entity, dp) =>
          copy(items = items + (entity.id -> entity), clausesDependencies = dp,
            changes = changes + (entity.id -> (changes.getOrElse(entity.id, 0) + 1)))

        case ItemsChanged(xs, changesList,dp) =>
          copy(items = items ++ xs, clausesDependencies = dp, changes =
            changes ++ changesList.map { e => e -> (changes.getOrElse(e, 0) + 1) } - temporaryId)

        case ChangesRegistered(records) =>
          copy(changes = changes.toSet.diff(records).toMap)

      }
    }
  }

}

object StoredQueryItemsView {

  import StoredQueryAggregateRoot.{BoolClause}

  case class Query(text: Option[String], tags: Option[String])

  case class StoredQueryItem(title: String, tags: Option[String], status: Option[String]) {
    require( title.nonEmpty )
  }

  case class QueryResponse(items: Set[(String, StoredQueryItem)], tags: Set[String])

  case class GetItem(id: String)

  case class GetItemClauses(id: String, occurrence: String)

  case class ItemDetailResponse(id: String, item: StoredQueryItem)

  case class ItemClausesResponse(clauses: Map[Int, BoolClause])

  case class ItemNotFound(id: String)

  val storedQueryItemsViewSingleton = "/user/stored-query-items-view/active"
}

class StoredQueryItemsView extends PersistentView with ImplicitActorLogging {

  import StoredQueryAggregateRoot._
  import StoredQueryItemsView._

  override val viewId: String = "stored-query-aggregate-root-view"

  override val persistenceId: String = "stored-query-aggregate-root"

  var items: Map[String, StoredQuery] = Map(temporaryId -> StoredQuery(temporaryId, "temporary"))
  var queryResp = QueryResponse(Set.empty, Set.empty)

  ClusterReceptionistExtension(context.system).registerService(self)

  def receive: Receive = {
    case ItemCreated(entity, dp) if isPersistent =>
      log.info(s"${entity.id}, ${entity.title} was created.")
      items = items + (entity.id -> entity)
      queryResp = queryResp.copy(tags = queryResp.tags ++ entity.tags)

    case ItemsChanged(xs, changes , _) =>
      changes.foreach { id => log.info(s"$id, ${xs(id).title} was changed.")}
      items = items ++ xs
      queryResp = queryResp.copy(tags = queryResp.tags ++ xs.map { case (k,v)=> v.tags }.flatten)

    case GetItem(id) =>
      items.get(id) match {
        case Some(StoredQuery(id, title, _, tags)) =>
          sender() ! ItemDetailResponse(id, StoredQueryItem(title, Some(tags.mkString(" ")), Some("enabled")))
        case None =>
          sender() ! ItemNotFound(id)
      }

    case GetItemClauses(id, occurrence) =>
      items.get(id) match {
        case Some(StoredQuery(id, _, clauses, _)) =>
          sender() ! ItemClausesResponse(clauses.filter { case (clausesId, clause) => clause.occurrence == occurrence })

        case None =>
          sender() ! ItemNotFound(id)
      }

    case ChangesRegistered(records) =>
      log.info(s"$records were registered.")


    case Query(queryString, queryTags) =>

      import com.sksamuel.elastic4s.ElasticDsl._
      import context.dispatcher
      import akka.pattern._
      import util.ElasticSupport._
      import scala.collection.JavaConversions._

      implicit val timeout = Timeout(5.seconds)

      val queries = List(
        queryString.map { queryStringQuery(_) asfields "_all" },
        queryTags.map { matchQuery("tags", _) }
      )

      client.execute {
        (search in percolatorIndex -> ".percolator" query bool {
          must {
            queries.flatten
          }
        } fields ("title", "tags")).logInfo(_.toString)
      }.map { resp =>
        queryResp.copy(items = resp.logInfo(_.toString).hits.map { hit => hit.id ->
          StoredQueryItem(hit.field("title").value[String],
            hit.fieldOpt("tags").map { _.values().mkString(" ") },
            Some("enabled")) }.toSet ) }
        .recover {
        case ex =>
          ex.logError(_.toString)
          queryResp
      } pipeTo sender()
  }
}

class StoredQueryAggregateRoot extends PersistentActor with ActorLogging {

  import StoredQueryAggregateRoot._
  import StoredQueryPercolatorProtocol._

  val persistenceId: String = "stored-query-aggregate-root"

  var state: Active = Active(items = Map(temporaryId -> StoredQuery(temporaryId, "temporary")))

  ClusterReceptionistExtension(context.system).registerService(self)

  val receiveCommand: Receive = {

    case CreateNewStoredQuery(title, referredId) =>
      state.getItem(referredId) match {
        case Some(item) =>
          def afterPersisted(`sender`: ActorRef, evt: ItemCreated) = {
            state = state.update(evt)
            `sender` ! evt
          }

          val newItem = item.copy(id = state.generateNewItemId, title = title)

          val itemCreated = ItemCreated(newItem, state.clausesDependencies ++ item.clauses.flatMap {
            case (k, v: NamedBoolClause) => Some((newItem.id, v.storedQueryId) -> k)
            case (k, v) => None
          })

          persist(itemCreated)(afterPersisted(sender(), _))
        case None =>
          sender() ! s"$referredId is not exist."
      }

    case AddClause(storedQueryId, clause) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>
          val newClauseId: Int = state.generateNewClauseId(item)
          val zero = state.items + (storedQueryId -> item.copy(clauses = item.clauses + (newClauseId -> clause)))

          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! ClauseAddedAck(s"$newClauseId")
          }

          clause match {
            case NamedBoolClause(clauseStoredQueryId, title, _, _) =>
              state.CreateAcyclicClauseDependencies(storedQueryId, clauseStoredQueryId, newClauseId) match {
                case Some(dp) =>
                  persist(cascadingUpdate(storedQueryId, zero, dp))(afterPersisted(sender(), _))
                case None =>
                  sender() ! CycleInDirectedGraphError
              }
            case _ =>
              persist(cascadingUpdate(storedQueryId, zero, state.clausesDependencies))(afterPersisted(sender(), _))
          }
        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case RemoveClauses(storedQueryId, specified) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>

          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! ClausesRemovedAck
          }

          val xs = item.clauses.flatMap {
            case (k, v: NamedBoolClause) if specified.contains(k) => Some((storedQueryId, v.storedQueryId))
            case (k, v) => None
          }

          val zero = state.items + (storedQueryId -> item.copy(clauses = item.clauses -- specified))

          persist(cascadingUpdate(storedQueryId, zero, state.clausesDependencies -- xs))(afterPersisted(sender(), _))

        case None =>
          sender() ! s"$storedQueryId is not exist."
      }

    case UpdateStoredQuery(storedQueryId, newTitle, newTags) =>
      state.getItem(storedQueryId) match {
        case Some(item) =>

          def afterPersisted(`sender`: ActorRef, evt: ItemsChanged) = {
            state = state.update(evt)
            `sender` ! UpdatedAck
          }

          val updateItem = storedQueryId -> item.copy(
                                                title = newTitle,
                                                tags = newTags.map { _.split(" ").toSet }.getOrElse(item.tags))
          val itemsChanged = if(item.title != newTitle) cascadingUpdate(storedQueryId, state.items + updateItem, state.clausesDependencies) else ItemsChanged(Map(updateItem), List(storedQueryId), state.clausesDependencies)

          persist(itemsChanged)(afterPersisted(sender(), _))
        case None =>
      }


    case Pull =>
      val items = (state.changes - temporaryId).map { case (k,v) => (state.items(k),v) }.toSet
      if(items.nonEmpty)
        sender() ! Changes(items)

    case RegisterQueryOK(records) =>
      persist(ChangesRegistered(records)){ evt =>
        state = state.update(evt)
        log.info(s"remains: ${state.changes.mkString(",")}")
      }
  }

  def cascadingUpdate(from: String, items: Map[String, StoredQuery], dp: Map[(String, String), Int]) = {

    val zero = (items, List(from))

    val (updatedItems, changesList) = collectPaths(from)(toPredecessor(dp.keys)).flatten.foldLeft(zero) { (acc, link) => {
      val (provider, consumer) = link
      val (accItems, changes) = acc
      val clauseId = dp((consumer, provider))
      val updatedNamedBoolClause = accItems(consumer).clauses(clauseId).asInstanceOf[NamedBoolClause]
        .copy(clauses = accItems(provider).clauses)
      val updatedConsumer = accItems(consumer).copy(clauses = accItems(consumer).clauses + (clauseId -> updatedNamedBoolClause))
      (accItems + (consumer -> updatedConsumer), consumer :: changes)
    } }

    ItemsChanged(updatedItems, changesList, dp)
  }

  val receiveRecover: Receive = {
    case evt: Event =>
      state = state.update(evt)
    case SnapshotOffer(_, snapshot: State) =>
  }
}
