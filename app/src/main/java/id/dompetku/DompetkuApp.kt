package id.dompetku

import android.app.Application

class DompetkuApp : Application() {
    /** Failure means the encrypted database could not be opened (e.g. key lost after moving phones). */
    var container: Result<AppContainer> = Result.failure(IllegalStateException("not opened"))
        private set

    fun openContainer(): Result<AppContainer> {
        if (container.isFailure) container = AppContainer.open(this)
        return container
    }

    /** Wipes unreadable storage and opens a fresh, empty database. */
    fun resetAndOpen(): Result<AppContainer> {
        AppContainer.resetStorage(this)
        container = AppContainer.open(this)
        return container
    }
}
