package cn.sffzh.tempus.navidrome.model

import android.os.Parcelable
import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.Date

@Keep
@Parcelize
data class MusicFile(
    val id: String,
    @PrimaryKey
    @ColumnInfo(name = "media_file_id")
    val mediaFileId: String,
    val playlistId: String?,
    val playCount: Int,
    val playDate: Date?,
    val bookmarkPosition: Int,
    val libraryId: Int,
    val libraryPath: String,
    val libraryName: String,
    val folderId: String,
    val path: String,
    val title: String,
    val album: String?,
    val artistId: String?,
    val artist: String?,
    val albumArtistId: String?,
    val albumArtist: String?,
    val albumId: String?,
    val hasCoverArt: Boolean,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val date: String?,
    val originalYear: Int?,
    val originalDate: String?,
    val releaseYear: Int,
    val size: Long,
    val suffix: String,
    val duration: Double,
    val bitRate: Int,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val genre: String?,
    val genres: List<Genre>?,
    val orderTitle: String,
    val orderAlbumName: String,
    val orderArtistName: String,
    val orderAlbumArtistName: String,
    val compilation: Boolean,
    val lyrics: String?,
    val explicitStatus: String,
    val rgAlbumGain: Float?,
    val rgAlbumPeak: Float?,
    val rgTrackGain: Float?,
    val rgTrackPeak: Float?,
    val tags: Map<String, List<String>>,
    val participants: Participants?,
    val missing: Boolean,
    val birthTime: Date,
    val createdAt: Date,
    val updatedAt: Date
): Parcelable

@Parcelize
@Keep
data class Genre(
    val id: String,
    val name: String
): Parcelable

@Parcelize
data class Participants(
    val albumartist: List<Artist>?,
    val artist: List<Artist>?,
    val lyricist: List<Artist>?
): Parcelable

@Parcelize
data class Artist(
    val id: String,
    val name: String,
    val missing: Boolean
): Parcelable
