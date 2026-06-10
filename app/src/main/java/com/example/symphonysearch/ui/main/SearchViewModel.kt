package com.example.symphonysearch.ui.main

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.symphonysearch.SymphonySearchApp
import com.example.symphonysearch.data.SearchResult
import com.example.symphonysearch.data.SemanticSearchRepository
import com.example.symphonysearch.ml.AudioDecoder
import com.example.symphonysearch.ml.ClapModelRunner
import com.example.symphonysearch.ml.MelSpectrogramExtractor
import com.example.symphonysearch.ml.RobertaTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SemanticSearchRepository
    private val tokenizer: RobertaTokenizer
    private val melExtractor: MelSpectrogramExtractor
    
    // Lazy initialization
    private var modelRunner: ClapModelRunner? = null

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    private val _statusMessage = MutableStateFlow("Ready")
    val statusMessage: StateFlow<String> = _statusMessage

    init {
        val app = application as SymphonySearchApp
        repository = SemanticSearchRepository(app.boxStore)
        tokenizer = RobertaTokenizer(app)
        melExtractor = MelSpectrogramExtractor()
    }
    
    fun initModelRunner() {
        if (modelRunner == null) {
            modelRunner = ClapModelRunner(getApplication<Application>())
        }
    }

    /**
     * Import an entire folder of audio files, split into chunks, extract embeddings, and save to DB.
     */
    fun indexAudioFolder(folderUri: Uri, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Scanning folder..."
            
            withContext(Dispatchers.IO) {
                initModelRunner()
                val runner = modelRunner ?: return@withContext
                val decoder = AudioDecoder(context)

                val documentFile = DocumentFile.fromTreeUri(context, folderUri)
                if (documentFile == null || !documentFile.isDirectory) {
                    _statusMessage.value = "Invalid folder selected."
                    return@withContext
                }

                val audioFiles = mutableListOf<DocumentFile>()
                findAudioFiles(documentFile, audioFiles)

                if (audioFiles.isEmpty()) {
                    _statusMessage.value = "No audio files found."
                    return@withContext
                }

                var processedCount = 0
                for (file in audioFiles) {
                    val title = file.name ?: "Unknown Track"
                    _statusMessage.value = "Processing (${processedCount + 1}/${audioFiles.size}): $title"

                    try {
                        val duration = decoder.getDurationSeconds(file.uri)
                        val chunks = decoder.extractChunks(file.uri)
                        
                        if (chunks.isEmpty()) {
                            Log.w("SearchViewModel", "No chunks extracted for $title")
                            continue
                        }

                        val chunkEmbeddings = chunks.map { chunk ->
                            val melSpec = melExtractor.extract(chunk.floatArray)
                            runner.getAudioEmbedding(melSpec)
                        }

                        repository.insertTrack(
                            filePath = file.uri.toString(),
                            title = title,
                            durationSeconds = duration,
                            chunkEmbeddings = chunkEmbeddings
                        )
                        processedCount++
                    } catch (e: Exception) {
                        Log.e("SearchViewModel", "Error processing $title", e)
                    }
                }
                
                _statusMessage.value = "Finished indexing $processedCount tracks."
            }
            
            _isProcessing.value = false
        }
    }

    /**
     * Import pre-computed embeddings from a JSON file.
     */
    fun importJsonDatabase(jsonUri: Uri, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Reading JSON database..."
            
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(jsonUri)
                    if (inputStream == null) {
                        _statusMessage.value = "Failed to open file."
                        return@withContext
                    }
                    
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    _statusMessage.value = "Parsing JSON..."
                    
                    val jsonFormat = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    val tracks = jsonFormat.decodeFromString<List<com.example.symphonysearch.data.TrackJson>>(jsonString)
                    
                    _statusMessage.value = "Saving to ObjectBox..."
                    var saved = 0
                    for (track in tracks) {
                        val floatArrayChunks = track.chunks.map { it.toFloatArray() }
                        repository.insertTrack(
                            filePath = track.filename,
                            title = track.title,
                            durationSeconds = track.duration,
                            chunkEmbeddings = floatArrayChunks
                        )
                        saved++
                        if (saved % 100 == 0) {
                            _statusMessage.value = "Saved $saved / ${tracks.size} tracks..."
                        }
                    }
                    
                    _statusMessage.value = "Successfully imported $saved tracks!"
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "JSON import error", e)
                    _statusMessage.value = "Error importing JSON: ${e.message}"
                }
            }
            
            _isProcessing.value = false
        }
    }

    private fun findAudioFiles(dir: DocumentFile, result: MutableList<DocumentFile>) {
        val allowedExtensions = listOf("mp3", "wav", "flac", "m4a")
        for (file in dir.listFiles()) {
            if (file.isDirectory) {
                findAudioFiles(file, result)
            } else {
                val ext = file.name?.substringAfterLast('.')?.lowercase()
                if (ext in allowedExtensions || file.type?.startsWith("audio/") == true) {
                    result.add(file)
                }
            }
        }
    }

    /**
     * Search using text query with Hybrid Ranking (Max + Mean).
     */
    fun searchByText(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Searching for '$query'..."
            
            val results = withContext(Dispatchers.Default) {
                initModelRunner()
                val runner = modelRunner ?: return@withContext emptyList()
                
                val (inputIds, attentionMask) = tokenizer.encode(query)
                val queryEmbedding = runner.getTextEmbedding(inputIds, attentionMask)
                
                repository.searchHybrid(queryEmbedding, topN = 10)
            }
            
            _searchResults.value = results
            _statusMessage.value = "Found ${results.size} matches."
            _isProcessing.value = false
        }
    }

    /**
     * Search using an audio file as the query.
     */
    fun searchByAudio(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Extracting audio features..."
            
            val results = withContext(Dispatchers.Default) {
                initModelRunner()
                val runner = modelRunner ?: return@withContext emptyList()
                val decoder = AudioDecoder(context)
                
                // Get just the first chunk for query
                val chunks = decoder.extractChunks(uri)
                if (chunks.isEmpty()) {
                    return@withContext emptyList()
                }
                
                // Average the chunks if there are multiple, or just use the first one
                // To keep it simple and fast for querying, we'll use the mean embedding of the query song
                val chunkEmbeddings = chunks.map { chunk ->
                    val melSpec = melExtractor.extract(chunk.floatArray)
                    runner.getAudioEmbedding(melSpec)
                }
                
                val meanEmb = FloatArray(512)
                for (emb in chunkEmbeddings) {
                    for (i in 0 until 512) {
                        meanEmb[i] += emb[i]
                    }
                }
                var norm = 0f
                for (i in 0 until 512) {
                    meanEmb[i] /= chunkEmbeddings.size.toFloat()
                    norm += meanEmb[i] * meanEmb[i]
                }
                norm = kotlin.math.sqrt(norm)
                if (norm > 0f) {
                    for (i in 0 until 512) {
                        meanEmb[i] /= norm
                    }
                }
                
                _statusMessage.value = "Searching for similar audio..."
                repository.searchHybrid(meanEmb, topN = 10)
            }
            
            _searchResults.value = results
            _statusMessage.value = "Found ${results.size} matches."
            _isProcessing.value = false
        }
    }
    
    fun clearDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
            _statusMessage.value = "Database cleared."
            _searchResults.value = emptyList()
        }
    }
}
