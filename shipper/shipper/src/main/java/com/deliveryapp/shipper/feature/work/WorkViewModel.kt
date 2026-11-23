package com.deliveryapp.shipper.feature.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deliveryapp.shipper.data.model.*
import com.deliveryapp.shipper.data.repository.*
import java.time.Clock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class WorkUiState(
    val availability: Availability = Availability.OFFLINE,
    val offer: Offer? = null, val seconds: Int = 0, val active: Trip? = null,
    val completed: Trip? = null, val history: List<Trip> = emptyList(),
    val loading: Boolean = false, val historyLoading: Boolean = false,
    val error: String? = null, val historyError: String? = null,
    val locationWarning: String? = null,
) {
    val canRespond get() = !loading && availability == Availability.AVAILABLE && offer?.status == OfferStatus.OFFERED && seconds > 0
}
sealed interface WorkEvent { data object Accepted : WorkEvent; data object Completed : WorkEvent }

/**
 * @param pollIntervalMs when set (live backend), offers are polled automatically while the driver is
 *   online and the position is reported every [locationEveryPolls] rounds. Null keeps the manual demo flow.
 */
class WorkViewModel(
    private val repository: WorkRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val pollIntervalMs: Long? = null,
    private val locationEveryPolls: Int = 5,
) : ViewModel() {
    private val mutable = MutableStateFlow(WorkUiState())
    val state = mutable.asStateFlow()
    private val eventChannel = Channel<WorkEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var countdown: Job? = null
    private var historyJob: Job? = null
    private var actionJob: Job? = null
    private var pollJob: Job? = null
    private var sessionGeneration = 0L

    private fun action(block: suspend () -> Unit) {
        if (state.value.loading) return
        val generation = sessionGeneration
        mutable.update { it.copy(loading = true, error = null) }
        actionJob = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: RepositoryException) {
                val terminal = when (e.code) {
                    "OFFER_EXPIRED" -> OfferStatus.EXPIRED; "OFFER_CANCELLED" -> OfferStatus.CANCELLED
                    "OFFER_NOT_AVAILABLE" -> OfferStatus.PROCESSED; else -> null
                }
                mutable.update { it.copy(error = e.message, offer = if (terminal != null) it.offer?.copy(status = terminal) else it.offer) }
            }
            catch (e: Exception) { mutable.update { it.copy(error = "Không thể tải dữ liệu. Vui lòng thử lại.") } }
            finally {
                // A cancelled old session must not unlock an action in the next session.
                if (generation == sessionGeneration) mutable.update { it.copy(loading = false) }
            }
        }
    }
    fun toggleAvailability() = action {
        if (state.value.active != null) throw RepositoryException("DRIVER_BUSY", "Hãy hoàn thành chuyến trước khi nghỉ.")
        val next = if (state.value.availability == Availability.OFFLINE) Availability.AVAILABLE else Availability.OFFLINE
        repository.availability(next)
        mutable.update { it.copy(availability = next, offer = null, seconds = 0, locationWarning = null) }
        countdown?.cancel()
        if (next == Availability.AVAILABLE) {
            updateOffer(repository.offer())
            startPolling()
        } else pollJob?.cancel()
    }

    private fun startPolling() {
        val interval = pollIntervalMs ?: return
        if (pollJob?.isActive == true) return
        val generation = sessionGeneration
        pollJob = viewModelScope.launch {
            var round = 0
            while (isActive && generation == sessionGeneration && state.value.availability != Availability.OFFLINE) {
                // Keep reporting while BUSY too, so the customer can follow the driver.
                if (round % locationEveryPolls == 0) {
                    val sent = try { repository.reportLocation() } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
                    mutable.update { it.copy(locationWarning = if (sent) null else LOCATION_WARNING) }
                }
                round++
                if (canReceiveOffer()) {
                    val fetched = try { repository.offer() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    // Re-check: the driver may have acted while the request was in flight.
                    if (fetched != null && fetched.offerId != state.value.offer?.offerId && canReceiveOffer()) updateOffer(fetched)
                }
                delay(interval)
            }
        }
    }
    private fun canReceiveOffer() = state.value.let {
        !it.loading && it.active == null && it.availability == Availability.AVAILABLE && it.offer?.status != OfferStatus.OFFERED
    }
    fun refreshOffer() = action {
        if (state.value.availability == Availability.AVAILABLE) updateOffer(repository.offer())
    }
    private fun updateOffer(offer: Offer?) {
        countdown?.cancel()
        mutable.update { it.copy(offer = offer, seconds = offer?.remainingSeconds(clock.instant()) ?: 0) }
        tick()
        if (state.value.offer?.status == OfferStatus.OFFERED) countdown = viewModelScope.launch {
            while (isActive && state.value.offer?.status == OfferStatus.OFFERED) { delay(250); tick() }
        }
    }
    fun tick() {
        val offer = state.value.offer ?: return
        if (offer.status != OfferStatus.OFFERED) return
        val seconds = offer.remainingSeconds(clock.instant())
        mutable.update { it.copy(seconds = seconds, offer = if (seconds == 0) offer.copy(status = OfferStatus.EXPIRED) else offer) }
    }
    fun accept() {
        tick()
        if (!state.value.canRespond) return
        val offer = state.value.offer ?: return
        action {
            val trip = repository.accept(offer).let {
                it.copy(distanceToPickupKm = it.distanceToPickupKm ?: offer.distanceToPickupKm,
                    incomeEstimate = it.incomeEstimate ?: offer.incomeEstimate)
            }
            countdown?.cancel()
            mutable.update { it.copy(active = trip, availability = Availability.BUSY, offer = offer.copy(status = OfferStatus.ACCEPTED)) }
            eventChannel.send(WorkEvent.Accepted)
        }
    }
    fun reject() {
        tick()
        if (!state.value.canRespond) return
        val offer = state.value.offer ?: return
        action {
            repository.reject(offer)
            countdown?.cancel()
            mutable.update { it.copy(offer = offer.copy(status = OfferStatus.REJECTED)) }
        }
    }
    fun advance(expected: TripStatus) = action {
        val trip = state.value.active
        if (trip == null || trip.status != expected || expected.next == null) throw RepositoryException("INVALID_DELIVERY_TRANSITION", "Chuyến đã thay đổi. Hãy kiểm tra trạng thái hiện tại.")
        val result = repository.advance(trip, expected.next!!).let {
            it.copy(distanceToPickupKm = it.distanceToPickupKm ?: trip.distanceToPickupKm,
                incomeEstimate = it.incomeEstimate ?: trip.incomeEstimate)
        }
        if (result.status == TripStatus.COMPLETED) {
            mutable.update { it.copy(active = null, completed = result, offer = null, availability = Availability.AVAILABLE,
                history = listOf(result) + it.history.filterNot { old -> old.deliveryId == result.deliveryId }) }
            eventChannel.send(WorkEvent.Completed)
        } else mutable.update { it.copy(active = result) }
    }
    fun loadHistory() {
        historyJob?.cancel()
        mutable.update { it.copy(historyLoading = true, historyError = null) }
        historyJob = viewModelScope.launch {
            try { val result = repository.history(); mutable.update { it.copy(history = result, historyLoading = false) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(historyLoading = false, historyError = e.message ?: "Không thể tải lịch sử.") } }
        }
    }
    fun scenario(value: DemoScenario) {
        if (state.value.loading || repository !is DemoControls) return
        repository.scenario(value)
        countdown?.cancel()
        mutable.update { it.copy(offer = null, seconds = 0, error = null) }
        if (state.value.availability == Availability.AVAILABLE) refreshOffer()
        loadHistory()
    }
    fun reset() {
        sessionGeneration++
        countdown?.cancel(); historyJob?.cancel(); actionJob?.cancel(); pollJob?.cancel()
        while (eventChannel.tryReceive().isSuccess) { /* Discard events from the previous session. */ }
        mutable.value = WorkUiState()
    }
}

private const val LOCATION_WARNING =
    "Chưa gửi được vị trí GPS nên hệ thống chưa thể gửi lời mời cho bạn. Hãy cấp quyền vị trí và bật GPS."
