package com.azarai.goworkbro.core.remote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What the radio needs before it can host the PC link. */
object RemoteBle {

    sealed interface Readiness {
        data object Ready : Readiness
        data class NeedsPermission(val permissions: Array<String>) : Readiness
        data object AdapterOff : Readiness
        data class Blocked(val reason: String) : Readiness
    }

    /** Every permission the link needs at once — one prompt, no drip-feeding. */
    fun requiredPermissions(): Array<String> = buildList {
        // API 31 split the old BLUETOOTH permission in three; we only advertise
        // and accept a connection, so SCAN (and with it location) stays off.
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // The foreground notification is the only sign of life for a link with
        // no UI; without it the user cannot tell it is running.
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    fun readiness(context: Context): Readiness {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) return Readiness.NeedsPermission(missing.toTypedArray())

        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return Readiness.Blocked("这台手机没有蓝牙")
        // A pure peripheral needs advertising; some BLE chips cannot do it.
        if (!adapter.isMultipleAdvertisementSupported) {
            return Readiness.Blocked("这台手机的蓝牙不支持广播，协同用不了")
        }
        if (!adapter.isEnabled) return Readiness.AdapterOff
        return Readiness.Ready
    }
}

/**
 * The phone as a BLE peripheral: it advertises [SERVICE] and turns GATT into a
 * byte pipe (`UP` in, `DOWN` out) for [Framing]-encoded [RemoteApi] requests.
 *
 * Two Android quirks are handled explicitly, both of them fatal if ignored
 * (verified against TwinDesk's production `BleServe.kt`):
 *
 *  - GATT server callbacks are serialized, and a Linux central refuses our
 *    write response while one of our notifications is still in flight — it
 *    then waits its 30 s ATT timeout and drops the link. So responses are
 *    handed to their own thread with a short retry, and notifications wait
 *    for any response still pending.
 *  - Notifications must go out one at a time: the next waits for
 *    `onNotificationSent`. A frame is therefore cut into (MTU − 3) chunks and
 *    fed to the notifier queue.
 *
 * One process, one PC at a time in practice — but several centrals may connect
 * and each keeps its own reassembly buffer.
 */
