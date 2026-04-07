package com.example.recipeapp.data

import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPlacesQueryBuilderTest {

    @Test
    fun buildSupermarketQuery_includesCoordinatesAndRadius() {
        val query = NearbyPlacesQueryBuilder.buildSupermarketQuery(32.123456, 34.987654, 2500)

        assertTrue(query.contains("around:2500"))
        assertTrue(query.contains("32.123456"))
        assertTrue(query.contains("34.987654"))
        assertTrue(query.contains("shop\"=\"supermarket\""))
    }
}

