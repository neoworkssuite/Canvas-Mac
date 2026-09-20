package com.neoworksuite.neocanvas.ui

/**
 * Temporary Gallery campaign promotion.
 *
 * Set [enabled] to false after launch to remove the banner without touching Gallery layout.
 * The website landing page can redirect to the live Kickstarter campaign whenever needed.
 */
internal object GalleryPromotion {
    const val enabled: Boolean = true
    const val url: String = "https://neoworkssuite.com/kickstarter"
    const val title: String = "Own your software again."
    const val message: String = "NeoWorks Creative Suite is coming to Kickstarter. See the campaign, rewards and progress."
}
