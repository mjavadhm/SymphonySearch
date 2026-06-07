package com.example.symphonysearch

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.symphonysearch.ml.ModelManager
import com.example.symphonysearch.ui.main.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val modelManager = ModelManager(this)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var areModelsReady by remember { mutableStateOf(modelManager.areModelsImported()) }
                    
                    if (areModelsReady) {
                        SearchScreen(viewModel)
                    } else {
                        ImportScreen(
                            modelManager = modelManager,
                            onImportComplete = { areModelsReady = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImportScreen(modelManager: ModelManager, onImportComplete: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isImportingAudio by remember { mutableStateOf(false) }
    var isImportingText by remember { mutableStateOf(false) }
    
    var audioImported by remember { mutableStateOf(modelManager.audioModelFile.exists()) }
    var textImported by remember { mutableStateOf(modelManager.textModelFile.exists()) }
    
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            isImportingAudio = true
            coroutineScope.launch(Dispatchers.IO) {
                val result = modelManager.importModel(it, isAudio = true)
                withContext(Dispatchers.Main) {
                    isImportingAudio = false
                    if (result.isSuccess) {
                        audioImported = true
                        Toast.makeText(context, "Audio model imported!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import audio model.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val textPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            isImportingText = true
            coroutineScope.launch(Dispatchers.IO) {
                val result = modelManager.importModel(it, isAudio = false)
                withContext(Dispatchers.Main) {
                    isImportingText = false
                    if (result.isSuccess) {
                        textImported = true
                        Toast.makeText(context, "Text model imported!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import text model.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Models Required", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Please import the ONNX models to continue.", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { audioPicker.launch(arrayOf("*/*")) },
            enabled = !isImportingAudio && !audioImported,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (audioImported) "Audio Model Imported ✅" else if (isImportingAudio) "Importing..." else "Import Audio Model")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { textPicker.launch(arrayOf("*/*")) },
            enabled = !isImportingText && !textImported,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (textImported) "Text Model Imported ✅" else if (isImportingText) "Importing..." else "Import Text Model")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { onImportComplete() },
            enabled = audioImported && textImported,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    var query by remember { mutableStateOf("") }
    
    val searchResults by viewModel.searchResults.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    
    // Ensure model runner is initialized when we enter the search screen
    LaunchedEffect(Unit) {
        viewModel.initModelRunner()
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Symphony AI Core", style = MaterialTheme.typography.headlineMedium)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // بخش شبیه‌سازی وارد کردن آهنگ به دیتابیس
        Button(
            onClick = {
                // شبیه‌سازی یک آرایه صوتی ۴۸ کیلواهرتز (مثلاً ۱۰ ثانیه نویز تصادفی)
                val dummyAudio = FloatArray(48000 * 10) { Random.nextFloat() * 2 - 1 }
                val trackName = "Random_Track_${System.currentTimeMillis() % 10000}"
                viewModel.indexDummyAudio(trackName, dummyAudio)
            },
            enabled = !isProcessing
        ) {
            Text("Index Random Dummy Audio")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search query (e.g. happy guitar)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = { viewModel.searchByText(query) },
            enabled = !isProcessing && query.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Semantic Search")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Status: $statusMessage", color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(searchResults) { track ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = track.title ?: "Unknown", style = MaterialTheme.typography.titleMedium)
                        Text(text = track.filePath ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
