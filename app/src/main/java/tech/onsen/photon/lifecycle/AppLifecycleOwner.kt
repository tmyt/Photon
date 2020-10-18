package tech.onsen.photon.lifecycle

import androidx.lifecycle.*

object AppLifecycleOwner : LifecycleOwner, LifecycleObserver {
    private val registry: LifecycleRegistry = LifecycleRegistry(this)
    private var count = 0
    private var created = false

    override fun getLifecycle(): Lifecycle {
        return registry
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreate() {
        if (created) return
        created = true
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (count++ == 0) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (--count == 0) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }
}