package com.mindsync.mindmapview.desktop

import kotlinx.coroutines.runBlocking
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlImageLoaderTest {
    @Test
    fun imageIsAskedForAsTheAppItIs() {
        val url = "https://upload.wikimedia.org/wikipedia/commons/a/a9/Example.jpg"
        val connection = assertNotNull(imageConnection(url))
        assertTrue(connection.getRequestProperty("User-Agent").orEmpty().startsWith("MindMapView/"))
    }

    @Test
    fun onlyWebAddressesAreLoaded() {
        assertNull(imageConnection("file:///etc/passwd"))
        assertNull(imageConnection("not an address"))
    }

    @Test
    fun imagesOfANoteAreFound() {
        assertEquals(
            listOf("https://e.org/a.png", "https://e.org/b.png?x=1&y=2"),
            imageSources(
                "<p>a <img src=\"https://e.org/a.png\"> b</p>" +
                    "<p><IMG width=\"10\" src=\"https://e.org/b.png?x=1&amp;y=2\"/></p>"
            ),
        )
        assertEquals(emptyList(), imageSources("<p>no images</p>"))
    }

    @Test
    fun anImageFromAFileIsStoredUnderAnAddressOfItsOwn() = runBlocking {
        val png = Base64.getDecoder().decode(ONE_PIXEL_PNG)

        val url = assertNotNull(UrlImageLoader.addImage(png))

        assertTrue(url.startsWith("ccmm-image:"))
        assertNotEquals(url, UrlImageLoader.addImage(png))
        assertContentEquals(png, UrlImageLoader.bytesOf(listOf(url))[url])
    }

    @Test
    fun aFileThatIsNoImageIsNotAdded() {
        assertNull(UrlImageLoader.addImage("not an image".encodeToByteArray()))
    }

    private companion object {
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
