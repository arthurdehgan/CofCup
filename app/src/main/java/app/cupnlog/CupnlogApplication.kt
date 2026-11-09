package app.cupnlog

import android.app.Application
import app.cupnlog.data.CuppingRepository

class CupnlogApplication : Application() {

    val repository: CuppingRepository by lazy { CuppingRepository(this) }
}
