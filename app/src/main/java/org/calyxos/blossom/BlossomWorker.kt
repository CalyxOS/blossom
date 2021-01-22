/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.calyxos.blossom

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.calyxos.blossom.BuildConfig.BLOSSOM_AUTHORITY
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import java.io.IOException

class BlossomWorker(
        context: Context,
        workerParams: WorkerParameters
) : Worker(context, workerParams) {

    companion object {
        private const val TAG = "Blossom"

        internal fun enqueueLoad(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(OneTimeWorkRequestBuilder<BlossomWorker>()
                    .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build())
        }
    }

    override fun doWork(): Result {
        val wallPapers = try {
            BlossomService.wallPapers()
        } catch (e: IOException) {
            Log.w(TAG, "Error reading Blossom response", e)
            return Result.retry()
        }

        if (wallPapers.isEmpty()) {
            Log.w(TAG, "No photos returned from API.")
            return Result.failure()
        }

        val providerClient = ProviderContract.getProviderClient(
                applicationContext, BLOSSOM_AUTHORITY)
        val attributionString = applicationContext.getString(R.string.attribution)
        providerClient.addArtwork(wallPapers.map { photo ->
            Artwork(
                    token = photo.id,
                    title = photo.description ?: attributionString,
                    byline = photo.user.name,
                    attribution = if (photo.description != null) attributionString else null,
                    persistentUri = photo.urls.full.toUri(),
                    webUri = photo.links.webUri,
                    metadata = photo.user.links.webUri.toString())
        })
        return Result.success()
    }
}
