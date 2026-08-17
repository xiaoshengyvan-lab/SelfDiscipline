package com.selfdiscipline.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** 观察全部任务：未完成在前，按优先级/创建时间排序 */
    @Query("SELECT * FROM tasks ORDER BY done ASC, priority ASC, createdAt ASC")
    fun observeAll(): Flow<List<Task>>

    /** 取下一个最该做的未完成任务 */
    @Query("SELECT * FROM tasks WHERE done = 0 ORDER BY priority ASC, createdAt ASC LIMIT 1")
    suspend fun nextPending(): Task?

    /** 取下一个未完成任务（排除指定 id，用于“换一个任务”） */
    @Query("SELECT * FROM tasks WHERE done = 0 AND id != :excludeId ORDER BY priority ASC, createdAt ASC LIMIT 1")
    suspend fun nextPendingExcluding(excludeId: Long): Task?

    /** 按 id 查询单个任务 */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    /** 标记完成 */
    @Query("UPDATE tasks SET done = 1, doneAt = :time WHERE id = :id")
    suspend fun markDone(id: Long, time: Long = System.currentTimeMillis())

    /** 重置为未完成 */
    @Query("UPDATE tasks SET done = 0, doneAt = NULL WHERE id = :id")
    suspend fun resetTask(id: Long)

    /** 每日 0 点刷新：全部已完成任务重置为未完成 */
    @Query("UPDATE tasks SET done = 0, doneAt = NULL WHERE done = 1")
    suspend fun resetAllDone()
}
