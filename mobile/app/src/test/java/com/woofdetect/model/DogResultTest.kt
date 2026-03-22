package com.woofdetect.model

import org.junit.Assert.*
import org.junit.Test

class DogResultTest {

    @Test
    fun `DogResult with all parameters creates correctly`() {
        val result = DogResult(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog",
            confidence = 95.5,
            breedPhoto = null
        )

        assertEquals("labrador", result.breedName)
        assertEquals("Labrador Retriever", result.breedFullName)
        assertEquals("Friendly dog", result.breedDescription)
        assertEquals(95.5, result.confidence, 0.1)
        assertNull(result.breedPhoto)
    }

    @Test
    fun `DogResult without breedPhoto uses null default`() {
        val result = DogResult(
            breedName = "beagle",
            breedFullName = "Beagle",
            breedDescription = "Small hound",
            confidence = 80.0
        )

        assertEquals("beagle", result.breedName)
        assertNull(result.breedPhoto)
    }

    @Test
    fun `DogResult getBreedPhoto returns null when not set`() {
        val result = DogResult(
            breedName = "poodle",
            breedFullName = "Poodle",
            breedDescription = "Intelligent dog",
            confidence = 90.0
        )

        assertNull(result.breedPhoto)
    }

    @Test
    fun `DogResult equals works correctly`() {
        val result1 = DogResult(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog",
            confidence = 95.5
        )
        val result2 = DogResult(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog",
            confidence = 95.5
        )

        assertEquals(result1, result2)
    }

    @Test
    fun `DogResult copy works correctly`() {
        val original = DogResult(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog",
            confidence = 95.5
        )
        val copy = original.copy(breedName = "beagle")

        assertEquals("beagle", copy.breedName)
        assertEquals("Labrador Retriever", copy.breedFullName)
    }
}