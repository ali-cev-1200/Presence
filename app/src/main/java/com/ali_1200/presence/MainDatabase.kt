package com.ali_1200.presence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 7,
    exportSchema = false,
    entities = [Logingdata::class, dateResponse::class, Student::class, Course::class, MyAttendance::class]
)
abstract class MainDatabase : RoomDatabase() {

    abstract val loginDao: LoginDao
    abstract val responseDao: ResponseDao
    abstract val studentDao: StudentDao
    abstract val courseDao: CourseDao
    abstract val backupDao: BackupDao


    companion object {

        @Volatile
        private var INSTANCE: MainDatabase? = null

        // نسخه ۴ فقط جدول لیست کلاس را اضافه می‌کند؛ حضورهای ثبت‌شده حفظ می‌شوند
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Student` (`studentID` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`studentID`))"
                )
            }
        }

        // نسخه ۵ چند درسی می‌شود: کد کلاس قبلی «درس ۱» می‌شود و همه داده‌ها به آن درس تعلق می‌گیرند
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Course` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `classCode` TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `Course` (`id`, `name`, `classCode`) SELECT 1, 'درس ۱', `classCode` FROM `Login` WHERE `classCode` != ''"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `MyAttendance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `courseId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
                // ثبت موفق امروز دانشجو (نسخه قبل فقط آخرین ثبت را نگه می‌داشت)
                db.execSQL(
                    "INSERT INTO `MyAttendance` (`courseId`, `timestamp`) SELECT 1, `timestamp` FROM `Login` " +
                        "WHERE `isTeacher` = 0 AND `is_send` = 1 AND `timestamp` IS NOT NULL AND `classCode` != ''"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Student_new` (`studentID` TEXT NOT NULL, `name` TEXT NOT NULL, `courseId` INTEGER NOT NULL, PRIMARY KEY(`courseId`, `studentID`))"
                )
                db.execSQL("INSERT INTO `Student_new` (`studentID`, `name`, `courseId`) SELECT `studentID`, `name`, 1 FROM `Student`")
                db.execSQL("DROP TABLE `Student`")
                db.execSQL("ALTER TABLE `Student_new` RENAME TO `Student`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Respons_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `studentID` TEXT NOT NULL, `name` TEXT NOT NULL, `deviceID` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `date` TEXT, `courseId` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `Respons_new` (`id`, `studentID`, `name`, `deviceID`, `timestamp`, `date`, `courseId`) " +
                        "SELECT `id`, `studentID`, `name`, `deviceID`, `timestamp`, `date`, 1 FROM `Respons`"
                )
                db.execSQL("DROP TABLE `Respons`")
                db.execSQL("ALTER TABLE `Respons_new` RENAME TO `Respons`")

                // ستون‌های قدیمی Login (کد کلاس و وضعیت ارسال) به جدول‌های جدید منتقل شدند
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Login_new` (`id` INTEGER NOT NULL, `studentID` TEXT NOT NULL, `name` TEXT NOT NULL, `deviceID` TEXT NOT NULL, `isTeacher` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `Login_new` (`id`, `studentID`, `name`, `deviceID`, `isTeacher`) " +
                        "SELECT `id`, `studentID`, `name`, `deviceID`, `isTeacher` FROM `Login`"
                )
                db.execSQL("DROP TABLE `Login`")
                db.execSQL("ALTER TABLE `Login_new` RENAME TO `Login`")
            }
        }

        // نسخه ۶: کلید عمومی استاد برای بررسی امضای گوشی استاد در گوشی دانشجو
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Course` ADD COLUMN `teacherKey` TEXT")
            }
        }

        // نسخه ۷: قدرت سیگنال هر حضور (برای محدوده کلاس و نمایش به استاد)
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Respons` ADD COLUMN `rssi` INTEGER")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        fun getDatabase(context: Context): MainDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MainDatabase::class.java,
                    "mainDatabase.db"
                )
                    .addMigrations(*MIGRATIONS)
                    // نسخه‌های ۱ و ۲ ساختار متفاوتی داشتند؛ داده‌های آزمایشی آن‌ها پاک می‌شوند
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
