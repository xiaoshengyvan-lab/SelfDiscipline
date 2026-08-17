package com.selfdiscipline.app.task

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.selfdiscipline.app.data.Task
import com.selfdiscipline.app.databinding.ItemTaskBinding

/**
 * 任务列表适配器
 */
class TaskListAdapter(
    private val onEdit: (Task) -> Unit,
    private val onDelete: (Task) -> Unit,
    private val onToggleDone: (Task) -> Unit
) : ListAdapter<Task, TaskListAdapter.TaskViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }

    inner class TaskViewHolder(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.binding.apply {
            tvTaskTitle.text = task.title
            tvTaskMeta.text = "预计 ${task.durationMinutes} 分钟 · 优先级 ${task.priority}"
            if (task.done) {
                tvTaskTitle.paintFlags = tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                tvTaskTitle.paintFlags = tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            cbDone.isChecked = task.done
            cbDone.setOnCheckedChangeListener { _, _ -> onToggleDone(task) }
            btnEdit.setOnClickListener { onEdit(task) }
            btnDelete.setOnClickListener { onDelete(task) }
        }
    }
}
