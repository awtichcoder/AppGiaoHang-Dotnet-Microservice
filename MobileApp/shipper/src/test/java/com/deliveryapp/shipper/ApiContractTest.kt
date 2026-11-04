package com.deliveryapp.shipper

import com.deliveryapp.shipper.data.api.*
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.core.network.apiCall
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ApiContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DeliveryApi
    @Before fun before() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(DeliveryApi::class.java)
    }
    @After fun after() { server.shutdown() }
    @Test fun acceptIsReceiptAndRejectHasReasonCode() = runTest {
        server.enqueue(MockResponse().setBody("""{"offerId":"o","deliveryId":"d","status":"ASSIGNED"}"""))
        assertEquals("d", api.accept("o").deliveryId)
        assertEquals("/api/delivery/offers/o/accept", server.takeRequest().path)
        server.enqueue(MockResponse().setBody("""{"offerId":"o","offerStatus":"REJECTED","reasonCode":"DRIVER_DECLINED"}"""))
        api.reject("o", RejectRequest())
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/delivery/offers/o/reject", request.path)
        assertTrue(request.body.readUtf8().contains("DRIVER_DECLINED"))
    }
    @Test fun contractUsesCodeNamesRatherThanSheetAliases() = runTest {
        server.enqueue(MockResponse().setBody("""{"page":1,"pageSize":20,"totalItems":1,"items":[{"offerId":"o","deliveryId":"d","orderId":"r","pickupAddress":"A","dropoffAddress":"B","distanceToPickupKm":1.2,"incomeEstimate":38000,"status":"OFFERED","sentAt":"2026-10-30T08:00:00Z","expiresAt":"2026-10-30T08:00:30Z"}]}"""))
        val offer = api.offers().items.single().toDomain()
        assertEquals(38000L, offer.incomeEstimate)
        assertEquals(OfferStatus.OFFERED, offer.status)
    }
    @Test fun serverConflictIsPreserved() = runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{"code":"OFFER_NOT_AVAILABLE","message":"Offer unavailable","details":null}"""))
        try { apiCall { api.accept("o") }; fail() }
        catch (e: RepositoryException) { assertEquals("OFFER_NOT_AVAILABLE", e.code) }
    }
}
