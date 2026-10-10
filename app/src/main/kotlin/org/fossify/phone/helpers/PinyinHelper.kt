package org.fossify.phone.helpers

import net.sourceforge.pinyin4j.PinyinHelper as Pinyin4J
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination

object PinyinHelper {
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    private fun charToDigit(c: Char): Char {
        return when (c.lowercaseChar()) {
            in 'a'..'c' -> '2'
            in 'd'..'f' -> '3'
            in 'g'..'i' -> '4'
            in 'j'..'l' -> '5'
            in 'm'..'o' -> '6'
            in 'p'..'s' -> '7'
            in 't'..'v' -> '8'
            in 'w'..'z' -> '9'
            else -> c
        }
    }

    private fun isChinese(c: Char): Boolean {
        return Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN
    }

    fun getPinyin(name: String): String {
        val result = StringBuilder()
        for (char in name) {
            if (isChinese(char)) {
                try {
                    val pinyins = Pinyin4J.toHanyuPinyinStringArray(char, format)
                    if (!pinyins.isNullOrEmpty()) {
                        if (result.isNotEmpty()) result.append(' ')
                        result.append(pinyins[0])
                    }
                } catch (_: BadHanyuPinyinOutputFormatCombination) {
                }
            } else if (char.isLetter()) {
                result.append(char.lowercaseChar())
            }
        }
        return result.toString().trim()
    }

    fun getPinyinDigits(name: String): String {
        val pinyin = getPinyin(name)
        return pinyin.map(::charToDigit)
            .filter { it.isDigit() }
            .joinToString("")
    }

    fun getPinyinInitials(name: String): String {
        val pinyin = getPinyin(name)
        return pinyin.split(" ")
            .mapNotNull { it.firstOrNull() }
            .joinToString("")
    }

    fun getPinyinInitialsDigits(name: String): String {
        return getPinyinInitials(name)
            .map(::charToDigit)
            .joinToString("")
    }

    fun hasChinese(name: String): Boolean {
        return name.any { isChinese(it) }
    }
}
