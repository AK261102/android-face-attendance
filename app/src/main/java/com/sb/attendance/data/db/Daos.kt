package com.sb.attendance.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<StaffEntity>>

    @Query("SELECT * FROM staff WHERE id = :id")
    fun observeById(id: Long): Flow<StaffEntity?>

    @Query("SELECT * FROM staff WHERE id = :id")
    suspend fun getById(id: Long): StaffEntity?

    @Query("SELECT * FROM staff WHERE employeeId = :employeeId LIMIT 1")
    suspend fun getByEmployeeId(employeeId: String): StaffEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(staff: StaffEntity): Long

    @Update
    suspend fun update(staff: StaffEntity)

    @Query("UPDATE staff SET faceEmbedding = :embedding, enrolmentPhotoPath = :photoPath WHERE id = :id")
    suspend fun setEnrolment(id: Long, embedding: ByteArray, photoPath: String)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE staffRowId = :staffRowId ORDER BY timestamp DESC")
    fun observeForStaff(staffRowId: Long): Flow<List<AttendanceEntity>>

    @Query("SELECT COUNT(*) FROM attendance WHERE staffRowId = :staffRowId")
    fun countForStaff(staffRowId: Long): Flow<Int>

    @Insert
    suspend fun insert(record: AttendanceEntity): Long
}
