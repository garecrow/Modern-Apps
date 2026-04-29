package com.vayunmathur.library.ui

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.vayunmathur.library.util.BackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun BackupButtons(
    dbConfigs: List<Pair<String, String>> = emptyList(),
    datastoreNames: List<String> = emptyList(),
    prefNames: List<String> = emptyList(),
    extraFiles: List<File> = emptyList(),
    extraFilesMapping: Map<String, File> = extraFiles.associateBy { it.name }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            Log.d("BackupButtons", "User selected export location: $it")
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { os ->
                        Log.d("BackupButtons", "Starting BackupHelper.performFullBackup")
                        BackupHelper.performFullBackup(context, dbConfigs, datastoreNames, prefNames, extraFiles, os)
                    }
                    Log.d("BackupButtons", "Export finished successfully")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup exported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("BackupButtons", "Export FAILED", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            Log.d("BackupButtons", "User selected import file: $it")
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { isStream ->
                        Log.d("BackupButtons", "Starting BackupHelper.performFullRestore")
                        BackupHelper.performFullRestore(context, dbConfigs, datastoreNames, prefNames, extraFilesMapping, isStream)
                    }
                    Log.d("BackupButtons", "Import finished successfully")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup imported successfully. Please restart the app.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("BackupButtons", "Import FAILED", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Backup import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    IconButton(onClick = { exportLauncher.launch("backup.zip") }) {
        IconBackup()
    }
    IconButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
        IconRestore()
    }
}