@SuppressLint("MissingPermission") // RemoteBle.readiness gates every entry point
class BleServer(
    private val context: Context,
    private val onRequest: suspend (String) -> String,
    private val onClients: (Int) -> Unit = {},
) {

    companion object {
        private const val TAG = "GWBRemote"

        /** Our own primary service; the PC scans for it before anything else. */
        val SERVICE: UUID = UUID.fromString("5c0de000-9a3e-4f6b-b1d2-7e8f4a6c2d10")

        /** read: a tiny hello, so the PC can prove it found the right service. */
        val INFO: UUID = UUID.fromString("5c0de001-9a3e-4f6b-b1d2-7e8f4a6c2d10")

        /** write / write-no-response: framed requests, PC -> phone. */
        val UP: UUID = UUID.fromString("5c0de002-9a3e-4f6b-b1d2-7e8f4a6c2d10")

        /** notify: framed responses, phone -> PC. */
        val DOWN: UUID = UUID.fromString("5c0de003-9a3e-4f6b-b1d2-7e8f4a6c2d10")

        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /** An attribute value is at most 512 bytes no matter how big the MTU is. */
        private const val MAX_ATTRIBUTE = 512

        private const val DEFAULT_MTU = 23
    }

    private class Incoming(val device: BluetoothDevice, val frame: String)
    private class Outgoing(val device: BluetoothDevice, val bytes: ByteArray)

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var server: BluetoothGattServer? = null
    private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
    private var down: BluetoothGattCharacteristic? = null
    private var notifier: Thread? = null
    private var responder: Thread? = null
    @Volatile private var running = false
    @Volatile private var added = CountDownLatch(0)

    /** Per central: its MTU, its half-received frame, and the device itself. */
    private val clients = ConcurrentHashMap<String, BluetoothDevice>()
    private val mtus = ConcurrentHashMap<String, Int>()
    private val readers = ConcurrentHashMap<String, Framing.Reader>()

    private val requests = Channel<Incoming>(Channel.UNLIMITED)
    private val outbox = LinkedBlockingQueue<Outgoing>()
    private val responses = LinkedBlockingQueue<() -> Unit>()
    private val noteSent = Semaphore(0)
    private val responding = AtomicInteger()

    // ---- lifecycle ----

    /** null when the radio is up, otherwise a reason for the settings page. */
    fun start(): String? {
        if (server != null) return null
        val adapter = manager?.adapter ?: return "这台手机没有蓝牙"
        if (!adapter.isEnabled) return "蓝牙没开"
        val gatt = runCatching { manager.openGattServer(context, callback) }.getOrNull()
            ?: return "打不开蓝牙服务"
        val service = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
            addCharacteristic(
                BluetoothGattCharacteristic(
                    INFO,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ,
                ),
            )
            addCharacteristic(
                BluetoothGattCharacteristic(
                    UP,
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE,
                ),
            )
            addCharacteristic(
                BluetoothGattCharacteristic(
                    DOWN,
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                    0,
                ).apply {
                    addDescriptor(
                        BluetoothGattDescriptor(
                            CCCD,
                            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
                        ),
                    )
                },
            )
        }
        added = CountDownLatch(1)
        if (!gatt.addService(service)) {
            runCatching { gatt.close() }
            return "蓝牙服务注册失败"
        }
        added.await(3, TimeUnit.SECONDS)

        server = gatt
        down = gatt.getService(SERVICE)?.getCharacteristic(DOWN)

        running = true
        notifier = Thread({ notifyLoop() }, "gwb-ble-notify").apply { isDaemon = true; start() }
        responder = Thread({ respondLoop() }, "gwb-ble-respond").apply { isDaemon = true; start() }
        scope.launch { requestLoop() }

        val btAdvertiser = adapter.bluetoothLeAdvertiser
        if (btAdvertiser == null) {
            Log.w(TAG, "no LE advertiser")
            return "蓝牙不支持广播"
        }
        advertiser = btAdvertiser
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE))
            .setIncludeDeviceName(false)
            .build()
        runCatching { btAdvertiser.startAdvertising(settings, data, advertiseCallback) }
            .onFailure {
                Log.w(TAG, "advertise failed: ${it.message}")
                return "蓝牙广播启动失败"
            }
        Log.i(TAG, "advertising as ${SERVICE}")
        return null
    }

    fun stop() {
        running = false
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
        advertiser = null
        clients.values.forEach { device -> runCatching { server?.cancelConnection(device) } }
        clients.clear()
        mtus.clear()
        readers.clear()
        outbox.clear()
        responses.clear()
        noteSent.drainPermits()
        runCatching { server?.close() }
        server = null
        notifier?.interrupt()
        notifier = null
        responder?.interrupt()
        responder = null
        scope.cancel()
        onClients(0)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "advertising")
        }

        override fun onStartFailure(errorCode: Int) {
            // ADVERTISE_FAILED_ALREADY_STARTED etc. — the link just stays silent.
            Log.w(TAG, "advertise failed with code $errorCode")
        }
    }

    // ---- request pipeline ----

    /** One request at a time: the ops all write the same database. */
    private suspend fun requestLoop() {
        for (incoming in requests) {
            val reply = runCatching { onRequest(incoming.frame) }.getOrElse { e ->
                JSONObject()
                    .put("ok", false)
                    .put("error", "internal")
                    .put("message", e.message ?: e.javaClass.simpleName)
                    .toString()
            }
            val frame = runCatching { Framing.encode(reply) }.getOrNull() ?: continue
            outbox.put(Outgoing(incoming.device, frame))
        }
    }

    private fun notifyLoop() {
        while (running) {
            val out = try {
                outbox.take()
            } catch (e: InterruptedException) {
                return
            }
            send(out)
        }
    }

    private fun send(out: Outgoing) {
        val characteristic = down ?: return
        val mtu = mtus[out.device.address] ?: DEFAULT_MTU
        val chunkSize = (mtu - 3).coerceIn(20, MAX_ATTRIBUTE)
        var offset = 0
        while (offset < out.bytes.size && running) {
            val length = minOf(chunkSize, out.bytes.size - offset)
            val chunk = out.bytes.copyOfRange(offset, offset + length)
            if (!notify(out.device, characteristic, chunk)) return
            offset += length
        }
    }

    private fun notify(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        chunk: ByteArray,
    ): Boolean {
        // Let a write response go out first: a Linux central drops it otherwise.
        var waited = 0
        while (responding.get() > 0 && waited++ < 1500) Thread.sleep(2)
        val gatt = server ?: return false
        noteSent.drainPermits()
        repeat(100) {
            val sent = runCatching {
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.notifyCharacteristicChanged(device, characteristic, false, chunk) ==
                        BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = chunk
                    @Suppress("DEPRECATION")
                    gatt.notifyCharacteristicChanged(device, characteristic, false)
                }
            }.getOrDefault(false)
            if (sent) {
                noteSent.tryAcquire(2, TimeUnit.SECONDS)
                return true
            }
            Thread.sleep(10)
        }
        Log.w(TAG, "notify failed, dropping the frame")
        return false
    }

    private fun respondLoop() {
        while (running) {
            val action = try {
                responses.take()
            } catch (e: InterruptedException) {
                return
            }
            action()
        }
    }

    /**
     * Acknowledges a write off the callback thread. Android serializes those
     * callbacks, so blocking in one would also stall `onNotificationSent`.
     */
    private fun respond(device: BluetoothDevice, requestId: Int, offset: Int, value: ByteArray?) {
        responding.incrementAndGet()
        responses.put {
            var ok = false
            var tries = 0
            while (!ok && tries++ < 400) {
                ok = runCatching {
                    server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value) ?: false
                }.getOrDefault(false)
                if (!ok) Thread.sleep(5)
            }
            if (!ok) Log.w(TAG, "a write response never went out")
            responding.decrementAndGet()
        }
    }

    // ---- GATT server ----

    private val callback = object : BluetoothGattServerCallback() {

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            added.countDown()
        }

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                clients[device.address] = device
                Log.i(TAG, "connected: ${device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clients.remove(device.address)
                mtus.remove(device.address)
                readers.remove(device.address)
                Log.i(TAG, "disconnected: ${device.address}")
            }
            onClients(clients.size)
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            mtus[device.address] = mtu
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val gatt = server ?: return
            if (characteristic.uuid != INFO) {
                gatt.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                return
            }
            val value = JSONObject()
                .put("app", "GoWorkBro")
                .put("protocol", RemoteApi.PROTOCOL)
                .toString()
                .toByteArray()
            if (offset > value.size) {
                gatt.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }
            gatt.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                value.copyOfRange(offset, value.size),
            )
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) respond(device, requestId, offset, value)
            if (characteristic.uuid != UP) return
            val chunk = value ?: return
            val reader = readers.getOrPut(device.address) { Framing.Reader() }
            val frames = runCatching { reader.feed(chunk) }.getOrElse {
                reader.reset()
                outbox.put(Outgoing(device, Framing.encode(badFrame())))
                return
            }
            frames.forEach { requests.trySend(Incoming(device, it)) }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor,
        ) {
            server?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                0,
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
            )
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            noteSent.release()
        }
    }

    private fun badFrame(): String = JSONObject()
        .put("ok", false)
        .put("error", "bad_frame")
        .put("message", "帧长度不合法")
        .toString()
}
