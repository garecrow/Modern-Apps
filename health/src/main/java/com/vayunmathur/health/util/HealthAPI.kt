package com.vayunmathur.health.util
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.feature.ExperimentalPersonalHealthRecordApi
import androidx.health.connect.client.records.FhirResource
import androidx.health.connect.client.records.MedicalResource
import android.util.Log
import android.net.Uri
import androidx.health.connect.client.records.FhirVersion
import androidx.health.connect.client.request.CreateMedicalDataSourceRequest
import androidx.health.connect.client.request.GetMedicalDataSourcesRequest
import androidx.health.connect.client.request.ReadMedicalResourcesInitialRequest
import androidx.health.connect.client.request.ReadMedicalResourcesPageRequest
import androidx.health.connect.client.request.UpsertMedicalResourceRequest
import com.vayunmathur.health.data.HealthDatabase
import com.vayunmathur.health.data.Record
import com.vayunmathur.health.data.RecordType
import com.vayunmathur.library.util.Tuple3
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Instant

object HealthAPI {
    lateinit var healthConnectClient: HealthConnectClient
    lateinit var db: HealthDatabase
    lateinit var preferences: SharedPreferences

    fun init(healthConnectClient: HealthConnectClient, context: Context, db: HealthDatabase) {
        this.healthConnectClient = healthConnectClient
        this.db = db
        preferences = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    }

