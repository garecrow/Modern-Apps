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
        return remember(recordType, startTime, endTime) { db.healthDao().sumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumProteinInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumProteinInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumCarbsInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumCarbsInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumFatInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumFatInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumFiberInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumFiberInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumSugarInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumSugarInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumSodiumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumSodiumInRange(recordType, startTime, endTime) }
    }
    
    @Composable
    fun sumBiotinInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumBiotinInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumCaffeineInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumCaffeineInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumCalciumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumCalciumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumChlorideInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumChlorideInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumCholesterolInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumCholesterolInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumChromiumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumChromiumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumCopperInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumCopperInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumFolateInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumFolateInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumFolicAcidInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumFolicAcidInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumIodineInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumIodineInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumIronInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumIronInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumMagnesiumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumMagnesiumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumManganeseInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumManganeseInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumMolybdenumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumMolybdenumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumMonounsaturatedFatInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumMonounsaturatedFatInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumNiacinInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumNiacinInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumPantothenicAcidInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumPantothenicAcidInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumPhosphorusInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumPhosphorusInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumPolyunsaturatedFatInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumPolyunsaturatedFatInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumPotassiumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumPotassiumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumRiboflavinInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumRiboflavinInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumSaturatedFatInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumSaturatedFatInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumSeleniumInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumSeleniumInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumThiaminInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumThiaminInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumTransFatInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumTransFatInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumUnsaturatedFatInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumUnsaturatedFatInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminAInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminAInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminB12InRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminB12InRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminB6InRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminB6InRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminCInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminCInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminDInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminDInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminEInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminEInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumVitaminKInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumVitaminKInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun sumZincInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double> {
        return remember { db.healthDao().sumZincInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun maxInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double?> {
        return remember(recordType, startTime, endTime) { db.healthDao().maxInRange(recordType, startTime, endTime) }
    }

    @Composable
    fun minInRange(recordType: RecordType, startTime: Instant, endTime: Instant): Flow<Double?> {
        return remember(recordType, startTime, endTime) { db.healthDao().minInRange(recordType, startTime, endTime) }
    }

    suspend inline fun lastRecord(recordType: RecordType): Record? {
        return db.healthDao().getLastRecord(recordType)
    }

    enum class PeriodType {
        Hourly, Daily, Weekly, Monthly
    }

    private val hourlyFormat = LocalDateTime.Format {
        year()
        chars("-")
        monthNumber()
        chars("-")
        day()
        chars(" ")
        hour()
        chars(":")
        minute()
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
                    val date = hourlyFormat.parse(it.hourBlock)
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
                    val date = hourlyFormat.parse(it.hourBlock)
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
