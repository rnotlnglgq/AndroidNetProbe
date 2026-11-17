package com.rnotlnglgq.netprobe.session

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owned by the main dispatcher. Blocking operations finish before their slot can be reused. */
class CollectionSession(private val scope: CoroutineScope, private val worker: CoroutineDispatcher = Dispatchers.IO) {
    private data class Task(val ticket: Long, val job: Job, val cancelResource: () -> Unit)
    private val tasks = mutableMapOf<String, Task>()
    private var generation = 0L
    private var nextTicket = 0L
    private var active = false
    private var pending: (suspend () -> Unit)? = null
    private val mutableBusy = MutableStateFlow<Set<String>>(emptySet())
    val busy = mutableBusy.asStateFlow()

    fun enter() { if (!active) { active = true; generation++ } }
    fun leave() { active = false; generation++; tasks.keys.toList().forEach(::cancel); pending = null }
    fun cancel(key: String) {
        if (key == "capture") pending = null
        tasks[key]?.let { it.cancelResource(); it.job.cancel() }
        mutableBusy.value -= key
    }
    fun <T> start(key: String, cancelResource: () -> Unit = {}, work: suspend () -> T,
        commit: (T) -> Unit, failed: (Exception) -> Unit = {}): Boolean {
        if (!active || tasks[key]?.job?.isCompleted == false) return false
        val epoch = generation
        val ticket = ++nextTicket
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(worker) { work() }
                ensureActive()
                if (active && epoch == generation && tasks[key]?.ticket == ticket) commit(result)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (active && epoch == generation) failed(e) }
            finally {
                cancelResource()
                if (tasks[key]?.ticket == ticket) {
                    tasks.remove(key)
                    mutableBusy.value -= key
                    if (key == "capture" && pending != null && active) drain()
                }
            }
        }
        tasks[key] = Task(ticket, job, cancelResource)
        mutableBusy.value += key
        job.start()
        return true
    }

    /** Refresh requests coalesce on the owner dispatcher; only collection work uses IO. */
    fun refresh(request: suspend () -> Unit) {
        if (!active) return
        pending = request
        if (tasks["capture"]?.job?.isCompleted != false) drain()
    }
    private fun drain() {
        val epoch = generation
        val ticket = ++nextTicket
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                while (active && epoch == generation && pending != null) {
                    delay(150)
                    val request = pending ?: break
                    pending = null
                    request()
                    ensureActive()
                }
            } finally {
                if (tasks["capture"]?.ticket == ticket) {
                    tasks.remove("capture"); mutableBusy.value -= "capture"
                    if (active && pending != null) drain()
                }
            }
        }
        tasks["capture"] = Task(ticket,job,{})
        mutableBusy.value += "capture"
        job.start()
    }
}
