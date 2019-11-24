package com.coderlab.cricketkotlindemo.paging.rest

import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory


class RetrofitClient private constructor() {
    private val retrofit: Retrofit

    val api: Api
        get() = retrofit.create(Api::class.java)


    init {
        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }

    companion object {
        private val BASE_URL = "https://api.stackexchange.com/2.1/"
        private var mInstance: RetrofitClient? = null
        val instance: RetrofitClient
            @Synchronized get() {
                if (mInstance == null) {
                    mInstance = RetrofitClient()
                }
                return mInstance!!
            }
    }
}