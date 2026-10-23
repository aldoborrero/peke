package dev.aldoborrero.peke.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
fun PekeDispatchers.configure(testDispatcher: TestDispatcher) {
    Dispatchers.setMain(testDispatcher)
    IO = testDispatcher
    Default = testDispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
fun PekeDispatchers.reset() {
    Dispatchers.resetMain()
    IO = Dispatchers.IO
    Default = Dispatchers.Default
}
