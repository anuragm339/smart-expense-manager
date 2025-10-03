package com.expensemanager.app.ui.profile

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.expensemanager.app.R
import com.expensemanager.app.data.dao.AICallDao
import com.expensemanager.app.data.models.CallFrequency
import com.expensemanager.app.data.repository.EnhancedAIInsightsRepository
import com.expensemanager.app.databinding.FragmentAiInsightsSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

/**
 * Fragment for managing AI insights settings and preferences
 * Allows users to control call frequency, view usage statistics, and manage cache
 */
@AndroidEntryPoint
class AIInsightsSettingsFragment : Fragment() {

    private var _binding: FragmentAiInsightsSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var repository: EnhancedAIInsightsRepository

    @Inject
    lateinit var aiCallDao: AICallDao

    @Inject
    @Named("ai_insights_prefs")
    lateinit var prefs: SharedPreferences

    private var currentFrequency = CallFrequency.BALANCED

    companion object {
        private const val TAG = "AIInsightsSettingsFragment"
        private const val PREF_SHOW_COSTS = "show_cost_estimates"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiInsightsSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFrequencySelection()
        setupAdvancedOptions()
        loadCurrentSettings()
        loadUsageStatistics()
    }

    private fun setupFrequencySelection() {
        // Set up click listeners for frequency cards
        binding.cardConservative.setOnClickListener { selectFrequency(CallFrequency.CONSERVATIVE) }
        binding.cardBalanced.setOnClickListener { selectFrequency(CallFrequency.BALANCED) }
        binding.cardFrequent.setOnClickListener { selectFrequency(CallFrequency.FREQUENT) }

        // Set up radio button listeners
        binding.radioConservative.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectFrequency(CallFrequency.CONSERVATIVE)
        }
        binding.radioBalanced.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectFrequency(CallFrequency.BALANCED)
        }
        binding.radioFrequent.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectFrequency(CallFrequency.FREQUENT)
        }
    }

    private fun setupAdvancedOptions() {
        // Manual refresh button
        binding.btnManualRefresh.setOnClickListener {
            performManualRefresh()
        }

        // Clear cache button
        binding.btnClearCache.setOnClickListener {
            showClearCacheConfirmation()
        }

        // Cost estimates toggle
        binding.switchShowCosts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_SHOW_COSTS, isChecked).apply()
            Log.d(TAG, "Show cost estimates: $isChecked")
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            try {
                // Load current frequency
                val tracker = aiCallDao.getCurrentTracker()
                val frequencyName = tracker?.callFrequency ?: "BALANCED"
                currentFrequency = try {
                    CallFrequency.valueOf(frequencyName)
                } catch (e: Exception) {
                    CallFrequency.BALANCED
                }

                updateFrequencyUI(currentFrequency)

                // Load cost estimates preference
                val showCosts = prefs.getBoolean(PREF_SHOW_COSTS, true)
                binding.switchShowCosts.isChecked = showCosts

            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings", e)
                Toast.makeText(context, "Error loading settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUsageStatistics() {
        lifecycleScope.launch {
            try {
                val tracker = aiCallDao.getCurrentTracker()

                // Total calls
                val totalCalls = tracker?.totalApiCalls ?: 0
                binding.tvMonthlyCalls.text = totalCalls.toString()

                // Estimated cost (rough calculation)
                val estimatedCost = totalCalls * 1.5 // ~₹1.5 per call
                binding.tvEstimatedCost.text = "₹${String.format("%.2f", estimatedCost)}"

                // Last call time
                val lastCallTime = tracker?.lastCallTimestamp ?: 0L
                binding.tvLastCall.text = if (lastCallTime > 0) {
                    formatTimeAgo(lastCallTime)
                } else {
                    "Never"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading usage statistics", e)
                binding.tvMonthlyCalls.text = "N/A"
                binding.tvEstimatedCost.text = "N/A"
                binding.tvLastCall.text = "N/A"
            }
        }
    }

    private fun selectFrequency(frequency: CallFrequency) {
        if (currentFrequency == frequency) return

        lifecycleScope.launch {
            try {
                // Update repository
                repository.updateCallFrequency(frequency)
                currentFrequency = frequency

                // Update UI
                updateFrequencyUI(frequency)

                // Show confirmation
                Toast.makeText(
                    context,
                    "AI call frequency updated to ${frequency.displayName}",
                    Toast.LENGTH_SHORT
                ).show()

                Log.d(TAG, "Updated call frequency to: ${frequency.displayName}")

            } catch (e: Exception) {
                Log.e(TAG, "Error updating frequency", e)
                Toast.makeText(context, "Error updating frequency", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFrequencyUI(frequency: CallFrequency) {
        // Clear all selections
        binding.radioConservative.isChecked = false
        binding.radioBalanced.isChecked = false
        binding.radioFrequent.isChecked = false

        // Reset card styles
        resetCardStyles()

        // Select current frequency
        when (frequency) {
            CallFrequency.CONSERVATIVE -> {
                binding.radioConservative.isChecked = true
                highlightCard(binding.cardConservative)
            }
            CallFrequency.BALANCED -> {
                binding.radioBalanced.isChecked = true
                highlightCard(binding.cardBalanced)
            }
            CallFrequency.FREQUENT -> {
                binding.radioFrequent.isChecked = true
                highlightCard(binding.cardFrequent)
            }
        }
    }

    private fun resetCardStyles() {
        val defaultColor = resources.getColor(R.color.surface_variant, null)
        binding.cardConservative.strokeColor = defaultColor
        binding.cardBalanced.strokeColor = defaultColor
        binding.cardFrequent.strokeColor = defaultColor
    }

    private fun highlightCard(card: View) {
        val primaryColor = resources.getColor(R.color.primary, null)
        when (card.id) {
            R.id.card_conservative -> binding.cardConservative.strokeColor = primaryColor
            R.id.card_balanced -> binding.cardBalanced.strokeColor = primaryColor
            R.id.card_frequent -> binding.cardFrequent.strokeColor = primaryColor
        }
    }

    private fun performManualRefresh() {
        binding.btnManualRefresh.isEnabled = false
        binding.btnManualRefresh.text = "Generating..."

        lifecycleScope.launch {
            try {
                val result = repository.manualRefresh()

                result.fold(
                    onSuccess = { insights ->
                        Toast.makeText(
                            context,
                            "Fresh insights generated (${insights.size} insights)",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Refresh usage statistics
                        loadUsageStatistics()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            context,
                            "Failed to generate insights: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error during manual refresh", e)
                Toast.makeText(context, "Error generating insights", Toast.LENGTH_SHORT).show()
            } finally {
                binding.btnManualRefresh.isEnabled = true
                binding.btnManualRefresh.text = "Generate Fresh Insights Now"
            }
        }
    }

    private fun showClearCacheConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Cache & Reset")
            .setMessage("This will:\n• Clear all cached insights\n• Reset usage statistics\n• Reset threshold tracking\n\nYou'll need to generate fresh insights after this.")
            .setPositiveButton("Clear") { _, _ ->
                performClearCache()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performClearCache() {
        lifecycleScope.launch {
            try {
                repository.clearCache()

                Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()

                // Refresh UI
                loadUsageStatistics()

                Log.d(TAG, "Cache cleared successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache", e)
                Toast.makeText(context, "Error clearing cache", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> "${days}d ago"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}