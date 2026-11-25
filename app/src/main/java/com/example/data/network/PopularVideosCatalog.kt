package com.example.data.network

import com.example.domain.model.PopularVideoItem
import com.example.domain.model.SocialSourcePlatform

/**
 * Curated open-access playable sample streams tagged by social format
 * so users on the Popular Videos discovery screen can search, preview,
 * trim, filter, and export real MP4 streams immediately.
 */
object PopularVideosCatalog {

    val items: List<PopularVideoItem> = listOf(
        PopularVideoItem(
            id = "pop_blazes",
            title = "Aksi Sinematik Api & Efek Visual",
            author = "@StudioReels",
            viewsLabel = "2.8M • Hari ini",
            durationMs = 15_000L,
            width = 1280,
            height = 720,
            platform = SocialSourcePlatform.YOUTUBE,
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerBlazes.jpg"
        ),
        PopularVideoItem(
            id = "pop_escapes",
            title = "Petualangan Alam & Transisi Cepat",
            author = "@TravelClipID",
            viewsLabel = "1.9M • 2 hari lalu",
            durationMs = 15_000L,
            width = 1280,
            height = 720,
            platform = SocialSourcePlatform.TIKTOK,
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerEscapes.jpg"
        ),
        PopularVideoItem(
            id = "pop_fun",
            title = "Momen Lucu & Reaksi Viral Harian",
            author = "@KomediShorts",
            viewsLabel = "3.4M • 1 hari lalu",
            durationMs = 60_000L,
            width = 1280,
            height = 720,
            platform = SocialSourcePlatform.INSTAGRAM,
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerFun.jpg"
        ),
        PopularVideoItem(
            id = "pop_joy",
            title = "Sorotan Gaya Hidup & Vibe Positif",
            author = "@DailyHighlight",
            viewsLabel = "1.4M • 3 hari lalu",
            durationMs = 15_000L,
            width = 1280,
            height = 720,
            platform = SocialSourcePlatform.TIKTOK,
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoy.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/ForBiggerJoy.jpg"
        ),
        PopularVideoItem(
            id = "pop_subaru",
            title = "Otomotif Offroad & Drift Extreme",
            author = "@AutoHighlight",
            viewsLabel = "4.1M • 5 hari lalu",
            durationMs = 594_000L,
            width = 1280,
            height = 720,
            platform = SocialSourcePlatform.YOUTUBE,
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/SubaruOutbackOnStreetAndDirt.jpg"
        ),
        PopularVideoItem(
            id = "pop_bullrun",
            title = "Roadtrip Supercar & Drone Footage",
            author = "@MotoReels",
            viewsLabel = "2.2M • 1 minggu lalu",
            durationMs = 47_000L,
            width = 1280,
            height = 720,
            platform = SocialSourcePlatform.INSTAGRAM,
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4",
            thumbnailUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images/WeAreGoingOnBullrun.jpg"
        )
    )
}
