import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import spray.json.DefaultJsonProtocol.{jsonFormat4, _}
import spray.json.RootJsonFormat

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}


object MovieGetter {
  case class Movie(id: String, title: String, description: String, director: String)
  case object GetTimeout
  case class RequestProblem(problem: String)

  def props(
             movieId: String,
             requester: ActorRef,
             timeout: FiniteDuration): Props = {
    Props(new MovieGetter(movieId, requester, timeout))
  }
}

/**
  * Actor that requests a movie information at https://ghibliapi.herokuapp.com/films/$movieId
  * @param movieId movieId used in the request.
  * @param requester The ActorRef who wants this information.
  * @param timeout Timeout of the requisition.
  */
class MovieGetter(movieId: String,
                  requester: ActorRef,
                  timeout: FiniteDuration) extends Actor
  with ActorLogging {
  import MovieGetter._
  import akka.pattern.pipe
  import context.dispatcher
  implicit val movieFormat:RootJsonFormat[Movie] = jsonFormat4(Movie)

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)
  val queryTimeoutTimer:Cancellable = context.system.scheduler.scheduleOnce(timeout, self, GetTimeout)

  override def preStart(): Unit = {
    log.info(s"Starting request at: https://ghibliapi.herokuapp.com/films/$movieId")
    http.singleRequest(HttpRequest(
      uri = s"https://ghibliapi.herokuapp.com/films/$movieId"
    )).pipeTo(self)
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }

  def receive = {
    // Case Success
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      val result = Unmarshal(entity).to[Movie]
      result onComplete {
        case Success(value: Movie) =>
          log.info(s"Request Successful $value")
          requester ! Left(value)
          context.stop(self)
        case Failure(err) =>
          log.info(s"err : $err")
          requester ! Right(RequestProblem("Failed on completing the future"))
          context.stop(self)
      }
    // Case Failure
    case resp @ HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
      resp.discardEntityBytes()
      requester ! Right(RequestProblem("Failed on the requisition"))
      context.stop(self)
    // Case Timeout
    case GetTimeout =>
      log.info("Timeout!")
      requester ! Right(RequestProblem("Timeout Failure"))
      context.stop(self)
  }
}