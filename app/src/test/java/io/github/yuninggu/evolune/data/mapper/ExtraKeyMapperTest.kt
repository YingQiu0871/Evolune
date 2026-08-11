package io.github.yuninggu.evolune.data.mapper

import io.github.yuninggu.evolune.core.adapter.toPkExtraKey
import io.github.yuninggu.evolune.core.model.ExtraKey as DomainExtraKey
import io.github.yuninggu.evolune.pk.DoseEvent.ExtraKey as PkExtraKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraKeyMapperTest {
    @Test
    fun allLegacyStorageKeysMapToDomainKeys() {
        assertEquals(
            DomainExtraKey.CONCENTRATION_MG_ML,
            success(extraKeyFromLegacyStorage("CONCENTRATION_MG_ML"))
        )
        assertEquals(DomainExtraKey.AREA_CM2, success(extraKeyFromLegacyStorage("AREA_CM2")))
        assertEquals(
            DomainExtraKey.RELEASE_RATE_UG_PER_DAY,
            success(extraKeyFromLegacyStorage("RELEASE_RATE_UG_PER_DAY"))
        )
        assertEquals(
            DomainExtraKey.SUBLINGUAL_THETA,
            success(extraKeyFromLegacyStorage("SUBLINGUAL_THETA"))
        )
        assertEquals(
            DomainExtraKey.SUBLINGUAL_TIER,
            success(extraKeyFromLegacyStorage("SUBLINGUAL_TIER"))
        )
        assertEquals(
            DomainExtraKey.ANTI_ANDROGEN_TYPE,
            success(extraKeyFromLegacyStorage("ANTI_ANDROGEN_TYPE"))
        )
    }

    @Test
    fun allDomainKeysMapToLegacyStorageKeys() {
        assertEquals("CONCENTRATION_MG_ML", DomainExtraKey.CONCENTRATION_MG_ML.toLegacyStorageKey())
        assertEquals("AREA_CM2", DomainExtraKey.AREA_CM2.toLegacyStorageKey())
        assertEquals(
            "RELEASE_RATE_UG_PER_DAY",
            DomainExtraKey.RELEASE_RATE_UG_PER_DAY.toLegacyStorageKey()
        )
        assertEquals("SUBLINGUAL_THETA", DomainExtraKey.SUBLINGUAL_THETA.toLegacyStorageKey())
        assertEquals("SUBLINGUAL_TIER", DomainExtraKey.SUBLINGUAL_TIER.toLegacyStorageKey())
        assertEquals("ANTI_ANDROGEN_TYPE", DomainExtraKey.ANTI_ANDROGEN_TYPE.toLegacyStorageKey())
    }

    @Test
    fun allDomainKeysMapToPkKeysExplicitly() {
        assertEquals(PkExtraKey.CONCENTRATION_MG_ML, DomainExtraKey.CONCENTRATION_MG_ML.toPkExtraKey())
        assertEquals(PkExtraKey.AREA_CM2, DomainExtraKey.AREA_CM2.toPkExtraKey())
        assertEquals(
            PkExtraKey.RELEASE_RATE_UG_PER_DAY,
            DomainExtraKey.RELEASE_RATE_UG_PER_DAY.toPkExtraKey()
        )
        assertEquals(PkExtraKey.SUBLINGUAL_THETA, DomainExtraKey.SUBLINGUAL_THETA.toPkExtraKey())
        assertEquals(PkExtraKey.SUBLINGUAL_TIER, DomainExtraKey.SUBLINGUAL_TIER.toPkExtraKey())
        assertEquals(PkExtraKey.ANTI_ANDROGEN_TYPE, DomainExtraKey.ANTI_ANDROGEN_TYPE.toPkExtraKey())
    }

    @Test
    fun allPkKeysMapToDomainKeysExplicitly() {
        assertEquals(DomainExtraKey.CONCENTRATION_MG_ML, PkExtraKey.CONCENTRATION_MG_ML.toDomainExtraKey())
        assertEquals(DomainExtraKey.AREA_CM2, PkExtraKey.AREA_CM2.toDomainExtraKey())
        assertEquals(
            DomainExtraKey.RELEASE_RATE_UG_PER_DAY,
            PkExtraKey.RELEASE_RATE_UG_PER_DAY.toDomainExtraKey()
        )
        assertEquals(DomainExtraKey.SUBLINGUAL_THETA, PkExtraKey.SUBLINGUAL_THETA.toDomainExtraKey())
        assertEquals(DomainExtraKey.SUBLINGUAL_TIER, PkExtraKey.SUBLINGUAL_TIER.toDomainExtraKey())
        assertEquals(DomainExtraKey.ANTI_ANDROGEN_TYPE, PkExtraKey.ANTI_ANDROGEN_TYPE.toDomainExtraKey())
    }

    @Test
    fun storageAndDomainMappingsRoundTripAllKeys() {
        assertStorageRoundTrip("CONCENTRATION_MG_ML", DomainExtraKey.CONCENTRATION_MG_ML)
        assertStorageRoundTrip("AREA_CM2", DomainExtraKey.AREA_CM2)
        assertStorageRoundTrip("RELEASE_RATE_UG_PER_DAY", DomainExtraKey.RELEASE_RATE_UG_PER_DAY)
        assertStorageRoundTrip("SUBLINGUAL_THETA", DomainExtraKey.SUBLINGUAL_THETA)
        assertStorageRoundTrip("SUBLINGUAL_TIER", DomainExtraKey.SUBLINGUAL_TIER)
        assertStorageRoundTrip("ANTI_ANDROGEN_TYPE", DomainExtraKey.ANTI_ANDROGEN_TYPE)
    }

    @Test
    fun domainAndPkMappingsRoundTripAllKeys() {
        assertPkRoundTrip(DomainExtraKey.CONCENTRATION_MG_ML)
        assertPkRoundTrip(DomainExtraKey.AREA_CM2)
        assertPkRoundTrip(DomainExtraKey.RELEASE_RATE_UG_PER_DAY)
        assertPkRoundTrip(DomainExtraKey.SUBLINGUAL_THETA)
        assertPkRoundTrip(DomainExtraKey.SUBLINGUAL_TIER)
        assertPkRoundTrip(DomainExtraKey.ANTI_ANDROGEN_TYPE)
    }

    @Test
    fun unknownStorageKeyReturnsExplicitFailure() {
        val result = extraKeyFromLegacyStorage("UNKNOWN_KEY")

        assertTrue(result is MappingResult.Failure)
        assertEquals(
            MappingError.InvalidExtraKey("UNKNOWN_KEY"),
            (result as MappingResult.Failure).error
        )
    }

    private fun success(result: MappingResult<DomainExtraKey>): DomainExtraKey {
        assertTrue(result is MappingResult.Success)
        return (result as MappingResult.Success).value
    }

    private fun assertStorageRoundTrip(storage: String, domain: DomainExtraKey) {
        assertEquals(storage, success(extraKeyFromLegacyStorage(storage)).toLegacyStorageKey())
        assertEquals(domain, success(extraKeyFromLegacyStorage(domain.toLegacyStorageKey())))
    }

    private fun assertPkRoundTrip(domain: DomainExtraKey) {
        assertEquals(domain, domain.toPkExtraKey().toDomainExtraKey())
    }
}
