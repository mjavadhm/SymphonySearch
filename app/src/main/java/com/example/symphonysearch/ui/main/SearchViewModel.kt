package com.example.symphonysearch.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.symphonysearch.SymphonySearchApp
import com.example.symphonysearch.data.SemanticSearchRepository
import com.example.symphonysearch.data.TrackEmbedding
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
    
    // Lazy initialization so it doesn't crash if models aren't imported yet
    private var modelRunner: ClapModelRunner? = null

    private val _searchResults = MutableStateFlow<List<TrackEmbedding>>(emptyList())
    val searchResults: StateFlow<List<TrackEmbedding>> = _searchResults

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
            modelRunner = ClapModelRunner(getApplication())
        }
    }

    /**
     * شبیه‌سازی پردازش و اضافه‌کردن آهنگ به دیتابیس.
     */
    fun indexDummyAudio(title: String, dummyAudioData: FloatArray) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Indexing $title..."
            
            withContext(Dispatchers.Default) {
                initModelRunner()
                val runner = modelRunner ?: return@withContext
                // ۱. استخراج Mel-Spectrogram (ابعاد ۱۰۰۱ فریم)
                val melSpec = melExtractor.extract(dummyAudioData)
                
                // ۲. استخراج Embedding با مدل ONNX Audio
                val embedding = runner.getAudioEmbedding(melSpec)
                
                // ۳. ذخیره در ObjectBox
                repository.insertTrack("fake_path/$title.mp3", title, embedding)
            }
            
            _statusMessage.value = "Added $title. Total tracks: ${repository.getAllTracksCount()}"
            _isProcessing.value = false
        }
    }

    /**
     * جستجوی معنایی بر اساس متن ورودی
     */
    fun searchByText(query: String) {
        if (query.isBlank()) return
        
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Searching for '$query'..."
            
            val results = withContext(Dispatchers.Default) {
                initModelRunner()
                val runner = modelRunner ?: return@withContext emptyList()
                // ۱. Tokenize کردن متن
                val (inputIds, attentionMask) = tokenizer.encode(query)
                
                // ۲. استخراج Embedding متن از روی مدل ONNX Text
                val queryEmbedding = runner.getTextEmbedding(inputIds, attentionMask)
                
                // ۳. پیدا کردن نزدیک‌ترین آهنگ‌ها با ObjectBox (HNSW)
                repository.searchNearestTracks(queryEmbedding, maxResults = 10)
            }
            
            _searchResults.value = results
            _statusMessage.value = "Found ${results.size} matches."
            _isProcessing.value = false
        }
    }
}
