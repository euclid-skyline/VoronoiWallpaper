package com.example.voronoiwallpaper.settings

// VoronoiSettingsActivity.kt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.voronoiwallpaper.R
import com.example.voronoiwallpaper.ui.theme.VoronoiWallpaperTheme

class VoronoiSettingsActivity : ComponentActivity() {
    private val voronoiSettingsViewModel: VoronoiSettingsViewModel by viewModels {
        VoronoiViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VoronoiWallpaperTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoronoiSettingsScreen(
                        viewModel = voronoiSettingsViewModel,
                        onBackPressed = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoronoiSettingsScreen(
    viewModel: VoronoiSettingsViewModel,
    onBackPressed: () -> Unit,
    demoMode: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var numPointsText by remember { mutableStateOf(uiState.numPoints.toString()) }

    LaunchedEffect(uiState.numPoints) {
        numPointsText = uiState.numPoints.toString()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.voronoi_settings_title)) },//"Voronoi Wallpaper Settings"
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, //Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading && !demoMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Number of Points
                OutlinedTextField(
                    value = numPointsText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.toIntOrNull() != null) {
                            numPointsText = newValue
                            viewModel.updateSettings(
                                uiState.copy(numPoints = newValue.toIntOrNull() ?: uiState.numPoints)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.num_points_label)) },//"Number of points"
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                Text(
                    text = stringResource(R.string.num_points_hint),//"Recommended: 2-2000 points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Draw Points Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.draw_points_label),//"Show control points",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.drawPoints,
                        onCheckedChange = { newValue ->
                            viewModel.updateSettings(uiState.copy(drawPoints = newValue))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Pixel Step Quality
                Text(
                    text = stringResource(R.string.render_quality_label), // "Render Quality"
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
//                val qualityOptions = listOf(1, 2, 3, 4, 5)
                val context = LocalContext.current
                val qualityOptions = remember {
                    context.resources.getStringArray(R.array.quality_options)
                }

                // Single outline container for all options
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    ),
                    shape = MaterialTheme.shapes.medium
                ) { Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        qualityOptions.forEachIndexed { index, optionText ->
                            val value = index + 1 // Convert array index to 1-based values

//                    qualityOptions.forEach { value ->
//                        val optionText = when (value) {
//                            1 -> "1 (Best)"
//                            2 -> "2 (High)"
//                            3 -> "3 (Balanced)"
//                            4 -> "4 (Fast)"
//                            5 -> "5 (Fastest)"
//                            else -> value.toString()
//                        }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateSettings(uiState.copy(pixelStep = value))
                                    }
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.pixelStep == value,
                                    onClick = {
                                        viewModel.updateSettings(uiState.copy(pixelStep = value))
                                    }
                                )
                                Text(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 1.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.render_quality_hint), // "Higher values improve performance..."
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Spatial Grid Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.spatial_grid_label),//"Use optimization grid",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.useSpatialGrid,
                        onCheckedChange = { newValue ->
                            viewModel.updateSettings(uiState.copy(useSpatialGrid = newValue))
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.spatial_grid_hint),//"Improves performance with many points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, start = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Save Button
                Button(
                    onClick = {
                        val points = numPointsText.toIntOrNull() ?: uiState.numPoints
                        viewModel.updateSettings(
                            uiState.copy(numPoints = points.coerceIn(2, 2000))
                        )
                        onBackPressed()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Save Settings", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/*****************************
 * Preview Composable
 *****************************/

@Preview(showBackground = true)
@Composable
fun VoronoiSettingsScreen_LightPreview() {
    val context = LocalContext.current
    val viewModel = VoronoiSettingsViewModel(VoronoiPreferences(context))

    VoronoiWallpaperTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            VoronoiSettingsScreen(
                viewModel = viewModel,
                onBackPressed = {},
                demoMode = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VoronoiSettingsScreen_DarkPreview() {
    val context = LocalContext.current
    val viewModel = VoronoiSettingsViewModel(VoronoiPreferences(context))

    VoronoiWallpaperTheme(darkTheme = true) {
//        Surface(color = MaterialTheme.colorScheme.background) {
//            VoronoiSettingsScreen(
//                viewModel = viewModel,
//                onBackPressed = {}
//            )
//        }
        VoronoiSettingsScreen(
            viewModel = viewModel,
            onBackPressed = {},
            demoMode = true
        )
    }
}


/*****************************
 * Multi-State Preview
 *****************************/

@Preview(showBackground = true, name = "All Settings States")
@Composable
fun VoronoiSettingsScreen_MultiStatePreview() {
    val context = LocalContext.current
    val viewModel = VoronoiSettingsViewModel(VoronoiPreferences(context)).apply {
        // Simulate different states
        updateSettings(VoronoiSettings(
            numPoints = 1153,
            drawPoints = false,
            pixelStep = 2,
            useSpatialGrid = true
        ))
    }

    VoronoiWallpaperTheme {
        Surface {
            VoronoiSettingsScreen(
                viewModel = viewModel,
                onBackPressed = {},
                demoMode = true
            )
        }
    }
}