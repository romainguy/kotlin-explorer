/*
 * Copyright (C) 2023 Romain Guy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.romainguy.kotlin.explorer

import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.min

/** Helps manage updates to a status bar */
class ProgressUpdater(
    val steps: Int,
    private val onUpdate: (String, Float) -> Unit,
) {
    private val stepCounter = AtomicInteger(0)
    private val lock = ReentrantReadWriteLock()
    private val jobs = mutableListOf<Job>()

    /** Update without advancing progress */
    fun update(message: String) {
        sendUpdate(message, stepCounter.get())
    }

    /**
     * Update and advance progress
     *
     * We can advance by more than one step, for example, if a step fails and that prevents the next 3 steps from being
     * able to run, we would advance by 4.
     */
    fun advance(message: String, steps: Int = 1) {
        sendUpdate(message, stepCounter.addAndGet(steps))
    }

    suspend fun waitForJobs() {
        lock.read {
            jobs.joinAll()
        }
    }

    fun skipToEnd(message: String) {
        stepCounter.set(steps)
        sendUpdate(message, steps)
    }

    /**
     * Joins all threads and sends the last update
     */
    suspend fun finish() {
        lock.read {
            jobs.joinAll()
        }
        val step = stepCounter.get()
        if (step < steps) {
            Logger.warn("finish() called but progress is not yet finished: step=$step")
        }
    }

    /** Add a job that needs to be joined before finishing */
    fun addJob(job: Job) {
        lock.write {
            jobs.add(job)
        }
    }

    private fun sendUpdate(message: String, step: Int) {
        if (step > steps) {
            Logger.warn("Progress already completed while sending: '$message'")
        }
        Logger.debug("Sending $step/$steps: '$message'")
        onUpdate(message, min(steps, step).toFloat() / steps)
    }
}