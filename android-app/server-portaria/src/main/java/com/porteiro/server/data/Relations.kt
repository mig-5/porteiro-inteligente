package com.porteiro.server.data

import androidx.room.Embedded
import androidx.room.Relation

data class ApartmentWithResidents(
    @Embedded val apartment: Apartment,
    @Relation(entity = Resident::class, parentColumn = "id", entityColumn = "apartment_id")
    val residents: List<ResidentWithDevices>
)

data class ResidentWithDevices(
    @Embedded val resident: Resident,
    @Relation(entity = Device::class, parentColumn = "id", entityColumn = "resident_id")
    val devices: List<Device>,
    @Relation(entity = FacialEmbedding::class, parentColumn = "id", entityColumn = "resident_id")
    val embeddings: List<FacialEmbedding>
)
