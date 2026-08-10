package cn.silverdragon.draarl.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutCompilation() {
        measureColdStartup(CompilationMode.None())
    }

    @Test
    fun coldStartupWithBaselineProfile() {
        measureColdStartup(CompilationMode.Partial(BaselineProfileMode.Require))
    }

    private fun measureColdStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = startupMetrics,
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5
        ) {
            pressHome()
            startActivityAndWait()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "cn.silverdragon.draarl"
        val startupMetrics: List<Metric> = listOf(
            StartupTimingMetric(),
            FrameTimingMetric(),
            MemoryUsageMetric(MemoryUsageMetric.Mode.Last)
        )
    }
}
