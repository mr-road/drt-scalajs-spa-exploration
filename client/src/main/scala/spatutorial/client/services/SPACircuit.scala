package spatutorial.client.services


import diode.ActionResult.EffectOnly

import scala.collection.immutable.{NumericRange, IndexedSeq, Iterable, Seq}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Date
import scala.scalajs.js.annotation.{JSExport, ScalaJSDefined}
import scala.util.{Failure, Success, Try}
import spatutorial.client.services.HandyStuff.{CrunchResultAndDeskRecs, QueueUserDeskRecs}
import autowire._
import boopickle.Default._
import diode._
import diode.data._
import diode.react.ReactConnector
import diode.util._
import spatutorial.client.components.DeskRecsChart
import spatutorial.client.logger._
import spatutorial.shared._
import spatutorial.shared.FlightsApi._
import scala.concurrent.duration._

import diode.Implicits.runAfterImpl
import scala.scalajs.js.timers._
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

case class DeskRecTimeslot(id: String, deskRec: Int)

// Actions
case object RefreshTodos extends Action

case class UpdateQueueUserDeskRecs(terminalName: TerminalName, queueName: QueueName, todos: Seq[DeskRecTimeslot]) extends Action

case class UpdateDeskRecsTime(terminalName: TerminalName, queueName: QueueName, item: DeskRecTimeslot) extends Action

case class DeleteTodo(item: DeskRecTimeslot) extends Action

case class UpdateMotd(potResult: Pot[String] = Empty) extends PotAction[String, UpdateMotd] {
  override def next(value: Pot[String]) = UpdateMotd(value)
}

case class UpdateCrunchResult(terminalName: TerminalName, queueName: QueueName, crunchResult: CrunchResult) extends Action

case class UpdateSimulationResult(terminalName: TerminalName, queueName: QueueName, simulationResult: SimulationResult) extends Action

case class UpdateWorkloads(workloads: Map[TerminalName, Map[QueueName, QueueWorkloads]]) extends Action

case class GetWorkloads(begin: String, end: String) extends Action

case class GetAirportConfig() extends Action

case class UpdateAirportConfig(airportConfig: AirportConfig) extends Action

case class RunSimulation(terminalName: TerminalName, queueName: QueueName, workloads: List[Double], desks: List[Int]) extends Action

case class ChangeDeskUsage(terminalName: TerminalName, queueName: QueueName, value: String, index: Int) extends Action

case class ProcessWork(desks: Seq[Double], workload: Seq[Double]) extends Action

trait WorkloadsUtil {
  def labelsFromAllQueues(startTime: Long) = {
    val oneMinute: Long = 60000
    val allMins = startTime until (startTime + 60000 * 60 * 24) by oneMinute

    allMins.map(millis => {
      val d = new js.Date(millis)
      f"${d.getHours()}%02d:${d.getMinutes()}%02d"
    })
  }

  def firstFlightTimeQueue(workloads: Map[String, (Seq[WL], Seq[Pax])]): Long = {
    workloads.values.flatMap(_._1.map(_.time)).min
  }

  def timeStampsFromAllQueues(workloads: Map[String, QueueWorkloads]) = {
    val timesMin: Long = firstFlightTimeQueue(workloads)
    minuteNumericRange(timesMin, 24)
  }

  def minuteNumericRange(start: Long, numberOfHours: Int = 24): NumericRange[Long] = {
    val oneMinute: Long = 60000
    val allMins = start until (start + 60000 * 60 * numberOfHours) by oneMinute
    allMins
  }
}

// The base model of our application
case class Workloads(workloads: Map[TerminalName, Map[QueueName, QueueWorkloads]]) extends WorkloadsUtil {
  lazy val labels = labelsFromAllQueues(startTime)

  def timeStamps(terminalName: TerminalName): NumericRange[Long] = minuteNumericRange(startTime, 24)

  def startTime: Long = {
    val now = new Date()
    val thisMorning = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    thisMorning.getTime().toLong
  }

  def firstFlightTimeAcrossTerminals: Long = workloads.values.map(firstFlightTimeQueue(_)).min

}

case class RootModel(
                      flightChanges: Vector[FlightChange] = Vector.empty,
                      motd: Pot[String] = Empty,
                      workload: Pot[Workloads] = Empty,
                      queueCrunchResults: Map[TerminalName, Map[QueueName, Pot[CrunchResultAndDeskRecs]]] = Map(),
                      userDeskRec: Map[TerminalName, QueueUserDeskRecs] = Map(),
                      simulationResult: Map[TerminalName, Map[QueueName, Pot[SimulationResult]]] = Map(),
                      flights: Pot[Flights] = Empty,
                      airportInfos: Map[String, Pot[AirportInfo]] = Map(),
                      airportConfig: Pot[AirportConfig] = Empty,
                      minutesInASlot: Int = 15,
                      slotsInADay: Int = 96
                    ) {
  override def toString: String =
    s"""
       |RootModel(
       |motd: $motd
       |paxload: $workload
       |queueCrunchResults: $queueCrunchResults
       |userDeskRec: $userDeskRec
       |simulationResult: $simulationResult
       |flights: $flights
       |airportInfos: $airportInfos
       |)
     """.stripMargin

}

