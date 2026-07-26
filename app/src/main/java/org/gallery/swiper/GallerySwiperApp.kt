package org.gallery.swiper

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import org.gallery.swiper.data.AppDatabase

class GallerySwiperApp : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .build()
        )
    }
}
