package com.porteiro.client

import android.app.Application
import com.porteiro.client.data.PairingStore

class MoradorApp : Application() {

    lateinit var store: PairingStore
        private set
    lateinit var controller: MoradorController
        private set

    override fun onCreate() {
        super.onCreate()
        store = PairingStore(this)
        controller = MoradorController(this, store)
    }
}