object RootModel {

  def mergeTerminalQueues[A](m1: Map[QueueName, Map[QueueName, A]], m2: Map[QueueName, Map[QueueName, A]]): Map[String, Map[String, A]] = {
    val merged = m1.toSeq ++ m2.toSeq
    val grouped = merged.groupBy(_._1)
    val cleaned = grouped.mapValues(_.flatMap(_._2).toMap)
    cleaned
  }
}

case class UserDeskRecs(items: Seq[DeskRecTimeslot]) {
  def updated(newItem: DeskRecTimeslot) = {
    log.info(s"will update ${newItem} into ${items.take(5)}...")
    items.indexWhere(_.id == newItem.id) match {
      case -1 =>
        // add new
        log.info("add new")
        UserDeskRecs(items :+ newItem)
      case idx =>
        log.info("add old")
        // replace old
        UserDeskRecs(items.updated(idx, newItem))
    }
  }
}

/**
  * Handles actions related to todos
  *
  * @param modelRW Reader/Writer to access the model
  */
class DeskTimesHandler[M](modelRW: ModelRW[M, Map[TerminalName, QueueUserDeskRecs]]) extends LoggingActionHandler(modelRW) {
  override def handle = {
    case RefreshTodos =>
      log.info("RefreshTodos")
      //      effectOnly(Effect(AjaxClient[Api].geAllTodos().call().map(UpdateAllTodos)))
      noChange
    case UpdateDeskRecsTime(terminalName, queueName, item) =>
      log.info(s"Update Desk Recs time ${item} into ${value}")
      // make a local update and inform server
      val newDesksPot: Pot[UserDeskRecs] = value(terminalName)(queueName).map(_.updated(item))
      updated(RootModel.mergeTerminalQueues(value, Map(terminalName -> Map(queueName -> newDesksPot))), Effect(Future(RunSimulation(terminalName, queueName, Nil, newDesksPot.get.items.map(_.deskRec).toList)))) //, Effect(AjaxClient[Api].updateDeskRecsTime(item).call().map(UpdateAllTodos)))
  }
}

abstract class LoggingActionHandler[M, T](modelRW: ModelRW[M, T]) extends ActionHandler(modelRW) {
  override def handleAction(model: M, action: Any): Option[ActionResult[M]] = {
    log.info(s"finding handler for ${action.toString.take(100)}")
    Try(super.handleAction(model, action)) match {
      case Failure(f) =>
        log.error(s"Exception from ${getClass}  ${f.getMessage}")
        throw f
      case Success(s) =>
        s
    }
  }
}

/**
  * Handles actions related to the Motd
  *
  * @param modelRW Reader/Writer to access the model
  */
class MotdHandler[M](modelRW: ModelRW[M, Pot[String]]) extends LoggingActionHandler(modelRW) {
  implicit val runner = new RunAfterJS

  override def handle = {
    case action: UpdateMotd =>
      val updateF = action.effect(AjaxClient[Api].welcomeMsg("User X").call())(identity _)
      action.handleWith(this, updateF)(PotAction.handler())
  }
}

class AirportConfigHandler[M](modelRW: ModelRW[M, Pot[AirportConfig]]) extends LoggingActionHandler(modelRW) {
  protected def handle = {
    case action: GetAirportConfig =>
      log.info("requesting workloadsWrapper from server")
      val call: Future[AirportConfig] = AjaxClient[Api].airportConfiguration().call()
      log.info(s"Airport config from server: ${call}")

      updated(Pending(), Effect(call.map(UpdateAirportConfig)))
    case UpdateAirportConfig(configHolder) =>
      log.info(s"Received airportConfig ${configHolder}")
      log.info("Subscribing to crunchs for terminal/queues")

      val crunchRequests: Seq[Effect] = for {tn <- configHolder.terminalNames
                                             qn <- configHolder.queues
      } yield Effect(Future(GetLatestCrunch(tn, qn)))

      val effectSet = crunchRequests.toList match {
        case h :: Nil =>
          updated(Ready(configHolder), h)
        case h :: ts =>
          updated(Ready(configHolder), new EffectSeq(h, ts, queue))
      }
      effectSet
  }
}

