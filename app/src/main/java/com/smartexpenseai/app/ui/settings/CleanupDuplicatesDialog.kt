package com.smartexpenseai.app.ui.settings

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartexpenseai.app.R
import com.smartexpenseai.app.utils.DuplicateCleanupHelper
import com.smartexpenseai.app.utils.logging.StructuredLogger
import kotlinx.coroutines.launch

/**
 * CleanupDuplicatesDialog - UI for cleaning up duplicate transactions
 *
 * This dialog allows users to:
 * 1. Preview duplicates before cleanup
 * 2. See how many duplicates will be removed
 * 3. Perform the cleanup with visual feedback
 * 4. View cleanup results
 */
class CleanupDuplicatesDialog : DialogFragment() {

    private val logger = StructuredLogger(
        featureTag = "UI",
        className = "CleanupDuplicatesDialog"
    )

    private var progressBar: ProgressBar? = null
    private var statusText: TextView? = null
    private var previewButton: Button? = null
    private var cleanupButton: Button? = null
    private var closeButton: Button? = null

    private var isProcessing = false

    companion object {
        const val TAG = "CleanupDuplicatesDialog"

        fun newInstance(): CleanupDuplicatesDialog {
            return CleanupDuplicatesDialog()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clean Up Duplicate Transactions")
            .setView(createView())
            .setCancelable(true)
            .create()
    }

    private fun createView(): View {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_cleanup_duplicates, null)

        progressBar = view.findViewById(R.id.progress_bar)
        statusText = view.findViewById(R.id.status_text)
        previewButton = view.findViewById(R.id.btn_preview)
        cleanupButton = view.findViewById(R.id.btn_cleanup)
        closeButton = view.findViewById(R.id.btn_close)

        setupButtons()
        updateUI(initialState = true)

        return view
    }

    private fun setupButtons() {
        previewButton?.setOnClickListener {
            if (!isProcessing) {
                previewDuplicates()
            }
        }

        cleanupButton?.setOnClickListener {
            if (!isProcessing) {
                confirmAndCleanup()
            }
        }

        closeButton?.setOnClickListener {
            dismiss()
        }
    }

    private fun previewDuplicates() {
        isProcessing = true
        updateUI(loading = true)

        lifecycleScope.launch {
            try {
                statusText?.text = "Scanning for duplicates..."

                val result = DuplicateCleanupHelper.previewDuplicates(requireContext())

                val message = buildPreviewMessage(result)
                statusText?.text = message

                // Enable cleanup button if duplicates found
                cleanupButton?.isEnabled = result.duplicatesFound > 0

                logger.info(
                    where = "previewDuplicates",
                    what = "Preview complete: ${result.duplicatesFound} duplicates found"
                )

            } catch (e: Exception) {
                logger.error(
                    where = "previewDuplicates",
                    what = "Preview failed",
                    throwable = e
                )
                statusText?.text = "Error: ${e.message}"
            } finally {
                isProcessing = false
                updateUI(loading = false)
            }
        }
    }

    private fun confirmAndCleanup() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirm Cleanup")
            .setMessage(
                "This will permanently remove duplicate transactions from your database.\n\n" +
                "The transaction with the highest confidence score will be kept.\n\n" +
                "This action cannot be undone. Continue?"
            )
            .setPositiveButton("Clean Up") { _, _ ->
                performCleanup()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performCleanup() {
        isProcessing = true
        updateUI(loading = true)

        lifecycleScope.launch {
            try {
                statusText?.text = "Cleaning up duplicates..."

                val result = DuplicateCleanupHelper.cleanupAllDuplicates(requireContext())

                val message = buildCleanupMessage(result)
                statusText?.text = message

                // Disable cleanup button after cleanup
                cleanupButton?.isEnabled = false

                logger.info(
                    where = "performCleanup",
                    what = "Cleanup complete: ${result.duplicatesRemoved} duplicates removed"
                )

                // Show success message
                if (result.duplicatesRemoved > 0) {
                    showSuccessDialog(result)
                }

            } catch (e: Exception) {
                logger.error(
                    where = "performCleanup",
                    what = "Cleanup failed",
                    throwable = e
                )
                statusText?.text = "Error during cleanup: ${e.message}"
            } finally {
                isProcessing = false
                updateUI(loading = false)
            }
        }
    }

    private fun buildPreviewMessage(result: DuplicateCleanupHelper.CleanupResult): String {
        return if (result.duplicatesFound == 0) {
            "✅ No duplicates found!\n\n" +
            "Total transactions: ${result.totalTransactions}\n" +
            "Your database is clean."
        } else {
            "🔍 Found ${result.duplicatesFound} duplicate transactions\n\n" +
            "Total transactions: ${result.totalTransactions}\n" +
            "Duplicates: ${result.duplicatesFound}\n" +
            "After cleanup: ${result.totalTransactions - result.duplicatesFound}\n\n" +
            "Tap 'Clean Up' to remove duplicates."
        }
    }

    private fun buildCleanupMessage(result: DuplicateCleanupHelper.CleanupResult): String {
        return if (result.duplicatesRemoved == 0) {
            "✅ No duplicates removed\n\n" +
            "Database was already clean."
        } else {
            "✅ Cleanup successful!\n\n" +
            "Removed: ${result.duplicatesRemoved} duplicates\n" +
            "Remaining: ${result.totalTransactions - result.duplicatesRemoved} transactions\n\n" +
            "Your database is now clean."
        }
    }

    private fun showSuccessDialog(result: DuplicateCleanupHelper.CleanupResult) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✅ Cleanup Successful")
            .setMessage(
                "Removed ${result.duplicatesRemoved} duplicate transactions.\n\n" +
                "Before: ${result.totalTransactions} transactions\n" +
                "After: ${result.totalTransactions - result.duplicatesRemoved} transactions\n\n" +
                "Your database has been optimized!"
            )
            .setPositiveButton("OK") { _, _ ->
                // Optionally refresh the UI
                dismiss()
            }
            .show()
    }

    private fun updateUI(loading: Boolean = false, initialState: Boolean = false) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE

        previewButton?.isEnabled = !loading
        cleanupButton?.isEnabled = !loading && !initialState
        closeButton?.isEnabled = !loading

        if (initialState) {
            statusText?.text = "Tap 'Preview' to scan for duplicate transactions."
            cleanupButton?.isEnabled = false
        }
    }
}
