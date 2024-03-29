package com.ismet.usbterminal.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.ismet.usb.UsbEmitter
import com.ismet.usbterminal.data.DirectoryType
import com.ismet.usbterminalnew.BuildConfig
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    @UsbWriteDispatcher
    fun provideUsbWriteDispatcher(exceptionHandler: CoroutineExceptionHandler): CoroutineContext = when(BuildConfig.DEBUG) {
        true -> Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        else -> Executors.newSingleThreadExecutor().asCoroutineDispatcher() + exceptionHandler
    }

    @Provides
    @Singleton
    @CacheCo2ValuesDispatcher
    fun provideCacheC02ValuesDispatcher(exceptionHandler: CoroutineExceptionHandler): CoroutineContext = when(BuildConfig.DEBUG){
        true -> Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        else -> Executors.newSingleThreadExecutor().asCoroutineDispatcher() + exceptionHandler
    }

    @Provides
    @Singleton
    fun provideExceptionHandler(formatter: SimpleDateFormat): CoroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        throwable.printStackTrace(PrintStream(File(DirectoryType.TEMPORARY.getDirectory(), "crash_at_${formatter.format(Date())}.txt")))
    }

    @Provides
    @Singleton
    fun provideDateTimeFileNameFormatter(): SimpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")

    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    @Provides
    @Singleton
    fun provideUsbEmitter(): UsbEmitter = UsbEmitter()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder().build()
}