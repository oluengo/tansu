package io.github.oluengo.tansu.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayInfoTest {
    @Test fun fichaCompleta() {
        val i = RelayInfo.parse(
            """{"name":"Pago","description":"Solo pagando","pubkey":"${"a".repeat(64)}","contact":"mailto:x@y.z",
            "supported_nips":[1,9,11,42],"software":"strfry","version":"1.0","terms_of_service":"https://x/tos",
            "limitation":{"max_message_length":524288,"max_content_length":262144,"min_pow_difficulty":0,
              "auth_required":true,"payment_required":true,"restricted_writes":true,"created_at_lower_limit":31536000},
            "fees":{"admission":[{"amount":21000000,"unit":"msats"}],"subscription":[{"amount":5000000,"unit":"msats","period":2592000}]},
            "payments_url":"https://x/pay","icon":"http://inseguro/icon.png"}""",
        )!!
        assertEquals("Pago", i.name)
        assertTrue(i.paid)
        assertEquals(true, i.supportsDeletion)
        assertEquals(262144, i.maxContentLength)
        assertEquals(2, i.fees.size)
        assertEquals(2592000L, i.fees.first { it.kind == "subscription" }.period)
        assertNull(i.icon) // solo https
        assertEquals("https://x/pay", i.paymentsUrl)
    }

    @Test fun fichaVaciaOMala() {
        val i = RelayInfo.parse("{}")!!
        assertNull(i.supportsDeletion)
        assertFalse(i.paid)
        assertNull(RelayInfo.parse("no es json"))
        assertEquals("https://nos.lol", RelayInfo.httpUrl("wss://nos.lol"))
    }
}
