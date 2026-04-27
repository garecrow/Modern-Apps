package com.vayunmathur.findfamily.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vayunmathur.findfamily.R
import com.vayunmathur.findfamily.Route
import com.vayunmathur.library.util.DataStoreUtils
import com.vayunmathur.library.util.NavBackStack
import com.vayunmathur.library.util.DatabaseViewModel
import com.vayunmathur.library.ui.IconNavigation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    backStack: NavBackStack<Route>,
    viewModel: DatabaseViewModel
) {
    val context = LocalContext.current
    val dataStore = DataStoreUtils.getInstance(context)
    val useNetworkLocation by dataStore.booleanFlow("useNetworkLocation").collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    var switchChecked by remember(useNetworkLocation) { mutableStateOf(useNetworkLocation) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconNavigation(backStack) }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_use_network_location)) },
                    supportingContent = { Text(stringResource(R.string.settings_use_network_location_desc)) },
                    trailingContent = {
                        Switch(
                            checked = switchChecked,
                            onCheckedChange = { enabled ->
                                switchChecked = enabled
                                scope.launch {
                                    dataStore.setBoolean("useNetworkLocation", enabled)
                                }
                            }
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}