package com.example.helpnearby.di

import android.content.Context
import com.example.BuildConfig
import com.example.helpnearby.core.network.BackendConfig
import com.example.helpnearby.data.remote.supabase.SupabaseAuthApi
import com.example.helpnearby.data.remote.supabase.SupabasePostgrestApi
import com.example.helpnearby.data.remote.supabase.SupabaseSdkProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("apikey")
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        moshi: Moshi,
        backendConfig: BackendConfig
    ): Retrofit {
        // Uses normalized Supabase URL from Secrets/BuildConfig when configured; falls back to
        // https://localhost/ in offline local storage mode where remote calls are gated off.
        return Retrofit.Builder()
            .baseUrl(backendConfig.normalizedSupabaseBaseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): SupabaseClient? {
        val supabaseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        if (!BackendConfig.isValidSupabaseUrl(supabaseUrl) ||
            !BackendConfig.isValidSupabaseKey(supabaseAnonKey)
        ) {
            return null
        }
        return runCatching {
            SupabaseSdkProvider.createClient(
                context = context,
                supabaseUrl = supabaseUrl,
                supabaseAnonKey = supabaseAnonKey,
                okHttpClient = okHttpClient
            )
        }.getOrNull()
    }

    @Provides
    @Singleton
    fun provideSupabaseAuthApi(retrofit: Retrofit): SupabaseAuthApi =
        retrofit.create(SupabaseAuthApi::class.java)

    @Provides
    @Singleton
    fun provideSupabasePostgrestApi(retrofit: Retrofit): SupabasePostgrestApi =
        retrofit.create(SupabasePostgrestApi::class.java)
}