    @Composable
    fun sumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun maxInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double?> {
        return remember { db.healthDao().maxInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun minInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double?> {
        return remember { db.healthDao().minInRange(recordType, startTime, endTime) }
    }

    suspend inline fun lastRecord(recordType: RecordType): Record? {
        return db.healthDao().getLastRecord(recordType)
    }

    enum class PeriodType {
        Hourly, Daily, Weekly, Monthly
    }

    suspend fun getListOfAverages(
        recordType: RecordType,
        startTime: Instant,
        endTime: Instant,
        period: PeriodType
    ): List<Tuple3<Long, Double, Double>> {
        when(period) {
            PeriodType.Daily -> {
                // yyyy-mm-dd
                val dailySums = db.healthDao().getDailyAvgs(recordType, startTime, endTime).sortedBy { it.day }
                return dailySums.map { Tuple3(LocalDate.parse(it.day).toEpochDays(), it.totalValue, it.totalValue2) }
            }
            PeriodType.Weekly -> {
                val dailySums = db.healthDao().getDailyAvgs(recordType, startTime, endTime).sortedBy { it.day }
                    .groupBy {
                        val date = LocalDate.parse(it.day)
                        val firstDayOfWeek = date.plus((date.dayOfWeek.ordinal+1)%7, DateTimeUnit.DAY)
                        firstDayOfWeek.toEpochDays()
                    }
                    .mapValues { day -> day.value.map { it.totalValue }.average() to day.value.map { it.totalValue2 }.average() }
                    .map { Tuple3(it.key, it.value.first, it.value.second) }
                return dailySums
            }
            PeriodType.Monthly -> {
                val dailySums = db.healthDao().getDailyAvgs(recordType, startTime, endTime).sortedBy { it.day }
                    .groupBy {
                        val date = LocalDate.parse(it.day)
                        val firstDayOfMonth = date.minus(date.day-1, DateTimeUnit.DAY)
                        firstDayOfMonth.toEpochDays()
                    }
                    .mapValues { day -> day.value.map { it.totalValue }.average() to day.value.map { it.totalValue2 }.average() }
                    .map { Tuple3(it.key, it.value.first, it.value.second) }
                return dailySums
            }
            else -> {
                val hourlySums = db.healthDao().getHourlyAvgs(recordType, startTime.toEpochMilliseconds(), endTime.toEpochMilliseconds()).sortedBy { it.hourBlock }
                return hourlySums.map {
                    val date = LocalDateTime.Format {
                        year()
                        chars("-")
                        monthNumber()
                        chars("-")
                        day()
                        chars(" ")
                        hour()
                        chars(":")
                        minute()
                    }.parse(it.hourBlock)
                    Tuple3(date.date.toEpochDays()*24 + date.hour, it.totalValue, it.totalValue2)
                }
            }
        }
    }

    suspend fun getListOfSums(
        recordType: RecordType,
        startTime: Instant,
        endTime: Instant,
        period: PeriodType
    ): List<Tuple3<Long, Double, Double>> {
        when(period) {
            PeriodType.Daily -> {
                // yyyy-mm-dd
                val dailySums = db.healthDao().getDailySums(recordType, startTime, endTime).sortedBy { it.day }
                return dailySums.map { Tuple3(LocalDate.parse(it.day).toEpochDays(), it.totalValue, it.totalValue2) }
            }
            PeriodType.Weekly -> {
                val dailySums = db.healthDao().getDailySums(recordType, startTime, endTime).sortedBy { it.day }
                    .groupBy {
                        val date = LocalDate.parse(it.day)
                        val firstDayOfWeek = date.plus((date.dayOfWeek.ordinal+1)%7, DateTimeUnit.DAY)
                        firstDayOfWeek.toEpochDays()
                    }
                    .mapValues { day -> day.value.map { it.totalValue }.average() to day.value.map { it.totalValue2 }.average() }
                    .map { Tuple3(it.key, it.value.first, it.value.second) }
                return dailySums
            }
            PeriodType.Monthly -> {
                val dailySums = db.healthDao().getDailySums(recordType, startTime, endTime).sortedBy { it.day }
                    .groupBy {
                        val date = LocalDate.parse(it.day)
                        val firstDayOfMonth = date.minus(date.day-1, DateTimeUnit.DAY)
                        firstDayOfMonth.toEpochDays()
                    }
                    .mapValues { day -> day.value.map { it.totalValue }.average() to day.value.map { it.totalValue2 }.average() }
                    .map { Tuple3(it.key, it.value.first, it.value.second) }
                return dailySums
            }
            else -> {
                val hourlySums = db.healthDao().getHourlySums(recordType, startTime.toEpochMilliseconds(), endTime.toEpochMilliseconds()).sortedBy { it.hourBlock }
                return hourlySums.map {
                    val date = LocalDateTime.parse(it.hourBlock)
                    Tuple3(date.date.toEpochDays()*24 + date.hour, it.totalValue, it.totalValue2)
                }
            }
        }
    }

    @OptIn(ExperimentalPersonalHealthRecordApi::class)
    suspend fun allMedicalRecords(type: Int): List<MedicalResource> {
        val allRecords = mutableListOf<MedicalResource>()
        var pageToken: String? = null
        do {
            val request = if (pageToken == null) ReadMedicalResourcesInitialRequest(type, setOf())
            else ReadMedicalResourcesPageRequest(pageToken)
            val response = healthConnectClient.readMedicalResources(request)
            allRecords += response.medicalResources
            pageToken = response.nextPageToken
        } while (pageToken != null)
        return allRecords
    }

    @OptIn(ExperimentalPersonalHealthRecordApi::class)
    suspend fun getOrCreateDataSource(): String {
        Log.d("HealthAPI", "getOrCreateDataSource")
        val dataSources = healthConnectClient.getMedicalDataSources(GetMedicalDataSourcesRequest(emptyList()))
        val existing = dataSources.find { it.displayName == "OpenAssistant Extraction" }
        if (existing != null) {
            Log.d("HealthAPI", "Found existing data source: ${existing.id}")
            return existing.id
        }

        Log.i("HealthAPI", "Creating new data source for OpenAssistant Extraction")
        return healthConnectClient.createMedicalDataSource(
            CreateMedicalDataSourceRequest(
                fhirBaseUri = Uri.parse("https://com.vayunmathur.openassistant/extraction"),
                displayName = "OpenAssistant Extraction",
                fhirVersion = FhirVersion(4, 0, 1)
            )
        ).id
    }

    @OptIn(ExperimentalPersonalHealthRecordApi::class)
    suspend fun writeMedicalRecord(fhirData: String) {
        val json = try {
            Json.parseToJsonElement(fhirData).jsonObject.toMutableMap()
        } catch (e: Exception) {
            Log.e("HealthAPI", "Failed to parse FHIR data as JSON", e)
            return
        }
        
        // Always ensure a random ID is present as requested
        json["id"] = JsonPrimitive(UUID.randomUUID().toString())
        
        val finalData = JsonObject(json).toString()

        Log.d("HealthAPI", "writeMedicalRecord (sanitized): $finalData")
        val dataSourceId = getOrCreateDataSource()
        try {
            healthConnectClient.upsertMedicalResources(listOf(
                UpsertMedicalResourceRequest(
                    dataSourceId = dataSourceId,
                    fhirVersion = FhirVersion(4, 0, 1),
                    data = finalData
                )
            ))
            Log.i("HealthAPI", "Medical record written successfully")
        } catch (e: Exception) {
            Log.e("HealthAPI", "Failed to write medical record to Health Connect", e)
            throw e
        }
    }
}
