package com.wynime.app.domain.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wynime.app.domain.settings.ServiceConnectionTester.Service
import com.wynime.datasources.api.source.ConnectionStatus
import com.wynime.datasources.bangumi.BangumiClient
import com.wynime.utils.coroutines.SingleTaskExecutor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.measureTimedValue

class ServiceConnectionTester(
    services: List<Service>,
    private val defaultDispatcher: CoroutineContext = Dispatchers.Default,
) {
    private val services = services.map { ServiceImpl(it) }

    val results: Flow<Results> =
        combine(this.services.map { service -> service.state.map { service.service to it } }) { states ->
            Results(states.toMap(LinkedHashMap()))
        }.shareIn(
            CoroutineScope(defaultDispatcher),
            started = SharingStarted.WhileSubscribed(), replay = 0,
        )

    private val singleTaskExecutor = SingleTaskExecutor(defaultDispatcher)

    suspend fun testAll() {
        singleTaskExecutor.invoke {
            for (service in services) {
                launch {
                    service.test()
                }
            }
        }
    }

    fun stopAll() {
        singleTaskExecutor.cancelCurrent()
    }

    class Service(

        val id: String,

        val test: suspend () -> Boolean,
    )

    sealed class TestState {

        data object Idle : TestState()

        data object Testing : TestState()
        data class Success(
            val time: Duration,
        ) : TestState()

        data object Failed : TestState()

        data class Error(
            val e: Throwable,
        ) : TestState()
    }

    class Results internal constructor(
        internal val states: Map<Service, TestState>,
    ) {
        val idToStateMap: Map<String, TestState> by lazy { states.mapKeys { it.key.id } }

        fun findStateById(id: String): TestState? = states.keys.find { it.id == id }?.let { states[it] }

        fun anyFailed() = states.values.any { it is TestState.Failed }
        fun allCompleted() = states.values.all {
            when (it) {
                is TestState.Error -> true
                TestState.Failed -> true
                TestState.Idle -> false
                is TestState.Success -> true
                TestState.Testing -> false
            }
        }
    }

    private class ServiceImpl(
        val service: Service,
    ) {
        private val _state: MutableStateFlow<TestState> = MutableStateFlow(TestState.Idle)
        val state: StateFlow<TestState> = _state.asStateFlow()
        private val lock = Mutex()

        suspend fun test() {

            lock.withLock(owner = this) {
                _state.value = TestState.Testing
                try {
                    val (res, t) = measureTimedValue { service.test() }
                    _state.value = if (res) TestState.Success(t) else TestState.Failed
                } catch (e: CancellationException) {
                    _state.value = TestState.Idle
                    throw e
                } catch (e: Throwable) {
                    _state.value = TestState.Error(e)
                }
            }
        }

        fun resetToIdle() {
            _state.value = TestState.Idle
        }
    }
}

object ServiceConnectionTesters {
    const val ID_BANGUMI = "BANGUMI"
    const val ID_BANGUMI_NEXT = "BANGUMI_NEXT"

    val DefaultServiceIds = setOf(ID_BANGUMI, ID_BANGUMI_NEXT)

    fun createDefault(
        bangumiClient: BangumiClient,
        serviceIds: Set<String> = DefaultServiceIds,
        defaultDispatcher: CoroutineContext = Dispatchers.Default,
    ): ServiceConnectionTester {
        return ServiceConnectionTester(
            listOf(
                Service(ID_BANGUMI) {
                    bangumiClient.testConnectionMaster() == ConnectionStatus.SUCCESS
                },
                Service(ID_BANGUMI_NEXT) {
                    bangumiClient.testConnectionNext() == ConnectionStatus.SUCCESS
                },
            ).filter { it.id in serviceIds },
            defaultDispatcher,
        )

    }
}
