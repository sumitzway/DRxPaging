package com.coderlab.cricketkotlindemo.paging.rest

import com.coderlab.cricketkotlindemo.paging.model.StackApiResponse
import com.coderlab.cricketkotlindemo.realtimesearch.model.SearchItem
import io.reactivex.Observable
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url


interface Api {

    @GET("answers")
    fun getAnswers(@Query("page") page: Int, @Query("pagesize") pagesize: Int, @Query("site") site: String): Call<StackApiResponse>

    @GET
    fun search(@Url url: String, @Query("search") query: String): Observable<List<SearchItem>>
}