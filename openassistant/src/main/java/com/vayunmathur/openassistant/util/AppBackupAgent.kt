package com.vayunmathur.openassistant.util

import com.vayunmathur.library.util.BaseBackupAgent
import com.vayunmathur.library.util.BiometricDatabaseHelper
import java.io.File

class AppBackupAgent : BaseBackupAgent() {
    override val dbConfigs: List<Pair<String, String>>
        get() {
            val helper = BiometricDatabaseHelper(this)
            return if (helper.isKeyGenerated(false)) {
                try {
                    val pass = helper.getPassphrase(false)
                    listOf("passwords-db" to pass)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }

    override val extraFiles: List<File>
        get() = emptyList()
}