class WorkloadHandler[M](modelRW: ModelRW[M, Pot[Workloads]]) extends LoggingActionHandler(modelRW) {
  protected def handle = {
    case action: GetWorkloads =>
      log.info("requesting workloadsWrapper from server")
      updated(Pending(), Effect(AjaxClient[Api].getWorkloads().call().map(UpdateWorkloads)))

    case UpdateWorkloads(terminalQueueWorkloads) =>
      updated(Ready(Workloads(terminalQueueWorkloads)))
  }
}

object HandyStuff {
  type CrunchResultAndDeskRecs = (Pot[CrunchResult], Pot[UserDeskRecs])
  type QueueUserDeskRecs = Map[String, Pot[UserDeskRecs]]
}

class SimulationHandler[M](modelR: ModelR[M, Pot[Workloads]], modelRW: ModelRW[M, Map[TerminalName, QueueUserDeskRecs]])
  extends LoggingActionHandler(modelRW) {
  protected def handle = {
    case RunSimulation(terminalName, queueName, workloads, desks) =>
      log.info(s"Requesting simulation for $terminalName, {queueName}")
      val workloads1: List[Double] = WorkloadsHelpers.workloadsByQueue(modelR.value.get.workloads(terminalName))(queueName)
      //      queueWorkloadsToFullyPopulatedDoublesList(modelR.value.get.workloads)
      log.info(s"Got workloads from model for $terminalName {queueName} desks: ${desks.take(15)}... workloads: ${workloads1.take(15)}...")
      val simulationResult: Future[SimulationResult] = AjaxClient[Api].processWork(terminalName, queueName, workloads1, desks).call()
      effectOnly(
        Effect(simulationResult.map(resp => UpdateSimulationResult(terminalName, queueName, resp)))
      )
    case ChangeDeskUsage(terminalName, queueName, v, k) =>
      log.info(s"Handler: ChangeDesk($terminalName, $queueName, $v, $k)")
      val model: Pot[UserDeskRecs] = value(terminalName)(queueName)
      val newUserRecs: UserDeskRecs = model.get.updated(DeskRecTimeslot(k.toString, v.toInt))
      updated(RootModel.mergeTerminalQueues(value, Map(terminalName -> Map(queueName -> Ready(newUserRecs)))))
  }
}

class SimulationResultHandler[M](modelRW: ModelRW[M, Map[TerminalName, Map[QueueName, Pot[SimulationResult]]]]) extends LoggingActionHandler(modelRW) {
  protected def handle = {
    case UpdateSimulationResult(terminalName, queueName, simResult) =>
      //      log.info(s"Got simulation result $queueName ${simResult.waitTimes}")
      updated(RootModel.mergeTerminalQueues(value, Map(terminalName -> Map(queueName -> Ready(simResult)))))
  }
}

case class GetLatestCrunch(terminalName: TerminalName, queueName: QueueName) extends Action

case class RequestFlights(from: Long, to: Long) extends Action

case class UpdateFlights(flights: Flights) extends Action

class FlightsHandler[M](modelRW: ModelRW[M, (Pot[Flights], Vector[FlightChange])]) extends LoggingActionHandler(modelRW) {
  protected def handle = {
    case RequestFlights(from, to) =>
      log.info(s"client requesting flights $from $to")
      effectOnly(Effect(AjaxClient[Api].flights(from, to).call().map(UpdateFlights)))
    case UpdateFlights(flights) =>
      log.info(s"client got ${flights.flights.length} flights")
      val currentFlights = value._1
      val currentFlightChanges = value._2
      val result = if (currentFlights.isReady) {
        val oldFlights: Flights = currentFlights.get
        val oldFlightsSet = oldFlights.flights.toSet
        val newFlightsSet = flights.flights.toSet
        if (oldFlightsSet != newFlightsSet) {
          val codes = flights.flights.map(_.Origin).toSet
          val oldFlightsByFlightId = oldFlights.flights.map(x => (x.FlightID, x)).toMap
          val changes = FlightChanges.diffFlightChanges(oldFlightsByFlightId, flights.flights)
          updated((Ready(flights), changes.flightChanges.toVector ++ currentFlightChanges), Effect(Future(GetAirportInfos(codes))))
        } else {
          log.info("no changes to flights")
          noChange
        }
      } else {
        val codes = flights.flights.map(_.Origin).toSet
        updated((Ready(flights), Vector.empty), Effect(Future(GetAirportInfos(codes))))
      }

      result
  }
}

