package spatutorial.client.components

import diode.data.{Pot, Ready}
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.vdom.prefix_<^.{<, TagMod, ^}
import spatutorial.client.modules.{FlightsWithSplitsView, GriddleComponentWrapper, ViewTools}
import spatutorial.shared.{AirportInfo, PaxTypeAndQueue, PaxTypesAndQueues}
import chandu0101.scalajs.react.components.Spinner
import diode.data.{Pot, Ready}
import japgolly.scalajs.react.{ReactComponentB, _}
import japgolly.scalajs.react.vdom.all.{ReactAttr => _, TagMod => _, _react_attrString => _, _react_autoRender => _, _react_fragReactNode => _}
import japgolly.scalajs.react.vdom.prefix_<^._
import spatutorial.client.logger
import spatutorial.client.modules.{GriddleComponentWrapper, ViewTools}
import spatutorial.shared.FlightsApi.FlightsWithSplits

import scala.scalajs.js
import scala.scalajs.js.Object

object FlightsWithSplitsTable {

  type Props = FlightsWithSplitsView.Props


  def originComponent(originMapper: (String) => (String)): js.Function = (props: js.Dynamic) => {
    val mod: TagMod = ^.title := originMapper(props.data.toString())
    <.span(props.data.toString(), mod).render
  }


  def reactTableFlightsAsJsonDynamic(flights: FlightsWithSplits): List[js.Dynamic] = {

    flights.flights.map(flightAndSplit => {
      val f = flightAndSplit.apiFlight
      val literal = js.Dynamic.literal
      val splitsTuples: Map[PaxTypeAndQueue, Int] = flightAndSplit.splits
        .splits.groupBy(split => {
        PaxTypeAndQueue(split.passengerType, split.queueType)
      }
      ).map(x => (x._1, x._2.map(_.paxCount).sum))
      logger.log.debug("flightAndSplit:" + splitsTuples)

      import spatutorial.shared.DeskAndPaxTypeCombinations._

      val total = "advPaxInfo total"

      def splitsField(fieldName: String, ptQ: PaxTypeAndQueue): (String, scalajs.js.Any) = {
        "Splits " + fieldName -> (splitsTuples.get(ptQ) match {
          case Some(v: Int) => Int.box(v)
          case None => ""
        })
      }

      literal(
        splitsField(deskEeaNonMachineReadable, PaxTypesAndQueues.eeaNonMachineReadableToDesk),
        splitsField(nationalsDeskVisa, PaxTypesAndQueues.visaNationalToDesk),
        splitsField(nationalsDeskNonVisa, PaxTypesAndQueues.nonVisaNationalToDesk),
        splitsField(egate, PaxTypesAndQueues.eeaMachineReadableToEGate),
        splitsField(deskEea, PaxTypesAndQueues.eeaMachineReadableToDesk),
        "Splits " + total -> splitsTuples.values.sum,
        "Operator" -> f.Operator,
        "Status" -> f.Status,
        "EstDT" -> makeDTReadable(f.EstDT),
        "ActDT" -> makeDTReadable(f.ActDT),
        "EstChoxDT" -> f.EstChoxDT,
        "ActChoxDT" -> makeDTReadable(f.ActChoxDT),
        "Gate" -> f.Gate,
        "Stand" -> f.Stand,
        "MaxPax" -> f.MaxPax,
        "ActPax" -> f.ActPax,
        "TranPax" -> f.TranPax,
        "RunwayID" -> f.RunwayID,
        "BaggageReclaimId" -> f.BaggageReclaimId,
        "FlightID" -> f.FlightID,
        "AirportID" -> f.AirportID,
        "Terminal" -> f.Terminal,
        "ICAO" -> f.ICAO,
        "IATA" -> f.IATA,
        "Origin" -> f.Origin,
        "SchDT" -> makeDTReadable(f.SchDT))
    })
  }

  val component = ReactComponentB[Props]("FlightsWithSplitsTable")
    .render_P(props => {
      logger.log.debug(s"rendering flightstable")

      val portMapper: Map[String, Pot[AirportInfo]] = props.airportInfoProxy

      def mappings(port: String): String = {
        val res: Option[Pot[String]] = portMapper.get(port).map { info =>
          info.map(i => s"${i.airportName}, ${i.city}, ${i.country}")
        }
        res match {
          case Some(Ready(v)) => v
          case _ => "waiting for info..."
        }
      }

      val columnMeta = Some(Seq(
        new GriddleComponentWrapper.ColumnMeta("Origin", customComponent = originComponent(mappings))))
      <.div(^.className := "table-responsive timeslot-flight-popover",
        props.flightsModelProxy.renderPending((t) => ViewTools.spinner),
        props.flightsModelProxy.renderEmpty(ViewTools.spinner),
        props.flightsModelProxy.renderReady(flights => {
          val rows = flights.toJsArray
          GriddleComponentWrapper(results = rows,
            columnMeta = columnMeta,
            initialSort = "SchDT",
            columns = props.activeCols)()
        })
      )
    }).build

  def apply(props: Props) = component(props)
}
