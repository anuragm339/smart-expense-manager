package com.expensemanager.app.ui.categories

import androidx.annotation.DrawableRes

/**
 * Sealed class representing different types of icons that can be used for categories
 * This allows for flexible visual representation - emojis now, images/vectors in the future
 */
sealed class CategoryIcon {
    
    /**
     * Unicode emoji representation
     * @param emoji The emoji character(s) to display
     */
    data class Emoji(val emoji: String) : CategoryIcon()
    
    /**
     * Image resource representation
     * @param resourceId Resource ID of the image drawable
     */
    data class Image(@DrawableRes val resourceId: Int) : CategoryIcon()
    
    /**
     * Vector drawable representation
     * @param vectorDrawable Resource ID of the vector drawable
     */
    data class Vector(@DrawableRes val vectorDrawable: Int) : CategoryIcon()
    
    /**
     * Remote image URL representation
     * @param imageUrl URL of the image to load
     */
    data class Url(val imageUrl: String) : CategoryIcon()
    
    /**
     * Default/fallback icon
     */
    object Default : CategoryIcon()
}