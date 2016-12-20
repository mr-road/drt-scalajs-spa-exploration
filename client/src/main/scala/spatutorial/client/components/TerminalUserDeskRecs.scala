package spatutorial.client.components

import diode.data.{Pot, Ready}
import diode.react._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import spatutorial.client.TableViewUtils
import spatutorial.client.TableViewUtils._
import spatutorial.client.logger._
import spatutorial.client.modules.FlightsView
import spatutorial.client.services.HandyStuff.QueueUserDeskRecs
import spatutorial.client.services._
import spatutorial.shared.FlightsApi.{Flights, QueueName, TerminalName}
import spatutorial.shared._

import scala.collection.immutable.{Map, NumericRange, Seq}
import scala.scalajs.js.Date

object TerminalUserDeskRecs {

  case class Props(terminalName: TerminalName,
                   workloads: Map[QueueName, Seq[Int]],
                   userDeskRecs: Map[QueueName, UserDeskRecs])

  val component = ReactComponentB[Props]("TerminalUserDeskRecs")
    .render_P(props =>
      <.table(
        <.tr(<.td())
      )
    )
}

object jsDateFormat {

  def zeroPadTo2Digits(number: Int) = {
    if (number < 10)
      "0" + number
    else
      number.toString
  }

  def formatDate(date: Date): String = {
    val formattedDate: String = date.getFullYear() + "-" + zeroPadTo2Digits(date.getMonth() + 1) + "-" + zeroPadTo2Digits(date.getDate()) + " " + date.toLocaleTimeString().replaceAll(":00$", "")
    formattedDate
  }
}


object TableTerminalDeskRecs {
  // shorthand for styles
  @inline private def bss = GlobalStyles.bootstrapStyles

  case class QueueDetailsRow(
                              timestamp: Long,
                              pax: Double,
                              crunchDeskRec: Int,
                              userDeskRec: DeskRecTimeslot,
                              waitTimeWithCrunchDeskRec: Int,
                              waitTimeWithUserDeskRec: Int,
                              queueName: QueueName
                            )

  case class TerminalUserDeskRecsRow(time: Long, queueDetails: Seq[Option[QueueDetailsRow]])


  case class Props(
                    terminalName: String,
                    items: Seq[TerminalUserDeskRecsRow],
                    flights: Pot[Flights],
                    airportConfigPot: Pot[AirportConfig],
                    airportInfos: ReactConnectProxy[Map[String, Pot[AirportInfo]]],
                    stateChange: (QueueName, DeskRecTimeslot) => Callback
                  )

  case class HoverPopoverState(hovered: Boolean = false)

  def HoverPopover(trigger: String,
                   matchingFlights: Pot[Flights],
                   airportInfos: ReactConnectProxy[Map[String, Pot[AirportInfo]]]) = ReactComponentB[Unit]("HoverPopover")
    .initialState_P((p) =>
      HoverPopoverState()
    ).renderS((scope, state) => {
    val popover = <.div(
      ^.onMouseEnter ==> ((e: ReactEvent) => scope.modState(s => s.copy(hovered = true))),
      ^.onMouseLeave ==> ((e: ReactEvent) => scope.modState(_.copy(hovered = false))),
      if (state.hovered) {
        PopoverWrapper(trigger = trigger)(
          airportInfos(airportInfo =>
            FlightsTable(FlightsView.Props(matchingFlights, airportInfo.value))))
      } else {
        trigger
      })
    popover
  }).build

  case class PracticallyEverything(
                                    airportInfos: Map[String, Pot[AirportInfo]],
                                    flights: Pot[Flights],
                                    simulationResult: Map[TerminalName, Map[QueueName, Pot[SimulationResult]]],
                                    workload: Pot[Workloads],
                                    queueCrunchResults: Map[TerminalName, Map[QueueName, Pot[(Pot[CrunchResult], Pot[UserDeskRecs])]]],
                                    userDeskRec: Map[TerminalName, QueueUserDeskRecs]
                                  )

  def buildTerminalUserDeskRecsComponent(terminalName: TerminalName) = {
    log.info(s"userdeskrecs for $terminalName")
    val airportFlightsSimresWorksQcrsUdrs = SPACircuit.connect(model =>
      PracticallyEverything(
        model.airportInfos,
        model.flights,
        model.simulationResult,
        model.workload,
        model.queueCrunchResults,
        model.userDeskRec
      ))
    val airportWrapper = SPACircuit.connect(_.airportInfos)
    val airportConfigPotRCP = SPACircuit.connect(_.airportConfig)
    airportFlightsSimresWorksQcrsUdrs(peMP => {
      <.div(
        <.h1(terminalName + " Desks"),
        peMP().workload.renderReady((workloads: Workloads) => {
          val crv = peMP().queueCrunchResults.getOrElse(terminalName, Map())
          val srv = peMP().simulationResult.getOrElse(terminalName, Map())
          log.info(s"tud: ${terminalName}")
          val timestamps = workloads.timeStamps(terminalName)
          val startFromMilli = WorkloadsHelpers.midnightBeforeNow()
          val minutesRangeInMillis: NumericRange[Long] = WorkloadsHelpers.minutesForPeriod(startFromMilli, 24)
          val paxloads: Map[String, List[Double]] = WorkloadsHelpers.paxloadPeriodByQueue(peMP().workload.get.workloads(terminalName), minutesRangeInMillis)
          val rows = terminalUserDeskRecsRows(timestamps, paxloads, crv, srv)
          airportConfigPotRCP(airportConfigPotMP => {
            <.div(
              TableTerminalDeskRecs(
                terminalName,
                rows,
                peMP().flights,
                airportConfigPotMP(),
                airportWrapper,
                (queueName: QueueName, deskRecTimeslot: DeskRecTimeslot) => {
                  peMP.dispatch(UpdateDeskRecsTime(terminalName, queueName, deskRecTimeslot))
                }
              )
            )
          })
        }),
        peMP().workload.renderPending(_ => <.div("Waiting for crunch results")))
    })
  }

