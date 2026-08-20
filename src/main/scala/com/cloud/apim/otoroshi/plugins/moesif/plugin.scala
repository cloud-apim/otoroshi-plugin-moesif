package otoroshi_plugins.com.cloud.apim.otoroshi.plugins.moesif

import otoroshi.env.Env
import otoroshi.events.DataExporter.DefaultDataExporter
import otoroshi.events.{CustomDataExporter, CustomDataExporterContext, ExportResult}
import otoroshi.models.DataExporterConfig
import otoroshi.next.plugins.api.{NgPluginCategory, NgPluginConfig, NgPluginVisibility, NgStep}
import otoroshi.utils.syntax.implicits.*
import play.api.libs.json.{Format, JsArray, JsError, JsObject, JsResult, JsSuccess, JsValue, Json}
import play.api.libs.ws.WSBodyWritables.given

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class NgMoesifValuesConfig(applicationId: String = "", customerKeyName: String =  "", companyKeyName: String = "", moesifActionName: String = "") extends NgPluginConfig {
  def json: JsValue = NgMoesifValuesConfig.format.writes(this)
}

// User can customize the location of the company and the customer key (in the metadata, tags) of the apikey
// He also can modify the action name displayed in Moesif
object NgMoesifValuesConfig {
  given format: Format[NgMoesifValuesConfig] = new Format[NgMoesifValuesConfig] {
    override def reads(json: JsValue): JsResult[NgMoesifValuesConfig] = Try {
      NgMoesifValuesConfig(
        applicationId =  json.select("app_id").asOpt[String].getOrElse(""),
        customerKeyName =  json.select("customer_key").asOpt[String].getOrElse(""),
        companyKeyName =  json.select("company_key").asOpt[String].getOrElse(""),
        moesifActionName =  json.select("action_name").asOpt[String].getOrElse("Calling moesif from CLOUD-APIM")
      )
    } match {
      case Failure(e) => JsError(e.getMessage)
      case Success(c) => JsSuccess(c)
    }
    override def writes(o: NgMoesifValuesConfig): JsValue =
      Json.obj(
        "app_id" -> o.applicationId,
        "customer_key" -> o.customerKeyName,
        "company_key" -> o.companyKeyName,
        "action_name" -> o.moesifActionName
      )
  }
}

extension (json: JsValue) {
  private def isGatewayEvent: Boolean                    = json.select("@type").asOpt[String].contains("GatewayEvent")
  private def stringAtPath(path: String): Option[String] = json.atPath(path).asOpt[String]
}

class InternalMoesifDataExporter(config: DataExporterConfig, internalConfig: JsValue)(using ec: ExecutionContext, env: Env) extends DefaultDataExporter(config) {

  private val moesifConfig = NgMoesifValuesConfig.format.reads(internalConfig).getOrElse(NgMoesifValuesConfig())
  private val MOESIF_BATCH_API_URL =  "https://api.moesif.net/v1/actions/batch"

  // an event is turned into a moesif action only if it is a gateway event issued by a known apikey
  // for which every configured field can be resolved
  private def moesifActionFor(event: JsValue): Option[JsObject] = {
    if (!event.isGatewayEvent) {
      None
    } else {
      for {
        identity  <- event.stringAtPath("$.identity.identity")
        apikey    <- env.proxyState.apikey(identity)
        apikeyJson = apikey.toJson
        userId    <- apikeyJson.stringAtPath(moesifConfig.customerKeyName)
        companyId <- apikeyJson.stringAtPath(moesifConfig.companyKeyName)
        urlScheme <- event.stringAtPath("$.to.scheme")
        urlHost   <- event.stringAtPath("$.to.host")
        urlTarget <- event.stringAtPath("$.target.uri")
      } yield Json.obj(
        "action_name" -> moesifConfig.moesifActionName,
        "user_id"     -> userId,
        "company_id"  -> companyId,
        "request"     -> Json.obj("uri" -> s"${urlScheme}://${urlHost}${urlTarget}")
      )
    }
  }

  override def send(events: Seq[JsValue]): Future[ExportResult] = {
    val moesifBatch = events.flatMap(moesifActionFor)

    // Send the request to moesif only if the bacth is not empty and the application ID is filled
    if (moesifConfig.applicationId.nonEmpty && moesifBatch.nonEmpty) {
      env.Ws
        .url(MOESIF_BATCH_API_URL)
        .withMethod("POST")
        .withHttpHeaders(
          env.Headers.OtoroshiClientId     -> env.clusterConfig.leader.clientId,
          env.Headers.OtoroshiClientSecret -> env.clusterConfig.leader.clientSecret,
          "Content-Type"                   -> "application/json",
          "Accept"                         -> "application/json",
          "X-Moesif-Application-Id" -> moesifConfig.applicationId
        )
        .withBody(JsArray(moesifBatch))
        .execute()
        .map { resp =>
          if (resp.status == 201) {
            env.logger.info(s"MOESIF DATA EXPORTER REQUEST SUCCESS  ${resp.status} ${resp.headers}")
            ExportResult.ExportResultSuccess
          } else {
            env.logger.info(
              s"error while fetching MOESIF URL ' - ${resp.status} - ${resp.headers} - ${resp.body}"
            )
            ExportResult.ExportResultFailure("Fail to send moesif data")
          }
        }
    } else {
      ExportResult.ExportResultFailure("Fail to send moesif data").future
    }
  }

  def onStart(): Unit = {
    env.logger.info("[Cloud APIM] the 'Moesif' plugin is available !")
  }

  def onStop(): Unit = {
    env.logger.info("[Cloud APIM] Stopping the 'Moesif' plugin !")
  }

}

class MoesifDataExporter extends CustomDataExporter {

  private val ref = new AtomicReference[InternalMoesifDataExporter]()

  override def categories: Seq[NgPluginCategory] = Seq(NgPluginCategory.Custom("Cloud APIM"))
  override def steps: Seq[NgStep]                = Seq.empty
  override def visibility: NgPluginVisibility    = NgPluginVisibility.NgUserLand
  override def core: Boolean                     = false

  override def name: String                                = "Moesif"
  override def description: Option[String]                 = "This exporter send otoroshi event to moesif".some


//  override def configSchema: Option[JsObject] = Some(Json.obj(
//    "app_id" -> Json.obj(
//      "type" -> "string",
//      "label" -> "Moesif Application ID",
//    ),
//    "customer_key" -> Json.obj(
//      "type" -> "string",
//      "label" -> "Customer key",
//    ),
//    "company_key" -> Json.obj(
//      "type" -> "string",
//      "label" -> "Company key",
//    )
//  ))

  override def accept(event: JsValue, ctx: CustomDataExporterContext)(using env: Env): Boolean = {
    ref.get().accept(event)
  }

  override def project(event: JsValue, ctx: CustomDataExporterContext)(using env: Env): JsValue = {
    ref.get().project(event)
  }

  override def send(events: Seq[JsValue], ctx: CustomDataExporterContext)(using ec: ExecutionContext, env: Env): Future[ExportResult] = {
    ref.get().send(events)
  }

  override def startExporter(ctx: CustomDataExporterContext)(using ec: ExecutionContext, env: Env): Future[Unit] = {
    ref.set(new InternalMoesifDataExporter(ctx.exporter.configUnsafe, ctx.config))
    ref.get().onStart()
    ().vfuture
  }

  override def stopExporter(ctx: CustomDataExporterContext)(using ec: ExecutionContext, env: Env): Future[Unit] = {
    ref.get().onStop()
    ().vfuture
  }
}
