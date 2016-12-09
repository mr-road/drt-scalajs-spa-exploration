package spatutorial.shared

import java.util.Date

import spatutorial.shared.FlightChanges.ChangeReason

import scala.collection.immutable._
import spatutorial.shared.FlightsApi._

import scala.{Predef, List}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable.{IndexedSeq, Map, Seq}


case class FlightChange(reason: String,
                        apiFlight: ApiFlight,
                        optOldFlight: Option[ApiFlight] = None)

case class FlightChanges(flightChanges: Seq[FlightChange])

object FlightChanges {
  type ChangeReason = String
  val Update = "Update"
  val Add = "Add"

  def diffFlightChanges(currFlightsById: Map[Int, ApiFlight], newFlights: List[ApiFlight]): FlightChanges = {
    val inboundFlightIds: Set[Int] = newFlights.map(_.FlightID).toSet
    val newFlightsById = newFlights.map(x => (x.FlightID, x)).toMap

    val existingFlightIds: Set[Int] = currFlightsById.keys.toSet

    val potentiallyUpdatingFlightIds = existingFlightIds intersect inboundFlightIds
    val newFlightIds = existingFlightIds diff inboundFlightIds

    val updatedFlightsSet = potentiallyUpdatingFlightIds map (
      (potentialFlightId: Int) => {
        val oldFlight = currFlightsById(potentialFlightId)
        val newFlight = newFlightsById(potentialFlightId)
        (oldFlight, newFlight)
      })
    val updatedFlights =  updatedFlightsSet.filter(x => x._1 != x._2).toVector

    val justNewFlights: List[ApiFlight] = newFlights.filter(newFlightIds contains _.FlightID)

    val changedFlights: scala.Vector[FlightChange] = updatedFlights.map { case (newFlight, oldFlight) => ChangedFlight(newFlight, oldFlight) }

    FlightChanges(changedFlights ++ justNewFlights.map(AddedFlight(_)))
  }

  def ChangedFlight(flight: ApiFlight, oldFlight: ApiFlight) = FlightChange(Update, flight, Option(oldFlight))

  def AddedFlight(flight: ApiFlight) = FlightChange(Add, flight)
}

case class ApiFlight(
                      Operator: String,
                      Status: String,
                      EstDT: String,
                      ActDT: String,
                      EstChoxDT: String,
                      ActChoxDT: String,
                      Gate: String,
                      Stand: String,
                      MaxPax: Int,
                      ActPax: Int,
                      TranPax: Int,
                      RunwayID: String,
                      BaggageReclaimId: String,
                      FlightID: Int,
                      AirportID: String,
                      Terminal: String,
                      ICAO: String,
                      IATA: String,
                      Origin: String,
                      SchDT: String,
                      PcpTime: Long)

case class CrunchResult(recommendedDesks: IndexedSeq[Int], waitTimes: Seq[Int])

object CrunchResult {
  def empty = CrunchResult(Vector[Int](), Nil)
}

case class NoCrunchAvailable()

case class SimulationResult(recommendedDesks: IndexedSeq[DeskRec], waitTimes: Seq[Int])

object FlightsApi {

  case class Flight(scheduleArrivalDt: Long, actualArrivalDt: Option[Long], reallyADate: Long,
                    flightNumber: String,
                    carrierCode: String,
                    pax: Int,
                    iata: Option[String],
                    icao: Option[String])

  case class Flights(flights: List[ApiFlight])

  type QueueWorkloads = (Seq[WL], Seq[Pax])

  type TerminalName = String

  type QueueName = String

  type WorkloadsResult = Map[QueueName, QueueWorkloads]
}

trait FlightsApi {
  def flights(startTimeEpoch: Long, endTimeEpoch: Long): Flights
}

case class AirportInfo(airportName: String, city: String, country: String, code: String)

trait WorkloadsHelpers {
  val oneMinute = 60000L

  def workloadsByQueue(workloads: Map[String, QueueWorkloads], numberOfHours: Int = 24): Map[String, List[Double]] = {
    val allWorkloadsForQueuesInThisTerminal: scala.List[(Seq[WL], Seq[Pax])] = workloads.values.toList
    val timesMin = minimumMinuteInWorkloads(allWorkloadsForQueuesInThisTerminal)
    val allMins: NumericRange[Long] = wholeDaysMinutesFromAllQueues(allWorkloadsForQueuesInThisTerminal, timesMin, numberOfHours = numberOfHours)
    println(s"allMins: ${allMins.min} to ${allMins.max}")
    workloads.mapValues(qwl => {
      val allWorkloadByMinuteForThisQueue = oneQueueWorkload(qwl)
      val queuesMinutesFoldedIntoWholeDay = foldQueuesMinutesIntoDay(allMins, allWorkloadByMinuteForThisQueue)
      queuesWorkloadByMinuteAsFullyPopulatedWorkloadSeq(queuesMinutesFoldedIntoWholeDay)
    })
  }

