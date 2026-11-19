package com.example.core.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface NetworkStatusProvider {
  fun isOnline(): Boolean
}

class AndroidNetworkStatusProvider(
  context: Context
) : NetworkStatusProvider {
  private val appContext = context.applicationContext

  override fun isOnline(): Boolean {
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      ?: return false
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}

class StaticNetworkStatusProvider(
  var online: Boolean = true
) : NetworkStatusProvider {
  override fun isOnline(): Boolean = online
}
