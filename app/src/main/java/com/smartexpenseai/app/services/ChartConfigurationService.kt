package com.smartexpenseai.app.services

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.smartexpenseai.app.R
import com.smartexpenseai.app.data.dao.CategorySpendingResult
import com.smartexpenseai.app.services.TimeSeriesAggregationService.TimeSeriesData
import com.smartexpenseai.app.utils.logging.StructuredLogger
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for chart configuration and setup
 * Eliminates ~300 lines of duplicated chart setup logic
 */
@Singleton
class ChartConfigurationService @Inject constructor(
    private val context: Context
) {
    private val logger = StructuredLogger("ChartConfigurationService","ChartConfigurationService")
    companion object {
        private const val TAG = "ChartConfigurationService"
    }
    
    /**
     * Data class for pie chart configuration
     */
    data class PieChartData(
        val categoryData: List<CategorySpendingResult>,
        val totalAmount: Double
    )
    
    /**
     * Data class for chart colors
     */
    data class ChartColors(
        val primary: Int,
        val secondary: Int,
        val success: Int,
        val warning: Int,
        val error: Int,
        val info: Int,
        val text: Int,
        val background: Int
    )
    
    /**
     * Setup category pie chart with consistent styling
     */
    fun setupCategoryPieChart(
        chart: PieChart,
        data: PieChartData,
        showLegend: Boolean = true,
        showLabels: Boolean = true
    ): Boolean {
        try {
            logger.debug("setupCategoryPieChart","Setting up category pie chart with ${data.categoryData.size} categories")
            
            if (!validatePieChartData(data)) {
                logger.debug("setupCategoryPieChart","Invalid pie chart data provided")
                return false
            }
            
            // Create pie entries
            val pieEntries = data.categoryData.map { category ->
                val percentage = if (data.totalAmount > 0) {
                    (category.total_amount / data.totalAmount) * 100
                } else 0.0
                
                PieEntry(
                    category.total_amount.toFloat(),
                    category.category_name,
                    category
                )
            }
            
            // Create dataset
            val dataSet = PieDataSet(pieEntries, "Categories").apply {
                colors = getCategoryColors(data.categoryData)
                valueTextSize = 12f
                valueTextColor = getChartColors().text
                sliceSpace = 2f
                
                // Configure value display
                if (showLabels) {
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val percentage = (value / data.totalAmount.toFloat()) * 100
                            return if (percentage >= 5.0) { // Only show label if >= 5%
                                "${String.format("%.1f", percentage)}%"
                            } else ""
                        }
                    }
                } else {
                    setDrawValues(false)
                }
            }
            
            // Create pie data
            val pieData = PieData(dataSet)
            
            // Configure chart
            chart.apply {
                this.data = pieData
                description.isEnabled = false
                legend.isEnabled = showLegend
                
                // Chart interaction
                isRotationEnabled = true
                isHighlightPerTapEnabled = true
                
                // Appearance
                setUsePercentValues(false)
                setDrawEntryLabels(showLabels)
                setDrawCenterText(true)
                centerText = "Categories"
                setCenterTextSize(16f)
                setCenterTextColor(getChartColors().text)
                
                // Disable hole in center for better visibility
                isDrawHoleEnabled = false
                
                // Animation
                animateXY(1000, 1000)
                
                // Refresh chart
                invalidate()
            }

            logger.debug("setupCategoryPieChart","Category pie chart setup completed successfully")
            return true
            
        } catch (e: Exception) {
            logger.error("setupCategoryPieChart","Error setting up category pie chart",e)
            return false
        }
    }
    
    /**
     * Setup time series bar chart with consistent styling
     */
    fun setupTimeSeriesBarChart(
        chart: BarChart,
        data: List<TimeSeriesData>,
        title: String = "Time Series"
    ): Boolean {
        try {
            logger.debug("setupTimeSeriesBarChart","Setting up time series bar chart with ${data.size} data points")
            
            if (!validateTimeSeriesData(data)) {
                logger.debug("setupTimeSeriesBarChart","Invalid time series data provided")
                return false
            }
            
            // Create bar entries
            val barEntries = data.mapIndexed { index, timeData ->
                BarEntry(index.toFloat(), timeData.amount.toFloat())
            }
            
            // Create dataset with proper color configuration
            val dataSet = BarDataSet(barEntries, title).apply {
                // Apply distinct colors to each bar
                val timeSeriesColors = getTimeSeriesColors(data.size)
                colors = timeSeriesColors
                
                // Improve bar appearance
                valueTextSize = 14f
                valueTextColor = Color.WHITE
                setDrawValues(true)
                
                // Add some visual enhancement
                barBorderWidth = 1f
                barBorderColor = getChartColors().primary
                
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value > 0) "₹${String.format("%.0f", value)}" else ""
                    }
                }

                logger.debug("setupTimeSeriesBarChart","Applied ${timeSeriesColors.size} colors to ${barEntries.size} bars")
            }
            
            // Create bar data
            val barData = BarData(dataSet).apply {
                barWidth = 0.8f
            }
            
            // Configure chart
            chart.apply {
                this.data = barData
                description.isEnabled = false
                legend.isEnabled = false
                
                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    labelCount = data.size
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < data.size) {
                                data[index].label
                            } else ""
                        }
                    }
                    textColor = Color.WHITE
                    textSize = 12f
                }
                
                // Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "₹${String.format("%.0f", value)}"
                        }
                    }
                    textColor = Color.WHITE
                    textSize = 12f
                }
                
                axisRight.isEnabled = false
                
                // Chart interaction
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                
                // Animation
                animateY(1000)
                
                // Refresh chart
                invalidate()
            }

            logger.debug("setupTimeSeriesBarChart","Time series bar chart setup completed successfully")
            return true
            
        } catch (e: Exception) {
            logger.error("setupTimeSeriesBarChart","Error setting up time series bar chart",e)
            return false
        }
    }
    
    /**
     * Setup trend line chart with consistent styling
     */
    fun setupTrendLineChart(
        chart: LineChart,
        data: List<TimeSeriesData>,
        title: String = "Trend",
        showDataPoints: Boolean = true
    ): Boolean {
        try {
            logger.debug("setupTrendLineChart","Setting up trend line chart with ${data.size} data points")
            
            if (!validateTimeSeriesData(data)) {
                logger.debug("setupTrendLineChart","Invalid trend line data provided")
                return false
            }
            
            // Create line entries
            val lineEntries = data.mapIndexed { index, timeData ->
                Entry(index.toFloat(), timeData.amount.toFloat())
            }
            
            // Create dataset
            val dataSet = LineDataSet(lineEntries, title).apply {
                color = getChartColors().primary
                lineWidth = 3f
                setCircleColor(getChartColors().primary)
                circleRadius = if (showDataPoints) 6f else 0f
                setDrawCircles(showDataPoints)
                setDrawValues(true)
                valueTextSize = 10f
                valueTextColor = getChartColors().text
                
                // Fill under line
                setDrawFilled(true)
                fillColor = getChartColors().primary
                fillAlpha = 50
                
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "₹${String.format("%.0f", value)}"
                    }
                }
            }
            
            // Create line data
            val lineData = LineData(dataSet)
            
            // Configure chart
            chart.apply {
                this.data = lineData
                description.isEnabled = false
                legend.isEnabled = false
                
                // X-axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    labelCount = data.size
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            return if (index >= 0 && index < data.size) {
                                data[index].label
                            } else ""
                        }
                    }
                    textColor = Color.WHITE
                    textSize = 12f
                }
                
                // Y-axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    granularity = 1f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "₹${String.format("%.0f", value)}"
                        }
                    }
                    textColor = Color.WHITE
                    textSize = 12f
                }
                
                axisRight.isEnabled = false
                
                // Chart interaction
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                
                // Animation
                animateX(1000)
                
                // Refresh chart
                invalidate()
            }

            logger.debug("setupTrendLineChart","Trend line chart setup completed successfully")
            return true
            
        } catch (e: Exception) {
            logger.error("setupTrendLineChart","Error setting up trend line chart",e)
            return false
        }
    }
    
    /**
     * Get standard chart colors from theme
     */
    private fun getChartColors(): ChartColors {
        return ChartColors(
            primary = ContextCompat.getColor(context, R.color.primary),
            secondary = ContextCompat.getColor(context, R.color.secondary),
            success = ContextCompat.getColor(context, R.color.success),
            warning = ContextCompat.getColor(context, R.color.warning),
            error = ContextCompat.getColor(context, R.color.error),
            info = ContextCompat.getColor(context, R.color.info),
            text = Color.BLACK,
            background = Color.WHITE
        )
    }
    
    /**
     * Get category-specific colors
     */
    private fun getCategoryColors(categories: List<CategorySpendingResult>): List<Int> {
        val colors = getChartColors()
        val standardColors = listOf(
            colors.primary,
            colors.secondary,
            colors.success,
            colors.warning,
            colors.error,
            colors.info
        )
        
        return categories.mapIndexed { index, category ->
            try {
                // Try to parse custom category color
                Color.parseColor(category.color)
            } catch (e: Exception) {
                // Fallback to standard colors
                standardColors[index % standardColors.size]
            }
        }
    }
    
    /**
     * Get time series colors with better distribution
     */
    private fun getTimeSeriesColors(count: Int): List<Int> {
        val colors = getChartColors()
        
        // Use a more diverse color palette for better visual distinction
        val baseColors = listOf(
            colors.primary,      // Blue
            colors.secondary,    // Orange
            colors.success,      // Green
            colors.error,        // Red
            colors.warning,      // Yellow/Orange
            colors.info          // Light Blue
        )
        
        // For small datasets, use predefined colors to ensure good contrast
        return if (count <= baseColors.size) {
            baseColors.take(count)
        } else {
            // For larger datasets, cycle through colors
            (0 until count).map { index ->
                baseColors[index % baseColors.size]
            }
        }.also {
            logger.debug("getTimeSeriesColors","Generated ${it.size} colors for $count data points")
        }
    }
    
    /**
     * Validate pie chart data
     */
    private fun validatePieChartData(data: PieChartData): Boolean {
        return data.categoryData.isNotEmpty() && 
               data.totalAmount > 0 &&
               data.categoryData.all { it.total_amount >= 0 }
    }
    
    /**
     * Validate time series data
     */
    private fun validateTimeSeriesData(data: List<TimeSeriesData>): Boolean {
        return data.isNotEmpty() && 
               data.all { it.amount >= 0 }
    }
    
    /**
     * Clear chart data and show empty state
     */
    fun clearChart(chart: com.github.mikephil.charting.charts.Chart<*>) {
        chart.clear()
        chart.invalidate()
    }
    
    /**
     * Show loading state on chart
     */
    fun showChartLoading(chart: com.github.mikephil.charting.charts.Chart<*>) {
        chart.clear()
        chart.invalidate()
        // You could add a loading indicator here
    }
    
    /**
     * Show error state on chart
     */
    fun showChartError(chart: com.github.mikephil.charting.charts.Chart<*>, error: String) {
        chart.clear()
        chart.invalidate()
        logger.debug("showChartError","Chart error: $error")
        // You could add an error message here
    }
}