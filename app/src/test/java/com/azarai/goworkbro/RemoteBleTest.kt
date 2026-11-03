package com.azarai.goworkbro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.azarai.goworkbro.core.remote.BleServer
import com.azarai.goworkbro.core.remote.RemoteApi
import com.azarai.goworkbro.core.remote.RemoteBle
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The radio itself cannot be exercised here (Robolectric has no Bluetooth), so
 * this covers exactly what can be: the gate in front of it, and the wire
 * identifiers the PC will hard-code.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = GoWorkBroApp::class)
class RemoteBleTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun readinessAsksForThePermissionsBeforeTouchingTheRadio() {
        val readiness = RemoteBle.readiness(context)
        assertTrue(readiness is RemoteBle.Readiness.NeedsPermission)
        val asked = (readiness as RemoteBle.Readiness.NeedsPermission).permissions.toList()
        assertTrue(asked.contains(android.Manifest.permission.BLUETOOTH_ADVERTISE))
        assertTrue(asked.contains(android.Manifest.permission.BLUETOOTH_CONNECT))
        // Advertising only: a scan permission here would also cost location.
        assertTrue(!asked.contains(android.Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun gattIdentifiersAreDistinctAndWellFormed() {
        val uuids: List<UUID> = listOf(BleServer.SERVICE, BleServer.INFO, BleServer.UP, BleServer.DOWN)
        assertEquals(uuids.size, uuids.toSet().size)
        uuids.forEach { assertEquals(it, UUID.fromString(it.toString())) }
        // One 128-bit base, only the first group differs: one service, three members.
        val base: String = BleServer.SERVICE.toString().substringAfter('-')
        uuids.forEach { assertEquals(base, it.toString().substringAfter('-')) }
        assertTrue(RemoteApi.PROTOCOL > 0)
    }
}
