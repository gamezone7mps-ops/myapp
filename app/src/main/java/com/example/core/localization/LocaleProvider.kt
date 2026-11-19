package com.example.core.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.domain.model.AppLanguage
import java.util.Locale

private class LocalizedContextWrapper(
  base: Context,
  private val localized: Context
) : ContextWrapper(base) {
  override fun getResources(): Resources = localized.resources
  override fun getAssets(): AssetManager = localized.assets
}

fun createLocalizedContext(base: Context, language: AppLanguage): Context {
  val locale = Locale.forLanguageTag(language.code)
  val config = Configuration(base.resources.configuration).apply {
    setLocale(locale)
    setLayoutDirection(locale)
  }
  val configContext = base.createConfigurationContext(config)
  return LocalizedContextWrapper(base, configContext)
}

@Composable
fun ProvideLifeOsLocale(
  language: AppLanguage,
  content: @Composable () -> Unit
) {
  val baseContext = LocalContext.current
  val localizedContext = remember(baseContext, language) {
    createLocalizedContext(baseContext, language)
  }
  val activityResultOwner = LocalActivityResultRegistryOwner.current
  val backPressedOwner = LocalOnBackPressedDispatcherOwner.current
  val layoutDirection = if (language == AppLanguage.PERSIAN) {
    LayoutDirection.Rtl
  } else {
    LayoutDirection.Ltr
  }

  val providers = buildList {
    add(LocalContext provides localizedContext)
    add(LocalConfiguration provides localizedContext.resources.configuration)
    add(LocalLayoutDirection provides layoutDirection)
    activityResultOwner?.let { add(LocalActivityResultRegistryOwner provides it) }
    backPressedOwner?.let { add(LocalOnBackPressedDispatcherOwner provides it) }
  }.toTypedArray()

  CompositionLocalProvider(
    *providers,
    content = content
  )
}
