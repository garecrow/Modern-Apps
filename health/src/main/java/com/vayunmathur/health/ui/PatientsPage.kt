package com.vayunmathur.health.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.MedicalResource
import com.google.fhir.model.r4b.Patient
import com.vayunmathur.health.R
import com.vayunmathur.health.Route
import com.vayunmathur.health.util.HealthAPI
import com.vayunmathur.library.ui.IconNavigation
import com.vayunmathur.library.util.NavBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPersonalHealthRecordApi::class)
@Composable
fun PatientsPage(backStack: NavBackStack<Route>) {
    val scope = rememberCoroutineScope()
    var patients by remember { mutableStateOf(listOf<Patient>()) }

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                patients = HealthAPI.allMedicalRecords(MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS).map {
                    JSON.decodeFromString<Patient>(it.fhirResource.data)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_patients)) },
                navigationIcon = {
                    IconNavigation(backStack)
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).padding(horizontal = 16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patients.forEach {
                    PatientCard(it)
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
