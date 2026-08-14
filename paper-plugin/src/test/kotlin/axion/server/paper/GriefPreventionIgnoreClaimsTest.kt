package axion.server.paper

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GriefPreventionIgnoreClaimsTest {
    private class FakePlayerData(val uuid: UUID) {
        @Suppress("unused")
        var ignoreClaims = false
    }

    private class FakeDataStore {
        private val byId = mutableMapOf<UUID, FakePlayerData>()

        fun getPlayerData(uuid: UUID): FakePlayerData = byId.getOrPut(uuid) { FakePlayerData(uuid) }
    }

    private class FakeDataStoreWithoutPlayerData {
        fun getSomethingElse(): Int = 42
    }

    private val uuid: UUID = UUID.fromString("fa8d60a7-9645-4a9f-b74d-173966174739")

    @Test
    fun `accessor resolves getPlayerData and the public ignoreClaims field`() {
        val accessor = reflectIgnoreClaimsAccessor(FakeDataStore())

        val getPlayerDataMethod: Method = accessor.first
        assertEquals("getPlayerData", getPlayerDataMethod.name)
        assertEquals(FakePlayerData::class.java, getPlayerDataMethod.returnType)

        val ignoreClaimsField: Field = accessor.second
        assertEquals("ignoreClaims", ignoreClaimsField.name)
        assertEquals(Boolean::class.javaPrimitiveType, ignoreClaimsField.type)
    }

    @Test
    fun `accessor reports not ignoring claims when the toggle is off`() {
        val dataStore = FakeDataStore()
        val accessor = reflectIgnoreClaimsAccessor(dataStore)

        assertTrue(
            !reflectIsIgnoringClaims(dataStore, uuid, accessor),
            "ignoreClaims=false should report false",
        )
    }

    @Test
    fun `accessor reports ignoring claims when the toggle is on`() {
        val dataStore = FakeDataStore()
        val accessor = reflectIgnoreClaimsAccessor(dataStore)
        accessor.second.set(accessor.first.invoke(dataStore, uuid), true)

        assertTrue(reflectIsIgnoringClaims(dataStore, uuid, accessor))
    }

    @Test
    fun `accessor reports not ignoring claims when player data is absent`() {
        val dataStore = FakeDataStore()
        val accessor = reflectIgnoreClaimsAccessor(dataStore)
        val unknownId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

        assertTrue(!reflectIsIgnoringClaims(dataStore, unknownId, accessor))
    }

    @Test
    fun `accessor fails to resolve when dataStore has no getPlayerData`() {
        val thrown = runCatching { reflectIgnoreClaimsAccessor(FakeDataStoreWithoutPlayerData()) }
        assertTrue(thrown.isFailure)
    }

    @Test
    fun `accessor fails to resolve when player data type lacks ignoreClaims`() {
        class DataStoreWithoutFlag {
            fun getPlayerData(uuid: UUID): Int = 0
        }

        val thrown = runCatching { reflectIgnoreClaimsAccessor(DataStoreWithoutFlag()) }
        assertTrue(thrown.isFailure)
    }

    @Test
    fun `findField finds inherited public fields`() {
        open class Base {
            @Suppress("unused")
            var inherited = 7
        }

        class Child : Base()

        val field = findField(Child::class.java, "inherited")
        assertNotNull(field)
        assertEquals("inherited", field.name)
    }
}
