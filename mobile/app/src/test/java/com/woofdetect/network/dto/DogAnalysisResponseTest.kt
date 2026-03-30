package com.woofdetect.network.dto

import org.junit.Assert.*
import org.junit.Test

class DogAnalysisResponseTest {

    @Test
    fun `DogAnalysisResponse with all parameters creates correctly`() {
        val response = DogAnalysisResponse(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog",
            mainPhotoData = "base64data",
            confidence = 95.5
        )

        assertEquals("labrador", response.breedName)
        assertEquals("Labrador Retriever", response.breedFullName)
        assertEquals("Friendly dog", response.breedDescription)
        assertEquals("base64data", response.mainPhotoData)
        assertEquals(95.5, response.confidence!!, 0.1)
    }

    @Test
    fun `DogAnalysisResponse without optional parameters uses defaults`() {
        val response = DogAnalysisResponse(
            breedName = "beagle",
            breedFullName = "Beagle",
            breedDescription = "Small hound"
        )

        assertEquals("beagle", response.breedName)
        assertNull(response.mainPhotoData)
        assertNull(response.confidence)
    }

    @Test
    fun `DogAnalysisResponse equals works correctly`() {
        val response1 = DogAnalysisResponse(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog"
        )
        val response2 = DogAnalysisResponse(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog"
        )

        assertEquals(response1, response2)
    }

    @Test
    fun `DogAnalysisResponse copy works correctly`() {
        val original = DogAnalysisResponse(
            breedName = "labrador",
            breedFullName = "Labrador Retriever",
            breedDescription = "Friendly dog",
            confidence = 95.5
        )
        val copy = original.copy(breedName = "beagle")

        assertEquals("beagle", copy.breedName)
        assertEquals(95.5, copy.confidence!!, 0.1)
    }
}