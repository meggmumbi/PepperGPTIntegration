package com.example.peppergptintegration

import retrofit2.http.GET
import retrofit2.http.Header

interface ChildrenApiService {
    @GET("children/")
    suspend fun getChildren(
        @Header("Authorization") token: String
    ): List<Child>
}