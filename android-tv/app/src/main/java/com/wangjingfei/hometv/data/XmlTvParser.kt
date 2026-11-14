package com.wangjingfei.hometv.data

import java.io.StringReader
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

class XmlTvParser @Inject constructor() {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    fun parse(content: String, from: Long, to: Long): List<Programme> {
        val result = mutableListOf<Programme>()
        val handler = object : DefaultHandler() {
            var channel = ""
            var start: Long? = null
            var stop: Long? = null
            var title = "未命名节目"
            var description = ""
            var textTarget: String? = null
            val text = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                when (qName) {
                    "programme" -> {
                        channel = attributes.getValue("channel").orEmpty()
                        start = parseTime(attributes.getValue("start"))
                        stop = parseTime(attributes.getValue("stop"))
                        title = "未命名节目"
                        description = ""
                    }
                    "title", "desc" -> {
                        textTarget = qName
                        text.clear()
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (textTarget != null) text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "title" -> title = text.toString().trim().ifBlank { title }
                    "desc" -> description = text.toString().trim()
                    "programme" -> {
                        val begin = start
                        val end = stop
                        if (channel.isNotBlank() && begin != null && end != null && end >= from && begin <= to) {
                            result += Programme(channel, begin, end, title, description)
                        }
                    }
                }
                if (qName == textTarget) textTarget = null
            }
        }
        SAXParserFactory.newInstance().newSAXParser().parse(InputSource(StringReader(content)), handler)
        return result
    }

    private fun parseTime(value: String?): Long? = runCatching {
        val normalized = value.orEmpty().trim().replace(Regex("\\s+"), " ")
        OffsetDateTime.parse(normalized, formatter).toInstant().toEpochMilli()
    }.getOrNull()
}
