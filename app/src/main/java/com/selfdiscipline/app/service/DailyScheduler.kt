package com.selfdiscipline.app.service

import android.content.Context
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.data.TaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每日调度：
 *  - 每日 0 点刷新：日期变化时把「已完成」任务全部重置为未完成（任务保留）
 *  - 每日任务提醒：每天按优先级提醒一次下一个最该做的任务
 *    （仅当天提醒一次，避免每次解锁都重复弹出造成冲突）
 */
object DailyScheduler {

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun checkDaily(context: Context, repository: TaskRepository) {
        val s = AppSettings(context)
        val today = today()

        // 跨天：刷新已完成任务
        if (today != s.lastResetDate) {
            repository.resetAllDone()
            s.lastResetDate = today
            s.lastTaskRemindDate = "" // 新的一天，允许再次提醒
        }

        // 每天按优先级提醒一次
        if (today != s.lastTaskRemindDate) {
            val task = repository.nextPending()
            if (task != null) {
                ReminderNotifier.showDailyTaskReminder(context, task)
            }
            s.lastTaskRemindDate = today
        }
    }
}
