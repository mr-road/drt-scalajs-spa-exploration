package services

import org.specs2.mutable.Specification
import server.protobuf.messages.ShiftMessage.{ShiftMessage, ShiftsMessage}
import actors.ShiftsMessageParser._

class ShiftsToProtoBufSpec extends Specification {

  "shiftStringToShiftMessage" should {
    "take a single shift string and return a case class representing it" in {
      val shiftString = "shift name, T1, 20/01/2017, 10:00, 20:00, 9"
      val shiftMessage = shiftStringToShiftMessage(shiftString)

      val expected = Some(ShiftMessage(
        Some("shift name"),
        Some("T1"),
        Some("20/01/2017"),
        Some("10:00"),
        Some("20:00"),
        Some("9")))

      shiftMessage === expected
    }
  }

  "shiftsStringToShiftsMessage" should {
    "take some lines of shift strings and return a case class representing all of the shifts" in {
      val shiftsString =
        """
          |shift name, T1, 20/01/2017, 10:00, 20:00, 5
          |shift name, T1, 20/01/2017, 10:00, 20:00, 9
        """.stripMargin.trim

      val shiftsMessage = shiftsStringToShiftsMessage(shiftsString)

      val expected = ShiftsMessage(List(
        ShiftMessage(
          Some("shift name"), Some("T1"), Some("20/01/2017"), Some("10:00"), Some("20:00"), Some("5")
        ), ShiftMessage(
          Some("shift name"), Some("T1"), Some("20/01/2017"), Some("10:00"), Some("20:00"), Some("9")
        )))

      shiftsMessage === expected
    }
  }

  "shiftsMessageToShiftsString" should {
    "take a ShiftsMessage and return the multiline string representation" in {
      val shiftsMessage = ShiftsMessage(List(ShiftMessage(
        Some("shift name"), Some("T1"), Some("20/01/2017"), Some("10:00"), Some("20:00"), Some("5")
      ), ShiftMessage(
        Some("shift name"), Some("T1"), Some("20/01/2017"), Some("10:00"), Some("20:00"), Some("9")
      )))

      val shiftsString = shiftsMessageToShiftsString(shiftsMessage)

      val expected =
        """
          |shift name, T1, 20/01/2017, 10:00, 20:00, 5
          |shift name, T1, 20/01/2017, 10:00, 20:00, 9
        """.stripMargin.trim

      shiftsString === expected
    }
  }
}
