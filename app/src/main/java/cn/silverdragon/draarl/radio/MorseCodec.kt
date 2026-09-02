package cn.silverdragon.draarl.radio

object MorseCodec {
    fun encode(text: String): String = normalizeText(text)
        .split(' ')
        .joinToString(" / ") { word -> word.mapNotNull(CODES::get).joinToString(" ") }

    fun decode(morse: String): String = morse
        .trim()
        .replace('|', '/')
        .split(Regex("\\s*/\\s*"))
        .joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString("") { code ->
                REVERSE_CODES[code]?.toString() ?: "?"
            }
        }

    fun normalizeText(text: String): String = text
        .trim()
        .uppercase()
        .map { character -> if (character == '\n' || character == '\t') ' ' else character }
        .filter { it == ' ' || CODES.containsKey(it) }
        .joinToString("")
        .replace(Regex(" +"), " ")

    internal fun codeFor(character: Char): String = CODES.getValue(character)

    private val CODES = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..", '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..",
        '9' to "----.", '/' to "-..-.", '?' to "..--..", '.' to ".-.-.-", ',' to "--..--",
        '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '@' to ".--.-."
    )
    private val REVERSE_CODES = CODES.entries.associate { (character, code) -> code to character }
}
