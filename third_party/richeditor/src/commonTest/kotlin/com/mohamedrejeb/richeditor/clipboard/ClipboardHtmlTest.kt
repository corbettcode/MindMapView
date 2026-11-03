package com.mohamedrejeb.richeditor.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardHtmlTest {
    // What a paste of "DemoClients" copied in IntelliJ on Linux was read as.
    private val misread = "㱨瑭氾㱨敡搾㱭整愠桴瑰ⵥ煵楶㴢捯湴敮琭瑹灥∠捯湴敮琽≴數琯桴浬㬠捨慲獥琽啔䘭㠢㸼⽨敡搾" +
        "㱢潤社㱤楶⁳瑹汥㴢扡捫杲潵湤ⵣ潬潲㨣ㅥㅦ㈲㭣潬潲㨣扣扥挴∾㱰牥⁳瑹汥㴢景湴ⵦ慭楬示❄敪慖甠卡湳⁍潮漧Ɑ" +
        "潮潳灡捥㭦潮琭獩穥㨸⸳灴㬢㹄敭潃汩敮瑳㰯灲放㰯摩瘾㰯扯摹㸼⽨瑭氾"

    @Test
    fun htmlReadAsUtf16IsReadAsUtf8() {
        val html = repairClipboardHtml(misread)

        assertEquals("<html><head>", html.take(12))
        assertEquals(true, ">DemoClients</pre>" in html)
    }

    @Test
    fun htmlReadAsLittleEndianUtf16IsReadAsUtf8() {
        val swapped = "<p>Hi</p>".chunked(2).joinToString("") { pair ->
            (pair[0].code or (pair.getOrElse(1) { ' ' }.code shl 8)).toChar().toString()
        }

        assertEquals("<p>Hi</p> ", repairClipboardHtml(swapped))
    }

    @Test
    fun htmlReadRightIsKept() {
        assertEquals("<b>中文</b>", repairClipboardHtml("<b>中文</b>"))
    }

    @Test
    fun textWithNoHtmlInItIsKept() {
        assertEquals("中文", repairClipboardHtml("中文"))
    }
}
