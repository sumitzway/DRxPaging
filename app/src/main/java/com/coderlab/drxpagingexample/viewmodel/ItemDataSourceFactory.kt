package com.coderlab.cricketkotlindemo.paging.viewmodel

import androidx.lifecycle.MutableLiveData
import com.coderlab.cricketkotlindemo.custom.DRxDataSource
import com.coderlab.cricketkotlindemo.custom.DRxPageKeyedDataSource

import com.coderlab.cricketkotlindemo.paging.model.Item


class ItemDataSourceFactory : DRxDataSource.Factory<Int, Item>() {
    //creating the mutable live data
    //getter for itemlivedatasource
    val itemLiveDataSource = MutableLiveData<DRxPageKeyedDataSource<Int, Item>>()

    override fun create(): DRxDataSource<Int, Item> {
        //getting our data source object
        val itemDataSource = ItemDataSourceDRx()

        //posting the datasource to get the values
        itemLiveDataSource.postValue(itemDataSource)

        //returning the datasource
        return itemDataSource
    }
}