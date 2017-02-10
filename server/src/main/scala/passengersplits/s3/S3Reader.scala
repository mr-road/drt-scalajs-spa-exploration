package passengersplits.s3

import java.io.{FileInputStream, InputStream, File => JFile}
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import java.io.{FileInputStream, InputStream}
import java.nio.file.{Path => JPath}
import java.io.{File => JFile}

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.routing.ActorRefRoutee
import akka.stream.actor.{ActorSubscriber, ActorSubscriberMessage, MaxInFlightRequestStrategy}
import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.stream._
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.scaladsl.{Flow, Sink, Source, StreamConverters}
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.S3ClientOptions
import com.mfglabs.commons.aws.s3.{AmazonS3AsyncClient, S3StreamBuilder}
import passengersplits._
import core.PassengerInfoRouterActor.VoyagePaxSplits
import core.{Core, CoreActors, CoreLogging, ZipUtils}
import core.ZipUtils.UnzippedFileContent
import org.apache.commons.logging.LogFactory
import org.apache.commons.logging.LogFactory
import passengersplits.parsing.PassengerInfoParser
import passengersplits.parsing.PassengerInfoParser.VoyagePassengerInfo
import spray.http.DateTime

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}


trait UnzippedFilesProvider {
  def unzippedFilesAsSource: Source[UnzippedFileContent, NotUsed]
}

trait FilenameProvider {

  def fileNameStream: Source[String, NotUsed]

  def zipFileNameFilter(filename: String): Boolean

  def latestFilePaths = fileNameStream.filter(zipFileNameFilter)
}


trait S3Reader extends CoreLogging with UnzippedFilesProvider with FilenameProvider {

  def builder: S3StreamBuilder

  def createBuilder: S3StreamBuilder

  def bucket: String

  def createS3client: AmazonS3AsyncClient

  def numberOfCores = 8

  def unzipTimeout = FiniteDuration(400, TimeUnit.SECONDS)

  override def fileNameStream: Source[String, NotUsed] = builder.listFilesAsStream(bucket).map(_._1)

  def zipFilenameToEventualFileContent(zipFileName: String)(implicit actorMaterializer: Materializer, ec: ExecutionContext): Future[List[UnzippedFileContent]] = Future {
    try {
      log.info(s"Will parse ${zipFileName}")
      println(s"Will parse " +
        s"${zipFileName}")
      val threadSpecificBuilder = createBuilder
      val zippedByteStream = threadSpecificBuilder.getFileAsStream(bucket, zipFileName)
      //todo! here is a timeout! why? am I insane?
      val inputStream: InputStream = zippedByteStream.runWith(
        StreamConverters.asInputStream(unzipTimeout)
      )(actorMaterializer)
      val unzippedFileContent: List[UnzippedFileContent] = ZipUtils.unzipAllFilesInStream(new ZipInputStream(inputStream)).toList
      unzippedFileContent.map(_.copy(zipFilename = Some(zipFileName)))
    } catch {
      case e: Throwable =>
        log.error(e, s"Error in S3Poller for ${zipFileName}: ")
        throw e
    }
  }


  def intermediate(paths: Source[String, NotUsed])
                  (implicit actorMaterializer: Materializer, ec: ExecutionContext): Source[List[UnzippedFileContent], NotUsed] = {
    paths
      .mapAsync(numberOfCores) {
        zipFilenameToEventualFileContent
      }
  }

  def unzippedFilesSource(implicit actorMaterializer: Materializer, ec: ExecutionContext): Source[UnzippedFileContent, NotUsed] = {
    intermediate(latestFilePaths).mapConcat {
      t => t
    }
  }

}

trait SimpleS3Reader extends S3Reader with Core {
  this: CoreActors =>
  val bucket: String = "drt-deveu-west-1"

  def createBuilder = S3StreamBuilder(new AmazonS3AsyncClient())

  val builder = createBuilder

  implicit val ec: ExecutionContext = system.dispatcher
  implicit val flowMaterializer = ActorMaterializer()


  lazy val unzippedFilesAsSource: Source[UnzippedFileContent, NotUsed] = unzippedFilesSource


  def streamAllThisToPrintln: Future[Done] = unzippedFilesAsSource.runWith(Sink.foreach(println))
}

object Decider {
  val decider: Supervision.Decider = {
    case _: java.io.IOException => Supervision.Restart
    case _ => Supervision.Stop
  }
}

object DqSettings {
  val fnameprefix = "drt_dq_"

}

trait SimpleAtmosReader extends S3Reader with Core {
  val bucket: String = "drtdqprod"
  val skyscapeAtmosHost: String = "cas00003.skyscapecloud.com:8443"


  override def createBuilder: S3StreamBuilder = S3StreamBuilder(createS3client)

  override lazy val builder = createBuilder

  override def createS3client: AmazonS3AsyncClient = {
    val key = ""
    val prefix = ""
    val configuration: ClientConfiguration = new ClientConfiguration()
    //    configuration.setSignerOverride("NoOpSignerType")
    configuration.setSignerOverride("S3SignerType")
    val provider: ProfileCredentialsProvider = new ProfileCredentialsProvider("drt-atmos")
    log.info("Creating S3 client")

    val client = new AmazonS3AsyncClient(provider, configuration)
    client.client.setS3ClientOptions(S3ClientOptions.builder().setPathStyleAccess(true).build)
    client.client.setEndpoint(skyscapeAtmosHost)
    client
  }

