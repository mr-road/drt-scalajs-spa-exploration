package services

import org.specs2.mutable.SpecificationLike
import passengersplits.core.PassengerInfoRouterActor
import spatutorial.shared.SplitRatiosNs.{SplitRatio, SplitRatios}
import spatutorial.shared._

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

class PaxSplitsProviderTests extends SpecificationLike {

  def apiFlight(iataFlightCode: String, schDT: String): ApiFlight =
    ApiFlight(
      Operator = "",
      Status = "",
      EstDT = "",
      ActDT = "",
      EstChoxDT = "",
      ActChoxDT = "",
      Gate = "",
      Stand = "",
      MaxPax = 1,
      ActPax = 0,
      TranPax = 0,
      RunwayID = "",
      BaggageReclaimId = "",
      FlightID = 2,
      AirportID = "STN",
      Terminal = "1",
      ICAO = "",
      IATA = iataFlightCode,
      Origin = "",
      PcpTime = 0,
      SchDT = schDT
    )

  "Voyage Number should be padded to 4 digits" >> {
    "3 digits should pad to 4" in {
    PassengerInfoRouterActor.padTo4Digits("123") === "0123"}
    "4 digitis should remain 4 " in {
      PassengerInfoRouterActor.padTo4Digits("0123") === "0123"
    }
    "we think 5 is invalid, but we should return unharmed" in {
      PassengerInfoRouterActor.padTo4Digits("45123") === "45123"
    }
  }

  "Splits from multiple providers" >> {

    "Given 1 provider with splits for a flight, when we ask for splits then we should see Some()" >> {
      def provider(apiFlight: ApiFlight) = Some[SplitRatios](SplitRatios())

      val providers: List[(ApiFlight) => Some[SplitRatios]] = List(provider)

      val flight = apiFlight("BA0001", "2016-01-01T00:00:00")

      val result = SplitsProvider.splitsForFlight(providers)(flight)

      result.isDefined
    }

    "Given 2 providers, the 1st with splits and 2nd without, when we ask for splits then we should see Some()" >> {
      def providerWith(apiFlight: ApiFlight) = Some[SplitRatios](SplitRatios())

      def providerWithout(apiFlight: ApiFlight) = None

      val providers: List[(ApiFlight) => Option[SplitRatios]] = List(providerWith, providerWithout)

      val flight = apiFlight("BA0001", "2016-01-01T00:00:00")

      val result = SplitsProvider.splitsForFlight(providers)(flight)

      result.isDefined
    }

    "Given 2 providers, the 1st without splits and 2nd with, when we ask for splits then we should see Some()" >> {
      def providerWith(apiFlight: ApiFlight) = None

      def providerWithout(apiFlight: ApiFlight) = Some[SplitRatios](SplitRatios())

      val providers: List[(ApiFlight) => Option[SplitRatios]] = List(providerWith, providerWithout)

      val flight = apiFlight("BA0001", "2016-01-01T00:00:00")

      val result: Option[SplitRatios] = SplitsProvider.splitsForFlight(providers)(flight)

      result.isDefined
    }

    "Given a stateful, non-idempotent provider, we get the different result each time" >> {
      val ratios1 = SplitRatios(
        SplitRatio(PaxTypeAndQueue(PaxTypes.EeaNonMachineReadable, "eea"), 23),
        SplitRatio(PaxTypeAndQueue(PaxTypes.EeaNonMachineReadable, "visa"), 10))
      val ratios2 = SplitRatios(
        SplitRatio(PaxTypeAndQueue(PaxTypes.EeaNonMachineReadable, "eea"), 4),
        SplitRatio(PaxTypeAndQueue(PaxTypes.EeaNonMachineReadable, "visa"), 3))

      val ratios = mutable.Queue(ratios1, ratios2)

      def statefulProvider(apiFlight: ApiFlight): Option[SplitRatios] = {
        val head = ratios.dequeue()
        Option(head)
      }


      val providers: List[(ApiFlight) => Option[SplitRatios]] = List(statefulProvider)

      val flight = apiFlight("BA0001", "2016-01-01T00:00:00")

      val splitsForFlight = SplitsProvider.splitsForFlight(providers) _

      val result1: Option[SplitRatios] = splitsForFlight(flight)

      assert(result1 === Some(ratios1))

      val result2: Option[SplitRatios] = splitsForFlight(flight)

      result2 == Some(ratios2)


    }
  }
}
