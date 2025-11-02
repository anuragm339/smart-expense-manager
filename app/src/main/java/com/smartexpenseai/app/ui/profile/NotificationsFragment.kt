package com.smartexpenseai.app.ui.profile

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.smartexpenseai.app.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {
    
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var prefs: SharedPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
        setupSwitchListeners()
        loadSettings()
    }
    
    private fun setupSwitchListeners() {
        binding.switchTransactionNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("transaction_notifications", isChecked).apply()
            showSettingChanged("Transaction notifications", isChecked)
        }
        
        binding.switchBudgetAlerts.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("budget_alerts", isChecked).apply()
            showSettingChanged("Budget alerts", isChecked)
        }
        
        binding.switch50PercentAlert.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alert_50_percent", isChecked).apply()
            showSettingChanged("50% budget alert", isChecked)
        }
        
        binding.switch80PercentAlert.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alert_80_percent", isChecked).apply()
            showSettingChanged("80% budget alert", isChecked)
        }
        
        binding.switchBudgetExceededAlert.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("alert_budget_exceeded", isChecked).apply()
            showSettingChanged("Budget exceeded alert", isChecked)
        }
        
        binding.switchWeeklySummary.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("weekly_summary", isChecked).apply()
            showSettingChanged("Weekly summary", isChecked)
        }
        
        binding.switchMonthlyReport.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("monthly_report", isChecked).apply()
            showSettingChanged("Monthly report", isChecked)
        }
    }
    
    private fun loadSettings() {
        binding.switchTransactionNotifications.isChecked = prefs.getBoolean("transaction_notifications", true)
        binding.switchBudgetAlerts.isChecked = prefs.getBoolean("budget_alerts", true)
        binding.switch50PercentAlert.isChecked = prefs.getBoolean("alert_50_percent", false)
        binding.switch80PercentAlert.isChecked = prefs.getBoolean("alert_80_percent", true)
        binding.switchBudgetExceededAlert.isChecked = prefs.getBoolean("alert_budget_exceeded", true)
        binding.switchWeeklySummary.isChecked = prefs.getBoolean("weekly_summary", true)
        binding.switchMonthlyReport.isChecked = prefs.getBoolean("monthly_report", true)
    }
    
    private fun showSettingChanged(settingName: String, enabled: Boolean) {
        val status = if (enabled) "enabled" else "disabled"
        Toast.makeText(requireContext(), "$settingName $status", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}