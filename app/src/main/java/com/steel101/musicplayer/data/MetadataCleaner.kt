package com.steel101.musicplayer.data

object MetadataCleaner {
    fun cleanFilename(filename: String): Pair<String, String> {
        val nameWithoutExtension = filename.substringBeforeLast(".")
        
        val delimiters = listOf(" - ", " \u2013 ", " \u2014 ")
        for (delimiter in delimiters) {
            if (nameWithoutExtension.contains(delimiter)) {
                val parts = nameWithoutExtension.split(delimiter)
                if (parts.size >= 2) {
                    return Pair(cleanString(parts[0]), cleanString(parts[1]))
                }
            }
        }
        
        return Pair("Unknown Artist", cleanString(nameWithoutExtension))
    }

    fun cleanString(input: String): String {
        var output = input
        
        val suffixesToRemove = listOf(
            "(Official Video)", "(Official Audio)", "(Lyric Video)", 
            "(Lyrics)", "[Official Video]", "[Lyrics]", 
            "feat.", "ft.", "featuring", "remastered", "remaster"
        )
        
        for (suffix in suffixesToRemove) {
            val index = output.lowercase().indexOf(suffix)
            if (index != -1) {
                output = output.substring(0, index)
            }
        }

        output = output.replace(Regex("""\s-\s\d+.*$"""), "")
        
        output = output.replace(Regex("""\s*\(.*?\)\s*$"""), "")

        return output.trim()
    }

    fun normalizeForComparison(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }
}
