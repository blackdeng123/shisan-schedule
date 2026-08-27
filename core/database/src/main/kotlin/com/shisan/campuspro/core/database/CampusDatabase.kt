package com.shisan.campuspro.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shisan.campuspro.core.model.ClassTimeSlot
import com.shisan.campuspro.core.model.ClassTimeSeason
import com.shisan.campuspro.core.model.Course
import com.shisan.campuspro.core.model.CourseSource
import com.shisan.campuspro.core.model.DefaultClassTimeSlots
import com.shisan.campuspro.core.model.DefaultWinterClassTimeSlots
import com.shisan.campuspro.core.model.Exam
import com.shisan.campuspro.core.model.ExamSource
import com.shisan.campuspro.core.model.Grade
import com.shisan.campuspro.core.model.Schedule
import com.shisan.campuspro.core.model.ScheduleDisplaySettings
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: String,
    val totalWeeks: Int,
    val termId: String?,
    val startDateConfirmed: Boolean,
    val showWeekend: Boolean,
    val showNonCurrentWeekCourses: Boolean,
    val backgroundUri: String?,
    val courseCardAlpha: Float,
    val outlineEnabled: Boolean,
    val classTimeSeason: String,
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val name: String,
    val location: String,
    val teacher: String,
    val day: Int,
    val startSlot: Int,
    val endSlot: Int,
    val color: String,
    val weeksCsv: String,
    val source: String,
    val jwxtKey: String,
    val conflict: Boolean = false,
)

@Entity(
    tableName = "class_time_slots",
    primaryKeys = ["scheduleId", "season", "slotIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ClassTimeSlotEntity(
    val scheduleId: String,
    val season: String,
    val slotIndex: Int,
    val startTime: String,
    val endTime: String,
)

@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val credits: Double,
    val score: Double?,
    val scoreText: String,
    val gpa: Double,
    val semester: String?,
)

