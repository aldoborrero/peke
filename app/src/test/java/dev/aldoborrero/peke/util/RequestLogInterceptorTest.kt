package dev.aldoborrero.peke.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLogInterceptorTest {
    @Test
    fun `ids are shortened in the path`() {
        val url = "http://srv/Users/0123456789abcdef0123456789abcdef/Items/a1b2c3d4-0000-1111-2222-333344445555".toHttpUrl()
        assertEquals("/Users/{012345}/Items/{a1b2c3}", RequestLogInterceptor.shortPath(url))
    }

    @Test
    fun `query shows names, chosen values and never the api key`() {
        val url = "http://srv/Items?parentId=abc&fields=Overview,Genres&limit=20&api_key=SECRET&ApiKey=X".toHttpUrl()
        val query = RequestLogInterceptor.shortQuery(url)
        assertEquals("fields=Overview,Genres,limit=20,parentId", query)
        assertFalse(query.contains("SECRET"))
    }

    @Test
    fun `images are told apart`() {
        assertTrue(RequestLogInterceptor.isImage("http://srv/Items/abc/Images/Primary?maxHeight=300".toHttpUrl()))
        assertFalse(RequestLogInterceptor.isImage("http://srv/Items/abc".toHttpUrl()))
    }
}
