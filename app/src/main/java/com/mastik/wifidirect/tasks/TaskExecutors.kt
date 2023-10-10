package com.mastik.wifidirect.tasks

import com.mastik.wifidirect.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class TaskExecutors private constructor(){

    companion object {
        private val fixedPool = run {
            val pool: ThreadPoolExecutor = Executors.newFixedThreadPool(R.integer.fixed_threads_count) as ThreadPoolExecutor
            pool.corePoolSize = 1
            pool.setKeepAliveTime(100, TimeUnit.SECONDS)
            pool
        } // For long running tasks
        private val cachedPool = Executors.newCachedThreadPool() // For short tasks, or network requests

        fun getFixedPool(): ExecutorService {
            return fixedPool
        }

        fun getCachedPool(): ExecutorService {
            return cachedPool
        }
    }
}