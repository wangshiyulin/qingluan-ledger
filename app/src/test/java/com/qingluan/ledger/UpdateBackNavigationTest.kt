@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.qingluan.ledger

import android.app.Application
import android.os.Looper
import android.os.NetworkOnMainThreadException
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.internal.connection.RealConnection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateBackNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private class GuardedSocket : Socket() {
        val closedOffMain = CountDownLatch(1)
        val closedOnMain = AtomicBoolean(false)
        var enforce = true

        override fun close() {
            if (enforce && Looper.myLooper() == Looper.getMainLooper()) {
                closedOnMain.set(true)
                throw NetworkOnMainThreadException()
            }
            closedOffMain.countDown()
        }
    }

    private data class Fixture(val client: OkHttpClient, val socket: GuardedSocket)

    private fun fixture(network: UpdateNetwork): Fixture {
        val json =
            """{"tag_name":"v1.1.0","draft":false,"prerelease":false,"assets":[{"name":"ledger-v1.1.0.apk","size":100,"browser_download_url":"https://github.com/wangshiyulin/qingluan-ledger/releases/download/v1.1.0/ledger-v1.1.0.apk"}]}"""
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(json.toResponseBody())
                        .build()
                }
                .build()
        UpdateNetwork::class
            .java
            .getDeclaredField("http")
            .apply { isAccessible = true }
            .set(network, client)
        val pool = client.connectionPool.delegate
        val address =
            Address(
                "api.github.com",
                443,
                client.dns,
                client.socketFactory,
                client.sslSocketFactory,
                client.hostnameVerifier,
                client.certificatePinner,
                client.proxyAuthenticator,
                null,
                client.protocols,
                client.connectionSpecs,
                client.proxySelector,
            )
        val connection =
            RealConnection(
                pool,
                Route(
                    address,
                    Proxy.NO_PROXY,
                    InetSocketAddress.createUnresolved("api.github.com", 443),
                ),
            )
        val socket = GuardedSocket()
        RealConnection::class
            .java
            .getDeclaredField("socket")
            .apply { isAccessible = true }
            .set(connection, socket)
        val queue =
            pool.javaClass.getDeclaredField("connections").apply { isAccessible = true }.get(pool)
        @Suppress("UNCHECKED_CAST") (queue as ConcurrentLinkedQueue<RealConnection>).add(connection)
        assertEquals(1, client.connectionPool.idleConnectionCount())
        return Fixture(client, socket)
    }

    @Test
    fun clearingUpdateClientClosesIdleSocketOffMain() {
        val network = UpdateNetwork()
        val fixture = fixture(network)
        try {
            compose.runOnUiThread { network.close() }
            assertTrue(fixture.socket.closedOffMain.await(5, TimeUnit.SECONDS))
            assertFalse(fixture.socket.closedOnMain.get())
        } finally {
            fixture.socket.enforce = false
            fixture.client.connectionPool.evictAll()
        }
    }

    @Test
    fun checkCloseReturnAndReenterAboutKeepsActivityOpen() {
        val app = compose.activity.application as Application
        val store = ViewModelStore()
        val owner =
            object : ViewModelStoreOwner {
                override val viewModelStore = store
            }
        val updater =
            ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(app))[
                AppUpdateViewModel::class.java]
        val field =
            AppUpdateViewModel::class.java.getDeclaredField("network").apply { isAccessible = true }
        val fixture = fixture(field.get(updater) as UpdateNetwork)
        val ledger = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        var about by mutableStateOf(true)
        try {
            compose.runOnUiThread {
                compose.activity.setContent {
                    MaterialTheme {
                        if (about)
                            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                                AboutPage(
                                    ledger,
                                    "关于",
                                    back = {
                                        about = false
                                        store.clear()
                                    },
                                    open = {},
                                )
                            }
                        else Text("我的页面")
                    }
                }
            }
            compose
                .onNode(
                    hasClickAction() and (hasText("检测更新") or hasAnyDescendant(hasText("检测更新"))),
                    useUnmergedTree = true,
                )
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(10000) {
                compose.onAllNodes(isRoot()).fetchSemanticsNodes()
                updater.state.value.phase == "current"
            }
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("我的页面").assertExists()
            assertFalse(compose.activity.isFinishing)
            assertFalse(compose.activity.isDestroyed)
            assertTrue(fixture.socket.closedOffMain.await(5, TimeUnit.SECONDS))
            assertFalse(fixture.socket.closedOnMain.get())
            val second =
                ViewModelProvider(
                    owner,
                    ViewModelProvider.AndroidViewModelFactory.getInstance(app),
                )[AppUpdateViewModel::class.java]
            val secondFixture = fixture(field.get(second) as UpdateNetwork)
            compose.runOnUiThread { about = true }
            compose
                .onNode(
                    hasClickAction() and (hasText("检测更新") or hasAnyDescendant(hasText("检测更新"))),
                    useUnmergedTree = true,
                )
                .performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(10000) {
                compose.onAllNodes(isRoot()).fetchSemanticsNodes()
                second.state.value.phase == "current"
            }
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("我的页面").assertExists()
            assertTrue(secondFixture.socket.closedOffMain.await(5, TimeUnit.SECONDS))
            assertFalse(compose.activity.isFinishing)
            assertFalse(secondFixture.socket.closedOnMain.get())
        } finally {
            fixture.socket.enforce = false
            compose.runOnUiThread {
                store.clear()
                compose.activity.setContent {}
            }
            fixture.client.connectionPool.evictAll()
        }
    }
}
