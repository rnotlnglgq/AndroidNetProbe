package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.session.CollectionSession
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CollectionSessionTest {
    @Test fun delayedBlockingReadCannotCommitAfterLeaveOrOverlapOnReentry() = runBlocking {
        val scope=CoroutineScope(coroutineContext+SupervisorJob(coroutineContext[Job]))
        val session=CollectionSession(scope)
        val entered=CountDownLatch(1); val release=CountDownLatch(1)
        var commits=0
        session.enter()
        try {
            assertTrue(session.start("native",work={ entered.countDown();release.await(3,TimeUnit.SECONDS);1 },commit={ commits++ }))
            yield()
            assertTrue(withContext(Dispatchers.IO) { entered.await(2,TimeUnit.SECONDS) })
            session.leave();session.enter()
            assertFalse(session.start("native",work={ 2 },commit={ commits++ }))
            release.countDown()
            withTimeout(3000) { while (!session.start("native",work={ 3 },commit={ commits++ })) yield() }
            withTimeout(3000) { while (session.busy.value.isNotEmpty()) yield() }
            assertEquals(1,commits)
        } finally { release.countDown();session.leave();scope.cancel() }
    }
    @Test fun cancellationFailureAndRetryReleaseTheirSlots() = runBlocking {
        val scope=CoroutineScope(coroutineContext+SupervisorJob(coroutineContext[Job]))
        val session=CollectionSession(scope,Dispatchers.Unconfined);session.enter()
        var errors=0;var commits=0;var releases=0
        session.start("history",cancelResource={ releases++ },work={ delay(10000);1 },commit={ commits++ })
        yield();session.cancel("history")
        yield()
        session.start<Int>("history",work={ error("failed") },commit={ commits++ },failed={ errors++ })
        yield()
        session.start("history",work={ 1 },commit={ commits++ })
        yield()
        assertTrue(releases>=1);assertEquals(1,errors);assertEquals(1,commits)
        session.leave();scope.cancel()
    }
    @Test fun refreshBurstKeepsLatestRequestAndDrainsNewRequests() = runBlocking {
        val scope=CoroutineScope(coroutineContext+SupervisorJob(coroutineContext[Job]))
        val session=CollectionSession(scope);session.enter()
        val values=mutableListOf<Int>()
        session.refresh { values+=1 };session.refresh { values+=2 }
        withTimeout(3000) { while (values.isEmpty()) yield() }
        session.refresh { values+=3 }
        withTimeout(3000) { while (session.busy.value.isNotEmpty()) yield() }
        assertEquals(listOf(2,3),values)
        session.leave();scope.cancel()
    }
}