  def paxloadsByQueue(workloads: Map[String, QueueWorkloads]): Map[String, List[Double]] = {
    val allWorkloadsForQueuesInThisTerminal: scala.List[(Seq[WL], Seq[Pax])] = workloads.values.toList
    val timesMin = minimumMinuteInWorkloads(allWorkloadsForQueuesInThisTerminal)
    val allMins: NumericRange[Long] = wholeDaysMinutesFromAllQueues(allWorkloadsForQueuesInThisTerminal, timesMin)
    println(s"allMins: ${allMins.min} to ${allMins.max}")
    workloads.mapValues(qwl => {
      val allPaxloadByMinuteForThisQueue = oneQueuePaxload(qwl)
      val queuesMinutesFoldedIntoWholeDay = foldQueuesMinutesIntoDay(allMins, allPaxloadByMinuteForThisQueue)
      queuesWorkloadByMinuteAsFullyPopulatedWorkloadSeq(queuesMinutesFoldedIntoWholeDay)
    })
  }

  def workloadsByPeriod(workloadsByMinute: Seq[WL], n: Int): scala.Seq[WL] =
    workloadsByMinute.grouped(n).toSeq.map((g: Seq[WL]) => WL(g.head.time, g.map(_.workload).sum))

  def queuesWorkloadByMinuteAsFullyPopulatedWorkloadSeq(res: Map[Long, Double]): List[Double] = {
    res.toSeq.sortBy(_._1).map(_._2).toList
  }

  def foldQueuesMinutesIntoDay(allMins: NumericRange[Long], workloadsByMinute: Map[Long, Double]): Map[Long, Double] = {
    allMins.foldLeft(Map[Long, Double]())(
      (minuteMap, minute) => minuteMap + (minute -> workloadsByMinute.getOrElse(minute, 0d)))
  }

  def oneQueueWorkload(workloads1: QueueWorkloads): Map[Long, Double] = {
    workloads1._1.map((wl) => (wl.time, wl.workload)).toMap
  }

  def oneQueuePaxload(paxloads: QueueWorkloads): Map[Long, Double] = {
    paxloads._2.map((paxLoad) => (paxLoad.time, paxLoad.pax)).toMap
  }

  def wholeDaysMinutesFromAllQueues(workloads: Seq[QueueWorkloads], timesMin: Long, numberOfHours: Int = 24): NumericRange[Long] = {
    val timeMinPlusOneDay: Long = timesMin + oneMinute * 60 * numberOfHours
    timesMin until timeMinPlusOneDay by oneMinute
  }

  def minimumMinuteInWorkloads(workloads: Seq[QueueWorkloads]): Long = {
    val now = new Date()
    val thisMorning = new Date(now.getYear, now.getMonth, now.getDate)
    thisMorning.getTime()
  }
}

object WorkloadsHelpers extends WorkloadsHelpers

case class WorkloadResponse(terminals: Seq[TerminalWorkload])

case class TerminalWorkload(terminalName: String,
                            queues: Seq[QueueWorkloads])

trait Time {
  def time: Long
}

case class WL(time: Long, workload: Double) extends Time

case class Pax(time: Long, pax: Double) extends Time

case class DeskRec(time: Long, desks: Int)

case class WorkloadTimeslot(time: Long, workload: Double, pax: Int, desRec: Int, waitTimes: Int)


trait WorkloadsApi {
  def getWorkloads(): Future[Map[TerminalName, Map[QueueName, QueueWorkloads]]]
}

//todo the size of this api is already upsetting me, can we make it smaller while keeping autowiring?
trait Api extends FlightsApi with WorkloadsApi {

  def welcomeMsg(name: String): String

  def airportInfoByAirportCode(code: String): Future[Option[AirportInfo]]

  def airportInfosByAirportCodes(codes: Set[String]): Future[Map[String, AirportInfo]]

  //  def crunch(terminalName: TerminalName, queueName: QueueName, workloads: List[Double]): Future[CrunchResult]
  //  def getFlightChanges(since: Long, numberOfChanges: Int = 100)
  def getLatestCrunchResult(terminalName: TerminalName, queueName: QueueName): Future[CrunchResult]

  def processWork(terminalName: TerminalName, queueName: QueueName, workloads: List[Double], desks: List[Int]): SimulationResult

  def airportConfiguration(): AirportConfig
}
