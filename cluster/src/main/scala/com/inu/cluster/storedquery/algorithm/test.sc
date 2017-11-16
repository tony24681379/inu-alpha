import com.inu.cluster.storedquery.StoredQueryRepoAggRoot.{BuildDependencies, CascadingUpdateOneByOne, CreateStoredQuery}
import com.inu.cluster.storedquery.algorithm.TopologicalSort
import com.inu.cluster.storedquery.algorithm.TopologicalSort.{collectPaths, toPredecessor}
import com.inu.cluster.storedquery.{CascadingUpdateGuide, StoredQueryRepo}
import com.inu.protocol.storedquery.messages._

val a = Map[String,String]("1"-> "5", "2"->"4", "3"-> "1","4"->"5")

val b = TopologicalSort.toPredecessor(a)

val c = TopologicalSort.append(b,("4"->"3"))

val d = TopologicalSort.sort(c)


import com.inu.cluster.storedquery.StoredQueryRepoAggRoot._

val stage0 = StoredQueries()

val stage1 = stage0.update(ItemCreated("0", "query0", None, Set("")))
val p = stage1.items
val stage2 = stage1.update(ItemCreated("1", "query1", None, Set("@archived")))
val CreateStoredQuery(aaaa) = (ItemCreated("2", "query1", Some("1"), Set("test")),StoredQueryRepo(stage2.items))
val stage3 = stage2.update(ItemCreated("2", "query2", None, Set("test2")))
val stage4 = stage3.update(ClauseAdded("0", (100, NamedClause("1", "query1", "must"))))
val stage5 = stage4.update(ClauseAdded("1", (200, NamedClause("2", "query1", "must"))))
val cycleClauseAdded = ClauseAdded("2", (100, NamedClause("0", "query0", "must")))

stage5.items
val test = stage4.testCycleInDirectedGraph(cycleClauseAdded)

//val bbb = BuildDependencies.acyclicProofing(Map[(String,String),Int](("1"-> "2",2),("2"->"1",1)))
val CreateStoredQuery(Right(create)) = (ItemCreated("0", "query0", None, Set("demo")),StoredQueryRepo(stage0.items))

val BuildDependencies(guides2,state2) = (create, stage0.copy(items = stage0.items.updated(create.id, create)))
val CascadingUpdateOneByOne(cccc,dddd) = (CascadingUpdateGuide(guides2), state2)

val UpdateClauses(Right(updatedStoredQuery)) = (ClauseAdded("1", (300, MatchClause("a b c", "dialogs", "AND", "must"))),StoredQueryRepo(stage4.items))
val BuildDependencies(guides,state) = (updatedStoredQuery, stage4.copy(items = stage4.items.updated(updatedStoredQuery.id, updatedStoredQuery)))
val CascadingUpdateOneByOne(ccc,ddd) = (CascadingUpdateGuide(guides), state)

val path = stage5.paths
val item = stage5.items
val change = stage5.changes

def acyclicProofing(dep: Map[(String, String), Int]): Option[Map[(String, String), Int]] = {
  import TopologicalSort._

  import scala.util._
  Try(sort(toPredecessor(dep.keys))) match {
    case Success(_) => Some(dep)
    case Failure(_) => None
  }
}
updatedStoredQuery.id

val newDep = updatedStoredQuery.clauses.flatMap {
  case (k, ref: NamedClause) => Some((updatedStoredQuery.id, ref.storedQueryId) -> k)
  case _ => None
}
val consumerPaths = stage4.paths.filterKeys({ case (consumer, _) => consumer == updatedStoredQuery.id })


val prof = (stage4.paths -- consumerPaths.keys ++ newDep)
acyclicProofing(prof).map{ p =>
  val guides = collectPaths[String](updatedStoredQuery.id)(toPredecessor(p.keys)).flatten.toList
  guides
}
val profpre = toPredecessor(prof.keys)
val coll = collectPaths[String]("5")(b)
val collflat = coll.flatten.toList

def retrieveDependencies(item: StoredQuery, items: Map[String, StoredQuery]): StoredQuery =
  item.clauses.foldLeft(item) { (acc, e) =>
    e match {
      case (clauseId, n: NamedClause) =>
        items.get(n.storedQueryId) match {
          case Some(innerItem) =>
            val attributedTitle = if (innerItem.archived)
              s"@archived ${innerItem.title}"
            else
              innerItem.title
            acc.copy(clauses = acc.clauses + (clauseId -> n.copy(storedQueryTitle = attributedTitle, clauses = Some(retrieveDependencies(innerItem,items).clauses))))
          case None =>
            println("{} doesn't exist", n)
            acc
        }
      case _ => acc
    }
  }

val itemArchived = stage5.items.get("0").get
retrieveDependencies(itemArchived, stage5.items)

itemArchived.clauses

val matchClause = MatchClause(query = "aa bb cc",field = "dialogs",occurrence = "should",operator = "OR")
