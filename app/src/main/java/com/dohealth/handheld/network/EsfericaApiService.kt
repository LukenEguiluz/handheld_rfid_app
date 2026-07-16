package com.dohealth.handheld.network

import com.dohealth.handheld.data.esferica.EsfericaClientDto
import com.dohealth.handheld.data.esferica.EsfericaWarehouseDto
import com.dohealth.handheld.data.esferica.ReconcileRequestDto
import com.dohealth.handheld.data.esferica.ReconcileResponseDto
import com.dohealth.handheld.data.esferica.RfidLookupBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface EsfericaApiService {

    @GET("clients")
    suspend fun getClients(): Response<List<EsfericaClientDto>>

    @GET("warehouses")
    suspend fun getWarehouses(): Response<List<EsfericaWarehouseDto>>

    @GET("inventory")
    suspend fun getInventory(
        @Query("client_id") clientId: String,
        @Query("warehouse_id") warehouseId: String,
        @Query("status") status: String = "DISPONIBLE",
    ): Response<ResponseBody>

    @POST("inventory/reconcile")
    suspend fun reconcile(@Body body: ReconcileRequestDto): Response<ReconcileResponseDto>

    @POST("rfid/lookup")
    suspend fun rfidLookup(@Body body: RfidLookupBody): Response<ResponseBody>
}
