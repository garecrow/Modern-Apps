package com.vayunmathur.music.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vayunmathur.library.util.DatabaseItem
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class Album(
    @PrimaryKey(autoGenerate = true) override val id: Long,
    val name: String,
    val artist: String,
    val artistId: Long,
    val uri: String
): DatabaseItem