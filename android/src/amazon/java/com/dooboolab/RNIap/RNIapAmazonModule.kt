package com.dooboolab.RNIap

import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.FulfillmentResult
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableNativeArray
import java.util.*

class RNIapAmazonModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {
    override fun getName(): String {
        return TAG
    }

    @ReactMethod
    fun initConnection(promise: Promise) {
        UiThreadUtil.runOnUiThread {
            PurchasingService.registerListener(
                reactApplicationContext,
                RNIapAmazonListener(reactApplicationContext)
            )
        }
        val lifecycleEventListener: LifecycleEventListener = object : LifecycleEventListener {
            override fun onHostResume() {
                PurchasingService.getUserData()
                PurchasingService.getPurchaseUpdates(false)
            }

            override fun onHostPause() {}
            override fun onHostDestroy() {}
        }
        reactApplicationContext.addLifecycleEventListener(lifecycleEventListener)
        promise.resolve(true)
    }

    @ReactMethod
    fun endConnection(promise: Promise) {
        promise.resolve(true)
    }

    @ReactMethod
    fun refreshItems(promise: Promise) {
        // TODO: Determine what needs to happen here on Amazon, if anything.
        // This is called from RNIap.consumeAllItemsAndroid()
        // Android only
        // Consume all items so they are able to buy again.
        promise.resolve(true)
    }

    @ReactMethod
    fun getUser(promise: Promise) {
        DoobooUtils.addPromiseForKey(PROMISE_GET_USER_DATA, promise)
    }

    @ReactMethod
    fun getItemsByType(type: String?, skuArr: ReadableArray, promise: Promise) {
        val productSkus: MutableSet<String?> = HashSet()
        var ii = 0
        val skuSize = skuArr.size()
        while (ii < skuSize) {
            productSkus.add(skuArr.getString(ii))
            ii++
        }
        DoobooUtils.addPromiseForKey(PROMISE_GET_PRODUCT_DATA, promise)
    }

    @ReactMethod
    fun getAvailableItemsByType(type: String?, promise: Promise) {
        DoobooUtils.addPromiseForKey(PROMISE_QUERY_AVAILABLE_ITEMS, promise)
        PurchasingService.getPurchaseUpdates(true)
    }

    @ReactMethod
    fun getPurchaseHistoryByType(type: String?, promise: Promise) {
        // TODO
        val items = WritableNativeArray()
        promise.resolve(items)
    }

    @ReactMethod
    fun buyItemByType(
        type: String?,
        sku: String?,
        oldSku: String?,
        purchaseToken: String?,
        prorationMode: Int?,
        obfuscatedAccountId: String?,
        obfuscatedProfileId: String?,
        promise: Promise
    ) {
        DoobooUtils.addPromiseForKey(PROMISE_BUY_ITEM, promise)
    }

    @ReactMethod
    fun acknowledgePurchase(
        token: String?, developerPayLoad: String?, promise: Promise
    ) {
        PurchasingService.notifyFulfillment(token, FulfillmentResult.FULFILLED)
        promise.resolve(true)
    }

    @ReactMethod
    fun consumeProduct(
        token: String?, developerPayLoad: String?, promise: Promise
    ) {
        PurchasingService.notifyFulfillment(token, FulfillmentResult.FULFILLED)
        promise.resolve(true)
    }

    private fun sendUnconsumedPurchases(promise: Promise) {
        PurchasingService.getPurchaseUpdates(false)
        DoobooUtils.addPromiseForKey(PROMISE_QUERY_PURCHASES, promise)
    }

    @ReactMethod
    fun startListening(promise: Promise) {
        sendUnconsumedPurchases(promise)
    }

    companion object {
        const val TAG = "RNIapAmazonModule"

        const val PROMISE_BUY_ITEM = "PROMISE_BUY_ITEM"
        const val PROMISE_GET_PRODUCT_DATA = "PROMISE_GET_PRODUCT_DATA"
        const val PROMISE_QUERY_PURCHASES = "PROMISE_QUERY_PURCHASES"
        const val PROMISE_QUERY_AVAILABLE_ITEMS = "PROMISE_QUERY_AVAILABLE_ITEMS"
        const val PROMISE_GET_USER_DATA = "PROMISE_GET_USER_DATA"
    }
}