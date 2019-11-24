package com.coderlab.cricketkotlindemo.paging.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView

import com.coderlab.cricketkotlindemo.paging.model.Item
import com.coderlab.drxpagingexample.R
import com.coderlab.drxpagingexample.databinding.ItemPagingListBinding
import com.sumitzway.drxpaging.DRxPagedListAdapter


class ItemAdapterDRx internal constructor(private val mCtx: Context) :
    DRxPagedListAdapter<Item, ItemAdapterDRx.ItemViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = DataBindingUtil.inflate<ItemPagingListBinding>(
            LayoutInflater.from(mCtx),
            R.layout.item_paging_list,
            parent, false
        )
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.item = item
    }

    inner class ItemViewHolder(val binding: ItemPagingListBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                getItem(adapterPosition)?.let { item ->

                }

            }
        }

    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem.question_id == newItem.question_id
            }

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean {
                return oldItem.equals(newItem)
            }
        }
    }
}