package com.travels.searchtravels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.api.services.vision.v1.model.LatLng
import com.travels.searchtravels.VisionApiTest.TestListener.EventType
import com.travels.searchtravels.activity.MainActivity
import com.travels.searchtravels.api.OnVisionApiListener
import com.travels.searchtravels.api.VisionApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
class VisionApiTest {
    companion object {
        private const val LOOP_MESSAGE_POST_TIMEOUT_MILLIS = 12000L // 12 seconds

        private const val VALID_GOOGLE_TOKEN = "access_token"

        private const val BEACH_IMAGE_URL = "https://littletravel.ru/wp-content/uploads/2019/06/original21195702.jpg"

        private const val SEA_IMAGE_URL = "https://holi-day.ru/media/img/fdgdfgf.jpg"

        private const val OCEAN_IMAGE_URL = "https://wallpaperscave.ru/images/original/18/01-09/earth-ocean-7437.jpg"

        private const val MOUNTAINS_IMAGE_URL = "https://www.sunhome.ru/i/wallpapers/145/gori-v6.orig.jpg"

        private const val SNOW_IMAGE_URL = "https://img1.goodfon.ru/original/2560x1440/1/52/sneg-zima-dom-peyzazh.jpg"

        private const val OTHER_IMAGE_URL = "https://konspekta.net/studopediaru/baza25/12429784907429.files/image024.jpg"
    }

    private lateinit var scenario: ActivityScenario<*>

    @Before
    fun startUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun positiveTestErrorSeaPlace() {
        executeErrorPlaceTest(SEA_IMAGE_URL, "sea")
    }

    @Test
    fun positiveTestErrorOceanPlace() {
        executeErrorPlaceTest(OCEAN_IMAGE_URL, "ocean")
    }

    @Test
    fun positiveTestErrorBeachPlace() {
        executeErrorPlaceTest(BEACH_IMAGE_URL, "beach")
    }

    @Test
    fun positiveTestErrorMountainsPlace() {
        executeErrorPlaceTest(MOUNTAINS_IMAGE_URL, "mountain")
    }

    @Test
    fun positiveTestErrorSnowPlace() {
        executeErrorPlaceTest(SNOW_IMAGE_URL, "snow")
    }

    @Test
    fun positiveTestErrorOtherPlace() {
        executeErrorTest(OTHER_IMAGE_URL, VALID_GOOGLE_TOKEN)
    }

    @Test
    fun negativeTestNullTokenResponse() {
        executeErrorTest(BEACH_IMAGE_URL, null)
    }

    @Test
    fun negativeTestIncorrectTokenResponse() {
        executeErrorTest(BEACH_IMAGE_URL, "some incorrect token")
    }

    @Test
    fun negativeTestNullImageResponse() {
        executeErrorTest(null, VALID_GOOGLE_TOKEN, expectedEventsCount = 0)
    }

    private fun executeSuccessTest(imageUrl: String, expectedLandmarkCoordinates: LatLng) {
        val image = downloadImage(imageUrl)
        val token = VALID_GOOGLE_TOKEN
        val listener = TestListener()

        performServiceCall(listener) { VisionApi.findLocation(image, token, listener) }

        assertEquals("Listener must be called once", 1, listener.listenerEvents.size)

        val (eventType, result) = listener.listenerEvents[0]
        assertEquals("Event 'SUCCESS' must be triggered", EventType.SUCCESS, eventType)
        assertEquals("Landmark coordinates must equal", expectedLandmarkCoordinates, result)
    }

    private fun executeErrorPlaceTest(imageUrl: String, expectedCategory: String?) {
        val image = downloadImage(imageUrl)
        val token = VALID_GOOGLE_TOKEN
        val listener = TestListener()

        performServiceCall(listener) { VisionApi.findLocation(image, token, listener) }

        assertEquals("Listener must be called once", 1, listener.listenerEvents.size)

        val (eventType, result) = listener.listenerEvents[0]
        assertEquals("Event 'ERROR_PLACE' must be triggered", EventType.ERROR_PLACE, eventType)
        assertEquals("Category '${expectedCategory}' must be resolved", expectedCategory, result)
    }

    private fun executeErrorTest(imageUrl: String?, token: String?, expectedEventsCount: Int = 1) {
        val image = imageUrl?.let { downloadImage(imageUrl) }
        val listener = TestListener()

        performServiceCall(listener) { VisionApi.findLocation(image, token, listener) }

        assertEquals("Listener must be called", expectedEventsCount, listener.listenerEvents.size)

        if(expectedEventsCount > 0) {
            listener.listenerEvents.forEach { event ->
                val (eventType, result) = event
                assertEquals("Error event must be triggered", EventType.ERROR, eventType)
                assertNull(result)
            }
        }
    }

    private fun performServiceCall(
        listener: TestListener,
        serviceCall: () -> Unit
    ) {
        scenario.onActivity { serviceCall() }

        listener.eventLatch.await(LOOP_MESSAGE_POST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("All events must be received", 0, listener.eventLatch.count)
    }

    private fun downloadImage(urlString: String): Bitmap {
        val url = URL(urlString)

        val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()

        val input = connection.inputStream
        return BitmapFactory.decodeStream(input)
    }

    private class TestListener(expectedEvents: Int = 1) : OnVisionApiListener {
        val eventLatch = CountDownLatch(expectedEvents)
        val listenerEvents = ArrayList<ListenerEvent<*>>()

        override fun onSuccess(latLng: LatLng?) {
            registerEvent(ListenerEvent(EventType.SUCCESS, latLng))
        }

        override fun onErrorPlace(category: String?) {
            registerEvent(ListenerEvent(EventType.ERROR_PLACE, category))
        }

        override fun onError() {
            registerEvent(ListenerEvent(EventType.ERROR))
        }

        private fun registerEvent(event: ListenerEvent<Any>) {
            listenerEvents.add(event)
            eventLatch.countDown()
        }

        data class ListenerEvent<T>(
            var eventType: EventType,
            var result: T? = null,
            var throwable: Throwable? = null
        )

        enum class EventType { SUCCESS, ERROR_PLACE, ERROR }
    }
}