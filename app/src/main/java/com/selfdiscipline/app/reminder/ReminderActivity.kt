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
 * 专注页（全屏弹出）：
 *  - 只展示居中的专注倒计时（不显示使用时长等冗余信息）
 *  - 点击卡片：开始倒计时；再次点击：暂停（不重置）；倒计时正常走完后自动重置
 *  - 倒计时状态跨页面/进程保留（返回首页再进入继续）
 *  - 倒计时结束弹出「专注完成」提醒
 *  - 「我已完成这个任务」「换一个任务」「关闭本次提醒」
 */
class ReminderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USAGE = "extra_usage"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val STATE_REMAINING = "state_remaining"
        private const val STATE_DEADLINE = "state_deadline"

        private const val MODE_IDLE = 0
        private const val MODE_COUNTING = 1
        private const val MODE_PAUSED = 2
    }

    private lateinit var binding: ActivityReminderBinding
    private val repository by lazy { TaskRepository.get(this) }
    private val settings by lazy { AppSettings(this) }

    private var currentTask: Task? = null
    private var remainingMs = 0L
    private var deadlineAt = 0L
    private var mode = MODE_IDLE
    private var tickerJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cancelReminderNotification()
        setupButtons()

        if (savedInstanceState != null) {
            remainingMs = savedInstanceState.getLong(STATE_REMAINING, 0L)
            deadlineAt = savedInstanceState.getLong(STATE_DEADLINE, 0L)
        } else {
            restoreFocusState()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        // 跨页面/进程恢复：刷新计时状态
        refreshFromPersistence()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_REMAINING, remainingMs)
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

    private fun setupButtons() {
        // 点击卡片：开始 / 暂停 / 继续
        binding.cardTask.setOnClickListener { onCardTap() }
        binding.btnComplete.setOnClickListener { completeTask() }
        binding.btnSkip.setOnClickListener { loadNextTask() }
        binding.btnMuteSession.setOnClickListener { muteSession() }
        binding.btnAddTask.setOnClickListener {
            TaskEditActivity.start(this, null)
        }
    }

    // ---------- 状态恢复 ----------

    private fun restoreFocusState() {
        val deadline = settings.focusDeadlineElapsed
        val now = SystemClock.elapsedRealtime()
        when {
            deadline > now -> {
                deadlineAt = deadline
                mode = MODE_COUNTING
            }
            settings.focusRemainingMs > 0L && settings.focusTaskId >= 0L -> {
                remainingMs = settings.focusRemainingMs
                mode = MODE_PAUSED
            }
            else -> mode = MODE_IDLE
        }
    }

    /** 从持久化状态刷新（进程被杀 / 返回页面时调用） */
    private fun refreshFromPersistence() {
        if (currentTask == null) {
            restoreFocusState()
            val intentTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            val taskId = if (mode != MODE_IDLE) {
                settings.focusTaskId.takeIf { it >= 0L } ?: -1L
            } else {
                intentTaskId
            }
            loadTask(taskId)
            return
        }
        if (mode == MODE_COUNTING) {
            val remaining = deadlineAt - SystemClock.elapsedRealtime()
            if (remaining <= 0L) {
                finishFocus()
            } else {
                startTicker()
            }
        } else {
            render()
        }
    }

    private fun loadTask(taskId: Long) {
        lifecycleScope.launch {
            val task = if (taskId > 0) repository.getById(taskId) else repository.nextPending()
            if (task != null) {
                currentTask = task
                if (mode == MODE_IDLE) {
                    remainingMs = task.durationMinutes * 60_000L
                }
                render()
                if (mode == MODE_COUNTING && deadlineAt > SystemClock.elapsedRealtime()) {
                    startTicker()
                }
            } else {
                showNoTask()
            }
        }
    }

    private fun showNoTask() {
        currentTask = null
        tickerJob?.cancel()
        binding.cardTask.visibility = View.GONE
        binding.tvNoTask.visibility = View.VISIBLE
        binding.btnAddTask.visibility = View.VISIBLE
        binding.btnComplete.visibility = View.GONE
        binding.btnSkip.visibility = View.GONE
        binding.btnMuteSession.visibility = View.GONE
    }

    // ---------- 卡片点击：开始 / 暂停 / 继续 ----------

    private fun onCardTap() {
        when (mode) {
            MODE_IDLE -> startFocus()
            MODE_COUNTING -> pauseFocus()
            MODE_PAUSED -> resumeFocus()
        }
    }

    private fun startFocus() {
        val task = currentTask ?: return
        remainingMs = task.durationMinutes * 60_000L
        settings.focusTaskId = task.id
        settings.focusTaskTitle = task.title
        settings.focusTaskDurationMinutes = task.durationMinutes
        settings.focusRemainingMs = remainingMs
        resumeFocus()
    }

    private fun resumeFocus() {
        if (remainingMs <= 0L) return
        deadlineAt = SystemClock.elapsedRealtime() + remainingMs
        settings.focusDeadlineElapsed = deadlineAt
        mode = MODE_COUNTING
        startTicker()
    }

    private fun pauseFocus() {
        tickerJob?.cancel()
        remainingMs = (deadlineAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        settings.focusRemainingMs = remainingMs
        settings.focusDeadlineElapsed = 0L
        mode = MODE_PAUSED
        render()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (true) {
                val remain = deadlineAt - SystemClock.elapsedRealtime()
                if (remain <= 0L) {
                    finishFocus()
                    break
                }
                binding.tvCountdown.text = TimeFormat.formatCountdown(remain)
                delay(250L)
            }
        }
        render()
    }

    /** 倒计时正常走完：重置并弹完成提醒 */
    private fun finishFocus() {
        tickerJob?.cancel()
        val title = currentTask?.title ?: settings.focusTaskTitle.ifEmpty { "专注" }
        settings.clearFocus()
        remainingMs = 0L
        deadlineAt = 0L
        mode = MODE_IDLE
        ReminderNotifier.showFocusFinished(this, title)
        loadTask(-1L)
    }

    // ---------- 渲染 ----------

    private fun render() {
        if (currentTask == null) return
        binding.cardTask.visibility = View.VISIBLE
        binding.tvNoTask.visibility = View.GONE
        binding.btnAddTask.visibility = View.GONE
        binding.btnComplete.visibility = View.VISIBLE
        binding.btnSkip.visibility = View.VISIBLE
        binding.btnMuteSession.visibility = View.VISIBLE
        binding.tvTaskTitle.text = currentTask?.title ?: "专注"

        val displayMs = when (mode) {
            MODE_COUNTING -> (deadlineAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            MODE_PAUSED -> remainingMs
            else -> (currentTask?.durationMinutes ?: 30) * 60_000L
        }
        binding.tvCountdown.text = TimeFormat.formatCountdown(displayMs)
        binding.tvFocusHint.text = when (mode) {
            MODE_COUNTING -> "点击暂停"
            MODE_PAUSED -> "点击继续"
            else -> "点击开始专注"
        }
    }

    // ---------- 其他操作 ----------

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
        tickerJob?.cancel()
        settings.clearFocus()
        remainingMs = 0L
        deadlineAt = 0L
        mode = MODE_IDLE
        lifecycleScope.launch {
            val next = if (current != null) {
                repository.nextPendingExcluding(current.id)
            } else {
                repository.nextPending()
            }
            if (next == null) {
                Toast.makeText(this@ReminderActivity, "没有其他待办任务了", Toast.LENGTH_SHORT).show()
                showNoTask()
                return@launch
            }
            currentTask = next
            remainingMs = next.durationMinutes * 60_000L
            render()
        }
    }

    private fun muteSession() {
        settings.sessionMuted = true
        Toast.makeText(this, "本次会话不再提醒，下次解锁后自动恢复", Toast.LENGTH_SHORT).show()
        finish()
    }
}
