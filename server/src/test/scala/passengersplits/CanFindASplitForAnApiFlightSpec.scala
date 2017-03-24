package passengersplits

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AfterAll
import passengersplits.core.PassengerInfoRouterActor.{PassengerSplitsAck, ReportVoyagePaxSplit}
import passengersplits.core.{Core, CoreActors}
import passengersplits.parsing.PassengerInfoParser.{EventCodes, PassengerInfoJson, VoyagePassengerInfo}
import services.SDate
import services.SDate.implicits._
import spatutorial.shared.PassengerSplits.{FlightNotFound, PaxTypeAndQueueCount, VoyagePaxSplits}
import spatutorial.shared.PaxTypes._
import spatutorial.shared.Queues._
import spatutorial.shared.{ApiFlight, PassengerQueueTypes, SDateLike}


class CanFindASplitForAnApiFlightSpec extends
  TestKit(ActorSystem("CanFindASplitForAnApiFlightSpec", ConfigFactory.empty())) with AfterAll with SpecificationLike with ImplicitSender with CoreActors with Core {
  test =>

  isolated
  ignoreMsg {
    case PassengerSplitsAck => true
  }


  "Can parse an IATA to carrier code and voyage number" >> {
    import spatutorial.shared.FlightParsing._
    parseIataToCarrierCodeVoyageNumber("FR8364") === Some(("FR", "8364"))
    parseIataToCarrierCodeVoyageNumber("RY836") === Some(("RY", "836"))
    parseIataToCarrierCodeVoyageNumber("RY836F") === Some(("RY", "836"))
  }

  "Should be able to find a flight" >> {
    "Given a single flight, with just one GBR passenger" in {
      flightPassengerReporter ! VoyagePassengerInfo(EventCodes.DoorsClosed, "LGW", "BRG", "12345", "EZ", "2017-04-02", "15:33:00",
        PassengerInfoJson(Some("P"), "GBR", "EEA", None) :: Nil)

      "When we ask for a report of voyage pax splits then we should see pax splits of the 1 passenger in eeaDesk queue" in {
        val flightScheduledDateTime = SDate(2017, 4, 2, 15, 33)
        flightPassengerReporter ! ReportVoyagePaxSplit("LGW", "EZ", "12345", flightScheduledDateTime)
        val expectedPaxSplits = List(
          PaxTypeAndQueueCount(EeaMachineReadable, EeaDesk, 1),
          PaxTypeAndQueueCount(EeaMachineReadable, EGate, 0)
        )
        expectMsg(VoyagePaxSplits("LGW", "EZ", "12345", 1, flightScheduledDateTime, expectedPaxSplits))
        success
      }
    }

    "Given a single flight STN EZ789 flight, with just one GBR and one nationals passenger" in {
      "When we ask for a report of voyage pax splits" in {
        flightPassengerReporter ! VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "BRG", "789", "EZ", "2015-06-01", "13:55:00",
          PassengerInfoJson(Some("P"), "GBR", "EEA", None) ::
            PassengerInfoJson(Some("P"), "NZL", "", None) ::
            Nil)

        val scheduleArrivalTime = SDate(2015, 6, 1, 13, 55)
        flightPassengerReporter ! ReportVoyagePaxSplit("STN", "EZ", "789", scheduleArrivalTime)

        val expectedPaxSplits = List(
          PaxTypeAndQueueCount(EeaMachineReadable, EeaDesk, 1),
          PaxTypeAndQueueCount(EeaMachineReadable, EGate, 0),
          PaxTypeAndQueueCount(NonVisaNational, NonEeaDesk, 1)
        )
        expectMsg(VoyagePaxSplits("STN", "EZ", "789", 2, scheduleArrivalTime, expectedPaxSplits))
        success
      }
    }
    "Given a single flight STN BA978 flight, with 100 passengers, and a default egate usage of 60%" in {
      "When we ask for a report of voyage pax splits" in {
        flightPassengerReporter ! VoyagePassengerInfo(EventCodes.DoorsClosed, "STN", "BCN", "978", "BA", "2015-07-12", "10:22:00",
          List.tabulate(80)(passengerNumber => PassengerInfoJson(Some("P"), "GBR", "EEA", Some((passengerNumber % 60 + 16).toString))) :::
            List.tabulate(20)(_ => PassengerInfoJson(Some("P"), "NZL", "", None)))

        val scheduleArrivalSDate: SDateLike = SDate(2015, 7, 12, 10, 22)
        flightPassengerReporter ! ReportVoyagePaxSplit("STN", "BA", "978", scheduleArrivalSDate)
        expectMsg(VoyagePaxSplits("STN", "BA", "978", 100, scheduleArrivalSDate, List(
          PaxTypeAndQueueCount(EeaMachineReadable, EeaDesk, 32),
          PaxTypeAndQueueCount(EeaMachineReadable, EGate, 48),
          PaxTypeAndQueueCount(NonVisaNational, NonEeaDesk, 20)
        )))
        success
      }
    }

    "Given no flights" in {
      "When we ask for a report of voyage pax splits of a flight we don't know about then we get FlightNotFound " in {
        flightPassengerReporter ! ReportVoyagePaxSplit("NON", "DNE", "999", SDate(2015, 6, 1, 13, 55))
        expectMsg(FlightNotFound("DNE", "999", SDate(2015, 6, 1, 13, 55)))
        success
      }
    }
  }

  def afterAll() = system.terminate()
}




