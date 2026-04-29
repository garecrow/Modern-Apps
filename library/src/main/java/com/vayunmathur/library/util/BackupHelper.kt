package com.vayunmathur.library.util

import android.content.Context
import android.net.Uri
import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupHelper {
    private const val TAG = "BackupHelper"

    fun exportDatabase(context: Context, dbName: String, password: String, outputFile: File) {
        loadSqlCipher()
        val dbFile = context.getDatabasePath(dbName)
        Log.d(TAG, "exportDatabase: dbName=$dbName, path=${dbFile.absolutePath}, exists=${dbFile.exists()}")
        if (!dbFile.exists()) {
            Log.w(TAG, "exportDatabase: Database file does not exist!")
            return
        }

        try {
            // Ensure parent directory exists and use canonical path to avoid symlink issues
            val parent = outputFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
            outputFile.createNewFile()
            
            val outputPath = outputFile.canonicalPath
            val sourcePath = dbFile.canonicalPath

            Log.d(TAG, "exportDatabase: Opening source database: $sourcePath")
            // Use SQLCipher to export to an unencrypted database
            val db = SQLiteDatabase.openDatabase(
                sourcePath,
                password,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null
            )
            Log.d(TAG, "exportDatabase: Source opened. Attaching plaintext destination: $outputPath")
            db.rawExecSQL("PRAGMA cipher_compatibility = 4")
            db.rawExecSQL("ATTACH DATABASE '$outputPath' AS plaintext KEY ''")
            db.rawExecSQL("SELECT sqlcipher_export('plaintext')")
            db.rawExecSQL("DETACH DATABASE plaintext")
            db.close()
            Log.d(TAG, "exportDatabase: Export successful. Output size: ${outputFile.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "exportDatabase: Error during export", e)
        }
    }

    fun importDatabase(context: Context, dbName: String, password: String, inputFile: File) {
        loadSqlCipher()
        val dbFile = context.getDatabasePath(dbName)
        Log.d(TAG, "importDatabase: From ${inputFile.absolutePath} to ${dbFile.absolutePath}")
        
        try {
            val inputPath = inputFile.canonicalPath
            val outputPath = dbFile.canonicalPath

            // Delete existing database files
            dbFile.delete()
            File("$outputPath-wal").delete()
            File("$outputPath-shm").delete()
            File("$outputPath-journal").delete()

            // Create a new encrypted database from the plaintext input
            val db = SQLiteDatabase.openDatabase(
                outputPath,
                password,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                null
            )
            db.rawExecSQL("PRAGMA cipher_compatibility = 4")
            db.rawExecSQL("ATTACH DATABASE '$inputPath' AS plaintext KEY ''")
            db.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext')")
            db.rawExecSQL("DETACH DATABASE plaintext")
            db.close()
            Log.d(TAG, "importDatabase: Success")
        } catch (e: Exception) {
            Log.e(TAG, "importDatabase: Error", e)
        }
    }

    fun zipFiles(files: List<File>, baseDir: File, outputStream: OutputStream) {
        Log.d(TAG, "zipFiles: Starting. files count=${files.size}, baseDir=${baseDir.absolutePath}")
        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            files.forEach { file ->
                if (file.isDirectory) {
                    Log.d(TAG, "zipFiles: Zipping directory ${file.name}")
                    zipDirectory(file, baseDir, zos)
                } else if (file.exists()) {
                    val entryName = file.relativeTo(baseDir).path
                    Log.d(TAG, "zipFiles: Adding file entry: $entryName (${file.length()} bytes)")
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                } else {
                    Log.w(TAG, "zipFiles: File/dir does not exist: ${file.absolutePath}")
                }
            }
        }
        Log.d(TAG, "zipFiles: Finished")
    }

    private fun zipDirectory(dir: File, baseDir: File, zos: ZipOutputStream) {
        val contents = dir.listFiles()
        Log.d(TAG, "zipDirectory: dir=${dir.name}, contents count=${contents?.size ?: 0}")
        contents?.forEach { file ->
            if (file.isDirectory) {
                zipDirectory(file, baseDir, zos)
            } else {
                val entryName = file.relativeTo(baseDir).path
                Log.d(TAG, "zipDirectory: Adding file entry: $entryName (${file.length()} bytes)")
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    fun unzipFiles(inputStream: InputStream, targetDir: File) {
        ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun performFullBackup(
        context: Context,
        dbConfigs: List<Pair<String, String>>, // dbName to password
        datastoreNames: List<String> = emptyList(),
        prefNames: List<String> = emptyList(),
        extraFiles: List<File>,
        outputStream: OutputStream
    ) {
        val tempDir = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        Log.d(TAG, "performFullBackup: Started. dbConfigs count=${dbConfigs.size}, datastore count=${datastoreNames.size}, prefs count=${prefNames.size}, extraFiles count=${extraFiles.size}")
        Log.d(TAG, "performFullBackup: Temp dir: ${tempDir.absolutePath}")

        val filesToZip = mutableListOf<File>()

        dbConfigs.forEach { (dbName, password) ->
            val plainDbFile = File(tempDir, "$dbName.db")
            Log.d(TAG, "performFullBackup: Exporting $dbName")
            exportDatabase(context, dbName, password, plainDbFile)
            if (plainDbFile.exists() && plainDbFile.length() > 0) {
                filesToZip.add(plainDbFile)
                Log.d(TAG, "performFullBackup: Added $dbName.db to filesToZip. Size: ${plainDbFile.length()}")
            } else {
                Log.w(TAG, "performFullBackup: Database export failed or file is empty: $dbName")
            }
        }

        datastoreNames.forEach { dsName ->
            val dsFile = File(context.filesDir, "datastore/$dsName.preferences_pb")
            val dsFileAlt = File(context.filesDir, "$dsName.preferences_pb")
            val actualFile = if (dsFile.exists()) dsFile else if (dsFileAlt.exists()) dsFileAlt else null
            if (actualFile != null) {
                val targetFile = File(tempDir, actualFile.name)
                actualFile.copyTo(targetFile, true)
                filesToZip.add(targetFile)
                Log.d(TAG, "performFullBackup: Added ${actualFile.name} to backup")
            }
        }

        prefNames.forEach { prefName ->
            val prefFile = File(context.dataDir, "shared_prefs/$prefName.xml")
            if (prefFile.exists()) {
                val targetFile = File(tempDir, prefFile.name)
                prefFile.copyTo(targetFile, true)
                filesToZip.add(targetFile)
                Log.d(TAG, "performFullBackup: Added ${prefFile.name} to backup")
            }
        }

        extraFiles.forEach { file ->
            if (file.exists()) {
                val targetFile = File(tempDir, file.name)
                Log.d(TAG, "performFullBackup: Copying extra file/dir: ${file.absolutePath}")
                if (file.isDirectory) {
                    file.copyRecursively(targetFile, true)
                } else {
                    file.copyTo(targetFile, true)
                }
                filesToZip.add(targetFile)
            } else {
                Log.w(TAG, "performFullBackup: Extra file does not exist: ${file.absolutePath}")
            }
        }

        if (filesToZip.isEmpty()) {
            Log.e(TAG, "performFullBackup: NO FILES TO BACKUP!")
        } else {
            Log.d(TAG, "performFullBackup: Zipping ${filesToZip.size} items")
        }

        zipFiles(filesToZip, tempDir, outputStream)
        tempDir.deleteRecursively()
        Log.d(TAG, "performFullBackup: Finished")
    }

    fun performFullRestore(
        context: Context,
        dbConfigs: List<Pair<String, String>>,
        datastoreNames: List<String> = emptyList(),
        prefNames: List<String> = emptyList(),
        extraFilesMapping: Map<String, File>, // filename in zip to target File
        inputStream: InputStream
    ) {
        val tempDir = File(context.cacheDir, "restore_temp")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        unzipFiles(inputStream, tempDir)

        dbConfigs.forEach { (dbName, password) ->
            val plainDbFile = File(tempDir, "$dbName.db")
            if (plainDbFile.exists()) {
                importDatabase(context, dbName, password, plainDbFile)
            }
        }

        datastoreNames.forEach { dsName ->
            val dsFile = File(tempDir, "$dsName.preferences_pb")
            if (dsFile.exists()) {
                val targetFile1 = File(context.filesDir, "datastore/$dsName.preferences_pb")
                val targetFile2 = File(context.filesDir, "$dsName.preferences_pb")
                val targetFile = if (dsName == "datastore_default") targetFile2 else targetFile1
                
                targetFile.parentFile?.mkdirs()
                dsFile.copyTo(targetFile, true)
            }
        }

        prefNames.forEach { prefName ->
            val prefFile = File(tempDir, "$prefName.xml")
            if (prefFile.exists()) {
                val targetFile = File(context.dataDir, "shared_prefs/$prefName.xml")
                targetFile.parentFile?.mkdirs()
                prefFile.copyTo(targetFile, true)
            }
        }

        extraFilesMapping.forEach { (zipName, targetFile) ->
            val extractedFile = File(tempDir, zipName)
            if (extractedFile.exists()) {
                if (targetFile.exists()) targetFile.deleteRecursively()
                if (extractedFile.isDirectory) {
                    extractedFile.copyRecursively(targetFile, true)
                } else {
                    targetFile.parentFile?.mkdirs()
                    extractedFile.copyTo(targetFile, true)
                }
            }
        }

        tempDir.deleteRecursively()
    }
}
