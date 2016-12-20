package spatutorial.client.services

import diode.data._
import spatutorial.client.components.TableTerminalDeskRecs.{QueueDetailsRow, TerminalUserDeskRecsRow}
import spatutorial.shared.FlightsApi._
import spatutorial.shared._
import utest._

import scala.collection.immutable.{IndexedSeq, Map, Seq}

object TerminalUserDeskRecsTests extends TestSuite {

  import spatutorial.client.TableViewUtils._

  def tests = TestSuite {

    "Given crunch results and simulation results for one minute in one queue, when we ask for a TerminalUserDeskRecsRow " +
      "then we should see the data for that minute" - {
      val workload = Map("eeaDesk" -> List(5.0))
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((Ready(CrunchResult(IndexedSeq(1), Seq(10))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 1))))))
      )
      val simulationResult = Map(
        "eeaDesk" ->
          Ready(SimulationResult(IndexedSeq(DeskRec(0, 2)), Seq(5)))
      )

      val result = terminalUserDeskRecsRows(Seq(0L), workload, queueCrunchResults, simulationResult)

      val expected = Seq(TerminalUserDeskRecsRow(0L, Seq(
        Some(QueueDetailsRow(0, pax = 5, crunchDeskRec = 1, userDeskRec = DeskRecTimeslot("0", 2), waitTimeWithCrunchDeskRec = 10, waitTimeWithUserDeskRec = 5, "eeaDesk")))))

      assert(expected == result)
    }

    "Given crunch results and simulation results for one minute in 2 queues, when we ask for a TerminalUserDeskRecsRow " +
      "then we should see the data for that minute from both queues" - {
      val workload = Map(
        "eeaDesk" -> List(5.0),
        "eGate" -> List(6.0)
      )
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((Ready(CrunchResult(IndexedSeq(1), Seq(10))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 1)))))),
        "eGate" ->
          Ready((Ready(CrunchResult(IndexedSeq(2), Seq(20))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 2)))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 2)), Seq(5))),
        "eGate" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 4)), Seq(10))))

      val result = terminalUserDeskRecsRows(Seq(0L), workload, queueCrunchResults, simulationResult)

      val expected = Seq(TerminalUserDeskRecsRow(0L, Seq(
        QueueDetailsRow(0, pax = 5, crunchDeskRec = 1, userDeskRec = DeskRecTimeslot("0", 2), waitTimeWithCrunchDeskRec = 10, waitTimeWithUserDeskRec = 5, "eeaDesk"),
        QueueDetailsRow(0, pax = 6, crunchDeskRec = 2, userDeskRec = DeskRecTimeslot("0", 4), waitTimeWithCrunchDeskRec = 20, waitTimeWithUserDeskRec = 10, "eGate")
      ).map(Some(_))))

      assert(expected == result)
    }

    "Given crunch results and simulation results for one minute in 3 queues, when we ask for a TerminalUserDeskRecsRow " +
      "then we should see the data for that minute from all 3 queues" - {
      val workload = Map(
        "eeaDesk" -> List(5.0),
        "nonEeaDesk" -> List(6.0),
        "eGate" -> List(7.0)
      )
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((Ready(CrunchResult(IndexedSeq(1), Seq(10))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 1)))))),
        "nonEeaDesk" ->
          Ready((Ready(CrunchResult(IndexedSeq(1), Seq(10))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 1)))))),
        "eGate" ->
          Ready((Ready(CrunchResult(IndexedSeq(2), Seq(20))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 2)))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 2)), Seq(5))),
        "nonEeaDesk" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 2)), Seq(5))),
        "eGate" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 4)), Seq(10))))

      val result = terminalUserDeskRecsRows(Seq(0L), workload, queueCrunchResults, simulationResult)

      val expected = Seq(TerminalUserDeskRecsRow(0L, Seq(
        QueueDetailsRow(0, pax = 5, crunchDeskRec = 1, userDeskRec = DeskRecTimeslot("0", 2), waitTimeWithCrunchDeskRec = 10, waitTimeWithUserDeskRec = 5, "eeaDesk"),
        QueueDetailsRow(0, pax = 6, crunchDeskRec = 1, userDeskRec = DeskRecTimeslot("0", 2), waitTimeWithCrunchDeskRec = 10, waitTimeWithUserDeskRec = 5, "nonEeaDesk"),
        QueueDetailsRow(0, pax = 7, crunchDeskRec = 2, userDeskRec = DeskRecTimeslot("0", 4), waitTimeWithCrunchDeskRec = 20, waitTimeWithUserDeskRec = 10, "eGate")
      ).map(Some(_))))

      assert(expected == result)
    }

    "Given crunch results and simulation results for 2 minutes in 1 queue, when we ask for a TerminalUserDeskRecsRow " +
      "then we should see the highest values for those minutes" - {
      val workload = Map(
        "eeaDesk" -> List(5.0)
      )
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((Ready(CrunchResult(IndexedSeq(2, 1), Seq(10, 20))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 1), DeskRecTimeslot("1", 2)))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 2), DeskRec(1, 1)), Seq(5, 10))))

      val result = terminalUserDeskRecsRows(List(0L, 60000L), workload, queueCrunchResults, simulationResult)

      val expected = List(TerminalUserDeskRecsRow(0L, Seq(
        QueueDetailsRow(0, pax = 5, crunchDeskRec = 2, userDeskRec = DeskRecTimeslot("0", 2), waitTimeWithCrunchDeskRec = 20, waitTimeWithUserDeskRec = 10, "eeaDesk")
      ).map(Some(_))))

      assert(expected == result)
    }

    "Given crunch results and simulation results for 2 minutes in 3 queues, when we ask for a TerminalUserDeskRecsRow " +
      "then we should see the highest values from each queue for those minutes" - {
      val workload = Map(
        "eeaDesk" -> List(5.0, 1.0),
        "eGate" -> List(6.0, 1.0),
        "nonEeaDesk" -> List(7.0, 1.0)
      )
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((Ready(CrunchResult(recommendedDesks = IndexedSeq(2, 1), waitTimes = Seq(10, 20))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 1), DeskRecTimeslot("1", 2)))))),
        "eGate" ->
          Ready((Ready(CrunchResult(IndexedSeq(23, 3), Seq(20, 27))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 5), DeskRecTimeslot("1", 15)))))),
        "nonEeaDesk" ->
          Ready((Ready(CrunchResult(IndexedSeq(15, 21), Seq(34, 23))), Ready(UserDeskRecs(Seq(DeskRecTimeslot("0", 45), DeskRecTimeslot("1", 30)))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 2), DeskRec(1, 1)), Seq(5, 10))),
        "eGate" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 5), DeskRec(1, 7)), Seq(15, 25))),
        "nonEeaDesk" -> Ready(SimulationResult(IndexedSeq(DeskRec(0, 11), DeskRec(1, 8)), Seq(30, 14))))

      val result = terminalUserDeskRecsRows(List(0L, 60000L), workload, queueCrunchResults, simulationResult)

      val expected = Seq(TerminalUserDeskRecsRow(0L, Seq(
        QueueDetailsRow(0, pax = 6, crunchDeskRec = 2, userDeskRec = DeskRecTimeslot("0", 2), waitTimeWithCrunchDeskRec = 20, waitTimeWithUserDeskRec = 10, "eeaDesk"),
        QueueDetailsRow(0, pax = 8, crunchDeskRec = 21, userDeskRec = DeskRecTimeslot("0", 11), waitTimeWithCrunchDeskRec = 34, waitTimeWithUserDeskRec = 30, "nonEeaDesk"),
        QueueDetailsRow(0, pax = 7, crunchDeskRec = 23, userDeskRec = DeskRecTimeslot("0", 7), waitTimeWithCrunchDeskRec = 27, waitTimeWithUserDeskRec = 25, "eGate")
      ).map(Some(_))))

      assert(expected == result)
    }

    val fifteenMinutesLater: Long = 15L * 60000

    "Given crunch results and simulation results for 16 minutes in 1 queue, when we ask for TerminalUserDeskRecsRows " +
      "then we should see the highest values for those minutes" - {
      val workload = Map(
        "eeaDesk" -> List(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0)
      )
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((
            Ready(CrunchResult(IndexedSeq(1, 1, 1, 1, 1, 7, 1, 1, 1, 1, 5, 1, 1, 1, 1, 3), Seq(1, 1, 1, 1, 1, 8, 1, 1, 1, 1, 5, 1, 1, 1, 1, 4))),
            Ready(UserDeskRecs(Seq(
              DeskRecTimeslot("0", 1), DeskRecTimeslot("1", 2),
              DeskRecTimeslot("2", 1), DeskRecTimeslot("3", 2),
              DeskRecTimeslot("4", 8), DeskRecTimeslot("5", 2),
              DeskRecTimeslot("6", 1), DeskRecTimeslot("7", 2),
              DeskRecTimeslot("8", 1), DeskRecTimeslot("9", 11),
              DeskRecTimeslot("10", 1), DeskRecTimeslot("11", 2),
              DeskRecTimeslot("12", 1), DeskRecTimeslot("13", 2),
              DeskRecTimeslot("14", 1), DeskRecTimeslot("15", 5)
            ))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(
          DeskRec(0, 1), DeskRec(1, 2),
          DeskRec(2, 1), DeskRec(3, 2),
          DeskRec(4, 8), DeskRec(5, 2),
          DeskRec(6, 1), DeskRec(7, 2),
          DeskRec(8, 1), DeskRec(9, 11),
          DeskRec(10, 1), DeskRec(11, 2),
          DeskRec(12, 1), DeskRec(13, 2),
          DeskRec(14, 1), DeskRec(15, 5)
        ), Seq(1, 1, 1, 1, 1, 12, 1, 1, 1, 1, 15, 1, 1, 1, 1, 9))))

      val timestamps = 0L to (60000 * 16) by 60000L
      val result = terminalUserDeskRecsRows(timestamps, workload, queueCrunchResults, simulationResult)

      val expected = Seq(
        TerminalUserDeskRecsRow(0L, Seq(Some(QueueDetailsRow(0L, pax = 75, crunchDeskRec = 7, userDeskRec = DeskRecTimeslot("0", 11), waitTimeWithCrunchDeskRec = 8, waitTimeWithUserDeskRec = 15, "eeaDesk")))),
        TerminalUserDeskRecsRow(fifteenMinutesLater, Seq(Some(QueueDetailsRow(fifteenMinutesLater, pax = 5, crunchDeskRec = 3, userDeskRec = DeskRecTimeslot("1", 5), waitTimeWithCrunchDeskRec = 4, waitTimeWithUserDeskRec = 9, "eeaDesk"))))
      ).map(Some(_))

      assert(expected == result)
    }

    "Given crunch results and simulation results for 32 minutes in 1 queue, when we ask for TerminalUserDeskRecsRows " +
      "then we should see the highest values for those minutes" - {
      val workload = Map("eeaDesk" -> List(
        5.0, 1.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0,

        5.0,
        5.0, 6.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,
        5.0, 5.0,

        5.0, 7.0
      ))
      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((
            Ready(CrunchResult(IndexedSeq(1, 1, 1, 1, 1, 7, 1, 1, 1, 1, 5, 1, 1, 1, 1, 3, 1, 1, 1, 1, 1, 9, 1, 1, 1, 1, 5, 1, 1, 1, 1, 3), Seq(1, 1, 1, 1, 1, 8, 1, 1, 1, 1, 5, 1, 1, 1, 1, 4, 1, 1, 1, 1, 1, 9, 1, 1, 1, 1, 5, 1, 1, 1, 1, 4))),
            Ready(UserDeskRecs(Seq(
              DeskRecTimeslot("0", 1), DeskRecTimeslot("1", 2),
              DeskRecTimeslot("2", 1), DeskRecTimeslot("3", 2),
              DeskRecTimeslot("4", 8), DeskRecTimeslot("5", 2),
              DeskRecTimeslot("6", 1), DeskRecTimeslot("7", 2),
              DeskRecTimeslot("8", 1), DeskRecTimeslot("9", 11),
              DeskRecTimeslot("10", 1), DeskRecTimeslot("11", 2),
              DeskRecTimeslot("12", 1), DeskRecTimeslot("13", 2),
              DeskRecTimeslot("14", 1), DeskRecTimeslot("15", 5),
              DeskRecTimeslot("16", 1), DeskRecTimeslot("17", 2),
              DeskRecTimeslot("18", 1), DeskRecTimeslot("19", 2),
              DeskRecTimeslot("20", 8), DeskRecTimeslot("21", 2),
              DeskRecTimeslot("22", 1), DeskRecTimeslot("23", 2),
              DeskRecTimeslot("24", 1), DeskRecTimeslot("25", 11),
              DeskRecTimeslot("26", 1), DeskRecTimeslot("27", 2),
              DeskRecTimeslot("28", 1), DeskRecTimeslot("29", 2),
              DeskRecTimeslot("30", 1), DeskRecTimeslot("31", 5)
            ))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(
          DeskRec(0, 1), DeskRec(1, 2),
          DeskRec(2, 1), DeskRec(3, 2),
          DeskRec(4, 8), DeskRec(5, 2),
          DeskRec(6, 1), DeskRec(7, 2),
          DeskRec(8, 1), DeskRec(9, 11),
          DeskRec(10, 1), DeskRec(11, 2),
          DeskRec(12, 1), DeskRec(13, 2),
          DeskRec(14, 1), DeskRec(15, 5),
          DeskRec(16, 1), DeskRec(17, 2),
          DeskRec(18, 1), DeskRec(19, 2),
          DeskRec(20, 8), DeskRec(21, 2),
          DeskRec(22, 1), DeskRec(23, 2),
          DeskRec(24, 1), DeskRec(25, 11),
          DeskRec(26, 1), DeskRec(27, 2),
          DeskRec(28, 1), DeskRec(29, 2),
          DeskRec(30, 1), DeskRec(31, 5)
        ), Seq(1, 1, 1, 1, 1, 12, 1, 1, 1, 1, 15, 1, 1, 1, 1, 9, 1, 1, 1, 1, 1, 12, 1, 1, 1, 1, 14, 1, 1, 1, 1, 9))))

      val timestamps = 0L to (60000 * 32) by 60000L
      val result = terminalUserDeskRecsRows(timestamps, workload, queueCrunchResults, simulationResult)

      val expected = Seq(
        TerminalUserDeskRecsRow(0L, Seq(Some(QueueDetailsRow(0L, pax = 71, crunchDeskRec = 7, userDeskRec = DeskRecTimeslot("0", 11), waitTimeWithCrunchDeskRec = 8, waitTimeWithUserDeskRec = 15, "eeaDesk")))),
        TerminalUserDeskRecsRow(fifteenMinutesLater, Seq(Some(QueueDetailsRow(fifteenMinutesLater, pax = 76, crunchDeskRec = 9, userDeskRec = DeskRecTimeslot("1", 11), waitTimeWithCrunchDeskRec = 9, waitTimeWithUserDeskRec = 14, "eeaDesk")))),
        TerminalUserDeskRecsRow(30L * 60000, Seq(Some(QueueDetailsRow(30L * 60000, pax = 12, crunchDeskRec = 3, userDeskRec = DeskRecTimeslot("2", 5), waitTimeWithCrunchDeskRec = 4, waitTimeWithUserDeskRec = 9, "eeaDesk"))))
      )

      assert(expected == result)
    }

    "Given crunch results and simulation results for 16 minutes in 2 queues, when we ask for TerminalUserDeskRecsRows " +
      "then we should see the highest values for those minutes in each queue" - {
      val workload = Map(
        "eeaDesk" -> List(
          1.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 9.0),
        "nonEeaDesk" -> List(
          2.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 5.0,
          5.0, 11.0)
      )

      val queueCrunchResults = Map(
        "eeaDesk" ->
          Ready((
            Ready(CrunchResult(IndexedSeq(1, 1, 1, 1, 1, 7, 1, 1, 1, 1, 5, 1, 1, 1, 1, 3), Seq(1, 1, 1, 1, 1, 8, 1, 1, 1, 1, 5, 1, 1, 1, 1, 4))),
            Ready(UserDeskRecs(Seq(
              DeskRecTimeslot("0", 1), DeskRecTimeslot("1", 2),
              DeskRecTimeslot("2", 1), DeskRecTimeslot("3", 2),
              DeskRecTimeslot("4", 8), DeskRecTimeslot("5", 2),
              DeskRecTimeslot("6", 1), DeskRecTimeslot("7", 2),
              DeskRecTimeslot("8", 1), DeskRecTimeslot("9", 11),
              DeskRecTimeslot("10", 1), DeskRecTimeslot("11", 2),
              DeskRecTimeslot("12", 1), DeskRecTimeslot("13", 2),
              DeskRecTimeslot("14", 1), DeskRecTimeslot("15", 5)
            ))))),
        "nonEeaDesk" ->
          Ready((
            Ready(CrunchResult(IndexedSeq(1, 1, 1, 1, 1, 8, 1, 1, 1, 1, 5, 1, 1, 1, 1, 4), Seq(1, 1, 1, 1, 1, 9, 1, 1, 1, 1, 5, 1, 1, 1, 1, 5))),
            Ready(UserDeskRecs(Seq(
              DeskRecTimeslot("0", 1), DeskRecTimeslot("1", 2),
              DeskRecTimeslot("2", 1), DeskRecTimeslot("3", 2),
              DeskRecTimeslot("4", 8), DeskRecTimeslot("5", 2),
              DeskRecTimeslot("6", 1), DeskRecTimeslot("7", 2),
              DeskRecTimeslot("8", 1), DeskRecTimeslot("9", 11),
              DeskRecTimeslot("10", 1), DeskRecTimeslot("11", 2),
              DeskRecTimeslot("12", 1), DeskRecTimeslot("13", 2),
              DeskRecTimeslot("14", 1), DeskRecTimeslot("15", 5)
            ))))))

      val simulationResult = Map(
        "eeaDesk" -> Ready(SimulationResult(IndexedSeq(
          DeskRec(0, 1), DeskRec(1, 2),
          DeskRec(2, 1), DeskRec(3, 2),
          DeskRec(4, 8), DeskRec(5, 2),
          DeskRec(6, 1), DeskRec(7, 2),
          DeskRec(8, 1), DeskRec(9, 11),
          DeskRec(10, 1), DeskRec(11, 2),
          DeskRec(12, 1), DeskRec(13, 2),
          DeskRec(14, 1), DeskRec(15, 5)
        ), Seq(1, 1, 1, 1, 1, 12, 1, 1, 1, 1, 15, 1, 1, 1, 1, 9))),
        "nonEeaDesk" -> Ready(SimulationResult(IndexedSeq(
          DeskRec(0, 1), DeskRec(1, 2),
          DeskRec(2, 1), DeskRec(3, 2),
          DeskRec(4, 8), DeskRec(5, 2),
          DeskRec(6, 1), DeskRec(7, 2),
          DeskRec(8, 1), DeskRec(9, 12),
          DeskRec(10, 1), DeskRec(11, 2),
          DeskRec(12, 1), DeskRec(13, 2),
          DeskRec(14, 1), DeskRec(15, 5)
        ), Seq(1, 1, 1, 1, 1, 12, 1, 1, 1, 1, 16, 1, 1, 1, 1, 10)))
      )

      val timestamps = 0L to (60000 * 16) by 60000L
      val result = terminalUserDeskRecsRows(timestamps, workload, queueCrunchResults, simulationResult)

      val expected = Seq(
        TerminalUserDeskRecsRow(0L, Seq(
          Some(QueueDetailsRow(0L, pax = 71, crunchDeskRec = 7,
            userDeskRec = DeskRecTimeslot("0", 11), waitTimeWithCrunchDeskRec = 8, waitTimeWithUserDeskRec = 15, "eeaDesk")),
          Some(QueueDetailsRow(0L, pax = 72, crunchDeskRec = 8,
            userDeskRec = DeskRecTimeslot("0", 12), waitTimeWithCrunchDeskRec = 9, waitTimeWithUserDeskRec = 16, "nonEeaDesk")),
          None)),
        TerminalUserDeskRecsRow(fifteenMinutesLater, Seq[Option[QueueDetailsRow]](
          Some(QueueDetailsRow(fifteenMinutesLater, pax = 9, crunchDeskRec = 3,
            userDeskRec = DeskRecTimeslot("1", 5), waitTimeWithCrunchDeskRec = 4, waitTimeWithUserDeskRec = 9, "eeaDesk")),
          Some(QueueDetailsRow(fifteenMinutesLater, pax = 11, crunchDeskRec = 4,
            userDeskRec = DeskRecTimeslot("1", 5), waitTimeWithCrunchDeskRec = 5, waitTimeWithUserDeskRec = 10, "nonEeaDesk")),
          None)))

//      val actual = List(
//        TerminalUserDeskRecsRow(0,
//          List(Some(QueueDetailsRow(0, 71, 7, DeskRecTimeslot(0, 11), 8, 15, eeaDesk))
//            , Some(QueueDetailsRow(0, 72, 8, DeskRecTimeslot(0, 12), 9, 16, nonEeaDesk)),
//            None)),
//        TerminalUserDeskRecsRow(60000,
//          List(None)),
//        TerminalUserDeskRecsRow(120000,
//          List(None)),
//        TerminalUserDeskRecsRow(180000, List(None)),
//        TerminalUserDeskRecsRow(240000, List(None)),
//        TerminalUserDeskRecsRow(300000, List(None)),
//        TerminalUserDeskRecsRow(360000, List(None)),
//        TerminalUserDeskRecsRow(420000, List(None)),
//        TerminalUserDeskRecsRow(480000, List(None)),
//        TerminalUserDeskRecsRow(540000, List(None)),
//        TerminalUserDeskRecsRow(600000, List(None)),
//        TerminalUserDeskRecsRow(660000, List(None)),
//        TerminalUserDeskRecsRow(720000, List(None)),
//        TerminalUserDeskRecsRow(780000, List(None)),
//        TerminalUserDeskRecsRow(840000, List(None)),
//        TerminalUserDeskRecsRow(900000, List(Some(QueueDetailsRow(900000, 9, 3, DeskRecTimeslot(1, 5), 4, 9, eeaDesk))
//          , Some(QueueDetailsRow(900000, 11, 4, DeskRecTimeslot(1, 5), 5, 10, nonEeaDesk)), None)),
//        TerminalUserDeskRecsRow(960000, List(None)))

      assert(expected == result)
    }
  }
}
