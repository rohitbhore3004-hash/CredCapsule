package com.danilkinkin.findtrack.di

import java.util.Date

class FakeGetCurrentDateUseCase: GetCurrentDateUseCase() {
    var value = Date()

    override operator fun invoke(): Date {
        return value
    }
}