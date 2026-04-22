package com.vayunmathur.photos.util

import com.vayunmathur.library.util.BaseBackupAgent
import com.vayunmathur.library.util.BiometricDatabaseHelper
import java.io.File

class AppBackupAgent : BaseBackupAgent() {
    override val dbConfigs: List<Pair<String, String>>
        get() {
            val pass = BiometricDatabaseHelper(this).getPassphrase(false)
            return listOf("vault-db" to pass)
        }

    override val extraFiles: List<File>
        get() = listOf(File(filesDir, "secure_vault"))
}
