package com.coderlab.drxpagingexample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import androidx.recyclerview.widget.RecyclerView

import com.coderlab.cricketkotlindemo.paging.adapter.ItemAdapterDRx
import com.coderlab.cricketkotlindemo.paging.model.Item
import com.coderlab.cricketkotlindemo.paging.viewmodel.ItemViewModel
import com.sumitzway.drxpaging.DRxLinearLayoutManager
import com.sumitzway.drxpaging.DRxPagedList


class PagingActivity : AppCompatActivity() {
    //    https://code.luasoftware.com/tutorials/android/android-data-binding-for-recyclerview-with-livedata/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paging)

        //setting up recyclerview
        val recyclerView: RecyclerView = findViewById(R.id.recyclerview)
        recyclerView.layoutManager = DRxLinearLayoutManager(this)
        recyclerView.setHasFixedSize(true)
        //getting our ItemViewModel
        val adapter = ItemAdapterDRx(this)
        recyclerView.setAdapter(adapter)

        val itemViewModel = ViewModelProvider(this, object :
            ViewModelProvider.NewInstanceFactory() {
            override fun <T : ViewModel?> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ItemViewModel() as T
            }
        }).get(ItemViewModel::class.java)

        //creating the Adapter

        //observing the itemDRxPagedList from view model
        itemViewModel.itemDRxPagedList.observe(this,
            Observer<DRxPagedList<Item>> { items ->
                //in case of any changes
                //submitting the items to adapter
                adapter.submitList(items)
            })
        //setting the adapter

    }


}