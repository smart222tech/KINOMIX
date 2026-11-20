package com.ivor.openstream.di

import com.ivor.openstream.data.streaming.sources.InternetArchiveCatalogProvider
import com.ivor.openstream.data.streaming.sources.SourceCatalogProvider
import com.ivor.openstream.data.streaming.sources.SourceFirstCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SourceCatalogModule {
    @Provides
    @Singleton
    fun provideSourceProviders(archive: InternetArchiveCatalogProvider): List<@JvmSuppressWildcards SourceCatalogProvider> =
        listOf(archive)

    @Provides
    @Singleton
    fun provideSourceFirstCatalog(providers: List<@JvmSuppressWildcards SourceCatalogProvider>) =
        SourceFirstCatalog(providers)
}
