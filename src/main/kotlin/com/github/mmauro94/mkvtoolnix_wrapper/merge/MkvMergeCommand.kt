package com.github.mmauro94.mkvtoolnix_wrapper.merge

import com.github.mmauro94.mkvtoolnix_wrapper.*
import com.github.mmauro94.mkvtoolnix_wrapper.MkvToolnixCommandException.MkvMergeException
import java.io.File

class MkvMergeCommand(val outputFile: File) : MkvToolnixCommand<MkvMergeCommand>(MkvToolnixBinary.MKV_MERGE) {

    object GlobalOptions : CommandArgs {

        var verbose = false
        var webm = false
        var title: String? = null
        var defaultLanguage: MkvToolnixLanguage? = null

        fun defaultLanguage(language: String) {
            defaultLanguage = MkvToolnixLanguage.all.getValue(language)
        }

        override fun commandArgs(): List<String> = ArrayList<String>().apply {
            if (verbose) {
                add("--verbose")
            }
            if (webm) {
                add("--webm")
            }
            title?.let {
                add("--title")
                add(it)
            }
        }
    }

    class InputFile(val file: File) : CommandArgs {

        class TracksCommand(val typeCommand: String, val excludeAllCommand: String) : CommandArgs {

            enum class Mode { INCLUDE, EXCLUDE }

            var mode = Mode.EXCLUDE
            val tracks = Tracks()

            sealed class Track {

                abstract fun partialArg(): String

                class TrackId(val id: Long) : Track() {
                    override fun partialArg() = id.toString()
                }

                class TrackLanguage(val language: MkvToolnixLanguage) : Track() {

                    constructor(language: String) : this(MkvToolnixLanguage.all.getValue(language))

                    override fun partialArg() = language.iso639_2
                }

                companion object {
                    fun of(track: MkvToolnixTrack) = TrackId(track.id)
                }
            }

            class Tracks {
                val tracks: MutableList<Track> = ArrayList()

                fun addById(id: Long) = apply { tracks.add(Track.TrackId(id)) }
                fun addByLanguage(language: String) = apply { tracks.add(Track.TrackLanguage(language)) }
                fun addByLanguage(language: MkvToolnixLanguage) = apply { tracks.add(Track.TrackLanguage(language)) }
            }

            fun excludeAll() = apply {
                //Excluding all means including nothing
                tracks.tracks.clear()
                mode = Mode.INCLUDE
            }

            fun includeAll() = apply {
                //Including all means excluding nothing
                tracks.tracks.clear()
                mode = Mode.EXCLUDE
            }

            fun include(f: Tracks.() -> Unit) = apply {
                excludeAll() //Include nothing
                tracks.apply(f)
            }

            fun exclude(f: Tracks.() -> Unit) = apply {
                includeAll() //Exclude nothing
                tracks.apply(f)
            }


            override fun commandArgs(): List<String> = ArrayList<String>().apply {
                if (mode == Mode.INCLUDE && tracks.tracks.isEmpty()) {
                    add(excludeAllCommand)
                } else if (!tracks.tracks.isEmpty()) {
                    add(typeCommand)
                    add(StringBuilder().apply {
                        if (mode == Mode.EXCLUDE) {
                            append('!')
                        }
                        append(tracks.tracks.joinToString(",") { it.partialArg() })
                    }.toString())
                }
            }
        }

        val videoTracks = TracksCommand("--audio-tracks", "--no-audio")
        val audioTracks = TracksCommand("--video-tracks", "--no-video")
        val subtitleTracks = TracksCommand("--subtitle-tracks", "--no-subtitles")
        val buttonTracks = TracksCommand("--button-track", "--no-buttons")
        val trackTags = TracksCommand("--track-tags", "--no-track-tags")


        override fun commandArgs(): List<String> = ArrayList<String>().apply {
            add(videoTracks)
            add(audioTracks)
            add(subtitleTracks)
            add(buttonTracks)
            add(trackTags)
        }

    }

    val inputFiles: MutableList<InputFile> = ArrayList()

    fun addInputFile(file: File, f: InputFile.() -> Unit = {}) = apply {
        inputFiles.add(InputFile(file).apply(f))
    }

    override fun commandArgs(): List<String> = ArrayList<String>().apply {
        add(GlobalOptions)
        add("--output", outputFile.absolutePath.toString())
        inputFiles.forEach { add(it) }
    }

    override fun executeLazy(): MkvToolnixCommandResult.Lazy<MkvMergeCommand> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override val exceptionInitializer = ::MkvMergeException
}