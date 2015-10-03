package es.indices

import org.elasticsearch.client.Client

object logs {

  def putIndexTemplate(implicit client: Client) =
    client.admin().indices().preparePutTemplate("template1")
      .setSource(
        s"""{
          | "template" : "logs-*",
          | "mappings" : { $ytx }
          |}""".stripMargin).execute()


  private val ytx = """
    |"ytx": {
    |        "_source": {
    |          "enabled": true
    |        },
    |        "dynamic": "dynamic",
    |        "dynamic_templates": [
    |          {
    |            "agent": {
    |              "mapping": {
    |                "analyzer": "whitespace",
    |                "type": "string"
    |              },
    |              "match_mapping_type": "string",
    |              "match": "agent*"
    |            }
    |          },
    |          {
    |            "customer": {
    |              "mapping": {
    |                "analyzer": "whitespace",
    |                "type": "string"
    |              },
    |              "match_mapping_type": "string",
    |              "match": "customer*"
    |            }
    |          }
    |        ],
    |        "dynamic_date_formats" : ["YYYY-MM-dd hh:mm:ss"],
    |        "_all": {
    |          "enabled": false
    |        },
    |        "properties": {
    |          "path": {
    |            "index": "not_analyzed",
    |            "type": "string"
    |          },
    |          "vtt": {
    |           "analyzer": "whitespace",
    |            "type": "string"
    |          },
    |          "parties": {
    |            "type": "string"
    |          },
    |          "dialogs": {
    |           "analyzer": "whitespace",
    |            "type": "string"
    |          },
    |          "agentTeamName": {
    |            "type": "string",
    |            "index": "not_analyzed"
    |          },
    |          "custGrade": { "type": "string", "index": "not_analyzed" },
    |          "gameType": { "type": "string", "index": "not_analyzed" },
    |          "recordRang": { "type": "long" },
    |          "recordTime": { "type": "date" }
    |        }
    |      }
  """.stripMargin


  def getTemplate(implicit client: Client) =
    client.admin()
      .indices()
      .prepareGetTemplates("template1")
      .execute()
}