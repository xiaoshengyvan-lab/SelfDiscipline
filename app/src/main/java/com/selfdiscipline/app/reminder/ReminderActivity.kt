package com.selfdiscipline.app.reminder

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.selfdiscipline.app.data.AppSettings
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.databinding.ActivityReminderBinding
import com.selfdiscipline.app.service.ReminderNotifier
import com.selfdiscipline.app.task.TaskEditActivity
import com.selfdiscipline.app.util.TimeFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 自律提醒页（全屏弹出）：
 *  - 显示本次解锁已使用时长
 *  - 显示“该去做什么”：自动推荐下一个最优先的未完成任务
 *  - 「开始专注」倒计时（时长 = 任务设置的预计时长），倒计时状态跨页面/进程保留，
 *    返回首页再进来会继续剩余时间，无需重新开始
 *  - 「稍后提醒」（10/30/60 分钟）、「关闭本次提醒」（下次解锁自动恢复）
 */
class ReminderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USAGE = "extra_usage"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val STATE_DEADLINE = "state_deadline"
    }

    private lateinit var binding: ActivityReminderBinding
    private val repository by lazy { TaskRepository.get(this) }
    private val settings by lazy { AppSettings(this) }

    private var currentTask: Task? = null
    private var deadlineAt = 0L
    private var tickerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cancelReminderNotification()
        setupButtons()
        deadlineAt = savedInstanceState?.getLong(STATE_DEADLINE, 0L) ?: 0L
        // 恢复进行中的专注（跨页面/进程保留）
        if (deadlineAt <= 0L && settings.focusDeadlineElapsed > SystemClock.elapsedRealtime()) {
            deadlineAt = settings.focusDeadlineElapsed
        }
        showUsageText(intent.getLongExtra(EXTRA_USAGE, 0L))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        cancelReminderNotification()
        showUsageText(intent.getLongExtra(EXTRA_USAGE, 0L))
        if (deadlineAt > SystemClock.elapsedRealtime()) {
            restoreFocus()
        } else {
            reloadTask()
        }
    }

    override fun onResume() {
        super.onResume()
        if (deadlineAt > SystemClock.elapsedRealtime()) {
            restoreFocus()
        } else {
            reloadTask()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_DEADLINE, deadlineAt)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        super.onDestroy()
    }

    // ---------- 初始化 ----------

    private fun cancelReminderNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ReminderNotifier.NOTIFICATION_ID)
    }

    private fun showUsageText(usageMillis: Long) {
        binding.tvRemindUsage.text = "本次解锁已使用 ${TimeFormat.formatDuration(usageMillis)}"
    }

    private fun setupButtons() {
        binding.btnStart.setOnClickListener { startFocus() }
        binding.btnComplete.setOnClickListener { completeTask() }
        binding.btnSkip.setOnClickListener { loadNextTask() }
        binding.btnSnooze10.setOnClickListener { snooze(10) }
        binding.btnSnooze30.setOnClickListener { snooze(30) }
        binding.btnSnooze60.setOnClickListener { snooze(60) }
        binding.btnMuteSession.setOnClickListener { muteSession() }
        binding.btnAddTask.setOnClickListener {
            TaskEditActivity.start(this, null)
        }
    }

    // ---------- 任务加载 ----------

    private fun reloadTask() {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        lifecycleScope.launch {
            val task = if (taskId > 0) repository.getById(taskId) else repository.nextPending()
            if (task != null) showTask(task) else showNoTask()
        }
    }

    /** 恢复进行中的专注倒计时 */
    private fun restoreFocus() {
        val taskId = settings.focusTaskId
        lifecycleScope.launch {
            val task = if (taskId > 0) repository.getById(taskId) else null
            if (task != null) {
                showTask(task)
            } else {
                // 任务可能已被删除：用保存的信息兜底显示
                showTask(
                    Task(
                        id = taskId,
                        title = settings.focusTaskTitle.ifEmpty { "专注中" },
                        durationMinutes = settings.focusTaskDurationMinutes
                    )
                )
            }
        }
    }

    private fun showNoTask() {
        currentTask = null
        tickerJob?.cancel()
        deadlineAt = 0L
        binding.cardTask.visibility = View.GONE
        binding.tvNoTask.visibility = View.VISIBLE
        binding.btnAddTask.visibility = View.VISIBLE
        binding.btnStart.visibility = View.GONE
        binding.btnComplete.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
        binding.tvCountdown.visibility = View.GONE
        binding.progressFocus.visibility = View.GONE
        binding.tvFocusHint.visibility = View.GONE
        binding.btnSnooze10.visibility = View.GONE
        binding.btnSnooze30.visibility = View.GONE
        binding.btnSnooze60.visibility = View.GONE
        binding.btnMuteSession.visibility = View.GONE
    }

    private fun showTask(task: Task) {
        currentTask = task
        binding.cardTask.visibility = View.VISIBLE
        binding.tvNoTask.visibility = View.GONE
        binding.btnAddTask.visibility = View.GONE
        binding.btnSkip.visibility = View.VISIBLE
        binding.btnSnooze10.visibility = View.VISIBLE
        binding.btnSnooze30.visibility = View.VISIBLE
        binding.btnSnooze60.visibility = View.VISIBLE
        binding.btnMuteSession.visibility = View.VISIBLE
        binding.tvTaskTitle.text = task.title
        binding.tvTaskMeta.text = "预计时长 ${task.durationMinutes} 分钟"

        val remaining = deadlineAt - SystemClock.elapsedRealtime()
        if (remaining > 0) {
            // 计时进行中（返回首页后再进入 / 旋转屏幕后恢复）
            binding.btnStart.visibility = View.GONE
            binding.btnComplete.visibility = View.VISIBLE
            binding.tvFocusHint.text = "专注中 · 返回后自动继续"
            startTicker()
        } else {
            deadlineAt = 0L
            tickerJob?.cancel()
            binding.btnStart.visibility = View.VISIBLE
            binding.btnComplete.visibility = View.GONE
            binding.tvFocusHint.text = "开始专注"
            binding.tvCountdown.text = TimeFormat.formatCountdown(task.durationMinutes * 60_000L)
            binding.progressFocus.progress = 0
        }
        binding.tvCountdown.visibility = View.VISIBLE
        binding.progressFocus.visibility = View.VISIBLE
        binding.tvFocusHint.visibility = View.VISIBLE
    }

    // ---------- 专注计时 ----------

    private fun startFocus() {
        val task = currentTask ?: return
        deadlineAt = SystemClock.elapsedRealtime() + task.durationMinutes * 60_000L
        // 持久化专注状态：返回首页/进程被杀后仍可恢复
        settings.focusTaskId = task.id
        settings.focusTaskTitle = task.title
        settings.focusTaskDurationMinutes = task.durationMinutes
        settings.focusDeadlineElapsed = deadlineAt

        binding.btnStart.visibility = View.GONE
        binding.btnComplete.visibility = View.VISIBLE
        binding.tvFocusHint.text = "专注中 · 返回后自动继续"
        startTicker()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                val remain = deadlineAt - SystemClock.elapsedRealtime()
                if (remain <= 0) {
                    binding.tvCountdown.text = "00:00"
                    binding.progressFocus.progress = 100
                    binding.tvFocusHint.text = "时间到！给自己一点奖励吧 🎉"
                    settings.clearFocus()
                    break
                }
                binding.tvCountdown.text = TimeFormat.formatCountdown(remain)
                val total = (currentTask?.durationMinutes ?: 30) * 60_000L
                binding.progressFocus.progress = ((total - remain) * 100 / total).toInt()
                delay(1000L)
            }
        }
    }

    private fun completeTask() {
        val task = currentTask ?: return
        tickerJob?.cancel()
        settings.clearFocus()
        lifecycleScope.launch {
            if (task.id > 0) repository.markDone(task.id)
            Toast.makeText(this@ReminderActivity, "太棒了！已完成「${task.title}」", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadNextTask() {
        val current = currentTask
        lifecycleScope.launch {
            val next = if (current != null) {
                repository.nextPendingExcluding(current.id)
            } else {
                repository.nextPending()
            }
            if (next == null) {
                Toast.makeText(this@ReminderActivity, "没有其他待办任务了", Toast.LENGTH_SHORT).show()
                return@launch
            }
            tickerJob?.cancel()
            deadlineAt = 0L
            showTask(next)
        }
    }

    // ---------- 稍后提醒 / 关闭本次 ----------

    private fun snooze(minutes: Int) {
        settings.snoozeUntilMs = System.currentTimeMillis() + minutes * 60_000L
        Toast.makeText(this, "$minutes 分钟后再次提醒", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun muteSession() {
        settings.sessionMuted = true
        Toast.makeText(this, "本次会话不再提醒，下次解锁后自动恢复", Toast.LENGTH_SHORT).show()
        finish()
    }
}
