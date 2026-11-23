package com.starception.submission.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Category transfers belong to the app scope, never a screen's composition scope. */
internal class CategoryDownloadJobs(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<String, Job>()

    @Synchronized
    fun launch(category: String, transfer: suspend () -> Unit): Job {
        jobs[category]?.takeIf { it.isActive }?.let { return it }
        val job = scope.launch(start = CoroutineStart.LAZY) { transfer() }
        jobs[category] = job
        job.invokeOnCompletion {
            synchronized(this) {
                if (jobs[category] === job) jobs.remove(category)
            }
        }
        job.start()
        return job
    }
}
