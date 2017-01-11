package spatutorial.client.components

import diode.react.ModelProxy
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import spatutorial.client.logger._
import spatutorial.client.services.JSDateConversions._
import spatutorial.client.services._
import spatutorial.shared.{SDate, WorkloadsHelpers}

import scala.collection.immutable.{NumericRange, Seq}
import scala.scalajs.js.Date
import scala.util.{Success, Try}

object Staffing {

  case class Props()

  class Backend($: BackendScope[Props, Unit]) {
    def render(props: Props) = {
      val shiftsRawRCP = SPACircuit.connect(_.shiftsRaw)
      shiftsRawRCP((shiftsMP: ModelProxy[String]) => {
        val rawShifts = shiftsMP()

        val shifts: List[Try[Shift]] = Shifts(rawShifts).parsedShifts.toList
        val didParseFail = shifts exists (s => s.isFailure)


        val today = new Date
        val date: SDate = SDate(today.getFullYear, today.getMonth, today.getDate, 0, 0)
        val shiftExamples = Seq(
          s"Morning shift,${today.getDate}/${today.getMonth + 1}/${today.getFullYear - 2000},00:00,07:59,5",
          s"Day shift,${today.getDate}/${today.getMonth + 1}/${today.getFullYear - 2000},08:00,15:59,20",
          s"Evening shift,${today.getDate}/${today.getMonth + 1}/${today.getFullYear - 2000},16:00,23:59,15"
        )
        <.div(
          <.h1("Staffing"),
          <.h2("Shifts"),
          <.div("One shift per line with values separated by commas, e.g.:"),
          <.div(shiftExamples.map(<.p(_))),
          <.textarea(^.value := rawShifts,
            ^.className := "staffing-editor",
            ^.onChange ==> ((e: ReactEventI) => shiftsMP.dispatch(UpdateShifts(e.target.value)))),
          <.h2("Staff over the day"), if (didParseFail) {
            <.div(^.className := "error", "Error in shifts")
          }
          else {
            val successfulShifts: List[Shift] = shifts.collect { case Success(s) => s }
            val ss = ShiftService(successfulShifts)

            staffingTableHourPerColumn(daysWorthOf15Minutes(date), ss)
          }
        )
      })
    }
  }

  def daysWorthOf15Minutes(startOfDay: SDate): NumericRange[Long] = {
    val timeMinPlusOneDay = startOfDay.addDays(1)
    val daysWorthOf15Minutes = startOfDay.millisSinceEpoch until timeMinPlusOneDay.millisSinceEpoch by (WorkloadsHelpers.oneMinute * 15)
    daysWorthOf15Minutes
  }

  def staffingTableHourPerColumn(daysWorthOf15Minutes: NumericRange[Long], ss: ShiftService) = {
    <.table(
      daysWorthOf15Minutes.grouped(8).flatMap {
       hoursWorthOf15Minutes =>
        Seq(
          <.tr({
          hoursWorthOf15Minutes.map((t: Long) => {
            val d = new Date(t)
            val display = s"${d.getHours}:${d.getMinutes}"
            <.td(^.key := t, display)
          })
        }),
        <.tr(
          hoursWorthOf15Minutes.map(t => <.td(^.key := t, s"${StaffMovements.staffAt(ss)(Nil)(t)}"))
        ))
      }
    )
  }


  private def simpleStaffingTable(daysWorthOf15Minutes: NumericRange[Long], ss: ShiftService) = {
    <.table(
      <.tr({
        daysWorthOf15Minutes.map((t: Long) => {
          val d = new Date(t)
          val display = s"${d.getHours}:${d.getMinutes}"
          <.td(^.key := t, display)
        })
      }),
      <.tr(
        daysWorthOf15Minutes.map(t => <.td(^.key := t, s"${StaffMovements.staffAt(ss)(Nil)(t)}"))
      )
    )
  }

  def apply(): ReactElement =
    component(Props())

  private val component = ReactComponentB[Props]("Staffing")
    .renderBackend[Backend]
    .build
}
