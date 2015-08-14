package elastics

import akka.actor.Actor
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.mappings.DynamicMapping._
import com.sksamuel.elastic4s.mappings.FieldType._
import com.sksamuel.elastic4s.mappings.{TermVector, DynamicTemplateDefinition, StringFieldDefinition}
import com.sksamuel.elastic4s.{WhitespaceAnalyzer, QueryDefinition, ElasticClient}

object LteTemplate {

  object fields {

    val agent = new DynamicTemplateDefinition("agent") matching "agent*" matchMappingType "string" mapping {
      field typed StringType indexAnalyzer "ik_stt_analyzer" searchAnalyzer WhitespaceAnalyzer
    }
    val customer = new DynamicTemplateDefinition("customer") matching "customer*" matchMappingType "string" mapping {
      field typed StringType indexAnalyzer "ik_stt_analyzer" searchAnalyzer WhitespaceAnalyzer
    }

    val vtt: StringFieldDefinition = "vtt" typed StringType

    val dialogs: StringFieldDefinition = "dialogs" typed StringType indexAnalyzer "ik_stt_analyzer" searchAnalyzer WhitespaceAnalyzer

    val parties: StringFieldDefinition = "parties" typed StringType
  }
  
  object mappings {
    import fields._
    lazy val default =
      mapping("_default_") as Seq(
        vtt,
        "path" typed StringType index "not_analyzed",
        dialogs
      ) all false source true dynamic Dynamic templates(agent, customer)
  }
}

trait LteTemplate extends util.ImplicitActorLogging{
  self: Actor ⇒

  lazy val `PUT _template/lte` = {
    import LteTemplate.mappings._
    import context.dispatcher

    client.execute { create template "lte" pattern "lte*" mappings { default } }.map { ("PUT _template/lte", _) }
  }
  
  def client: ElasticClient
}




