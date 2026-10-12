package com.smartexpenseai.app.ui.onboarding

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.smartexpenseai.app.R
import com.smartexpenseai.app.databinding.ActivityMainBinding

class FeatureTourController(
    private val binding: ActivityMainBinding,
    private val onFinished: () -> Unit,
) {

    private var currentStep = 0

    private val steps = listOf(
        FeatureTourStep(
            destination = R.id.navigation_dashboard,
            tabName = R.string.nav_dashboard,
            title = R.string.feature_tour_dashboard_title,
            description = R.string.feature_tour_dashboard_description,
            action = R.string.feature_tour_dashboard_action,
        ),
        FeatureTourStep(
            destination = R.id.navigation_messages,
            tabName = R.string.nav_messages,
            title = R.string.feature_tour_messages_title,
            description = R.string.feature_tour_messages_description,
            action = R.string.feature_tour_messages_action,
        ),
        FeatureTourStep(
            destination = R.id.navigation_categories,
            tabName = R.string.nav_categories,
            title = R.string.feature_tour_categories_title,
            description = R.string.feature_tour_categories_description,
            action = R.string.feature_tour_categories_action,
        ),
        FeatureTourStep(
            destination = R.id.navigation_insights,
            tabName = R.string.nav_insights,
            title = R.string.feature_tour_insights_title,
            description = R.string.feature_tour_insights_description,
            action = R.string.feature_tour_insights_action,
        ),
        FeatureTourStep(
            destination = R.id.navigation_profile,
            tabName = R.string.nav_profile,
            title = R.string.feature_tour_profile_title,
            description = R.string.feature_tour_profile_description,
            action = R.string.feature_tour_profile_action,
        ),
    )

    fun start() {
        currentStep = 0
        binding.btnFeatureTourSkip.setOnClickListener { finish() }
        binding.btnFeatureTourBack.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                renderStep()
            }
        }
        binding.btnFeatureTourNext.setOnClickListener {
            if (currentStep == steps.lastIndex) {
                finish()
            } else {
                currentStep++
                renderStep()
            }
        }
        renderStep()
    }

    fun dismiss() {
        setNavigationEnabled(true)
        binding.featureTourOverlay.visibility = View.GONE
    }

    private fun renderStep() {
        val step = steps[currentStep]

        // Enable destinations just long enough for NavigationUI to switch the real
        // screen, then lock the bar so the explanation always matches the screen.
        setNavigationEnabled(true)
        binding.bottomNavigation.selectedItemId = step.destination
        setNavigationEnabled(false)

        binding.tvFeatureTourCounter.text = binding.root.context.getString(
            R.string.feature_tour_step_counter,
            currentStep + 1,
            steps.size,
        )
        binding.tvFeatureTourViewing.text = binding.root.context.getString(
            R.string.feature_tour_viewing,
            binding.root.context.getString(step.tabName),
        )
        binding.tvFeatureTourTitle.setText(step.title)
        binding.tvFeatureTourDescription.setText(step.description)
        binding.tvFeatureTourAction.setText(step.action)
        binding.btnFeatureTourBack.isEnabled = currentStep > 0
        binding.btnFeatureTourBack.alpha = if (currentStep > 0) 1f else 0.35f
        binding.btnFeatureTourNext.setText(
            if (currentStep == steps.lastIndex) {
                R.string.feature_tour_finish
            } else {
                R.string.onboarding_next
            },
        )
        binding.featureTourProgress.max = steps.size
        binding.featureTourProgress.progress = currentStep + 1
        binding.featureTourOverlay.visibility = View.VISIBLE
    }

    private fun finish() {
        dismiss()
        onFinished()
    }

    private fun setNavigationEnabled(enabled: Boolean) {
        for (index in 0 until binding.bottomNavigation.menu.size()) {
            binding.bottomNavigation.menu.getItem(index).isEnabled = enabled
        }
    }

    private data class FeatureTourStep(
        @IdRes val destination: Int,
        @StringRes val tabName: Int,
        @StringRes val title: Int,
        @StringRes val description: Int,
        @StringRes val action: Int,
    )
}
