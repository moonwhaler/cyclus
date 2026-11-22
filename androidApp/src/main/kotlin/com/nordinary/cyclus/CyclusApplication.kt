package com.nordinary.cyclus

import android.app.Application
import com.nordinary.cyclus.store.StoreHolder

class CyclusApplication : Application() {
    val store: StoreHolder by lazy { StoreHolder(this) }
}
