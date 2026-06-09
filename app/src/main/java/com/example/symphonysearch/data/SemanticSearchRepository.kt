package com.example.symphonysearch.data

import io.objectbox.Box
import io.objectbox.BoxStore

class SemanticSearchRepository(boxStore: BoxStore) {
    private val trackBox: Box<TrackEmbedding> = boxStore.boxFor(TrackEmbedding::class.java)

    /**
     * ذخیره کردن یک فایل صوتی جدید همراه با Embedding محاسبه شده.
     */
    fun insertTrack(filePath: String, title: String, embedding: FloatArray) {
        val track = TrackEmbedding(
            filePath = filePath,
            title = title,
            embedding = embedding
        )
        trackBox.put(track)
    }

    /**
     * پیدا کردن نزدیک‌ترین آهنگ‌ها به بردارهای استخراج شده (کوئری متن یا آهنگ مشابه)
     * با استفاده از جستجوی HNSW ObjectBox.
     * 
     * @param queryEmbedding بردار ۵۱۲ بعدی متن یا صوت (Normalized)
     * @param maxResults تعداد نتایج برگردانده شده (پیش‌فرض ۱۰)
     */
    fun searchNearestTracks(queryEmbedding: FloatArray, maxResults: Long = 10): List<TrackEmbedding> {
        // از آنجایی که بردارهای ما نرمالایز شده هستند، الگوریتم پیش‌فرض
        // Euclidean در نزدیک‌ترین همسایه‌ها تقریباً معادل Cosine Similarity عمل می‌کند.
        // ObjectBox از HNSW برای اجرای بسیار سریع جستجوی نزدیک‌ترین بردار استفاده می‌کند.
        return trackBox.query(
                TrackEmbedding_.embedding.nearestNeighbors(queryEmbedding, maxResults.toInt())
            )
            .build()
            .find()
    }
    
    fun getAllTracksCount(): Long {
        return trackBox.count()
    }
}
