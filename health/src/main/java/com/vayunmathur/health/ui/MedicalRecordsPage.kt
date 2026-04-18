package com.vayunmathur.health.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import androidx.core.content.FileProvider
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import com.google.fhir.model.r4b.Immunization
import com.google.fhir.model.r4b.Patient
import com.vayunmathur.library.util.SecureResultReceiver
import com.vayunmathur.health.util.HealthAPI
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.ui.IconUpload
import com.vayunmathur.library.util.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPersonalHealthRecordApi::class)
@Composable
fun MedicalRecordsPage(backStack: NavBackStack<Route>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var patients by remember { mutableStateOf(listOf<Patient>()) }
    var immunizations by remember { mutableStateOf(listOf<Immunization>()) }
    var showInstallDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                patients = HealthAPI.allMedicalRecords(MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS).map {
                    JSON.decodeFromString<Patient>(it.fhirResource.data)
                }
                immunizations = HealthAPI.allMedicalRecords(MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES).map {
                    JSON.decodeFromString<Immunization>(it.fhirResource.data)
                }
            }
        }
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            title = { Text("OpenAssistant Required") },
            text = { Text("The OpenAssistant app is required for offline, secure, medical document extraction. Please install it from GitHub.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/vayun-mathur/Modern-Apps".toUri())
                    context.startActivity(intent)
                    showInstallDialog = false
                }) {
                    Text("View on GitHub")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInstallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    val immunizationSchema = """
        {
          "type": "object",
          "description": "FHIR Immunization resource schema",
          "properties": {
            "resourceType": { "const": "Immunization", "description": "Required: Resource type must be 'Immunization'" },
            "status": { "type": "string", "enum": ["completed", "entered-in-error", "not-done"], "description": "Required: Current status of the immunization event" },
            "vaccineCode": {
              "type": "object",
              "description": "Required: Vaccine that was administered",
              "properties": {
                "text": { "type": "string", "description": "Required: Plain text representation of the vaccine (e.g. 'COVID-19', 'Influenza')" },
                "coding": {
                  "type": "array",
                  "description": "Optional: Code defined by a terminology system",
                  "items": {
                    "type": "object",
                    "properties": {
                      "system": { "type": "string", "description": "Optional" },
                      "code": { "type": "string", "description": "Optional" },
                      "display": { "type": "string", "description": "Optional" }
                    }
                  }
                }
              },
              "required": ["text"]
            },
            "patient": {
              "type": "object",
              "description": "Required: The patient who received the immunization. Capture the patient's name here in the 'display' field.",
              "properties": {
                "display": { "type": "string", "description": "The name of the person being vaccinated." },
                "reference": { "type": "string", "description": "Optional reference ID" }
              },
              "required": ["display"]
            },
            "occurrenceDateTime": { "type": "string", "format": "date-time", "description": "Optional: Date/time vaccine was administered (ISO 8601 format)" },
            "occurrenceString": { "type": "string", "description": "Optional: Date vaccine was administered if dateTime is not available" },
            "lotNumber": { "type": "string", "description": "Optional: Lot number of the vaccine product" },
            "expirationDate": { "type": "string", "format": "date", "description": "Optional: Date vaccine batch expires" },
            "manufacturer": {
              "type": "object",
              "description": "Optional: Vaccine manufacturer",
              "properties": {
                "display": { "type": "string", "description": "Optional: Name of manufacturer" }
              }
            },
            "note": {
              "type": "array",
              "description": "Optional: Extra information",
              "items": {
                "type": "object",
                "properties": {
                  "text": { "type": "string" }
                }
              }
            }
          },
          "required": ["resourceType", "status", "vaccineCode", "patient"]
        }
    """.trimIndent()

    val resultReceiver = remember {
        SecureResultReceiver(null) { resultCode, resultData ->
            Log.d("MedicalRecordsPage", "ResultReceiver onReceiveResult: $resultCode")
            if (resultCode == 0) {
                val jsonResult = resultData?.getString("json_result")
                if (jsonResult != null) {
                    Log.i("MedicalRecordsPage", "Received extraction result from AI")
                    scope.launch {
                        try {
                            HealthAPI.writeMedicalRecord(jsonResult)
                            Log.i("MedicalRecordsPage", "Successfully wrote medical record to Health Connect")
                            refresh()
                        } catch (e: Exception) {
                            Log.e("MedicalRecordsPage", "Failed to write medical record", e)
                        }
                    }
                } else {
                    Log.w("MedicalRecordsPage", "Received success code but JSON result is null")
                }
            } else {
                val error = resultData?.getString("error")
                Log.e("MedicalRecordsPage", "Extraction failed with code $resultCode: $error")
            }
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Log.i("MedicalRecordsPage", "PDF selected: $uri")
            scope.launch {
                try {
                    val imagePaths = convertPdfToImages(context, uri)
                    if (imagePaths.isNotEmpty()) {
                        Log.d("MedicalRecordsPage", "Converted PDF to ${imagePaths.size} images. Starting InferenceService.")
                        val intent = Intent().apply {
                            setClassName("com.vayunmathur.openassistant", "com.vayunmathur.openassistant.util.InferenceService")
                            putExtra("user_text", "Extract immunization details from these images.")
                            
                            val uris = imagePaths.map { path ->
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
                                context.grantUriPermission("com.vayunmathur.openassistant", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                uri
                            }

                            putParcelableArrayListExtra("image_uris", ArrayList(uris))
                            
                            putExtra("schema", immunizationSchema)
                            putExtra("RECEIVER", resultReceiver)
                        }
                        context.startService(intent)
                    } else {
                        Log.w("MedicalRecordsPage", "Failed to convert PDF to images")
                    }
                } catch (e: Exception) {
                    Log.e("MedicalRecordsPage", "Error processing PDF", e)
                }
            }
        } else {
            Log.d("MedicalRecordsPage", "PDF selection cancelled")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_medical_records)) },
                navigationIcon = {
                    IconNavigation(backStack)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (isOpenAssistantInstalled(context)) {
                    pdfLauncher.launch("application/pdf")
                } else {
                    showInstallDialog = true
                }
            }) {
                IconUpload()
            }
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patients.forEach {
                    PatientCard(it)
                }
                immunizations.forEach {
                    ImmunizationCard(it)
                }
            }
        }
    }
}

