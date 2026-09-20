package com.sb.attendance.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A staff member. [faceEmbedding] is the 512-float FaceNet template produced at enrolment,
 * stored as raw little-endian bytes. Null until the admin completes face enrolment.
 */
@Entity(tableName = "staff", indices = [Index(value = ["employeeId"], unique = true)])
data class StaffEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val employeeId: String,
    val faceEmbedding: ByteArray? = null,
    val enrolmentPhotoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isEnrolled: Boolean get() = faceEmbedding != null

    // Room data class with a ByteArray field: override equals/hashCode to compare by content.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StaffEntity) return false
        return id == other.id &&
            name == other.name &&
            employeeId == other.employeeId &&
            faceEmbedding.contentEqualsOrNull(other.faceEmbedding) &&
            enrolmentPhotoPath == other.enrolmentPhotoPath &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + employeeId.hashCode()
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + (enrolmentPhotoPath?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }
}

private fun ByteArray?.contentEqualsOrNull(other: ByteArray?): Boolean =
    if (this == null || other == null) this == null && other == null else this.contentEquals(other)

/**
 * One successful attendance mark. Only written when face verification passed, so every
 * row in this table is by construction a matched attendance.
 */
@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = StaffEntity::class,
            parentColumns = ["id"],
            childColumns = ["staffRowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("staffRowId")]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val staffRowId: Long,
    val employeeId: String,
    val timestamp: Long,
    val selfiePath: String,
    val latitude: Double?,
    val longitude: Double?,
    /** Cosine similarity against the enrolled template, kept for auditability. */
    val matchScore: Float
)
