package com.kimseojin.instadownloader.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class InterstitialAdManager(private val context: Context) {
    
    companion object {
        private const val TAG = "InterstitialAdManager"
        
        // 테스트용 (개발 중에 사용) - 현재 활성화
        private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        
        // 실제 전면 광고 단위 ID (배포 시에만 사용)
        // private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-7064030534194691/3162337341"
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    
    init {
        loadAd()
    }
    
    fun loadAd() {
        if (isLoading) {
            Log.d(TAG, "전면 광고가 이미 로딩 중입니다.")
            return
        }
        
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "전면 광고 로드 성공")
                    interstitialAd = ad
                    isLoading = false
                }
                
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "전면 광고 로드 실패: ${adError.message}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }
    
    fun showAd(activity: Activity, onAdDismissed: (() -> Unit)? = null) {
        if (interstitialAd != null) {
            Log.d(TAG, "전면 광고 표시")
            
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "전면 광고 닫힘")
                    interstitialAd = null
                    onAdDismissed?.invoke()
                    // 다음 광고 미리 로드
                    loadAd()
                }
                
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "전면 광고 표시 실패: ${adError.message}")
                    interstitialAd = null
                    onAdDismissed?.invoke()
                    // 실패 시에도 다음 광고 미리 로드
                    loadAd()
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "전면 광고 표시됨")
                }
            }
            
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "전면 광고가 아직 로드되지 않음")
            onAdDismissed?.invoke()
            // 광고가 없을 때도 로드 시도
            loadAd()
        }
    }
    
    fun isAdReady(): Boolean {
        return interstitialAd != null
    }
}