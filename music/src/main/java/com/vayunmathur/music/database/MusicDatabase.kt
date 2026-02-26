package com.vayunmathur.music.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.RoomDatabase
import com.vayunmathur.library.util.TrueDao

@Dao
interface MusicDao: TrueDao<Music>
@Dao
interface AlbumDao: TrueDao<Album>
@Dao
interface ArtistDao: TrueDao<Artist>

@Database(entities = [Music::class, Album::class, Artist::class], version = 1)
abstract class MusicDatabase: RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
}