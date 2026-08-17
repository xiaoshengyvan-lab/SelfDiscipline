package com.selfdiscipline.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * 任务仓库：封装数据库访问，供界面与后台服务统一使用。
 */
class TaskRepository(private val dao: TaskDao) {

    val allTasks: Flow<List<Task>> = dao.observeAll()

    suspend fun nextPending(): Task? = dao.nextPending()

    suspend fun nextPendingExcluding(id: Long): Task? = dao.nextPendingExcluding(id)

    suspend fun getById(id: Long): Task? = dao.getById(id)

    suspend fun add(title: String, durationMinutes: Int, priority: Int): Long =
        dao.insert(Task(title = title, durationMinutes = durationMinutes, priority = priority))

    suspend fun update(task: Task) = dao.update(task)

    suspend fun delete(task: Task) = dao.delete(task)

    suspend fun markDone(id: Long) = dao.markDone(id)

    suspend fun reset(id: Long) = dao.resetTask(id)

    suspend fun resetAllDone() = dao.resetAllDone()

    companion object {
        @Volatile
        private var instance: TaskRepository? = null

        fun get(context: Context): TaskRepository =
            instance ?: synchronized(this) {
                instance ?: TaskRepository(AppDatabase.get(context).taskDao()).also { instance = it }
            }
    }
}
