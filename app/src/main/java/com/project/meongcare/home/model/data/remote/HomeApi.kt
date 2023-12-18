package com.project.meongcare.home.model.data.remote

import com.project.meongcare.home.model.entities.HomeGetDogListResponse
import com.project.meongcare.home.model.entities.HomeGetProfileResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header

interface HomeApi {
    // 유저 프로필 받아오는 api
    @GET("/member/profile")
    suspend fun getUserProfile(
        @Header("AccessToken") accessToken: String,
    ): Response<HomeGetProfileResponse>

    // 강아지 목록 받아오는 api
    @GET("/dog")
    suspend fun getDogList(
        @Header("AccessToken") accessToken: String,
    ): Response<HomeGetDogListResponse>
}