@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val credits: Double,
    val location: String,
    val date: String,
    val daysLeft: Int,
    val source: String,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId")
    fun observeCourses(scheduleId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses")
    fun observeAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM class_time_slots")
    fun observeAllClassTimeSlots(): Flow<List<ClassTimeSlotEntity>>

    @Upsert
    suspend fun upsertSchedules(schedules: List<ScheduleEntity>)

    @Upsert
    suspend fun upsertCourses(courses: List<CourseEntity>)

    @Upsert
    suspend fun upsertClassTimeSlots(slots: List<ClassTimeSlotEntity>)

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteCoursesForSchedule(scheduleId: String)

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId AND id = :courseId")
    suspend fun deleteCourse(scheduleId: String, courseId: String)

    @Query("DELETE FROM class_time_slots WHERE scheduleId = :scheduleId AND season = :season")
    suspend fun deleteClassTimeSlotsForSchedule(scheduleId: String, season: String)

    @Query("DELETE FROM class_time_slots WHERE scheduleId = :scheduleId")
    suspend fun deleteAllClassTimeSlotsForSchedule(scheduleId: String)

    @Query("DELETE FROM schedules WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)

    @Transaction
    suspend fun replaceCoursesForSchedule(scheduleId: String, courses: List<CourseEntity>) {
        deleteCoursesForSchedule(scheduleId)
        upsertCourses(courses)
    }

    @Transaction
    suspend fun replaceClassTimeSlotsForSchedule(
        scheduleId: String,
        season: String,
        slots: List<ClassTimeSlotEntity>,
    ) {
        deleteClassTimeSlotsForSchedule(scheduleId, season)
        upsertClassTimeSlots(slots)
    }

    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()

    @Query("DELETE FROM class_time_slots")
    suspend fun deleteAllClassTimeSlots()

    @Query("DELETE FROM schedules")
    suspend fun deleteAllSchedules()

    @Transaction
    suspend fun clearAllSchedules() {
        deleteAllCourses()
        deleteAllClassTimeSlots()
        deleteAllSchedules()
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades")
    fun observeGrades(): Flow<List<GradeEntity>>

    @Upsert
    suspend fun upsertGrades(grades: List<GradeEntity>)

    @Query("DELETE FROM grades")
    suspend fun clearGrades()
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY daysLeft ASC")
    fun observeExams(): Flow<List<ExamEntity>>

    @Upsert
    suspend fun upsertExams(exams: List<ExamEntity>)

    @Query("DELETE FROM exams WHERE source = 'JWXT'")
    suspend fun clearJwxtExams()

    @Query("DELETE FROM exams")
    suspend fun clearAllExams()
}

@Database(
    entities = [
        ScheduleEntity::class,
        CourseEntity::class,
        ClassTimeSlotEntity::class,
        GradeEntity::class,
        ExamEntity::class,
    ],
    version = 5,
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
    abstract fun gradeDao(): GradeDao
    abstract fun examDao(): ExamDao

    companion object {
        fun create(context: Context): CampusDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CampusDatabase::class.java,
                "campus.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN showWeekend INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE schedules ADD COLUMN showNonCurrentWeekCourses INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedules ADD COLUMN backgroundUri TEXT")
                db.execSQL("ALTER TABLE schedules ADD COLUMN courseCardAlpha REAL NOT NULL DEFAULT 0.92")
                db.execSQL("ALTER TABLE schedules ADD COLUMN outlineEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS class_time_slots (
                        scheduleId TEXT NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        startTime TEXT NOT NULL,
                        endTime TEXT NOT NULL,
                        PRIMARY KEY(scheduleId, slotIndex),
                        FOREIGN KEY(scheduleId) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN conflict INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN classTimeSeason TEXT NOT NULL DEFAULT 'SUMMER'")
                db.execSQL(
                    """
                    CREATE TABLE class_time_slots_new (
                        scheduleId TEXT NOT NULL,
                        season TEXT NOT NULL,
                        slotIndex INTEGER NOT NULL,
                        startTime TEXT NOT NULL,
                        endTime TEXT NOT NULL,
                        PRIMARY KEY(scheduleId, season, slotIndex),
                        FOREIGN KEY(scheduleId) REFERENCES schedules(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO class_time_slots_new(scheduleId, season, slotIndex, startTime, endTime)
                    SELECT scheduleId, 'SUMMER', slotIndex, startTime, endTime FROM class_time_slots
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO class_time_slots_new(scheduleId, season, slotIndex, startTime, endTime)
                    SELECT scheduleId, 'WINTER', slotIndex,
                        CASE WHEN slotIndex < 5 THEN startTime ELSE printf('%02d:%02d',
                            (CAST(substr(startTime, 1, 2) AS INTEGER) * 60 + CAST(substr(startTime, 4, 2) AS INTEGER) - 30) / 60,
                            (CAST(substr(startTime, 1, 2) AS INTEGER) * 60 + CAST(substr(startTime, 4, 2) AS INTEGER) - 30) % 60) END,
                        CASE WHEN slotIndex < 5 THEN endTime ELSE printf('%02d:%02d',
                            (CAST(substr(endTime, 1, 2) AS INTEGER) * 60 + CAST(substr(endTime, 4, 2) AS INTEGER) - 30) / 60,
                            (CAST(substr(endTime, 1, 2) AS INTEGER) * 60 + CAST(substr(endTime, 4, 2) AS INTEGER) - 30) % 60) END
                    FROM class_time_slots
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE class_time_slots")
                db.execSQL("ALTER TABLE class_time_slots_new RENAME TO class_time_slots")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 默认节次从 12 节缩减为 10 节：清理存量课表中未被任何课程引用的第 11 节之后的遗留节次；
                // 有课程排到 10 节之后的课表保持原样，避免丢失节次时间标注。
                db.execSQL(
                    """
                    DELETE FROM class_time_slots
                    WHERE slotIndex > 10
                      AND scheduleId NOT IN (
                          SELECT scheduleId FROM courses WHERE startSlot > 10 OR endSlot > 10
                      )
                    """.trimIndent(),
                )
            }
        }
    }
}

fun Schedule.toEntity(): ScheduleEntity =
    ScheduleEntity(
        id = id,
        name = name,
        startDate = startDate,
        totalWeeks = totalWeeks,
        termId = termId,
        startDateConfirmed = startDateConfirmed,
        showWeekend = displaySettings.showWeekend,
        showNonCurrentWeekCourses = displaySettings.showNonCurrentWeekCourses,
        backgroundUri = displaySettings.backgroundUri,
        courseCardAlpha = displaySettings.courseCardAlpha,
        outlineEnabled = displaySettings.outlineEnabled,
        classTimeSeason = classTimeSeason.name,
    )

fun ScheduleEntity.toModel(
    courses: List<CourseEntity>,
    classTimeSlots: List<ClassTimeSlotEntity> = emptyList(),
): Schedule =
    Schedule(
        id = id,
        name = name,
        courses = courses.map { it.toModel() },
        startDate = startDate,
        totalWeeks = totalWeeks,
        dataSources = emptyList(),
        deletedJwxtKeys = emptySet(),
        termId = termId,
        startDateConfirmed = startDateConfirmed,
        displaySettings = ScheduleDisplaySettings(
            showWeekend = showWeekend,
            showNonCurrentWeekCourses = showNonCurrentWeekCourses,
            backgroundUri = backgroundUri,
            courseCardAlpha = courseCardAlpha,
            outlineEnabled = outlineEnabled,
        ),
        classTimeSeason = runCatching { ClassTimeSeason.valueOf(classTimeSeason) }
            .getOrDefault(ClassTimeSeason.SUMMER),
        summerClassTimeSlots = classTimeSlots
            .filter { it.season == ClassTimeSeason.SUMMER.name }
            .sortedBy { it.slotIndex }
            .map { it.toModel() }
            .ifEmpty { DefaultClassTimeSlots },
        winterClassTimeSlots = classTimeSlots
            .filter { it.season == ClassTimeSeason.WINTER.name }
            .sortedBy { it.slotIndex }
            .map { it.toModel() }
            .ifEmpty { DefaultWinterClassTimeSlots },
    )

fun Course.toEntity(scheduleId: String): CourseEntity =
    CourseEntity(
        id = id,
        scheduleId = scheduleId,
        name = name,
        location = location,
        teacher = teacher,
        day = day,
        startSlot = startSlot,
        endSlot = endSlot,
        color = color,
        weeksCsv = weeks.joinToString(","),
        source = source.name,
        jwxtKey = jwxtKey,
        conflict = conflict,
    )

fun CourseEntity.toModel(): Course =
    Course(
        id = id,
        name = name,
        location = location,
        teacher = teacher,
        day = day,
        startSlot = startSlot,
        endSlot = endSlot,
        color = color,
        weeks = weeksCsv.split(",").mapNotNull { it.toIntOrNull() },
        source = CourseSource.valueOf(source),
        jwxtKey = jwxtKey,
        conflict = conflict,
    )

fun ClassTimeSlot.toEntity(scheduleId: String, season: ClassTimeSeason): ClassTimeSlotEntity =
    ClassTimeSlotEntity(
        scheduleId = scheduleId,
        season = season.name,
        slotIndex = index,
        startTime = startTime,
        endTime = endTime,
    )

fun ClassTimeSlotEntity.toModel(): ClassTimeSlot =
    ClassTimeSlot(
        index = slotIndex,
        startTime = startTime,
        endTime = endTime,
    )

fun Grade.toEntity(): GradeEntity =
    GradeEntity(
        id = id,
        name = name,
        type = type,
        credits = credits,
        score = score,
        scoreText = scoreText,
        gpa = gpa,
        semester = semester,
    )

fun GradeEntity.toModel(): Grade =
    Grade(
        id = id,
        name = name,
        type = type,
        credits = credits,
        score = score,
        scoreText = scoreText,
        gpa = gpa,
        semester = semester,
    )

fun Exam.toEntity(): ExamEntity =
    ExamEntity(
        id = id,
        name = name,
        type = type,
        credits = credits,
        location = location,
        date = date,
        daysLeft = daysLeft,
        source = source.name,
    )

fun ExamEntity.toModel(): Exam =
    Exam(
        id = id,
        name = name,
        type = type,
        credits = credits,
        location = location,
        date = date,
        daysLeft = daysLeft,
        source = ExamSource.valueOf(source),
    )
