import MovieGetter.{Movie, RequestProblem}
import MovieManager.MovieGet
import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{PathMatchers, Route}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import spray.json.DefaultJsonProtocol._
import spray.json.RootJsonFormat

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.io.StdIn

object WebServer {
  def main(args: Array[String]) {
    implicit val movieFormat:RootJsonFormat[Movie] = jsonFormat4(Movie)
    implicit val requestProblemFormat:RootJsonFormat[RequestProblem] = jsonFormat1(RequestProblem)
    implicit val system:ActorSystem = ActorSystem()
    implicit val materializer:ActorMaterializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext:ExecutionContextExecutor = system.dispatcher
    val movieManager = system.actorOf(Props[MovieManager], "movieManager")

    val route: Route =
      get {
        pathPrefix("movie" /  PathMatchers.Segment ) { id:String =>
          implicit val timeout:Timeout = Timeout(10 seconds)
          val response: Future[Either[Movie, RequestProblem]] = (movieManager ? MovieGet(id.toString)).mapTo[Either[Movie, RequestProblem]]
          complete(response)
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}