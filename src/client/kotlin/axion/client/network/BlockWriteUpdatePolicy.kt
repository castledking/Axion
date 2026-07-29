package axion.client.network

/**
 * World block-write flags used by Axion edits.
 *
 * Both policies notify clients while deliberately omitting neighbor updates. Minecraft 1.21.5+
 * can additionally skip the old/new block callbacks that schedule gravity and fluid ticks. Older
 * versions need their newly scheduled ticks removed immediately after the write instead.
 */
object BlockWriteUpdatePolicy {
    const val NOTIFY_CLIENTS = 2
    const val KEEP_KNOWN_SHAPE = 16
    const val SUPPRESS_DROPS = 32
    const val SKIP_REPLACED_CALLBACK = 256
    const val SKIP_ADDED_CALLBACK = 512

    const val LEGACY_NO_PHYSICS_FLAGS = NOTIFY_CLIENTS or KEEP_KNOWN_SHAPE
    const val MODERN_NO_PHYSICS_FLAGS =
        LEGACY_NO_PHYSICS_FLAGS or SUPPRESS_DROPS or SKIP_REPLACED_CALLBACK or SKIP_ADDED_CALLBACK

    const val NO_UPDATES_FLAGS =
        NOTIFY_CLIENTS or KEEP_KNOWN_SHAPE or SUPPRESS_DROPS or SKIP_REPLACED_CALLBACK or SKIP_ADDED_CALLBACK
}
