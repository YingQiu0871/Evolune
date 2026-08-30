@file:Suppress("UseKtx")

package io.github.yingqiu0871.evolune.wear

import android.content.Context
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerNegotiationResult
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentityRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequest
import io.github.yingqiu0871.evolune.experience.wear.WearAppRequestRules
import io.github.yingqiu0871.evolune.experience.wear.negotiateWearAppProducerIdentity
import java.util.UUID

/**
 * Keeps the Phone producer identity private to the Phone process. A new
 * identity is created only when the private record is absent (for example
 * after clearing the Phone app data), while normal clock changes do not alter
 * the producer generation.
 */
internal object WearAppProducerIdentityStore {
    private const val PREFERENCES_NAME = "wear_dashboard_cache"
    private const val KEY_INSTANCE_ID = "wear_app_producer_instance_id"
    private const val KEY_GENERATION = "wear_app_producer_generation"

    @Synchronized
    fun current(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): WearAppProducerIdentity {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val existing = runCatching {
            WearAppProducerIdentity(
                producerInstanceId = UUID.fromString(
                    preferences.getString(KEY_INSTANCE_ID, null)
                        ?: error("missing producer instance")
                ),
                producerGeneration = preferences.getLong(KEY_GENERATION, 0L)
            ).also { check(WearAppProducerIdentityRules.isValid(it)) }
        }.getOrNull()
        if (existing != null) return existing

        val identity = WearAppProducerIdentity(
            producerInstanceId = UUID.randomUUID(),
            producerGeneration = WearAppProducerIdentityRules.INITIAL_GENERATION
        )
        persist(preferences, identity)
        return identity
    }

    @Synchronized
    fun negotiate(
        context: Context,
        request: WearAppRequest
    ): WearAppProducerNegotiationResult {
        if (!WearAppRequestRules.isValid(request)) {
            return WearAppProducerNegotiationResult.InvalidObservedProducer
        }
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val current = current(context)
        val result = negotiateWearAppProducerIdentity(
            current = current,
            observedProducerInstanceId = request.observedProducerInstanceId,
            observedProducerGeneration = request.observedProducerGeneration
        )
        if (result is WearAppProducerNegotiationResult.Accepted && result.identity != current) {
            persist(preferences, result.identity)
        }
        return result
    }

    private fun persist(
        preferences: android.content.SharedPreferences,
        identity: WearAppProducerIdentity
    ) {
        check(preferences.edit()
            .putString(KEY_INSTANCE_ID, identity.producerInstanceId.toString())
            .putLong(KEY_GENERATION, identity.producerGeneration)
            .commit()) {
            "Unable to persist Wear App producer identity"
        }
    }
}
