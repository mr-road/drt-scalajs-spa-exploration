package drt.services.workload

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.AskableActorRef
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import drt.services.workload.SplitsMocks.MockSplitsActor
import org.specs2.mutable.SpecificationLike
import passengersplits.core.PassengerInfoRouterActor.ReportVoyagePaxSplit
import services.SDate.implicits._
import services.workloadcalculator.PaxLoadCalculator
import services.{SDate, WorkloadCalculatorTests}
import spatutorial.shared.PassengerSplits.{PaxTypeAndQueueCount, VoyagePaxSplits}
import spatutorial.shared.PaxTypes.EeaMachineReadable
import spatutorial.shared.SplitRatiosNs.{SplitRatio, SplitRatios}
import spatutorial.shared._
import spatutorial.shared.Queues._

import scala.concurrent.Await
import scala.concurrent.duration._

object SplitsMocks {

  class MockSplitsActor extends Actor {
    def receive: Receive = {
      case ReportVoyagePaxSplit(dp, carrierCode, voyageNumber, scheduledArrivalDateTime) =>
        val splits: VoyagePaxSplits = testVoyagePaxSplits(scheduledArrivalDateTime)


        sender ! splits
    }
  }

  def testVoyagePaxSplits(scheduledArrivalDateTime: SDateLike) = {
    val expectedPaxSplits = List(
      PaxTypeAndQueueCount(EeaMachineReadable, EeaDesk, 10),
      PaxTypeAndQueueCount(EeaMachineReadable, EGate, 10)
    )
    val splits = VoyagePaxSplits("LGW", "BA", "0001", expectedPaxSplits.map(_.paxCount).sum, scheduledArrivalDateTime, expectedPaxSplits)
    splits
  }
}


class WorkloadWithAdvPaxSplitsTests extends TestKit(ActorSystem("WorkloadwithAdvPaxInfoSplits", ConfigFactory.empty())) with SpecificationLike {
  isolated

  implicit val timeout: Timeout = 3 seconds

  import WorkloadCalculatorTests._

  import scala.concurrent.ExecutionContext.Implicits.global

  def voyagePaxSplitsAsPaxLoadPaxTypeAndQueueCount(splits: VoyagePaxSplits) = {
    Some(
      SplitRatios(
        SplitRatio(
          PaxTypeAndQueue(EeaMachineReadable, EeaDesk), 0.5),
        SplitRatio(
          PaxTypeAndQueue(EeaMachineReadable, EGate), 0.5)))
  }

  "VoyagePaxSplits can  be converted to a SplitRatios as used by the extant PaxLoadCalculator" >> {
    val splits = SplitsMocks.testVoyagePaxSplits(SDate(2017, 1, 1, 12, 20))
    voyagePaxSplitsAsPaxLoadPaxTypeAndQueueCount(splits) === Some(
      SplitRatios(
        SplitRatio(
          PaxTypeAndQueue(EeaMachineReadable, EeaDesk), 0.5),
        SplitRatio(
          PaxTypeAndQueue(EeaMachineReadable, EGate), 0.5)))
  }


  "WorkloadCalculator with AdvancePassengerInfoSplitProvider" >> {
    """Given AdvancePassengerInfo paxSplits for a flight
      |When we calculate paxload then it uses the splits from the actor
    """.stripMargin in {
      implicit def tupleToPaxTypeAndQueueCounty(t: (PaxType, String)): PaxTypeAndQueue = PaxTypeAndQueue(t._1, t._2)

      "queueWorkloadCalculator" in {
        def defaultProcTimesProvider(paxTypeAndQueue: PaxTypeAndQueue) = 1

        "with simple pax splits all at the same paxType" in {
          val passengerInfoRouterActor: AskableActorRef = system.actorOf(Props(classOf[MockSplitsActor]))

          def splitRatioProvider(flight: ApiFlight): Option[SplitRatios] = {
            FlightParsing.parseIataToCarrierCodeVoyageNumber(flight.IATA) match {
              case Some((cc, number)) =>
                val futResp = passengerInfoRouterActor ? ReportVoyagePaxSplit(flight.Origin, cc, number, SDate.parseString(flight.SchDT))
                val splitsFut = futResp.map {
                  case voyagePaxSplits: VoyagePaxSplits =>
                    voyagePaxSplitsAsPaxLoadPaxTypeAndQueueCount(voyagePaxSplits)
                }
                Await.result(splitsFut, 1 second)
            }
          }

          val calcPaxTypeAndQueueCountForAFlightOverTime = PaxLoadCalculator.voyagePaxSplitsFlowOverTime(splitRatioProvider) _

          val sut = PaxLoadCalculator.queueWorkAndPaxLoadCalculator(calcPaxTypeAndQueueCountForAFlightOverTime, defaultProcTimesProvider) _

          "Examining workloads specifically" in {

            "Given a single flight with one minute's worth of flow when we apply paxSplits and flow rate, then we should see flow applied to the flight, and splits applied to that flow" in {
              val startTime: String = "2020-01-01T00:00:00Z"
              val flights = List(apiFlight("BA0001", "LHR", 20, startTime))

              val workloads = extractWorkloads(sut(flights)).toSet
              val expected = Map(
                Queues.EGate -> List(WL(asMillis("2020-01-01T00:00:00Z"), 10.0)),
                Queues.EeaDesk -> List(WL(asMillis("2020-01-01T00:00:00Z"), 10.0))).toSet
              workloads === expected
            }
          }
        }

      }
    }
  }
}
