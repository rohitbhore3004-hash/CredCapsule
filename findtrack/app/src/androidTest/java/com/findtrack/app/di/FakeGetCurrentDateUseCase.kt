package com.findtrack.app.di

import java.util.Date

class FakeGetCurrentDateUseCase: GetCurrentDateUseCase() {
    var value = Date()

    override operator fun invoke(): Date {
        return value
    }
}