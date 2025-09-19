package com.expensemanager.app.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.expensemanager.app.R
import com.expensemanager.app.databinding.FragmentLoggingSettingsBinding
import com.expensemanager.app.utils.AppLogger
import com.expensemanager.app.utils.logging.LogConfig
import timber.log.Timber
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Fragment for managing application logging settings.
 * Allows users to configure log levels, enable/disable file logging,
 * export log files, and clear log files.
 */
@AndroidEntryPoint
class LoggingSettingsFragment : Fragment() {

    private var _binding: FragmentLoggingSettingsBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appLogger: AppLogger

    companion object {
        private const val TAG = "LoggingSettings"
        private const val STORAGE_PERMISSION_REQUEST = 101
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoggingSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        loadCurrentSettings()
        
        appLogger.info(TAG, "Logging settings screen opened")
    }

    private fun setupUI() {
        // Log Level Spinner
        binding.spinnerLogLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val logLevel = when (position) {
                    0 -> LogConfig.LogLevel.ERROR
                    1 -> LogConfig.LogLevel.WARN
                    2 -> LogConfig.LogLevel.INFO
                    3 -> LogConfig.LogLevel.DEBUG
                    4 -> LogConfig.LogLevel.VERBOSE
                    else -> LogConfig.LogLevel.INFO
                }
                appLogger.setLogLevel(logLevel)
                appLogger.info(TAG, "Log level changed to: $position")
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }

        // File Logging Toggle
        binding.switchFileLogging.setOnCheckedChangeListener { _, isChecked ->
            appLogger.setFileLoggingEnabled(isChecked)
            updateUI()
            appLogger.info(TAG, "File logging ${if (isChecked) "enabled" else "disabled"}")
        }

        // External Storage Logging Toggle
        binding.switchExternalLogging.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !hasStoragePermission()) {
                requestStoragePermission()
                binding.switchExternalLogging.isChecked = false
            } else {
                appLogger.setExternalLoggingEnabled(isChecked)
                appLogger.info(TAG, "External logging ${if (isChecked) "enabled" else "disabled"}")
            }
        }

        // Export Logs Button
        binding.buttonExportLogs.setOnClickListener {
            if (!hasStoragePermission()) {
                requestStoragePermission()
                return@setOnClickListener
            }
            exportLogFiles()
        }

        // Clear Logs Button
        binding.buttonClearLogs.setOnClickListener {
            clearLogFiles()
        }

        // Refresh Log Info Button
        binding.buttonRefreshLogInfo.setOnClickListener {
            updateLogFileInfo()
        }
    }

    private fun loadCurrentSettings() {
        // Load current log level
        val currentLevel = appLogger.getLogLevel()
        val spinnerPosition = when (currentLevel) {
            LogConfig.LogLevel.ERROR -> 0
            LogConfig.LogLevel.WARN -> 1
            LogConfig.LogLevel.INFO -> 2
            LogConfig.LogLevel.DEBUG -> 3
            LogConfig.LogLevel.VERBOSE -> 4
            else -> 2
        }
        binding.spinnerLogLevel.setSelection(spinnerPosition)

        // Load current settings
        binding.switchFileLogging.isChecked = appLogger.isFileLoggingEnabled()
        binding.switchExternalLogging.isChecked = appLogger.isExternalLoggingEnabled()

        updateUI()
        updateLogFileInfo()
    }

    private fun updateUI() {
        val fileLoggingEnabled = binding.switchFileLogging.isChecked
        binding.switchExternalLogging.isEnabled = fileLoggingEnabled
        binding.buttonExportLogs.isEnabled = fileLoggingEnabled
        binding.buttonClearLogs.isEnabled = fileLoggingEnabled
    }

    private fun updateLogFileInfo() {
        lifecycleScope.launch {
            try {
                val logFiles = appLogger.getLogFiles()
                val totalSize = logFiles.sumOf { it.length() }
                val fileCount = logFiles.size

                withContext(Dispatchers.Main) {
                    binding.textLogFileInfo.text = getString(
                        R.string.log_file_info,
                        fileCount,
                        formatFileSize(totalSize)
                    )
                }

                appLogger.debug(TAG, "Log file info updated - Files: $fileCount, Size: ${formatFileSize(totalSize)}")

            } catch (e: Exception) {
                appLogger.error(TAG, "Error updating log file info", e)
                withContext(Dispatchers.Main) {
                    binding.textLogFileInfo.text = "Error reading log files"
                }
            }
        }
    }

    private fun exportLogFiles() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    binding.buttonExportLogs.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                }

                val zipFile = createLogZipFile()

                withContext(Dispatchers.Main) {
                    if (zipFile != null) {
                        shareLogFile(zipFile)
                        Toast.makeText(
                            context, 
                            "Log files exported successfully", 
                            Toast.LENGTH_SHORT
                        ).show()
                        appLogger.info(TAG, "Log files exported: ${zipFile.name}")
                    } else {
                        Toast.makeText(
                            context, 
                            "Failed to export log files", 
                            Toast.LENGTH_SHORT
                        ).show()
                        appLogger.warn(TAG, "Failed to export log files")
                    }
                    
                    binding.progressBar.visibility = View.GONE
                    binding.buttonExportLogs.isEnabled = true
                }

            } catch (e: Exception) {
                appLogger.error(TAG, "Error exporting log files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Error exporting log files: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                    binding.progressBar.visibility = View.GONE
                    binding.buttonExportLogs.isEnabled = true
                }
            }
        }
    }

    private suspend fun createLogZipFile(): File? {
        return withContext(Dispatchers.IO) {
            try {
                val logFiles = appLogger.getLogFiles()
                if (logFiles.isEmpty()) return@withContext null

                val dateFormatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                val zipFileName = "expense-manager-logs_${dateFormatter.format(Date())}.zip"
                val zipFile = File(requireContext().getExternalFilesDir(null), zipFileName)

                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    logFiles.forEach { logFile ->
                        FileInputStream(logFile).use { fileIn ->
                            val entry = ZipEntry(logFile.name)
                            zipOut.putNextEntry(entry)
                            
                            val buffer = ByteArray(1024)
                            var length: Int
                            while (fileIn.read(buffer).also { length = it } > 0) {
                                zipOut.write(buffer, 0, length)
                            }
                            zipOut.closeEntry()
                        }
                    }
                }

                zipFile

            } catch (e: IOException) {
                appLogger.error(TAG, "Error creating zip file", e)
                null
            }
        }
    }

    private fun shareLogFile(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Smart Expense Manager - Log Files")
                putExtra(Intent.EXTRA_TEXT, "Log files from Smart Expense Manager app")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share Log Files"))

        } catch (e: Exception) {
            appLogger.error(TAG, "Error sharing log file", e)
            Toast.makeText(context, "Error sharing log file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearLogFiles() {
        lifecycleScope.launch {
            try {
                appLogger.clearLogFiles()
                
                withContext(Dispatchers.Main) {
                    updateLogFileInfo()
                    Toast.makeText(
                        context, 
                        "Log files cleared successfully", 
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                appLogger.info(TAG, "Log files cleared by user")

            } catch (e: Exception) {
                appLogger.error(TAG, "Error clearing log files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context, 
                        "Error clearing log files: ${e.message}", 
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_REQUEST
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                binding.switchExternalLogging.isChecked = true
                appLogger.setExternalLoggingEnabled(true)
                appLogger.info(TAG, "Storage permission granted, external logging enabled")
            } else {
                Toast.makeText(
                    context, 
                    "Storage permission is required for external logging", 
                    Toast.LENGTH_LONG
                ).show()
                appLogger.warn(TAG, "Storage permission denied")
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}