package com.dustin.callguard

import com.dustin.callguard.screening.CarrierKind
import com.dustin.callguard.screening.CarrierKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarrierKindsTest {
    @Test
    fun recognizesWirelessNames() {
        assertEquals(CarrierKind.WIRELESS, CarrierKinds.classify("CELLCO PARTNERSHIP DBA VERIZON WIRELESS"))
        assertEquals(CarrierKind.WIRELESS, CarrierKinds.classify("T-MOBILE USA, INC."))
        assertEquals(CarrierKind.WIRELESS, CarrierKinds.classify("SOME CARRIER PCS, LLC"))
    }

    @Test
    fun recognizesInternetPhoneNames() {
        assertEquals(CarrierKind.INTERNET_PHONE, CarrierKinds.classify("ONVOY, LLC- NY"))
        assertEquals(CarrierKind.INTERNET_PHONE, CarrierKinds.classify("BANDWIDTH.COM CLEC, LLC"))
        assertEquals(CarrierKind.INTERNET_PHONE, CarrierKinds.classify("TWC IP ENABLED SERVICES, LLC"))
    }

    @Test
    fun everythingElseIsOtherAndBlankIsUnknown() {
        assertEquals(CarrierKind.OTHER, CarrierKinds.classify("VERIZON NEW YORK, INC."))
        assertEquals(CarrierKind.OTHER, CarrierKinds.classify("SPECSAVERS TELECOM"))
        assertNull(CarrierKinds.classify(""))
        assertNull(CarrierKinds.classify(null))
    }
}