  implicit val flowMaterializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  override lazy val unzippedFilesAsSource: Source[UnzippedFileContent, NotUsed] = unzippedFilesSource
}

trait UnzippedFilePublisher {
  self: SimpleAtmosReader =>
  def flightPassengerReporter: ActorRef

  def streamAllThis: ActorRef = unzippedFilesAsSource
    .map {
      case x =>
        log.debug(s"passing: $x")
        x
    }
    .runWith(Sink.actorSubscriber(WorkerPool.props(flightPassengerReporter)))

  def streamAllThisToPrintln: Future[Done] = unzippedFilesAsSource
    .withAttributes(ActorAttributes.supervisionStrategy(Decider.decider))
    .runWith(Sink.foreach(ln =>
      println(ln.toString.take(200))))
}

object PromiseSignals {
  def promisedDone = Promise[Done]()

}

trait PollingAtmosReader[-In, +Out] {
  def log: LoggingAdapter

  implicit val system: ActorSystem
  implicit val mat: Materializer
  implicit val executionContext: ExecutionContext

  val statefulPoller = AtmosStatefulPoller()
  val runOnce = FileSystemAkkaStreamReading.runOnce(log)(statefulPoller.unzippedFileProvider) _

  val millisBetweenAttempts = 40000
  val atMostForResponsesFromAtmos = 100000 seconds

  import PromiseSignals._

  def beginPolling[Mat2](sink: Graph[SinkShape[Any], Mat2]): Unit = {
    for (i <- Range(0, Int.MaxValue)) {
      log.info(s"Beginning run ${i}")
      val unzipFlow = Flow[String].mapAsync(10)(statefulPoller.unzippedFileProvider.zipFilenameToEventualFileContent(_)).mapConcat(t => t)

      val unzippedSink = unzipFlow.to(sink)
      runOnce(i, (td) => promisedDone.complete(td), statefulPoller.onNewFileSeen, unzippedSink)
      val resultOne = Await.result(promisedDone.future, atMostForResponsesFromAtmos)
      log.info(s"Got result ${i}")

      //todo get rid of the sleep by inverting the flow such that this is triggered by a pulse, embrace the streams!
      Thread.sleep(millisBetweenAttempts)
    }
  }
}

//case class S3PollingActor(bucket: String) extends Actor with S3Poller {
//  val builder = S3StreamBuilder(new AmazonS3AsyncClient())
//
//  def receive = ???
//}

object WorkerPool {

  case class Msg(id: Int, replyTo: ActorRef)

  case class Work(id: Int)

  case class Reply(id: Int)

  case class Done(id: Int)

  def props(passengerInfoRouter: ActorRef): Props = Props(new WorkerPool(passengerInfoRouter))
}

class WorkerPool(flightPassengerInfoRouter: ActorRef) extends ActorSubscriber with ActorLogging {
  import ActorSubscriberMessage._
  import PassengerInfoParser._

  val MaxQueueSize = 10
  var queue = Map.empty[Int, ActorRef]

  override val requestStrategy = new MaxInFlightRequestStrategy(max = MaxQueueSize) {
    override def inFlightInternally: Int = queue.size
  }

  def receive = {
    case OnNext(UnzippedFileContent(filename, content, _)) =>
      //      queue += sender
      // todo move this passengersplits.parsing to be part of the akka flow pipe? because then we can just apply a port level filter without akka stateful magic.
      // ln(s"Found a file content $filename")
      val parsed = VoyagePassengerInfoParser.parseVoyagePassengerInfo(content)
      log.info(s"flubflub ${parsed}")
      parsed match {
        case Success(voyagePassengerInfo) =>
          flightPassengerInfoRouter ! voyagePassengerInfo
        case Failure(f) =>
          log.error(f, s"Could not parse $content")
      }
    case OnNext(voyagePassengerInfo: VoyagePassengerInfo) =>
      flightPassengerInfoRouter ! voyagePassengerInfo
    case OnComplete =>
      log.info(s"WorkerPool OnComplete")
    case unknown =>
      log.error(s"WorkerPool got unknown ${unknown}")
  }


}

case class FlightId(flightNumber: String, carrier: String, schDateTime: DateTime)

class SplitCalculatorWorkerPool extends ActorSubscriber with ActorLogging {
  import ActorSubscriberMessage._
  import PassengerInfoParser._

  val MaxQueueSize = 10
  var queue = Map.empty[Int, ActorRef]

  override val requestStrategy = new MaxInFlightRequestStrategy(max = MaxQueueSize) {
    override def inFlightInternally: Int = queue.size
  }


  var flightSplits: Map[FlightId, VoyagePaxSplits] = Map()

  def receive = {
    case OnNext(voyagePassengerInfo: VoyagePassengerInfo) =>
      val flightId = FlightId(voyagePassengerInfo.VoyageNumber, voyagePassengerInfo.CarrierCode, voyagePassengerInfo.scheduleArrivalDateTime.get)
    case OnComplete =>
      log.info(s"WorkerPool OnComplete")
    case unknown =>
      log.error(s"WorkerPool got unknown ${unknown}")
  }
  
}



object VoyagePassengerInfoParser {
  import WorkerPool._
  import ActorSubscriberMessage._
  import PassengerInfoParser._
  import FlightPassengerInfoProtocol._
  import spray.json._
  import PassengerInfoParser._

  def parseVoyagePassengerInfo(content: String) = {
    Try(content.parseJson.convertTo[VoyagePassengerInfo])
  }
}

//trait S3Actors {
//  self: Core =>
//  val s3PollingActor = system.actorOf(Props[])
//}