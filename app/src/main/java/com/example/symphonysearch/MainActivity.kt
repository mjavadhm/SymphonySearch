package com.example.symphonysearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.symphonysearch.ui.main.SearchViewModel
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SearchScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel) {
    var query by remember { mutableStateOf("") }
    
    val searchResults by viewModel.searchResults.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    
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
