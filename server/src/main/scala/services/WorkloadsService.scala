package services

import org.slf4j.LoggerFactory
import services.workloadcalculator.PaxLoadCalculator
import spatutorial.shared.FlightsApi._
import spatutorial.shared._

import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}


trait FlightsService extends FlightsApi {
  def getFlights(st: Long, end: Long): Future[List[ApiFlight]]

  def getFlightChanges(since: Long, number: Int): Future[List[FlightChange]]

  def flights(startTimeEpoch: Long, endTimeEpoch: Long): Flights = {
    val fsFuture = getFlights(startTimeEpoch, endTimeEpoch)
    Flights(Await.result(fsFuture, Duration.Inf))
  }

}

trait WorkloadsService extends WorkloadsApi with WorkloadsCalculator {
  self: (FlightsService) =>
}

trait WorkloadsCalculator {
  private val log = LoggerFactory.getLogger(getClass)

  type TerminalQueueWorkloads = Map[TerminalName, Map[QueueName, (Seq[WL], Seq[Pax])]]


  def splitRatioProvider: (ApiFlight) => Option[List[SplitRatio]]

  def procTimesProvider(terminalName: TerminalName)(paxTypeAndQueue: PaxTypeAndQueue): Double

  def getWorkloadsByTerminal(flights: Future[List[ApiFlight]]): Future[TerminalQueueWorkloads] = {
    val flightsByTerminalFut: Future[Map[TerminalName, List[ApiFlight]]] = flights.map(fs => {
      val flightsByTerminal = fs.filterNot(freightOrEngineering).groupBy(_.Terminal)
      flightsByTerminal
    })

    val calcPaxTypeAndQueueCountForAFlightOverTime = PaxLoadCalculator.voyagePaxSplitsFlowOverTime(splitRatioProvider)_

    val workloadByTerminal = flightsByTerminalFut.map((flightsByTerminal: Map[TerminalName, List[ApiFlight]]) =>
      flightsByTerminal.map((fbt: (TerminalName, List[ApiFlight])) => {
        log.info(s"Got flights by terminal ${fbt}")
        val terminalName = fbt._1
        val flights = fbt._2
        val plc = PaxLoadCalculator.queueWorkloadCalculator(calcPaxTypeAndQueueCountForAFlightOverTime, procTimesProvider(terminalName)) _
        (terminalName -> plc(flights))
      }))

    workloadByTerminal
  }

  def freightOrEngineering(flight: ApiFlight): Boolean = Set("FRT", "ENG").contains(flight.Terminal)
}

