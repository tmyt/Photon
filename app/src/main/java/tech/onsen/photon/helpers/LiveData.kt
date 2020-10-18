package tech.onsen.photon.helpers

import androidx.lifecycle.MutableLiveData

fun <T> liveData() = lazy { MutableLiveData<T>() }

fun <T> liveData(initialValue: T) = lazy { MutableLiveData<T>(initialValue) }

fun <T> MutableLiveData<T>.postAndGetValue(value: T?): T? {
    postValue(value)
    return value
}
