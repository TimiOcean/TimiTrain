package de.timitrain

import android.app.Application
import de.timitrain.data.TimiTrainRepository
import de.timitrain.data.db.TimiTrainDatabase
import de.timitrain.domain.SystemAppClock
import de.timitrain.timer.RestScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class TimiTrainApp : Application() {
    lateinit var repository: TimiTrainRepository
        private set
    lateinit var restScheduler: RestScheduler
        private set
    val clock = SystemAppClock()

    override fun onCreate() {
        super.onCreate()
        val database = TimiTrainDatabase.create(this)
        repository = TimiTrainRepository(database)
        restScheduler = RestScheduler(this)
        runBlocking(Dispatchers.IO) { repository.seedStarterPlanIfEmpty() }
    }
}
