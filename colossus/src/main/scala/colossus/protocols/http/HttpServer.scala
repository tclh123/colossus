package colossus
package protocols.http
package server

import colossus.metrics.TagMap
import core._
import controller._
import service._

import scala.concurrent.duration._

class HttpServiceHandler(rh: RequestHandler, defaultHeaders: HttpHeaders) 
extends DSLService[Http](rh) {

  val codec = new StaticHttpServerCodec(defaultHeaders)

  val defaults = new Http.ServerDefaults

  override def tagDecorator = new ReturnCodeTagDecorator

  override def processRequest(input: Http#Input): Callback[Http#Output] = {
    val response = super.processRequest(input)
    if(!input.head.persistConnection) connection.disconnect()
    response
  }
  def unhandledError = {
    case error => defaults.errorResponse(error)
  }

}


protected[server] class Generator(context: InitContext) extends HandlerGenerator[Http, RequestHandler](context) {
  
  val DateHeader = new DateHeader
  val ServerHeader = HttpHeader("Server", context.server.name.idString)

  val defaultHeaders = HttpHeaders(DateHeader, ServerHeader)

  def fullHandler = requestHandler => new CoreHandler(
    new Controller(
      requestHandler.context.context,
      new HttpServiceHandler(requestHandler, defaultHeaders),
      new StaticHttpServerCodec(defaultHeaders),
      ControllerConfig(50, Duration.Inf)      
    ),
    requestHandler,
    requestHandler.context.context
  )

}

abstract class Initializer(ctx: InitContext) extends Generator(ctx) with ServiceInitializer[Http, RequestHandler]


abstract class RequestHandler(config: ServiceConfig, ctx: ServerContext) extends GenRequestHandler[Http](config, ctx) {
  def this(ctx: ServerContext) = this(ServiceConfig.load(ctx.name), ctx)
}

object HttpServer extends ServiceDSL[Http, RequestHandler, Initializer]{

  def basicInitializer = new Generator(_)
  
  def basic(name: String, port: Int)(handler: PartialFunction[HttpRequest, Callback[HttpResponse]])(implicit io: IOSystem) = start(name, port){new Initializer(_) {
    def onConnect = new RequestHandler(_) { def handle = handler }
  }}
}

