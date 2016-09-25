package spatutorial.client.components

import diode.Action
import diode.data.Pot
import diode.react.{ReactConnectProxy, ModelProxy}
import diode.react.ReactPot._
import japgolly.scalajs.react
import japgolly.scalajs.react.vdom.DomCallbackResult._
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{Callback, ReactComponentB, _}
import spatutorial.client.components.Bootstrap.Panel.Props
import spatutorial.client.components.Bootstrap.{Button, CommonStyle, Panel}
import spatutorial.client.logger._
import spatutorial.client.modules.Dashboard.DashboardModels
import spatutorial.client.services._
import spatutorial.shared._

import scala.collection.immutable

object DeskRecsChart {
  type DeskRecsModel = DashboardModels

  log.info("initialising deskrecschart")

  case class State(deskRecs: ReactConnectProxy[Pot[UserDeskRecs]])

  val DeskRecs = ReactComponentB[ModelProxy[DeskRecsModel]]("CrunchResults")
    .render_P(proxy => {
      log.info(s"rendering desk recs")
      <.div(
        proxy().queueCrunchResults.map {
        case (queueName, queueCrunchResults) =>
          <.div(
            queueCrunchResults.renderPending(t => s"Waiting for crunchResult for ${queueName}"),
            queueCrunchResults.renderReady(queueWorkload => {
              val potCrunchResult: Pot[CrunchResult] = queueWorkload._1
              //todo this seems to be at the wrong level
              val potSimulationResult: Pot[SimulationResult] = proxy().potSimulationResult(queueName)
              val workloads = proxy().workloads
              <.div(workloads.renderReady(wl => {
                val labels = wl.labels
                Panel(Panel.Props(s"Desk Recommendations and Wait times for '${queueName}'"),
                  potCrunchResult.renderPending(time => <.p(s"Waiting for crunch result ${time}")),
                  potCrunchResult.renderEmpty(<.p("Waiting for crunch result")),
                  potCrunchResult.renderFailed((t) => <.p("Error retrieving crunch result")),
                  deskRecsChart(queueName, labels, potCrunchResult),
                  waitTimesChart(labels, potCrunchResult))
              }))
            }))
      })
    })
    .componentDidMount(scope =>
      Callback.log("Mounted DeskRecs")
    ).build


  def waitTimesChart(labels: IndexedSeq[String], potCrunchResult: Pot[CrunchResult]): ReactNode = {
    potCrunchResult.render(chartData => {
      val sampledWaitTimesSimulation: List[Double] = sampledWaitTimes(chartData.waitTimes)

      val sampledLabels = takeEvery15th(labels)
      Chart(Chart.ChartProps("Wait Times",
        Chart.LineChart,
        ChartData(sampledLabels, Seq(ChartDataset(sampledWaitTimesSimulation, "Wait Times")))
      ))
    })
  }

  case class UserSimulationProps(simulationResult: ModelProxy[Pot[SimulationResult]],
                                 crunchResult: ModelProxy[Pot[CrunchResult]])

  def userSimulationWaitTimesChart(queueName: QueueName,
                                   labels: IndexedSeq[String],
                                   simulationResult: ModelProxy[Pot[SimulationResult]],
                                   crunchResult: ModelProxy[Pot[CrunchResult]]) = {
    val component = ReactComponentB[UserSimulationProps]("UserSimulationChart").render_P(props => {
      log.info("rendering chart")
      val proxy: Pot[SimulationResult] = props.simulationResult()
      if (proxy.isReady) {
        log.info(s"Think our simulation result is ready! ${proxy}")
        val sampledWaitTimesSimulation: List[Double] = sampledWaitTimes(proxy.get.waitTimes)
        val sampledWaitTimesCrunch: List[Double] = sampledWaitTimes(props.crunchResult().get.waitTimes)
        log.info(s"charting ${queueName} ${sampledWaitTimesCrunch.take(10)}, ${sampledWaitTimesSimulation.take(10)}")
        val sampledLabels = takeEvery15th(labels)
        Chart(Chart.ChartProps("Simulated Wait Times",
          Chart.LineChart,
          ChartData(sampledLabels,
            Seq(
              ChartDataset(sampledWaitTimesSimulation, "Simulated Wait Times with your actual desk"),
              ChartDataset(sampledWaitTimesCrunch, "Predicted Wait Times with Recommended Desks", backgroundColor = "red", borderColor = "red")))
        ))
      } else {
        <.p("waiting for data")
      }
    }).build

    component(UserSimulationProps(simulationResult, crunchResult))
  }


  def sampledWaitTimes(times: immutable.Seq[Int]): List[Double] = {
    val grouped: Iterator[Seq[Int]] = times.grouped(15)
    val maxInEachGroup: Iterator[Int] = grouped.map(_.max)
    val sampledWaitTimes = maxInEachGroup.map(_.toDouble).toList
    sampledWaitTimes
  }

  def deskRecsChart(queueName: QueueName, labels: IndexedSeq[String], potCrunchResult: Pot[CrunchResult]): ReactNode = {
    potCrunchResult.render(chartData =>
      Chart(Chart.ChartProps(s"Desk Recs ${queueName}",
        Chart.LineChart,
        ChartData(takeEvery15th(labels), Seq(
          ChartDataset(
            takeEvery15th(chartData.recommendedDesks).map(_.toDouble), s"Desk Recommendations ${queueName}")))
      )))
  }

  def takeEvery15th[N](desks: IndexedSeq[N]) = desks.zipWithIndex.collect {
    case (n, i) if (i % 15 == 0) => n
  }

  def apply(proxy: ModelProxy[DeskRecsModel]) = DeskRecs(proxy)
}
