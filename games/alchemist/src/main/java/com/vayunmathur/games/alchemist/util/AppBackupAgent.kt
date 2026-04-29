package com.vayunmathur.games.alchemist.util

import com.vayunmathur.library.util.BaseBackupAgent

class AppBackupAgent : BaseBackupAgent() {
    override val datastoreNames: List<String>
        get() = listOf("datastore_default")
}
