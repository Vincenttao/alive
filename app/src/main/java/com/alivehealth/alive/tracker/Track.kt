package com.alivehealth.alive.tracker

import com.alivehealth.alive.data.Person

data class Track(
    val person: Person,
    val lastTimestamp: Long
)

