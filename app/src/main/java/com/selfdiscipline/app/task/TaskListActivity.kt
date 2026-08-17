package com.selfdiscipline.app.task

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.data.TaskRepository
import com.selfdiscipline.app.databinding.ActivityTaskListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 任务清单：查看 / 添加 / 编辑 / 删除 / 标记完成
 */
class TaskListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskListBinding
    private val repository by lazy { TaskRepository.get(this) }

    private val adapter = TaskListAdapter(
        onEdit = { task -> TaskEditActivity.start(this, task.id) },
        onDelete = { task -> deleteTask(task) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter

        binding.fabAddTask.setOnClickListener {
            TaskEditActivity.start(this, null)
        }

        lifecycleScope.launch {
            repository.allTasks.collectLatest { tasks ->
                adapter.submitList(tasks)
                binding.tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun deleteTask(task: Task) {
        lifecycleScope.launch {
            repository.delete(task)
            Toast.makeText(this@TaskListActivity, "已删除「${task.title}」", Toast.LENGTH_SHORT).show()
        }
    }
}
