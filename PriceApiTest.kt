package com.travels.searchtravels

import android.os.Looper.getMainLooper
import android.widget.TextView
import androidx.core.widget.doOnTextChanged
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.travels.searchtravels.activity.ChipActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


@RunWith(AndroidJUnit4::class)
class PriceApiTest {
    companion object {
        private const val PRICE_CHANGE_TIMEOUT_MILLIS = 12000L // 12 seconds
    }

    private lateinit var scenario: ActivityScenario<ChipActivity>
    private lateinit var eventLatch: CountDownLatch
    private lateinit var priceChangeHistory: MutableList<Int>

    @Before
    fun startUp() {
        scenario = launch(ChipActivity::class.java)
        eventLatch = CountDownLatch(1)
        priceChangeHistory = ArrayList()
    }

    @Test
    fun positiveTestPriceTextChange() {
        val cityName = "Римини"

        scenario.onActivity { activity ->
            val testView = activity.findViewById<TextView>(R.id.airticketTV)

            activity.getInfoNomad(cityName)

            // testView.text example value: "от $finalTicketPrice1\u20BD"
            testView.doOnTextChanged { text, _, _, _ -> onPriceTextChanged(text.toString()) }
        }

        eventLatch.await(PRICE_CHANGE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        shadowOf(getMainLooper()).idle()

        assertEquals("All events must be received", 0, eventLatch.count)
        assertEquals("Listener must be called once", 1, priceChangeHistory.size)

        val price = priceChangeHistory[0]
        assertTrue("Price must be positive", price > 0)
    }

    private fun onPriceTextChanged(text: String) {
        val price = extractPriceFromText(text)
        registerPriceChange(price)
    }

    private fun extractPriceFromText(priceText: String): Int {
        return priceText
            .replace(Regex("от\\s+"), "")
            .replace(Regex("\\u20BD"), "")
            .toInt()
    }

    private fun registerPriceChange(price: Int) {
        priceChangeHistory.add(price)
        eventLatch.countDown()
    }
}