@Composable
fun ImmunizationCard(immunization: Immunization) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.baseline_favorite_24),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(immunization.vaccineCode.text?.value ?: "Unknown Vaccine", style = MaterialTheme.typography.titleLarge)
                Text("Status: ${immunization.status.value?.getDisplay() ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
                
                val occurrenceDisplay = when (val occ = immunization.occurrence) {
                    is Immunization.Occurrence.DateTime -> occ.value.value?.toString()
                    is Immunization.Occurrence.String -> occ.value.value
                }
                if (occurrenceDisplay != null) {
                    Text("Date: $occurrenceDisplay", style = MaterialTheme.typography.bodyMedium)
                }
                
                if (immunization.lotNumber?.value != null) {
                    Text("Lot: ${immunization.lotNumber!!.value}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun PatientCard(patient: Patient) {
    val nameString = patient.name.firstOrNull()?.text?.value
    val addressString = patient.address.firstOrNull()?.text?.value
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painterResource(R.drawable.baseline_favorite_24),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(nameString ?: stringResource(R.string.unknown), style = MaterialTheme.typography.titleLarge)
                patient.gender?.value?.getDisplay()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                patient.birthDate?.value?.toString()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                if (addressString != null)
                    Text(addressString, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun isOpenAssistantInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.vayunmathur.openassistant", 0)
        true
    } catch (e: Exception) {
        false
    }
}

private fun convertPdfToImages(context: Context, uri: Uri): List<String> {
    val imagePaths = mutableListOf<String>()
    try {
        val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        val renderer = PdfRenderer(parcelFileDescriptor)
        for (i in 0 until renderer.pageCount) {
            Log.d("MedicalRecordsPage", "Rendering PDF page $i")
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val file = File(context.cacheDir, "pdf_page_$i.png")
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.close()
            page.close()
            imagePaths.add(file.absolutePath)
        }
        renderer.close()
        parcelFileDescriptor.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return imagePaths
}
