package com.coderlab.cricketkotlindemo.paging.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.coderlab.cricketkotlindemo.custom.DRxLivePagedListBuilder
import com.coderlab.cricketkotlindemo.custom.DRxPageKeyedDataSource
import com.coderlab.cricketkotlindemo.custom.DRxPagedList

import com.coderlab.cricketkotlindemo.paging.model.Item


class ItemViewModel : ViewModel() {

    //creating livedata for DRxPagedList  and PagedKeyedDataSource
    internal var itemDRxPagedList: LiveData<DRxPagedList<Item>>
    internal var liveDataSourceDRx: LiveData<DRxPageKeyedDataSource<Int, Item>>

    //constructor
    init {
        //getting our data source factory
        val itemDataSourceFactory = ItemDataSourceFactory()

        //getting the live data source from data source factory
        liveDataSourceDRx = itemDataSourceFactory.itemLiveDataSource

        //Getting DRxPagedList config
        val pagedListConfig = DRxPagedList.Config.Builder()
            .setEnablePlaceholders(false)
            .setPageSize(ItemDataSourceDRx.PAGE_SIZE).build()

        //Building the paged list
        itemDRxPagedList = DRxLivePagedListBuilder(itemDataSourceFactory, pagedListConfig)
            .build()
    }
}