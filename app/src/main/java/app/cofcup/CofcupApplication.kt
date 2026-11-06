package app.cofcup

import android.app.Application
import app.cofcup.data.CuppingRepository

class CofcupApplication : Application() {

    val repository: CuppingRepository by lazy { CuppingRepository(this) }
}
