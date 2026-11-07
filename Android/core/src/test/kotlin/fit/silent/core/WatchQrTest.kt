package fit.silent.core

import fit.silent.core.protocol.WatchQr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchQrTest {
    private val mac = "48:5A:E0:EA:CD:9D"

    @Test fun readsEveryFormNoiseFitAccepts() {
        // Exactly what an NF 2 (firmware 1.0.39) shows on its QR CODE screen.
        assertEquals(mac, WatchQr.address("https://noise-images.s3.ap-south-1.amazonaws.com/qr_code/download_noisefit.html?radio=485AE0EACD9D7CE6270001BD0000485AE0EACD9D&name=NF%202_CD9D&mac=485AE0EACD9D"))
        assertEquals(mac, WatchQr.address("https://www.gonoise.com/app?mac=48:5A:E0:EA:CD:9D&name=NF%202"))
        assertEquals(mac, WatchQr.address("http://x.y/z?MAC=485AE0EACD9D"))
        assertEquals(mac, WatchQr.address("http://x.y/z?m=48-5a-e0-ea-cd-9d"))
        assertEquals(mac, WatchQr.address("http://x.y/z#g=NF2|485AE0EACD9D"))
        assertEquals(mac, WatchQr.address("59004|48:5A:E0:EA:CD:9D|1.0.39"))
        assertEquals(mac, WatchQr.address("type MAC:48:5A:E0:EA:CD:9D"))
        assertEquals(mac, WatchQr.address("NF 2 485ae0eacd9d\u0000"))
    }

    @Test fun rejectsCodesWithoutAnAddress() {
        assertNull(WatchQr.address("https://noise-images.s3.ap-south-1.amazonaws.com/qr_code/download_noisefit.html"))
        assertNull(WatchQr.address(""))
        assertNull(WatchQr.address("?mac=000000000000"))
    }
}
