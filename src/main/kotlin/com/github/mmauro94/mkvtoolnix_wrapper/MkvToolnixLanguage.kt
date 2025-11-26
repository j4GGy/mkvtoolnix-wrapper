package com.github.mmauro94.mkvtoolnix_wrapper

import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.time.measureTime

/**
 * A language recognized by MKV Toolnix
 *
 * @param name the english name of the language
 * @param iso639_3 the three-letter ISO 639-3 code
 * @param iso639_2 the three-letter ISO 639-2 code. Can be null
 * @param iso639_1 the two-letter ISO 639-1 code. Can be null
 */
class MkvToolnixLanguage internal constructor(
    val name: String,
    val iso639_3: String,
    val iso639_2: String?,
    val iso639_1: String?
) {

    override fun equals(other: Any?) = other is MkvToolnixLanguage && other.iso639_3 == iso639_3

    override fun hashCode() = iso639_3.hashCode()

    override fun toString() = "$name ($iso639_3)"

    fun isUndefined() = iso639_3 == "und"

    fun isEnglish() = iso639_3 == "eng"

    companion object {

        /**
         * Old format of "mkvmerge --list-languages":
         * English language name | ISO 639-3 code | ISO 639-2 code | ISO 639-1 code*
         */
        private val LANGUAGE_LINE_PATTERN_WITH_ISO_639_3 = "^\\s*([^|]+)\\s+\\|\\s*([a-z]{3})\\s*\\|\\s*([a-z]{3})?\\s*\\|\\s*([a-z]{2})?\\s*$".toRegex()
        private val languageLineParserWithIso_639_3: (String) -> MkvToolnixLanguage? = { line ->
            val match = LANGUAGE_LINE_PATTERN_WITH_ISO_639_3.matchEntire(line)
            when {
                match != null -> {
                    val (name, iso3, iso2, iso1) = match.destructured
                    MkvToolnixLanguage(
                        name = name.trim(),
                        iso639_3 = iso3,
                        iso639_2 = iso2.ifBlank { null },
                        iso639_1 = iso1.ifBlank { null }
                    )
                }
                else -> null
            }
        }


        /**
         * Old format of "mkvmerge --list-languages":
         * English language name | ISO 639-2 code | ISO 639-1 code
         */
        private val LANGUAGE_LINE_PATTERN_OLD = "^\\s*([^|]+)\\s+\\|\\s*([a-z]{3})\\s*\\|\\s*([a-z]{2})?\\s*\$".toRegex()
        private val languageLineParserOld: (String) -> MkvToolnixLanguage? = { line ->
            val match = LANGUAGE_LINE_PATTERN_OLD.matchEntire(line)
            when {
                match != null -> {
                    val (name, iso2, iso1) = match.destructured
                    MkvToolnixLanguage(
                        name = name.trim(),
                        iso639_3 = iso2, // Note: library architecture assumes that ios639_3 always exists, so we take iso2
                        iso639_2 = iso2.ifBlank { null },
                        iso639_1 = iso1.ifBlank { null }
                    )
                }
                else -> null
            }
        }


        val english by lazy {
            all.getValue("eng")
        }

        val undefined by lazy {
            all.getValue("und")
        }

        /**
         * Map of all the available languages in `mkvmerge`
         * This value is lazily evaluated the first time it is used.
         */
        val all: Map<String, MkvToolnixLanguage> by lazy {

            val p = MkvToolnixBinary.MKV_MERGE.processBuilder("--list-languages").start()
            BufferedReader(InputStreamReader(p.inputStream)).use { input ->
                val lines = input.lines()

                // check first line for
                val lineParser = when {
                    lines.findFirst().get().contains("ISO 639-3") -> languageLineParserWithIso_639_3
                    else -> languageLineParserOld
                }

                HashMap<String, MkvToolnixLanguage>(350).apply {
                    for (line in input.lines().skip(1)) {
                        lineParser(line)?.let { lang -> put(lang.iso639_3, lang) }
                    }
                }
            }
        }
    }
}

fun MkvToolnixLanguage?.isNullOrUndefined() = this == null || this.isUndefined()