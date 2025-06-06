// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.google.gson.GsonBuilder
import com.intellij.cce.metric.MetricInfo
import com.intellij.cce.report.ijmetric.AiPerformanceMetricsDto
import com.intellij.cce.util.isUnderTeamCity
import com.intellij.cce.workspace.filter.SpanFilter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.tools.ide.metrics.collector.OpenTelemetrySpanCollector
import com.intellij.tools.ide.metrics.collector.metrics.PerformanceMetrics
import com.intellij.tools.ide.metrics.collector.publishing.ApplicationMetricDto
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

class IntellijPerfJsonReportGenerator(
  outputDir: String,
  filterName: String,
  comparisonFilterName: String,
  val spanFilter: SpanFilter?,
) : JsonReportGeneratorBase(
  outputDir,
  filterName,
  comparisonFilterName,
  "ij-perf"
) {
  override fun generateGlobalReport(globalMetrics: List<MetricInfo>): Path {
    val reportFile = dir.resolve(metricsInfoName)
    LOG.info("generateGlobalReport. save to ${reportFile.absolutePathString()}")
    try {
      val globalPerfMetrics = globalMetrics.map { it.toPerfMetric() }
      val openTelemetryMetrics = spanFilter?.let { getOpenTelemetryMetrics(spanFilter) } ?: emptyList()

      val metricsDto = AiPerformanceMetricsDto
        .create(projectName = "#feature#_#lang#_#model#_#os#", //#**# will be used in TC builds, pls don't change it
                projectURL = "",
                projectDescription = "",
                methodName = "",
                buildNumber = BuildNumber.currentVersion(),
                metrics = globalPerfMetrics + openTelemetryMetrics,
        )

      reportFile.writeText(gson.toJson(metricsDto))
      helper.afterReport(reportFile)
    } catch (t: Throwable) { //temp catch all
      LOG.error("fail to create report for ij-perf", t)
    }

    return reportFile
  }

  private val helper: ReportHelper = if (isUnderTeamCity) TeamCityReportHelper() else ReportHelperLocalMock()

  companion object {
    private const val metricsInfoName = "metrics.performance.json"
    private val LOG = logger<IntellijPerfJsonReportGenerator>()
  }

  private val gson = GsonBuilder().apply {
    setPrettyPrinting()
    disableHtmlEscaping()
    serializeSpecialFloatingPointValues()
  }.create()
}

private fun MetricInfo.toPerfMetric(namePrefix: String = ""): ApplicationMetricDto<Double> {
  val metricName = "${namePrefix}${name}"
  return ApplicationMetricDto(metricName, c=value)
}


private interface ReportHelper {
  fun afterReport(source: Path)
}

private class ReportHelperLocalMock : ReportHelper {
  override fun afterReport(source: Path) {
  }
}

private class TeamCityReportHelper : ReportHelper {
  companion object {
    private val LOG = logger<TeamCityReportHelper>()
  }

  override fun afterReport(source: Path) {
    LOG.info("publishTeamCityArtifacts. source=${source.absolutePathString()}")
    LOG.info("report content: ${source.readText()}")
  }
}

@Suppress("TestOnlyProblems")
private fun getOpenTelemetryMetrics(spanFilter: SpanFilter): List<ApplicationMetricDto<Double>> {
  TelemetryManager.getInstance().forceFlushMetricsBlocking()
  val openTelemetryMetrics =
    OpenTelemetrySpanCollector(spanFilter.toCollectorSpanFilter())
      .collect(Path.of(System.getProperty("idea.diagnostic.opentelemetry.file")))
      .map {
        ApplicationMetricDto(
          n = it.id.name,
          d = if (it.id is PerformanceMetrics.MetricId.Duration) it.value.toDouble() else null,
          c = if (it.id is PerformanceMetrics.MetricId.Counter) it.value.toDouble() else null
        )
      }
  return openTelemetryMetrics
}
