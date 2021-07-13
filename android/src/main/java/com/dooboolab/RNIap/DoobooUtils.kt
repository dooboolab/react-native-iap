package com.dooboolab.RNIap

import android.util.Log
import com.facebook.react.bridge.ObjectAlreadyConsumedException
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableNativeArray
import com.facebook.react.bridge.WritableNativeMap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

object DoobooUtils {
    private val promises = HashMap<String, ArrayList<Promise>>()
    fun addPromiseForKey(key: String, promise: Promise) {
        try {
            val list: ArrayList<Promise>
            if (promises.containsKey(key)) {
                list = promises[key]!!
            } else {
                list = ArrayList()
                promises[key] = list
            }
            list.add(promise)
        } catch (oce: ObjectAlreadyConsumedException) {
            Log.e(TAG, oce.message!!)
        }
    }

    fun resolvePromisesForKey(key: String, value: Any?) {
        try {
            if (promises.containsKey(key)) {
                val list = promises[key]!!
                for (promise in list) {
                    promise.resolve(value)
                }
                promises.remove(key)
            }
        } catch (oce: ObjectAlreadyConsumedException) {
            Log.e(TAG, oce.message!!)
        }
    }

    fun rejectPromisesForKey(
        key: String, code: String?, message: String?, err: Exception?
    ) {
        try {
            if (promises.containsKey(key)) {
                val list = promises[key]!!
                for (promise in list) {
                    promise.reject(code, message, err)
                }
                promises.remove(key)
            }
        } catch (oce: ObjectAlreadyConsumedException) {
            Log.e(TAG, oce.message!!)
        }
    }

    @Throws(JSONException::class)
    fun convertJsonToMap(jsonObject: JSONObject): WritableMap {
        val map: WritableMap = WritableNativeMap()
        val iterator = jsonObject.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = jsonObject[key]
            if (value is JSONObject) {
                map.putMap(key, convertJsonToMap(value))
            } else if (value is JSONArray) {
                map.putArray(key, convertJsonToArray(value))
            } else if (value is Boolean) {
                map.putBoolean(key, value)
            } else if (value is Int) {
                map.putInt(key, value)
            } else if (value is Double) {
                map.putDouble(key, value)
            } else if (value is String) {
                map.putString(key, value)
            } else {
                map.putString(key, value.toString())
            }
        }
        return map
    }

    @Throws(JSONException::class)
    fun convertJsonToArray(jsonArray: JSONArray): WritableArray {
        val array: WritableArray = WritableNativeArray()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray[i]
            if (value is JSONObject) {
                array.pushMap(convertJsonToMap(value))
            } else if (value is JSONArray) {
                array.pushArray(convertJsonToArray(value))
            } else if (value is Boolean) {
                array.pushBoolean(value)
            } else if (value is Int) {
                array.pushInt(value)
            } else if (value is Double) {
                array.pushDouble(value)
            } else if (value is String) {
                array.pushString(value)
            } else {
                array.pushString(value.toString())
            }
        }
        return array
    }

    @Throws(JSONException::class)
    fun convertMapToJson(readableMap: ReadableMap?): JSONObject {
        val `object` = JSONObject()
        val iterator = readableMap!!.keySetIterator()
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            when (readableMap.getType(key)) {
                ReadableType.Null -> `object`.put(key, JSONObject.NULL)
                ReadableType.Boolean -> `object`.put(key, readableMap.getBoolean(key))
                ReadableType.Number -> `object`.put(key, readableMap.getDouble(key))
                ReadableType.String -> `object`.put(key, readableMap.getString(key))
                ReadableType.Map -> `object`.put(key, convertMapToJson(readableMap.getMap(key)))
                ReadableType.Array -> `object`.put(
                    key,
                    convertArrayToJson(readableMap.getArray(key))
                )
            }
        }
        return `object`
    }

    @Throws(JSONException::class)
    fun convertArrayToJson(readableArray: ReadableArray?): JSONArray {
        val array = JSONArray()
        for (i in 0 until readableArray!!.size()) {
            when (readableArray.getType(i)) {
                ReadableType.Null -> {
                }
                ReadableType.Boolean -> array.put(readableArray.getBoolean(i))
                ReadableType.Number -> array.put(readableArray.getDouble(i))
                ReadableType.String -> array.put(readableArray.getString(i))
                ReadableType.Map -> array.put(convertMapToJson(readableArray.getMap(i)))
                ReadableType.Array -> array.put(convertArrayToJson(readableArray.getArray(i)))
            }
        }
        return array
    }

        private const val TAG = "DoobooUtils"
        const val E_UNKNOWN = "E_UNKNOWN"
        const val E_NOT_PREPARED = "E_NOT_PREPARED"
        const val E_ALREADY_PREPARED = "E_ALREADY_PREPARED"
        const val E_NOT_ENDED = "E_NOT_ENDED"
        const val E_USER_CANCELLED = "E_USER_CANCELLED"
        const val E_ITEM_UNAVAILABLE = "E_ITEM_UNAVAILABLE"
        const val E_NETWORK_ERROR = "E_NETWORK_ERROR"
        const val E_SERVICE_ERROR = "E_SERVICE_ERROR"
        const val E_ALREADY_OWNED = "E_ALREADY_OWNED"
        const val E_REMOTE_ERROR = "E_REMOTE_ERROR"
        const val E_USER_ERROR = "E_USER_ERROR"
        const val E_DEVELOPER_ERROR = "E_DEVELOPER_ERROR"
        const val E_BILLING_RESPONSE_JSON_PARSE_ERROR = "E_BILLING_RESPONSE_JSON_PARSE_ERROR"

}