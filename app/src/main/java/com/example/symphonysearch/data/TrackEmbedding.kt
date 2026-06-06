package com.example.symphonysearch.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
data class TrackEmbedding(
    @Id var id: Long = 0,
    var filePath: String? = null,
    var title: String? = null,
    
    // 512 is the typical dimension for CLAP text/audio projections
    // Cosine similarity is represented via inner product if embeddings are normalized
    @HnswIndex(dimensions = 512)
    var embedding: FloatArray? = null
)
