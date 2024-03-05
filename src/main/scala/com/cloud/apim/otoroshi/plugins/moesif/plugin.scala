package otoroshi_plugins.com.cloud.apim.otoroshi.plugins.moesif

import otoroshi.env.Env
import otoroshi.events.DataExporter.DefaultDataExporter
import otoroshi.events.{CustomDataExporter, CustomDataExporterContext, ExportResult}
import otoroshi.models.DataExporterConfig
import otoroshi.next.plugins.api.{NgPluginCategory, NgPluginConfig, NgPluginVisibility, NgStep}
import otoroshi.utils.syntax.implicits._
import play.api.libs.json.{Format, JsError, JsResult, JsSuccess, JsValue, Json}

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class NgMoesifValuesConfig(applicationId: String = "", customerKeyName: String =  "", companyKeyName: String = "", moesifActionName: String = "") extends NgPluginConfig {
  def json: JsValue = NgMoesifValuesConfig.format.writes(this)
}

// User can customize the location of the company and the customer key (in the metadata, tags) of the apikey
// He also can modify the action name displayed in Moesif
object NgMoesifValuesConfig {
  val format = new Format[NgMoesifValuesConfig] {
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

class InternalMoesifDataExporter(config: DataExporterConfig, internalConfig: JsValue)(implicit ec: ExecutionContext, env: Env) extends DefaultDataExporter(config)(ec, env) {

  private val moesifDataExporterConfig = NgMoesifValuesConfig.format.reads(internalConfig).getOrElse(NgMoesifValuesConfig())
  private val MOESIF_BATCH_API_URL =  "https://api.moesif.net/v1/actions/batch"
  override def send(events: Seq[JsValue]): Future[ExportResult] = {
    var moesifBatch = Json.arr()
    events.map { m =>
      if (m.select("@type").asOpt[String].contains("GatewayEvent")) {
        m.atPath("$.identity.identity").asOpt[String] match {
          case None => ()
          case Some(keyValue) =>

            env.proxyState.apikey(keyValue) match {
              case None => ()
              case Some(apikey) =>
                for {
                  userId <- apikey.toJson.atPath(moesifDataExporterConfig.customerKeyName).asOpt[String]
                  companyId <- apikey.toJson.atPath(moesifDataExporterConfig.companyKeyName).asOpt[String]
                  urlScheme <- m.atPath("$.to.scheme").asOpt[String]
                  urlHost <- m.atPath("$.to.host").asOpt[String]
                  urlTargetUri <- m.atPath("$.target.uri").asOpt[String]
                } yield {
                  moesifBatch = moesifBatch :+ Json.obj(
                    "action_name" -> moesifDataExporterConfig.moesifActionName,
                    "user_id" -> userId,
                    "company_id" -> companyId,
                    "request" -> Json.obj("uri" -> s"${urlScheme}://${urlHost}${urlTargetUri}")
                  )
                }
            }
        }
      }
    }

    // Send the request to moesif only if the bacth is not empty and the application ID is filled
    if(moesifDataExporterConfig.applicationId.nonEmpty && moesifBatch.value.nonEmpty){
      env.Ws
        .url(MOESIF_BATCH_API_URL)
        .withMethod("POST")
        .withHttpHeaders(
          env.Headers.OtoroshiClientId     -> env.clusterConfig.leader.clientId,
          env.Headers.OtoroshiClientSecret -> env.clusterConfig.leader.clientSecret,
          "Content-Type"                   -> "application/json",
          "Accept"                         -> "application/json",
          "X-Moesif-Application-Id" -> moesifDataExporterConfig.applicationId
        )
        .withBody(moesifBatch)
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
    }else{
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

  override def accept(event: JsValue, ctx: CustomDataExporterContext)(implicit env: Env): Boolean = {
    ref.get().accept(event)
  }

  override def project(event: JsValue, ctx: CustomDataExporterContext)(implicit env: Env): JsValue = {
    ref.get().project(event)
  }

  override def send(events: Seq[JsValue], ctx: CustomDataExporterContext)(implicit ec: ExecutionContext, env: Env): Future[ExportResult] = {
    ref.get().send(events)
  }

  override def startExporter(ctx: CustomDataExporterContext)(implicit ec: ExecutionContext, env: Env): Future[Unit] = {
    ref.set(new InternalMoesifDataExporter(ctx.exporter.configUnsafe, ctx.config)(ec, env))
    ref.get().onStart()
    ().vfuture
  }

  override def stopExporter(ctx: CustomDataExporterContext)(implicit ec: ExecutionContext, env: Env): Future[Unit] = {
    ref.get().onStop()
    ().vfuture
  }
}