package com.fongmi.android.tv.utils

import android.content.Context
import org.fourthline.cling.android.AndroidRouter
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceImpl
import org.fourthline.cling.protocol.ProtocolFactory
import org.fourthline.cling.transport.impl.StreamClientConfigurationImpl
import org.fourthline.cling.transport.impl.StreamClientImpl
import org.fourthline.cling.transport.spi.InitializationException
import org.fourthline.cling.transport.Router
import org.fourthline.cling.transport.spi.NetworkAddressFactory
import org.fourthline.cling.transport.spi.StreamServer
import org.fourthline.cling.transport.spi.StreamServerConfiguration
import java.net.InetAddress
import java.util.logging.Logger

/**
 * 空的 StreamServer 实现
 *
 * 因为本项目仅需控制点功能（发现设备 + 发送 SOAP 控制），
 * 不需要本地服务端（接收其他设备的请求），故所有方法为空操作。
 * 这样可以完全避免 Jetty 依赖。
 */
class NoOpStreamServer : StreamServer<StreamServerConfiguration> {

    private val log = Logger.getLogger(NoOpStreamServer::class.java.name)

    override fun init(inetAddress: InetAddress?, router: Router?) {
        log.fine("NoOpStreamServer: init skipped (no local server needed)")
    }

    override fun getPort(): Int = -1

    override fun stop() {
        log.fine("NoOpStreamServer: stop skipped")
    }

    override fun getConfiguration(): StreamServerConfiguration {
        return StreamServerConfiguration { 0 }
    }

    override fun run() {
        log.fine("NoOpStreamServer: run skipped (no local server needed)")
    }
}

/**
 * 自定义 UPnP 服务配置
 *
 * - createStreamClient: 使用基于 HttpURLConnection 的 StreamClientImpl，不依赖 Jetty
 * - createStreamServer: 返回 NoOpStreamServer，不启动本地服务端
 */
class CastUpnpServiceConfiguration : AndroidUpnpServiceConfiguration() {

    @Throws(InitializationException::class)
    override fun createStreamClient(): StreamClientImpl {
        return try {
            StreamClientImpl(
                StreamClientConfigurationImpl(defaultExecutorService)
            )
        } catch (e: Exception) {
            throw InitializationException("Failed to create StreamClient: ${e.message}", e)
        }
    }

    override fun createStreamServer(networkAddressFactory: NetworkAddressFactory?): StreamServer<*> {
        return NoOpStreamServer()
    }
}

/**
 * 自定义 AndroidUpnpService，使用 CastUpnpServiceConfiguration
 *
 * 替代 AndroidUpnpServiceImpl，避免 Jetty 依赖。
 */
class CastUpnpServiceImpl : AndroidUpnpServiceImpl() {

    override fun createConfiguration(): CastUpnpServiceConfiguration {
        return CastUpnpServiceConfiguration()
    }

    override fun createRouter(
        configuration: org.fourthline.cling.UpnpServiceConfiguration?,
        protocolFactory: ProtocolFactory?,
        context: Context?
    ): AndroidRouter {
        return AndroidRouter(configuration!!, protocolFactory!!, context!!)
    }
}
