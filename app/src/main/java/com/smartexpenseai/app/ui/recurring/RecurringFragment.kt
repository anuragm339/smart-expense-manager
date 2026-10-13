package com.smartexpenseai.app.ui.recurring

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.smartexpenseai.app.R
import com.smartexpenseai.app.data.repository.ExpenseRepository
import com.smartexpenseai.app.databinding.FragmentRecurringBinding
import com.smartexpenseai.app.services.RecurringDetectionService
import com.smartexpenseai.app.services.RecurringExclusionStore
import com.smartexpenseai.app.services.RecurringSeries
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class RecurringFragment : Fragment() {

    private var _binding: FragmentRecurringBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var recurringDetectionService: RecurringDetectionService
    @Inject lateinit var exclusionStore: RecurringExclusionStore

    private val adapter by lazy { RecurringAdapter(::markNotSubscription) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecurringBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.recyclerRecurring.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerRecurring.adapter = adapter
        loadRecurring()
    }

    private fun loadRecurring() {
        viewLifecycleOwner.lifecycleScope.launch {
            val series = recurringDetectionService.detect()
            adapter.submitList(series)

            val hasData = series.isNotEmpty()
            binding.cardSummary.visibility = if (hasData) View.VISIBLE else View.GONE
            binding.recyclerRecurring.visibility = if (hasData) View.VISIBLE else View.GONE
            binding.tvEmpty.visibility = if (hasData) View.GONE else View.VISIBLE

            if (hasData) {
                binding.tvSummaryCount.text = series.size.toString()
                val monthly = series.sumOf { it.monthlyEquivalent }
                binding.tvSummaryMonthly.text = "₹${String.format(Locale.getDefault(), "%,.0f", monthly)}"
            }
        }
    }

    private fun markNotSubscription(series: RecurringSeries) {
        exclusionStore.exclude(series)
        notifyRecurringChanged()
        loadRecurring()
        Snackbar.make(
            binding.root,
            getString(R.string.recurring_removed, series.displayName),
            Snackbar.LENGTH_LONG,
        ).setAction(R.string.undo) {
            exclusionStore.restore(series)
            notifyRecurringChanged()
            loadRecurring()
        }.show()
    }

    private fun notifyRecurringChanged() {
        requireContext().sendBroadcast(
            Intent(ExpenseRepository.ACTION_DATA_CHANGED)
                .setPackage(requireContext().packageName)
                .putExtra("reason", "recurring_exclusion_changed"),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
