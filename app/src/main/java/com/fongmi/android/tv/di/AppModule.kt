package com.fongmi.android.tv.di

import android.content.Context
import com.fongmi.android.tv.bean.Backup
import com.fongmi.android.tv.db.AppDatabase
import com.fongmi.android.tv.player.Source
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .disableHtmlEscaping()
            .create()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.get(context)
    }

    @Provides
    @Singleton
    fun provideSource(): Source {
        return Source
    }
}
