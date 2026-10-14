package de.timitrain.data

import android.app.Application
import de.timitrain.data.db.TimiTrainDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppearanceSettingsTest {
    @Test
    fun appearanceDefaultsToLightAndStaysWithTheRestSetting() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = TimiTrainDatabase.createInMemory(context)
        val repository = TimiTrainRepository(db)
        assertFalse(repository.isDarkMode())
        repository.setDarkMode(true)
        repository.setRestSeconds(45)
        assertTrue(repository.isDarkMode())
        assertEquals(45, repository.restSeconds())
        repository.setDarkMode(false)
        assertFalse(repository.isDarkMode())
        assertEquals(45, repository.restSeconds())
        db.close()
    }
}
