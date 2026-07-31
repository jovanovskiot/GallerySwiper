package org.gallery.swiper

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import org.gallery.swiper.data.AppDatabase

class GallerySwiperApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .build()
        )
    }
}
