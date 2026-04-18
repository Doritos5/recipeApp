package com.example.recipeapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipeapp.R
import com.example.recipeapp.model.recipes.Comment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CommentAdapter(
    private var comments: List<Comment> = emptyList(),
    private val currentUserId: String? = null,
    private val onEdit: ((Comment) -> Unit)? = null,
    private val onDelete: ((Comment) -> Unit)? = null
) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    fun setComments(newComments: List<Comment>) {
        comments = newComments
        notifyDataSetChanged()
    }

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameTv: TextView = itemView.findViewById(R.id.commentUserName)
        val dateTv: TextView = itemView.findViewById(R.id.commentDate)
        val textTv: TextView = itemView.findViewById(R.id.commentText)
        val actionsLayout: View = itemView.findViewById(R.id.commentActionsLayout)
        val editBtn: View = itemView.findViewById(R.id.commentEditBtn)
        val deleteBtn: View = itemView.findViewById(R.id.commentDeleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.userNameTv.text = comment.userName.ifBlank { "Unknown User" }
        holder.textTv.text = comment.text
        
        if (comment.timestamp > 0) {
            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            holder.dateTv.text = formatter.format(Date(comment.timestamp))
        } else {
            holder.dateTv.text = ""
        }

        if (currentUserId != null && comment.userId == currentUserId) {
            holder.actionsLayout.visibility = View.VISIBLE
            holder.editBtn.setOnClickListener { onEdit?.invoke(comment) }
            holder.deleteBtn.setOnClickListener { onDelete?.invoke(comment) }
        } else {
            holder.actionsLayout.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = comments.size
}
