import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import scala.concurrent.duration._


object MovieManager{
  case class MovieGet(movieId: String)
  def props(): Props = {
    Props(new MovieManager)
  }
}

/**
  * Movie Manager - Singleton Actor, created by the WebServer, to deal with Movie Requests.
  */
class MovieManager extends Actor
  with ActorLogging {
  import MovieManager._

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  val http = Http(context.system)
  var requester: ActorRef = _

  def receive = {
    case MovieGet(movieId) =>
      val movieGetter = context.actorOf(MovieGetter.props(movieId, sender(), 5.seconds), s"movieGetter-$movieId")
  }
}