package spatutorial.client.components

import chandu0101.scalajs.react.components.Spinner
import diode.data.{Pot, Ready}
import japgolly.scalajs.react.{ReactComponentB, _}
import japgolly.scalajs.react.vdom.all.{ReactAttr => _, TagMod => _, _react_attrString => _, _react_autoRender => _, _react_fragReactNode => _}
import japgolly.scalajs.react.vdom.prefix_<^._
import spatutorial.client.modules.{FlightsView, GriddleComponentWrapper}
import spatutorial.shared.AirportInfo
import spatutorial.shared.FlightsApi.Flights

import scala.language.existentials
import scala.scalajs.js

object FlightsTable {
  def originComponent(originMapper: (String) => (String)): js.Function = (props: js.Dynamic) => {
    val mod: TagMod = ^.title := originMapper(props.data.toString())
    <.span(props.data.toString(), mod).render
  }

  def reactTableFlightsAsJsonDynamic(flights: Flights): List[js.Dynamic] = {
    flights.flights.map(f => {
      js.Dynamic.literal(
        Operator = f.Operator,
        Status = f.Status,
        EstDT = makeDTReadable(f.EstDT),
        ActDT = makeDTReadable(f.ActDT),
        EstChoxDT = f.EstChoxDT,
        ActChoxDT = makeDTReadable(f.ActChoxDT),
        Gate = f.Gate,
        Stand = f.Stand,
        MaxPax = f.MaxPax,
        ActPax = f.ActPax,
        TranPax = f.TranPax,
        RunwayID = f.RunwayID,
        BaggageReclaimId = f.BaggageReclaimId,
        FlightID = f.FlightID,
        AirportID = f.AirportID,
        Terminal = f.Terminal,
        ICAO = f.ICAO,
        IATA = f.IATA,
        Origin = f.Origin,
        SchDT = makeDTReadable(f.SchDT))
    })
  }

  def columnNames: List[String] = {
    List(
      "SchDT",
      "Origin",
      "Operator",
      "Status",
      "EstDT",
      "ActDT",
      //      "EstChoxDT",
      "ActChoxDT",
      "Gate",
      "Stand",
      "MaxPax",
      "ActPax",
      //      "TranPax",
      //      "RunwayID",
      //      "BaggageReclaimId",
      //      "FlightID",
      //      "AirportID",
      "Terminal",
      //      "ICAO",
      "IATA"
    )
  }


  val component = ReactComponentB[FlightsView.Props]("FlightsTable")
    .render_P(props => {
      val portMapper: Map[String, Pot[AirportInfo]] = props.airportInfoProxy

      def mappings(port: String) = {
        val res: Option[Pot[String]] = portMapper.get(port).map { info =>
          info.map(i => s"${i.airportName}, ${i.city}, ${i.country}")
        }
        res match {
          case Some(Ready(v)) => v
          case _ => "waiting for info..."
        }
      }

      val columnMeta = Some(Seq(new GriddleComponentWrapper.ColumnMeta("Origin", customComponent = originComponent(mappings))))
      <.div(^.className := "table-responsive",
        props.flightsModelProxy.renderPending((t) => Spinner()()),
        props.flightsModelProxy.renderEmpty(Spinner()()),
        props.flightsModelProxy.renderReady(flights => {
          GriddleComponentWrapper(results = reactTableFlightsAsJsonDynamic(flights).toJsArray,
            columnMeta = columnMeta,
            columns = columnNames)()
        })
      )
    }).build

  def apply(props: FlightsView.Props) = component(props)
}