class CrunchHandler[M](modelRW: ModelRW[M, (Map[TerminalName, QueueUserDeskRecs], Map[TerminalName, Map[QueueName, Pot[CrunchResultAndDeskRecs]]])])
  extends LoggingActionHandler(modelRW) {

  override def handle = {
    case GetLatestCrunch(terminalName, queueName) =>
      val fe: Future[Action] = AjaxClient[Api].getLatestCrunchResult(terminalName, queueName).call().map {
        case Right(cr) =>
          UpdateCrunchResult(terminalName, queueName, cr)

        case Left(ncr) =>
          log.info(s"Failed to fetch crunch - has a crunch run yet? $ncr")
         NoAction
      }

      effectOnly(Effect(fe))
    case UpdateCrunchResult(terminalName, queueName, crunchResult) =>
      log.info(s"UpdateCrunchResult $queueName")
      //todo zip with labels?, or, probably better, get these prepoluated from the server response?
      val newDeskRec: UserDeskRecs = UserDeskRecs(DeskRecsChart
        .takeEvery15th(crunchResult.recommendedDesks)
        .zipWithIndex.map(t => DeskRecTimeslot(id = t._2.toString, deskRec = t._1)).toList)

      updated(value.copy(
        _1 = RootModel.mergeTerminalQueues(value._1, Map(terminalName -> Map(queueName -> Ready(newDeskRec)))),
        _2 = RootModel.mergeTerminalQueues(value._2, Map(terminalName -> Map(queueName -> Ready((Ready(crunchResult), Ready(newDeskRec))))))
      ))
  }

}

class AirportCountryHandler[M](timeProvider: () => Long, modelRW: ModelRW[M, Map[String, Pot[AirportInfo]]]) extends LoggingActionHandler(modelRW) {
  def mkPending = Pending(timeProvider())

  override def handle = {
    case GetAirportInfos(codes) =>
      //      log.info(s"Will request infos for $codes")
      val stringToObject: Map[String, Pot[AirportInfo]] = value ++ Map("BHX" -> mkPending, "EDI" -> mkPending)
      updated(stringToObject, Effect(AjaxClient[Api].airportInfosByAirportCodes(codes).call().map(UpdateAirportInfos(_))))
    case UpdateAirportInfos(infos) =>
      val infosReady = infos.map(kv => (kv._1, Ready(kv._2)))
      updated(value ++ infosReady)
    case GetAirportInfo(code) =>
      value.get(code) match {
        case None =>
          val stringToObject = value + (code -> Empty)
          log.info(s"sending request for info for ${code}")
          updated(stringToObject, Effect(AjaxClient[Api].airportInfoByAirportCode(code).call().map(res => UpdateAirportInfo(code, res))))
        case Some(v) =>
          noChange
      }
    case UpdateAirportInfo(code, Some(airportInfo)) =>
      val newValue = value + ((code -> Ready(airportInfo)))
      log.info(s"got a new value for ${code} ${airportInfo}")
      updated(newValue)
  }
}

// Application circuit
object SPACircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  val blockWidth = 15

  def timeProvider() = new Date().getTime.toLong

  // initial application model
  override protected def initialModel = RootModel()

  // combine all handlers into one
  override val actionHandler = {
    println("composing handlers")
    composeHandlers(
      new DeskTimesHandler(zoomRW(_.userDeskRec)((m, v) => m.copy(userDeskRec = v))),
      new MotdHandler(zoomRW(_.motd)((m, v) => m.copy(motd = v))),
      new WorkloadHandler(zoomRW(_.workload)((m, v) => {
        //        log.info(s"Updateing workloads to $v")
        m.copy(workload = v)
      })),
      new CrunchHandler(zoomRW(m => (m.userDeskRec, m.queueCrunchResults))((m, v) => {
        //        log.info(s"setting crunch result and userdesk recs desks in model ${v}")
        m.copy(
          userDeskRec = v._1,
          queueCrunchResults = v._2
        )
      })),
      new SimulationHandler(zoom(_.workload), zoomRW(m => m.userDeskRec)((m, v) => {
        log.info("setting simulation result in model")
        m.copy(userDeskRec = v)
      })),
      new SimulationResultHandler(zoomRW(_.simulationResult)((m, v) => m.copy(simulationResult = v))),
      new FlightsHandler(zoomRW(m=> (m.flights, m.flightChanges))((m, v) => m.copy(flights = v._1, flightChanges = v._2))),
      new AirportCountryHandler(timeProvider, zoomRW(_.airportInfos)((m, v) => m.copy(airportInfos = v))),
      new AirportConfigHandler(zoomRW(_.airportConfig)((m, v) => m.copy(airportConfig = v)))
    )
  }

}

case class GetAirportInfos(code: Set[String]) extends Action

case class GetAirportInfo(code: String) extends Action

case class UpdateAirportInfo(code: String, info: Option[AirportInfo]) extends Action

case class UpdateAirportInfos(infos: Map[String, AirportInfo]) extends Action

