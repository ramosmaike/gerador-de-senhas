package com.example.geradordesenhas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PasswordAdapter(
    private var passwords: List<PasswordEntry>,
    private val onEditClick: (PasswordEntry) -> Unit,
    private val onDeleteClick: (PasswordEntry) -> Unit,
    private val onCopyClick: (String) -> Unit
) : RecyclerView.Adapter<PasswordAdapter.PasswordViewHolder>() {

    class PasswordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_item_name)
        val tvPassword: TextView = view.findViewById(R.id.tv_item_password)
        val btnEdit: ImageButton = view.findViewById(R.id.btn_edit)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PasswordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_password, parent, false)
        return PasswordViewHolder(view)
    }

    override fun onBindViewHolder(holder: PasswordViewHolder, position: Int) {
        val entry = passwords[position]
        holder.tvName.text = entry.name
        holder.tvPassword.text = entry.password

        holder.itemView.setOnClickListener { onCopyClick(entry.password) }
        holder.btnEdit.setOnClickListener { onEditClick(entry) }
        holder.btnDelete.setOnClickListener { onDeleteClick(entry) }
    }

    override fun getItemCount() = passwords.size

    fun updateData(newPasswords: List<PasswordEntry>) {
        this.passwords = newPasswords
        notifyDataSetChanged()
    }
}
