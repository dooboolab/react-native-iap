package com.dooboolab.RNIap

import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ObjectAlreadyConsumedException
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.math.BigDecimal
import java.util.*

class RNIapModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext),
    PurchasesUpdatedListener {
    val TAG = "RNIapModule"
    private val reactContext: ReactContext
    private var billingClientCache: BillingClient? = null
    private val skus: MutableList<SkuDetails>
    override fun getName(): String {
        return "RNIapModule"
    }

    private fun ensureConnection(promise: Promise, callback: (billingClient: BillingClient)-> Unit) {
        val billingClient = billingClientCache
        if (billingClient != null && billingClient.isReady) {
            callback(billingClient)
            return
        }
        promise.reject(DoobooUtils.E_NOT_PREPARED, "Not initialized, Please call initConnection()")
    }

    @ReactMethod
    fun initConnection(promise: Promise) {
        if (billingClientCache != null) {
            Log.i(
                TAG,
                "Already initialized, you should only call initConnection() once when your app starts"
            )
        }
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(reactContext)
            != ConnectionResult.SUCCESS
        ) {
            Log.i(TAG, "Google Play Services are not available on this device")
            //      promise.reject(
            //          PlayUtils.E_PLAY_SERVICES_UNAVAILABLE,
            //          "Google Play Services are not available on this device");
            //      return;
        }
        val billingClient =
            BillingClient.newBuilder(reactContext).enablePendingPurchases().setListener(this)
                .build()
        billingClientCache = billingClient
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    val responseCode = billingResult.responseCode
                    try {
                        if (responseCode == BillingClient.BillingResponseCode.OK) {
                            promise.resolve(true)
                        } else {
                            PlayUtils
                                .rejectPromiseWithBillingError(promise, responseCode)
                        }
                    } catch (oce: ObjectAlreadyConsumedException) {
                        Log.e(TAG, oce.message!!)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    try {
                        promise.reject("initConnection", "Billing service disconnected")
                    } catch (oce: ObjectAlreadyConsumedException) {
                        Log.e(TAG, oce.message!!)
                    }
                }
            })
    }

    @ReactMethod
    fun endConnection(promise: Promise) {
        if (billingClientCache != null) {
            billingClientCache = try {
                billingClientCache!!.endConnection()
                null
            } catch (e: Exception) {
                promise.reject("endConnection", e.message)
                return
            }
        }
        try {
            promise.resolve(true)
        } catch (oce: ObjectAlreadyConsumedException) {
            Log.e(TAG, oce.message!!)
        }
    }

    private fun consumeItems(
        purchases: List<Purchase>,
        promise: Promise,
        expectedResponseCode: Int = BillingClient.BillingResponseCode.OK
    ) {
        for (purchase in purchases) {
            ensureConnection(
                promise
            ) { billingClient: BillingClient ->
                val consumeParams =
                    ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                val listener =
                    ConsumeResponseListener { billingResult: BillingResult, outToken: String? ->
                        if (billingResult.responseCode != expectedResponseCode) {
                            PlayUtils
                                .rejectPromiseWithBillingError(
                                    promise,
                                    billingResult.responseCode
                                )
                            return@ConsumeResponseListener
                        }
                        try {
                            promise.resolve(true)
                        } catch (oce: ObjectAlreadyConsumedException) {
                            promise.reject(oce.message)
                        }
                    }
                billingClient.consumeAsync(consumeParams, listener)
            }
        }
    }

    @ReactMethod
    fun refreshItems(promise: Promise) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            billingClient.queryPurchasesAsync(
                BillingClient.SkuType.INAPP
            ) { billingResult: BillingResult?, list: List<Purchase>? ->
                if (list == null || list.size == 0) {
                    promise.reject("refreshItem", "No purchases found")
                    return@queryPurchasesAsync
                }
                consumeItems(list, promise)
            }
        }
    }

    @ReactMethod
    fun flushFailedPurchasesCachedAsPending(promise: Promise) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            val array = WritableNativeArray()
            billingClient.queryPurchasesAsync(
                BillingClient.SkuType.INAPP
            ) { billingResult: BillingResult?, list: List<Purchase>? ->
                if (list == null) {
                    // No purchases found
                    promise.resolve(false)
                    return@queryPurchasesAsync
                }
                val pendingPurchases: MutableList<Purchase> =
                    ArrayList()
                for (purchase in list) {
                    // we only want to try to consume PENDING items, in order to force cache-refresh
                    // for
                    // them
                    if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                        pendingPurchases.add(purchase)
                    }
                }
                if (pendingPurchases.size == 0) {
                    promise.resolve(false)
                    return@queryPurchasesAsync
                }
                consumeItems(
                    pendingPurchases, promise, BillingClient.BillingResponseCode.ITEM_NOT_OWNED
                )
            }
        }
    }

    @ReactMethod
    fun getItemsByType(type: String?, skuArr: ReadableArray, promise: Promise) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            val skuList = ArrayList<String?>()
            for (i in 0 until skuArr.size()) {
                skuList.add(skuArr.getString(i))
            }
            val params = SkuDetailsParams.newBuilder()
            params.setSkusList(skuList).setType(type!!)
            billingClient.querySkuDetailsAsync(
                params.build()
            ) { billingResult: BillingResult, skuDetailsList: List<SkuDetails>? ->
                Log.d(TAG, "responseCode: " + billingResult.responseCode)
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    PlayUtils.rejectPromiseWithBillingError(promise, billingResult.responseCode)
                    return@querySkuDetailsAsync
                }
                if (skuDetailsList != null) {
                    for (sku in skuDetailsList) {
                        if (!skus.contains(sku)) {
                            skus.add(sku)
                        }
                    }
                }
                val items = WritableNativeArray()
                for (skuDetails in skuDetailsList!!) {
                    val item = Arguments.createMap()
                    item.putString("productId", skuDetails.sku)
                    val introductoryPriceMicros = skuDetails.introductoryPriceAmountMicros
                    val priceAmountMicros = skuDetails.priceAmountMicros
                    // Use valueOf instead of constructors.
                    // See:
                    // https://www.javaworld.com/article/2073176/caution--double-to-bigdecimal-in-java.html
                    val priceAmount = BigDecimal.valueOf(priceAmountMicros)
                    val introductoryPriceAmount = BigDecimal.valueOf(introductoryPriceMicros)
                    val microUnitsDivisor = BigDecimal.valueOf(1000000)
                    val price = priceAmount.divide(microUnitsDivisor).toString()
                    val introductoryPriceAsAmountAndroid =
                        introductoryPriceAmount.divide(microUnitsDivisor).toString()
                    item.putString("price", price)
                    item.putString("currency", skuDetails.priceCurrencyCode)
                    item.putString("type", skuDetails.type)
                    item.putString("localizedPrice", skuDetails.price)
                    item.putString("title", skuDetails.title)
                    item.putString("description", skuDetails.description)
                    item.putString("introductoryPrice", skuDetails.introductoryPrice)
                    item.putString("typeAndroid", skuDetails.type)
                    item.putString("packageNameAndroid", skuDetails.zzc())
                    item.putString("originalPriceAndroid", skuDetails.originalPrice)
                    item.putString("subscriptionPeriodAndroid", skuDetails.subscriptionPeriod)
                    item.putString("freeTrialPeriodAndroid", skuDetails.freeTrialPeriod)
                    item.putString(
                        "introductoryPriceCyclesAndroid",
                        skuDetails.introductoryPriceCycles.toString()
                    )
                    item.putString(
                        "introductoryPricePeriodAndroid", skuDetails.introductoryPricePeriod
                    )
                    item.putString(
                        "introductoryPriceAsAmountAndroid", introductoryPriceAsAmountAndroid
                    )
                    item.putString("iconUrl", skuDetails.iconUrl)
                    item.putString("originalJson", skuDetails.originalJson)
                    val originalPriceAmountMicros =
                        BigDecimal.valueOf(skuDetails.originalPriceAmountMicros)
                    val originalPrice =
                        originalPriceAmountMicros.divide(microUnitsDivisor).toString()
                    item.putString("originalPrice", originalPrice)
                    items.pushMap(item)
                }
                try {
                    promise.resolve(items)
                } catch (oce: ObjectAlreadyConsumedException) {
                    Log.e(TAG, oce.message!!)
                }
            }
        }
    }

    @ReactMethod
    fun getAvailableItemsByType(type: String, promise: Promise) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            val items = WritableNativeArray()
            billingClient.queryPurchasesAsync(
                if (type == "subs") BillingClient.SkuType.SUBS else BillingClient.SkuType.INAPP
            ) { billingResult: BillingResult?, purchases: List<Purchase>? ->
                if (purchases != null) {
                    for (i in purchases.indices) {
                        val purchase = purchases[i]
                        val item = WritableNativeMap()
                        item.putString("productId", purchase.skus[0])
                        item.putString("transactionId", purchase.orderId)
                        item.putDouble("transactionDate", purchase.purchaseTime.toDouble())
                        item.putString("transactionReceipt", purchase.originalJson)
                        item.putString("orderId", purchase.orderId)
                        item.putString("purchaseToken", purchase.purchaseToken)
                        item.putString("developerPayloadAndroid", purchase.developerPayload)
                        item.putString("signatureAndroid", purchase.signature)
                        item.putInt("purchaseStateAndroid", purchase.purchaseState)
                        item.putBoolean("isAcknowledgedAndroid", purchase.isAcknowledged)
                        item.putString("packageNameAndroid", purchase.packageName)
                        item.putString(
                            "obfuscatedAccountIdAndroid",
                            purchase.accountIdentifiers!!.obfuscatedAccountId
                        )
                        item.putString(
                            "obfuscatedProfileIdAndroid",
                            purchase.accountIdentifiers!!.obfuscatedProfileId
                        )
                        if (type == BillingClient.SkuType.SUBS) {
                            item.putBoolean("autoRenewingAndroid", purchase.isAutoRenewing)
                        }
                        items.pushMap(item)
                    }
                }
                try {
                    promise.resolve(items)
                } catch (oce: ObjectAlreadyConsumedException) {
                    Log.e(TAG, oce.message!!)
                }
            }
        }
    }

    @ReactMethod
    fun getPurchaseHistoryByType(type: String, promise: Promise) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            billingClient.queryPurchaseHistoryAsync(
                if (type == "subs") BillingClient.SkuType.SUBS else BillingClient.SkuType.INAPP
            ) { billingResult, purchaseHistoryRecordList ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    PlayUtils.rejectPromiseWithBillingError(promise, billingResult.responseCode)
                    return@queryPurchaseHistoryAsync
                }
                Log.d(TAG, purchaseHistoryRecordList.toString())
                val items = Arguments.createArray()
                for (i in purchaseHistoryRecordList!!.indices) {
                    val item = Arguments.createMap()
                    val purchase = purchaseHistoryRecordList[i]
                    item.putString("productId", purchase.skus[0])
                    item.putDouble("transactionDate", purchase.purchaseTime.toDouble())
                    item.putString("transactionReceipt", purchase.originalJson)
                    item.putString("purchaseToken", purchase.purchaseToken)
                    item.putString("dataAndroid", purchase.originalJson)
                    item.putString("signatureAndroid", purchase.signature)
                    item.putString("developerPayload", purchase.developerPayload)
                    items.pushMap(item)
                }
                try {
                    promise.resolve(items)
                } catch (oce: ObjectAlreadyConsumedException) {
                    Log.e(TAG, oce.message!!)
                }
            }
        }
    }

    @ReactMethod
    fun buyItemByType(
        type: String,
        sku: String,
        oldPurchaseToken: String?,
        purchaseToken: String?,
        prorationMode: Int?,
        obfuscatedAccountId: String?,
        obfuscatedProfileId: String?,
        promise: Promise
    ) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject(DoobooUtils.E_UNKNOWN, "getCurrentActivity returned null")
            return
        }
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            DoobooUtils.addPromiseForKey(
                PROMISE_BUY_ITEM, promise
            )
            val builder = BillingFlowParams.newBuilder()
            var selectedSku: SkuDetails? = null
            for (skuDetail in skus) {
                if (skuDetail.sku == sku) {
                    selectedSku = skuDetail
                    break
                }
            }
            if (selectedSku == null) {
                val debugMessage =
                    "The sku was not found. Please fetch products first by calling getItems"
                val error = Arguments.createMap()
                error.putString("debugMessage", debugMessage)
                error.putString("code", PROMISE_BUY_ITEM)
                error.putString("message", debugMessage)
                error.putString("productId", sku)
                sendEvent(reactContext, "purchase-error", error)
                promise.reject(PROMISE_BUY_ITEM, debugMessage)
                return@ensureConnection
            }
            builder.setSkuDetails(selectedSku)
            val subscriptionUpdateParams = SubscriptionUpdateParams.newBuilder()
            if (oldPurchaseToken != null) {
                subscriptionUpdateParams.setOldSkuPurchaseToken(oldPurchaseToken)
            }
            if (obfuscatedAccountId != null) {
                builder.setObfuscatedAccountId(obfuscatedAccountId)
            }
            if (obfuscatedProfileId != null) {
                builder.setObfuscatedProfileId(obfuscatedProfileId)
            }
            if (prorationMode != null && prorationMode != -1) {
                if (prorationMode
                    == BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE
                ) {
                    subscriptionUpdateParams.setReplaceSkusProrationMode(
                        BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_PRORATED_PRICE
                    )
                    if (type != BillingClient.SkuType.SUBS) {
                        val debugMessage =
                            ("IMMEDIATE_AND_CHARGE_PRORATED_PRICE for proration mode only works in"
                                    + " subscription purchase.")
                        val error = Arguments.createMap()
                        error.putString("debugMessage", debugMessage)
                        error.putString("code", PROMISE_BUY_ITEM)
                        error.putString("message", debugMessage)
                        error.putString("productId", sku)
                        sendEvent(reactContext, "purchase-error", error)
                        promise.reject(PROMISE_BUY_ITEM, debugMessage)
                        return@ensureConnection
                    }
                } else if (prorationMode
                    == BillingFlowParams.ProrationMode.IMMEDIATE_WITHOUT_PRORATION
                ) {
                    subscriptionUpdateParams.setReplaceSkusProrationMode(
                        BillingFlowParams.ProrationMode.IMMEDIATE_WITHOUT_PRORATION
                    )
                } else if (prorationMode == BillingFlowParams.ProrationMode.DEFERRED) {
                    subscriptionUpdateParams.setReplaceSkusProrationMode(
                        BillingFlowParams.ProrationMode.DEFERRED
                    )
                } else if (prorationMode
                    == BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION
                ) {
                    subscriptionUpdateParams.setReplaceSkusProrationMode(
                        BillingFlowParams.ProrationMode.IMMEDIATE_WITHOUT_PRORATION
                    )
                } else if (prorationMode
                    == BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE
                ) {
                    subscriptionUpdateParams.setReplaceSkusProrationMode(
                        BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE
                    )
                } else {
                    subscriptionUpdateParams.setReplaceSkusProrationMode(
                        BillingFlowParams.ProrationMode.UNKNOWN_SUBSCRIPTION_UPGRADE_DOWNGRADE_POLICY
                    )
                }
            }
            val flowParams = builder.build()
            val billingResult = billingClient.launchBillingFlow(activity, flowParams)
            val errorData: Array<String?> = PlayUtils
                .getBillingResponseData(billingResult.responseCode)
        }
    }

    @ReactMethod
    fun acknowledgePurchase(
        token: String?, developerPayLoad: String?, promise: Promise
    ) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            val acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(
                    token!!
                ).build()
            billingClient.acknowledgePurchase(
                acknowledgePurchaseParams
            ) { billingResult: BillingResult ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    PlayUtils
                        .rejectPromiseWithBillingError(promise, billingResult.responseCode)
                }
                try {
                    val map = Arguments.createMap()
                    map.putInt("responseCode", billingResult.responseCode)
                    map.putString("debugMessage", billingResult.debugMessage)
                    val errorData: Array<String?> = PlayUtils
                        .getBillingResponseData(billingResult.responseCode)
                    map.putString("code", errorData[0])
                    map.putString("message", errorData[1])
                    promise.resolve(map)
                } catch (oce: ObjectAlreadyConsumedException) {
                    Log.e(TAG, oce.message!!)
                }
            }
        }
    }

    @ReactMethod
    fun consumeProduct(
        token: String?, developerPayLoad: String?, promise: Promise
    ) {
        val params = ConsumeParams.newBuilder().setPurchaseToken(token!!).build()
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            billingClient.consumeAsync(
                params
            ) { billingResult: BillingResult, purchaseToken: String? ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    PlayUtils
                        .rejectPromiseWithBillingError(promise, billingResult.responseCode)
                }
                try {
                    val map = Arguments.createMap()
                    map.putInt("responseCode", billingResult.responseCode)
                    map.putString("debugMessage", billingResult.debugMessage)
                    val errorData: Array<String?> = PlayUtils
                        .getBillingResponseData(billingResult.responseCode)
                    map.putString("code", errorData[0])
                    map.putString("message", errorData[1])
                    promise.resolve(map)
                } catch (oce: ObjectAlreadyConsumedException) {
                    promise.reject(oce.message)
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        val responseCode = billingResult.responseCode
        if (responseCode != BillingClient.BillingResponseCode.OK) {
            val error = Arguments.createMap()
            error.putInt("responseCode", responseCode)
            error.putString("debugMessage", billingResult.debugMessage)
            val errorData: Array<String?> =
                PlayUtils.getBillingResponseData(responseCode)
            error.putString("code", errorData[0])
            error.putString("message", errorData[1])
            sendEvent(reactContext, "purchase-error", error)
            if (responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                PlayUtils.rejectPromisesWithBillingError(PROMISE_BUY_ITEM, responseCode)
            }
            return
        }
        if (purchases != null) {
            var promiseItem: WritableMap? = null
            for (i in purchases.indices) {
                val item = Arguments.createMap()
                val purchase = purchases[i]
                item.putString("productId", purchase.skus[0])
                item.putString("transactionId", purchase.orderId)
                item.putDouble("transactionDate", purchase.purchaseTime.toDouble())
                item.putString("transactionReceipt", purchase.originalJson)
                item.putString("purchaseToken", purchase.purchaseToken)
                item.putString("dataAndroid", purchase.originalJson)
                item.putString("signatureAndroid", purchase.signature)
                item.putBoolean("autoRenewingAndroid", purchase.isAutoRenewing)
                item.putBoolean("isAcknowledgedAndroid", purchase.isAcknowledged)
                item.putInt("purchaseStateAndroid", purchase.purchaseState)
                item.putString("packageNameAndroid", purchase.packageName)
                item.putString("developerPayloadAndroid", purchase.developerPayload)
                val accountIdentifiers = purchase.accountIdentifiers
                if (accountIdentifiers != null) {
                    item.putString(
                        "obfuscatedAccountIdAndroid",
                        accountIdentifiers.obfuscatedAccountId
                    )
                    item.putString(
                        "obfuscatedProfileIdAndroid",
                        accountIdentifiers.obfuscatedProfileId
                    )
                }
                promiseItem = WritableNativeMap()
                promiseItem.merge(item)
                sendEvent(reactContext, "purchase-updated", item)
            }
            if (promiseItem != null) {
                DoobooUtils.resolvePromisesForKey(PROMISE_BUY_ITEM, promiseItem)
            }
        } else {
            val result = Arguments.createMap()
            result.putInt("responseCode", billingResult.responseCode)
            result.putString("debugMessage", billingResult.debugMessage)
            result.putString(
                "extraMessage",
                "The purchases are null. This is a normal behavior if you have requested DEFERRED"
                        + " proration. If not please report an issue."
            )
            sendEvent(reactContext, "purchase-updated", result)
            DoobooUtils.resolvePromisesForKey(PROMISE_BUY_ITEM, null)
        }
    }

    private fun sendUnconsumedPurchases(promise: Promise) {
        ensureConnection(
            promise
        ) { billingClient: BillingClient ->
            val types = arrayOf(BillingClient.SkuType.INAPP, BillingClient.SkuType.SUBS)
            for (type in types) {
                billingClient.queryPurchasesAsync(
                    type
                ) { billingResult: BillingResult, list: List<Purchase>? ->
                    val unacknowledgedPurchases = ArrayList<Purchase>()
                    if (list == null || list.size == 0) {
                        //                    continue;
                    }
                    for (purchase in list!!) {
                        if (!purchase.isAcknowledged) {
                            unacknowledgedPurchases.add(purchase)
                        }
                    }
                    onPurchasesUpdated(billingResult, unacknowledgedPurchases)
                }
            }
            promise.resolve(true)
        }
    }

    @ReactMethod
    fun startListening(promise: Promise) {
        sendUnconsumedPurchases(promise)
    }

    @get:ReactMethod
    val packageName: String
        get() = reactApplicationContext.packageName

    private fun sendEvent(
        reactContext: ReactContext, eventName: String, params: WritableMap?
    ) {
        reactContext
            .getJSModule(RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    companion object {
        private const val PROMISE_BUY_ITEM = "PROMISE_BUY_ITEM"
    }

    init {
        this.reactContext = reactContext
        skus = ArrayList()
        val lifecycleEventListener: LifecycleEventListener = object : LifecycleEventListener {
            override fun onHostResume() {}
            override fun onHostPause() {}
            override fun onHostDestroy() {
                if (billingClientCache != null) {
                    billingClientCache!!.endConnection()
                    billingClientCache = null
                }
            }
        }
        reactContext.addLifecycleEventListener(lifecycleEventListener)
    }
}