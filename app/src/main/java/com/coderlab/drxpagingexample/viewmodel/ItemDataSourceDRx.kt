package com.coderlab.cricketkotlindemo.paging.viewmodel

import com.coderlab.cricketkotlindemo.custom.DRxPageKeyedDataSource
import com.coderlab.cricketkotlindemo.paging.model.Item
import com.coderlab.cricketkotlindemo.paging.model.StackApiResponse
import com.coderlab.cricketkotlindemo.paging.rest.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class ItemDataSourceDRx() : DRxPageKeyedDataSource<Int, Item>() {


    //this will be called once to load the initial data
    override fun loadInitial(
        params: DRxPageKeyedDataSource.LoadInitialParams<Int>,
        callback: DRxPageKeyedDataSource.LoadInitialCallback<Int, Item>
    ) {
        RetrofitClient.instance.api.getAnswers(FIRST_PAGE, PAGE_SIZE, SITE_NAME)
            .enqueue(object : Callback<StackApiResponse> {
                override fun onResponse(
                    call: Call<StackApiResponse>,
                    response: Response<StackApiResponse>
                ) {
                    if (response.body() != null && response.body()?.items?.isNotEmpty() == true) {
                        callback.onResult(response.body()!!.items!!, null, FIRST_PAGE + 1)
                    }
                }

                override fun onFailure(call: Call<StackApiResponse>, t: Throwable) {
                    t.printStackTrace()
                }
            })
    }

    //this will load the previous page
    override fun loadBefore(
        params: LoadParams<Int>,
        callback: LoadCallback<Int, Item>
    ) {
        RetrofitClient.instance
            .api.getAnswers(params.key, PAGE_SIZE, SITE_NAME)
            .enqueue(object : Callback<StackApiResponse> {
                override fun onResponse(
                    call: Call<StackApiResponse>,
                    response: Response<StackApiResponse>
                ) {

                    //if the current page is greater than one
                    //we are decrementing the page number
                    //else there is no previous page
                    val adjacentKey = if (params.key > 1) params.key - 1 else null
                    if (response.body() != null) {

                        //passing the loaded data
                        //and the previous page key
                        callback.onResult(response.body()!!.items!!, adjacentKey)
                    }
                }

                override fun onFailure(call: Call<StackApiResponse>, t: Throwable) {

                }
            })
    }

    //this will load the next page
    override fun loadAfter(
        params: LoadParams<Int>,
        callback: LoadCallback<Int, Item>
    ) {
        RetrofitClient.instance
            .api
            .getAnswers(params.key, PAGE_SIZE, SITE_NAME)
            .enqueue(object : Callback<StackApiResponse> {
                override fun onResponse(
                    call: Call<StackApiResponse>,
                    response: Response<StackApiResponse>
                ) {

                    if (response.body() != null) {
                        //if the response has next page
                        //incrementing the next page number
                        val key = if (response.body()!!.has_more) params.key + 1 else null

                        //passing the loaded data and next page value
                        callback.onResult(response.body()!!.items!!, key)
                    }
                }

                override fun onFailure(call: Call<StackApiResponse>, t: Throwable) {

                }
            })
    }

    companion object {

        //the size of a page that we want
        val PAGE_SIZE = 5

        //we will start from the first page which is 1
        private val FIRST_PAGE = 1

        //we need to fetch from stackoverflow
        private val SITE_NAME = "stackoverflow"
    }
}