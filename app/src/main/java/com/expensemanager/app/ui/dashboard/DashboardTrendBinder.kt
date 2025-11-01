package com.expensemanager.app.ui.dashboard

import android.content.Context
import androidx.core.content.ContextCompat
import com.expensemanager.app.R
import com.expensemanager.app.data.entities.TransactionEntity
import com.expensemanager.app.data.repository.ExpenseRepository
import com.expensemanager.app.databinding.FragmentDashboardBinding
import com.expensemanager.app.utils.logging.StructuredLogger
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles all weekly trend chart logic for DashboardFragment.
 * Isolates chart setup, calculations, and rendering.
 */
class DashboardTrendBinder(
    private val binding: FragmentDashboardBinding,
    private val context: Context,
    private val repository: ExpenseRepository,
    private val logger: StructuredLogger
) {

    /**
     * Initial chart setup - call once during fragment initialization
     */
    fun setupChart() {
        val chart = binding.chartWeeklyTrend

        chart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = false

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = ContextCompat.getColor(context, R.color.divider)
                textColor = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 10f
                axisMinimum = 0f // Always start from 0
                granularity = 1f // Minimum interval between values
            }

            axisRight.isEnabled = false
        }
    }

    /**
     * Update trend chart for the given date range
     */
    suspend fun updateTrend(startDate: Date, endDate: Date) {
        try {
            logger.debug("updateTrend", "Updating chart for range: $startDate to $endDate")

            // Always show last 7 days ending at endDate
            val calendar = Calendar.getInstance()
            calendar.time = endDate

            // Set to end of day
            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val chartEndDate = calendar.time

            // Go back 6 days for 7 days total
            calendar.add(Calendar.DAY_OF_YEAR, -6)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val chartStartDate = calendar.time

            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            logger.debug("updateTrend", "Chart showing last 7 days: ${dateFormat.format(chartStartDate)} to ${dateFormat.format(chartEndDate)}")

            val transactions = repository.getTransactionsByDateRange(chartStartDate, chartEndDate)
            logger.debug("updateTrend", "Found ${transactions.size} transactions in 7-day range")

            val chartData = calculateLast7DaysData(transactions, chartStartDate, chartEndDate)

            withContext(Dispatchers.Main) {
                renderChart(chartData)
            }

        } catch (e: Exception) {
            logger.error("updateTrend", "Error updating chart", e)
            withContext(Dispatchers.Main) {
                showEmptyChart("Chart Error")
            }
        }
    }

    /**
     * Calculate daily spending for the last 7 days
     */
    private fun calculateLast7DaysData(
        transactions: List<TransactionEntity>,
        startDate: Date,
        endDate: Date
    ): List<ChartDataPoint> {
        val calendar = Calendar.getInstance()
        val dailyData = mutableListOf<ChartDataPoint>()
        val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

        logger.debug("calculateLast7DaysData", "Calculating 7 days from ${dateFormat.format(startDate)} to ${dateFormat.format(endDate)}")

        for (i in 0 until 7) {
            calendar.time = startDate
            calendar.add(Calendar.DAY_OF_YEAR, i)

            val dayStart = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            val dayEnd = Calendar.getInstance().apply {
                time = calendar.time
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time

            val daySpending = transactions.filter {
                it.transactionDate >= dayStart && it.transactionDate <= dayEnd
            }.sumOf { it.amount }

            val dayLabel = dateFormat.format(calendar.time)

            dailyData.add(ChartDataPoint(
                index = i.toFloat(),
                value = daySpending.toFloat(),
                label = dayLabel
            ))

            logger.debug("calculateLast7DaysData", "Day ${i + 1}: $dayLabel = ₹$daySpending")
        }

        logger.debug("calculateLast7DaysData", "Generated ${dailyData.size} daily data points")
        return dailyData
    }

    /**
     * Render chart with calculated data points
     */
    private fun renderChart(chartData: List<ChartDataPoint>) {
        val chart = binding.chartWeeklyTrend

        if (chartData.isEmpty()) {
            showEmptyChart("No Data Available")
            return
        }

        logger.debug("renderChart", "Updating chart with ${chartData.size} data points")

        val entries = chartData.map { Entry(it.index, it.value) }

        // Calculate max value for proper Y-axis scaling
        val maxValue = chartData.maxOfOrNull { it.value } ?: 100f
        val yAxisMax = if (maxValue > 0) maxValue * 1.2f else 100f // Add 20% padding

        val dataSet = LineDataSet(entries, "Spending Trend").apply {
            color = ContextCompat.getColor(context, R.color.primary)
            lineWidth = 2f
            setCircleColor(ContextCompat.getColor(context, R.color.primary))
            circleRadius = 4f
            setDrawValues(true) // Enable value labels
            valueTextColor = ContextCompat.getColor(context, R.color.text_primary)
            valueTextSize = 10f
            mode = LineDataSet.Mode.LINEAR
            setDrawFilled(true)
            fillColor = ContextCompat.getColor(context, R.color.primary_light)
            fillAlpha = 50

            // Custom value formatter to show ₹ symbol and hide ₹0
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) "₹${value.toInt()}" else ""
                }
            }
        }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return chartData.getOrNull(value.toInt())?.label ?: ""
            }
        }

        chart.axisLeft.apply {
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "₹${value.toInt()}"
                }
            }
            axisMaximum = yAxisMax
            axisMinimum = 0f
        }

        chart.data = LineData(dataSet)
        chart.invalidate()

        logger.debug("renderChart", "Chart rendered successfully with Y-axis range: 0 to ₹${yAxisMax.toInt()}")
    }

    /**
     * Show empty state when no data available
     */
    private fun showEmptyChart(message: String) {
        val chart = binding.chartWeeklyTrend
        chart.clear()
        chart.setNoDataText(message)
        logger.debug("showEmptyChart", message)
    }
}

/**
 * Data class for chart data points with proper labels
 */
data class ChartDataPoint(
    val index: Float,
    val value: Float,
    val label: String
)
