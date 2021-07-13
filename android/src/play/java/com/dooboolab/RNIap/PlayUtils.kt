package com.dooboolab.RNIap

import android.util.Log
import com.android.billingclient.api.BillingClient
import com.facebook.react.bridge.Promise

object PlayUtils {
    fun rejectPromiseWithBillingError(promise: Promise, responseCode: Int) {
        val errorData = getBillingResponseData(responseCode)
        promise.reject(errorData[0], errorData[1])
    }

    fun getBillingResponseData(responseCode: Int): Array<String?> {
        val errorData = arrayOfNulls<String>(2)
        when (responseCode) {
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                errorData[0] = DoobooUtils.E_SERVICE_ERROR
                errorData[1] = "This feature is not available on your device."
            }
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                errorData[0] = DoobooUtils.E_NETWORK_ERROR
                errorData[1] = "The service is disconnected (check your internet connection.)"
            }
            BillingClient.BillingResponseCode.OK -> {
                errorData[0] = "OK"
                errorData[1] = ""
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                errorData[0] = DoobooUtils.E_USER_CANCELLED
                errorData[1] = "Payment is Cancelled."
            }
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                errorData[0] = DoobooUtils.E_SERVICE_ERROR
                errorData[1] =
                    "The service is unreachable. This may be your internet connection, or the Play Store may be down."
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                errorData[0] = DoobooUtils.E_SERVICE_ERROR
                errorData[1] =
                    "Billing is unavailable. This may be a problem with your device, or the Play Store may be down."
            }
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                errorData[0] = DoobooUtils.E_ITEM_UNAVAILABLE
                errorData[1] = "That item is unavailable."
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                errorData[0] = DoobooUtils.E_DEVELOPER_ERROR
                errorData[1] = "Google is indicating that we have some issue connecting to payment."
            }
            BillingClient.BillingResponseCode.ERROR -> {
                errorData[0] = DoobooUtils.E_UNKNOWN
                errorData[1] = "An unknown or unexpected error has occured. Please try again later."
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                errorData[0] = DoobooUtils.E_ALREADY_OWNED
                errorData[1] = "You already own this item."
            }
            else -> {
                errorData[0] = DoobooUtils.E_UNKNOWN
                errorData[1] = "Purchase failed with code: $responseCode"
            }
        }
        Log.e(TAG, "Error Code : $responseCode")
        return errorData
    }

    fun rejectPromisesWithBillingError(key: String, responseCode: Int) {
        val errorData = getBillingResponseData(responseCode)
        DoobooUtils.rejectPromisesForKey(key, errorData[0], errorData[1], null)
    }


        private const val TAG = "PlayUtils"
        const val E_PLAY_SERVICES_UNAVAILABLE = "E_PLAY_SERVICES_UNAVAILABLE"

}