  class Backend($: BackendScope[Props, Unit]) {

    import jsDateFormat.formatDate

    def render(p: Props) = {
      log.info("%%%%%%%rendering table...")
      val style = bss.listGroup
      def queueColour(queueName: String): String = queueName + "-user-desk-rec"

      def renderItem(itemWithIndex: (TerminalUserDeskRecsRow, Int)) = {
        val item = itemWithIndex._1

        val time = item.time
        val windowSizeInMillis = 60000 * 15
        val flights: Pot[Flights] = p.flights.map(flights =>
          flights.copy(flights =
            flights.flights.filter(
              f => time <= f.PcpTime && f.PcpTime <= (time + windowSizeInMillis))))

        val date: Date = new Date(item.time)
        val formattedDate: String = formatDate(date)
        val queueSpecificCells = item.queueDetails.flatMap {
          case None => {
            Seq(<.td(^.colSpan := 5, "Nothing here"))
          }
          case Some(q: QueueDetailsRow) => {
            val warningClasses = if (q.waitTimeWithCrunchDeskRec < q.waitTimeWithUserDeskRec) "table-warning" else ""
            val dangerWait = p.airportConfigPot match {
              case Ready(airportConfig) =>
                if (q.waitTimeWithUserDeskRec > airportConfig.slaByQueue(q.queueName)) "table-danger"
              case _ =>
                ""
            }
            def qtd(xs: TagMod*) = <.td(((^.className := queueColour(q.queueName)) :: xs.toList): _*)
            val hasChangeClasses = if (q.userDeskRec.deskRec != q.crunchDeskRec) "table-info" else ""
            Seq(
              qtd(q.pax),
              qtd(q.crunchDeskRec),
              qtd(
                ^.cls := hasChangeClasses,
                <.input.number(
                  ^.className := "desk-rec-input",
                  ^.value := q.userDeskRec.deskRec,
                  ^.onChange ==> ((e: ReactEventI) => p.stateChange(q.queueName, DeskRecTimeslot(q.userDeskRec.id, deskRec = e.target.value.toInt)))
                )),
              qtd(q.waitTimeWithCrunchDeskRec + " mins"),
              qtd(^.cls := dangerWait + " " + warningClasses, q.waitTimeWithUserDeskRec + " mins"))
          }
          case what =>
            log.error(s"got rhubarb ${what}")
            Seq()
        }.toList

        val airportInfo: ReactConnectProxy[Map[String, Pot[AirportInfo]]] = p.airportInfos
        val airportInfoPopover = HoverPopover(formattedDate, flights, airportInfo)

        <.tr(<.td(^.cls := "date-field", airportInfoPopover()) :: queueSpecificCells: _*)
      }


      val headerGroupStart = ^.borderLeft := "solid 1px #fff"
      val subHeadingLevel1 = queueNameMappingOrder.flatMap(queueName => {
        val deskUnitLabel = DeskRecsTable.deskUnitLabel(queueName)
        val qc = queueColour(queueName)
        List(<.th("", ^.className := qc),
          <.th(headerGroupStart, deskUnitLabel, ^.className := qc, ^.colSpan := 2),
          <.th(headerGroupStart, "Wait Times with", ^.className := qc, ^.colSpan := 2))
      })
      val subHeadingLevel2: List[TagMod] = queueNameMappingOrder.map(queueName =>
        List(<.th("Pax"),
          <.th(headerGroupStart, "Required"), <.th("Available"),
          <.th(headerGroupStart, "Recs"), <.th("Available"))
          .map(t => t.copy(modifiers = (List(^.className := queueColour(queueName)) :: t.modifiers)))
      ).flatten
      def qth(queueName: String, xs: TagMod*) = <.th(((^.className := queueName + "-user-desk-rec") :: xs.toList): _*)
      <.table(^.cls := "table table-striped table-hover table-sm user-desk-recs",
        <.thead(
          ^.display := "block",
          <.tr(<.th("") :: queueNameMappingOrder.map {
            case (queueName) =>
              qth(queueName, <.h3(queueDisplayName(queueName)), ^.colSpan := 5)
          }: _*),
          <.tr(<.th("") :: subHeadingLevel1: _*),
          <.tr(<.th("Time") :: subHeadingLevel2: _*)),
        <.tbody(
          ^.display := "block",
          ^.overflow := "scroll",
          ^.height := "500px",
          p.items.zipWithIndex map renderItem))
    }

  }

  private val component = ReactComponentB[Props]("TerminalUserDeskRecs")
    .renderBackend[Backend]
    .build

  def apply(terminalName: String, items: Seq[TerminalUserDeskRecsRow], flights: Pot[Flights],
            airportConfigPot: Pot[AirportConfig],
            airportInfos: ReactConnectProxy[Map[String, Pot[AirportInfo]]],
            stateChange: (QueueName, DeskRecTimeslot) => Callback) =
    component(Props(terminalName, items, flights, airportConfigPot, airportInfos, stateChange))
}

