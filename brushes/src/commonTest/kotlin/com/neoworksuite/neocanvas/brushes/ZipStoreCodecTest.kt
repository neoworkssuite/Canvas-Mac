package com.neoworksuite.neocanvas.brushes

import kotlin.test.*

class ZipStoreCodecTest {
    @Test fun stored_zip_round_trip_preserves_order_and_bytes() {
        val entries = linkedMapOf("manifest.json" to "{}".encodeToByteArray(), "brushes/a.neobrush" to byteArrayOf(1, 2))
        val decoded = ZipStoreCodec.decode(ZipStoreCodec.encode(entries))
        assertEquals(entries.keys.toList(), decoded.keys.toList())
        entries.forEach { (name, bytes) -> assertContentEquals(bytes, decoded.getValue(name)) }
    }

    @Test fun unsafe_duplicate_nested_and_non_store_entries_are_rejected() {
        listOf("../evil", "/absolute", "folder\\..\\evil", "nested.zip").forEach { name ->
            assertFailsWith<PackValidationException>(name) { ZipStoreCodec.encode(linkedMapOf(name to byteArrayOf(1))) }
        }
        val valid = ZipStoreCodec.encode(linkedMapOf("a" to byteArrayOf(1)))
        assertFailsWith<PackValidationException> { ZipStoreCodec.decode(valid.copyOf().also { it[8] = 8 }) }
        assertFailsWith<PackValidationException> { ZipStoreCodec.decode(valid.copyOf().also { it[6] = 1 }) }
    }

    @Test fun archive_limits_are_enforced_before_allocation() {
        assertFailsWith<PackValidationException> {
            ZipStoreCodec.encode((0..250).associate { "entry-$it" to byteArrayOf(1) })
        }
    }
}
