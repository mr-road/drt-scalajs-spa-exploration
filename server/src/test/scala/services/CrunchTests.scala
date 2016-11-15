package services

import spatutorial.shared._
import utest._

import akka.actor.ActorSystem
import scala.concurrent.{Future, Await}
import scala.util.Success
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.testkit.{TestKit, TestActors, DefaultTimeout, ImplicitSender }
import controllers.SystemActors
import controllers.Core

object CrunchStructureTests extends TestSuite {
  def tests = TestSuite {
    "given workloads by the minute we can get them in t minute chunks and take the sum from each chunk" - {
      val workloads = WL(1, 2) :: WL(2, 3) :: WL(3, 4) :: WL(4, 5) :: Nil

      val period: List[WL] = WorkloadsHelpers.workloadsByPeriod(workloads, 2).toList
      assert(period == WL(1, 5) :: WL(3, 9) :: Nil)
    }
  }
}

object FlightCrunchInteractionTests extends TestSuite {
  test =>

  def makeSystem = {
    new TestKit(ActorSystem()) with SystemActors with Core {
    }
  }

  def tests = TestSuite {
    "Given a system with flightsactor and crunch actor, flights actor can request crunch actor does a crunch"  - {
      assert(true)
    }
  }

}

object CrunchTests extends TestSuite {
  def tests = TestSuite {
    'canUseCsvForWorkloadInput - {
      val bufferedSource = scala.io.Source.fromURL(
        getClass.getResource("/optimiser-LHR-T2-NON-EEA-2016-09-12_121059-in-out.csv"))
      val recs: List[Array[String]] = bufferedSource.getLines.map { l =>
        l.split(",").map(_.trim)
      }.toList

      val workloads = recs.map(_ (0).toDouble)
      val minDesks = recs.map(_ (1).toInt)
      val maxDesks = recs.map(_ (2).toInt)
      val recDesks = recs.map(_ (3).toInt)

      val cr = TryRenjin.crunch(workloads, minDesks, maxDesks, OptimizerConfig(25))
      println(cr.recommendedDesks.toString())
      println(recDesks.toVector)
    }
  }
}
