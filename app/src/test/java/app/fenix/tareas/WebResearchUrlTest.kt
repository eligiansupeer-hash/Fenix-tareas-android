package app.fenix.tareas

import app.fenix.tareas.ai.WebResearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebResearchUrlTest {
    @Test
    fun allowsPublicHttpAndHttpsUrls() {
        assertTrue(WebResearch.isPublicWebUrl("https://example.org/article"))
        assertTrue(WebResearch.isPublicWebUrl("http://public.example.org:8080/path?q=1"))
    }

    @Test
    fun rejectsNonWebSchemesAndRelativeUrls() {
        assertFalse(WebResearch.isPublicWebUrl("file:///etc/passwd"))
        assertFalse(WebResearch.isPublicWebUrl("javascript:alert(1)"))
        assertFalse(WebResearch.isPublicWebUrl("/relative/path"))
        assertFalse(WebResearch.isPublicWebUrl("https://"))
    }

    @Test
    fun rejectsLocalAndPrivateIpv4Addresses() {
        listOf(
            "http://localhost/",
            "http://printer.local/",
            "http://127.0.0.1/",
            "http://10.1.2.3/",
            "http://192.168.1.1/",
            "http://172.20.1.1/",
            "http://169.254.169.254/",
            "http://100.64.0.1/",
            "http://192.0.2.10/",
            "http://198.51.100.20/",
            "http://203.0.113.7/",
            "http://127.1/"
        ).forEach { assertFalse("Expected rejection: $it", WebResearch.isPublicWebUrl(it)) }
    }

    @Test
    fun rejectsIpv6LoopbackAndUniqueLocalAddresses() {
        assertFalse(WebResearch.isPublicWebUrl("http://[::1]/"))
        assertFalse(WebResearch.isPublicWebUrl("http://[fc00::1]/"))
        assertFalse(WebResearch.isPublicWebUrl("http://[fe80::1]/"))
        assertFalse(WebResearch.isPublicWebUrl("http://[2001:db8::1]/"))
    }

    @Test
    fun rejectsCredentialsEmbeddedInUrl() {
        assertFalse(WebResearch.isPublicWebUrl("https://user:password@example.org/path"))
    }
}
