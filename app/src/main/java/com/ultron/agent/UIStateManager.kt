package com.ultron.agent

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream

class UIStateManager(private val shizukuManager: ShizukuManager) {
    private val TAG = "UIStateManager"

    data class UITarget(
        val x1: Int, val y1: Int, val x2: Int, val y2: Int,
        val centerX: Double, val centerY: Double,
        val packageName: String,
        val text: String,
        val contentDesc: String,
        val resourceId: String
    )

    suspend fun dumpUI(): List<UITarget> {
        // RAM-only dump for maximum speed
        val output = shizukuManager.executeCommand("uiautomator dump /dev/stdout")
        Log.d(TAG, "UI dump length: ${output.length}")
        return parseUIOutput(output)
    }

    private fun parseUIOutput(xmlString: String): List<UITarget> {
        val targets = mutableListOf<UITarget>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8)), null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name.equals("node", ignoreCase = true)) {
                    val clickable = parser.getAttributeValue(null, "clickable")?.toBoolean() ?: false
                    val bounds = parser.getAttributeValue(null, "bounds")
                    val packageName = parser.getAttributeValue(null, "package") ?: ""
                    val text = parser.getAttributeValue(null, "text") ?: ""
                    val contentDesc = parser.getAttributeValue(null, "content-desc") ?: ""
                    val resourceId = parser.getAttributeValue(null, "resource-id") ?: ""

                    if (clickable && bounds != null) {
                        val (x1, y1, x2, y2) = parseBounds(bounds)
                        if (x1 != -1) {
                            val centerX = (x1 + x2) / 2.0
                            val centerY = (y1 + y2) / 2.0
                            targets.add(
                                UITarget(
                                    x1, y1, x2, y2,
                                    centerX, centerY,
                                    packageName, text, contentDesc, resourceId
                                )
                            )
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing UI dump", e)
        }
        return targets
    }

    private fun parseBounds(bounds: String): Quadruple {
        val pattern = Regex("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]")
        val match = pattern.find(bounds)
        return if (match != null) {
            val (x1, y1, x2, y2) = match.destructured
            Quadruple(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
        } else {
            Quadruple(-1, -1, -1, -1)
        }
    }

    private data class Quadruple(val x1: Int, val y1: Int, val x2: Int, val y2: Int)
}

