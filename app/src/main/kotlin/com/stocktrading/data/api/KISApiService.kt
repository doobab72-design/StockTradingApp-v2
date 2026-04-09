package com.stocktrading.data.api

import com.stocktrading.data.model.BalanceResponse
import com.stocktrading.data.model.DailyPriceResponse
import com.stocktrading.data.model.OrderRequest
import com.stocktrading.data.model.OrderResponse
import retrofit2.Response
import retrofit2.http.*

/**
 * KIS(한국투자증권) Open API Retrofit 인터페이스
 * Base URL: https://openapi.koreainvestment.com:9443
 * 모의투자 Base URL: https://openapivts.koreainvestment.com:29443
 */
interface KISApiService {

    // ============================
    // 인증 관련
    // ============================

    /**
     * OAuth 2.0 접근 토큰 발급
     * POST /oauth2/tokenP
     */
    @POST("oauth2/tokenP")
    suspend fun getAccessToken(
        @Body request: TokenRequest
    ): Response<TokenResponse>

    /**
     * 접근 토큰 폐기
     * POST /oauth2/revokeP
     */
    @POST("oauth2/revokeP")
    suspend fun revokeToken(
        @Body request: RevokeTokenRequest
    ): Response<RevokeTokenResponse>

    // ============================
    // 시세 조회
    // ============================

    /**
     * 주식 일별 시세 조회
     * GET /uapi/domestic-stock/v1/quotations/inquire-daily-price
     *
     * @param authorization Bearer {access_token}
     * @param appkey 앱 키
     * @param appsecret 앱 시크릿
     * @param trId 거래 ID (실전: FHKST01010400, 모의: FHKST01010400)
     * @param stockCode 종목 코드
     * @param periodDivCode 기간 구분 코드 (D:일, W:주, M:월)
     * @param adjustPrice 수정주가 여부 (0:미반영, 1:반영)
     */
    @GET("uapi/domestic-stock/v1/quotations/inquire-daily-price")
    suspend fun getDailyPrice(
        @Header("authorization") authorization: String,
        @Header("appkey") appkey: String,
        @Header("appsecret") appsecret: String,
        @Header("tr_id") trId: String,
        @Query("FID_COND_MRKT_DIV_CODE") marketDivCode: String,
        @Query("FID_INPUT_ISCD") stockCode: String,
        @Query("FID_PERIOD_DIV_CODE") periodDivCode: String,
        @Query("FID_ORG_ADJ_PRC") adjustPrice: String
    ): Response<DailyPriceResponse>

    /**
     * 주식 현재가 조회
     * GET /uapi/domestic-stock/v1/quotations/inquire-price
     */
    @GET("uapi/domestic-stock/v1/quotations/inquire-price")
    suspend fun getCurrentPrice(
        @Header("authorization") authorization: String,
        @Header("appkey") appkey: String,
        @Header("appsecret") appsecret: String,
        @Header("tr_id") trId: String,
        @Query("FID_COND_MRKT_DIV_CODE") marketDivCode: String,
        @Query("FID_INPUT_ISCD") stockCode: String
    ): Response<CurrentPriceResponse>

    // ============================
    // 계좌/잔고 조회
    // ============================

    /**
     * 주식 잔고 조회 (보유 종목)
     * GET /uapi/domestic-stock/v1/trading/inquire-balance
     *
     * @param trId 실전: TTTC8434R, 모의: VTTC8434R
     */
    @GET("uapi/domestic-stock/v1/trading/inquire-balance")
    suspend fun getBalance(
        @Header("authorization") authorization: String,
        @Header("appkey") appkey: String,
        @Header("appsecret") appsecret: String,
        @Header("tr_id") trId: String,
        @Query("CANO") accountNo: String,
        @Query("ACNT_PRDT_CD") accountProductCode: String,
        @Query("AFHR_FLPR_YN") afterHoursFlprYn: String = "N",
        @Query("OFL_YN") offlineYn: String = "N",
        @Query("INQR_DVSN") inquiryDivision: String = "02",
        @Query("UNPR_DVSN") unitPriceDivision: String = "01",
        @Query("FUND_STTL_ICLD_YN") fundSettlementIncludeYn: String = "N",
        @Query("FNCG_AMT_AUTO_RDPT_YN") fundAutoRedemptionYn: String = "N",
        @Query("PRCS_DVSN") processDivision: String = "01",
        @Query("CTX_AREA_FK100") continuousKey: String = "",
        @Query("CTX_AREA_NK100") continuousKey2: String = ""
    ): Response<BalanceResponse>

    // ============================
    // 주문
    // ============================

    /**
     * 주식 현금 매수 주문
     * POST /uapi/domestic-stock/v1/trading/order-cash
     *
     * @param trId 실전 매수: TTTC0802U, 모의 매수: VTTC0802U
     */
    @POST("uapi/domestic-stock/v1/trading/order-cash")
    suspend fun buyStock(
        @Header("authorization") authorization: String,
        @Header("appkey") appkey: String,
        @Header("appsecret") appsecret: String,
        @Header("tr_id") trId: String,
        @Header("hashkey") hashkey: String,
        @Body request: OrderRequest
    ): Response<OrderResponse>

    /**
     * 주식 현금 매도 주문
     * POST /uapi/domestic-stock/v1/trading/order-cash
     *
     * @param trId 실전 매도: TTTC0801U, 모의 매도: VTTC0801U
     */
    @POST("uapi/domestic-stock/v1/trading/order-cash")
    suspend fun sellStock(
        @Header("authorization") authorization: String,
        @Header("appkey") appkey: String,
        @Header("appsecret") appsecret: String,
        @Header("tr_id") trId: String,
        @Header("hashkey") hashkey: String,
        @Body request: OrderRequest
    ): Response<OrderResponse>

    /**
     * Hashkey 발급 (주문 요청 시 필요)
     * POST /uapi/hashkey
     */
    @POST("uapi/hashkey")
    suspend fun getHashkey(
        @Header("appkey") appkey: String,
        @Header("appsecret") appsecret: String,
        @Body body: Map<String, String>
    ): Response<HashkeyResponse>
}

// ============================
// 추가 DTO
// ============================

data class TokenRequest(
    val grant_type: String = "client_credentials",
    val appkey: String,
    val appsecret: String
)

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val access_token_token_expired: String
)

data class RevokeTokenRequest(
    val appkey: String,
    val appsecret: String,
    val token: String
)

data class RevokeTokenResponse(
    val code: Int,
    val message: String
)

data class CurrentPriceResponse(
    val rt_cd: String,
    val msg_cd: String,
    val msg1: String,
    val output: CurrentPriceOutput?
)

data class CurrentPriceOutput(
    val stck_prpr: String,    // 주식 현재가
    val prdy_vrss: String,    // 전일 대비
    val prdy_ctrt: String,    // 전일 대비율
    val acml_vol: String,     // 누적 거래량
    val hts_kor_isnm: String  // 종목명
)

data class HashkeyResponse(
    val HASH: String
)
