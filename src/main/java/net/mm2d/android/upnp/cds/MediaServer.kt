/*
 * Copyright (c) 2016 大前良介 (OHMAE Ryosuke)
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/MIT
 */

package net.mm2d.android.upnp.cds

import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import net.mm2d.android.upnp.DeviceWrapper
import net.mm2d.upnp.Action
import net.mm2d.upnp.Device
import net.mm2d.upnp.Service

/**
 * MediaServerを表現するクラス。
 *
 * @author [大前良介 (OHMAE Ryosuke)](mailto:ryo@mm2d.net)
 */
class MediaServer
/**
 * インスタンスを作成する。
 *
 * パッケージ外でのインスタンス化禁止
 *
 * @param device デバイス
 */
internal constructor(
    val device: Device
) : DeviceWrapper(device) {
    private val cdsService: Service
    private val browse: Action
    private val destroyObject: Action?

    init {
        require(device.deviceType.startsWith(Cds.MS_DEVICE_TYPE)) { "device is not MediaServer" }
        cdsService = device.findServiceById(Cds.CDS_SERVICE_ID)
            ?: throw IllegalArgumentException("Device don't have cds service")
        browse = cdsService.findAction(BROWSE)
            ?: throw IllegalArgumentException("Device don't have browse action")
        destroyObject = cdsService.findAction(DESTROY_OBJECT)
    }

    class Result(
        val list: List<CdsObject>,
        val start: Int,
        val number: Int,
        val total: Int,
        val description: String
    )

    /**
     * Browseを実行する。
     *
     * @param objectId       ObjectID
     * @param filter         filter
     * @param sortCriteria   sortCriteria
     * @param startingIndex  startIndex
     * @param requestedCount requestedCount
     * @return 結果
     */
    fun browse(
        objectId: String,
        filter: String? = "*",
        sortCriteria: String? = null,
        startingIndex: Int = 0,
        requestedCount: Int = 0
    ): Observable<Result> {
        val argument = BrowseArgument()
            .setObjectId(objectId)
            .setBrowseDirectChildren()
            .setFilter(filter)
            .setSortCriteria(sortCriteria)
        val request = if (requestedCount == 0) Integer.MAX_VALUE else requestedCount
        return Observable.create { emitter: ObservableEmitter<Result> ->
            var start = startingIndex
            while (!emitter.isDisposed) {
                argument.setStartIndex(start)
                    .setRequestCount(minOf(request - start, REQUEST_MAX))
                val response = BrowseResponse(browse.invokeSync(argument.get(), false))
                val number = response.numberReturned
                val total = response.totalMatches
                if (number == 0 || total == 0) {
                    emitter.onComplete()
                    return@create
                }
                val result = CdsObjectFactory.parseDirectChildren(udn, response.result)
                if (result.isEmpty() || number < 0 || total < 0) {
                    emitter.onError(IllegalStateException())
                    return@create
                }
                emitter.onNext(
                    Result(
                        result,
                        start,
                        number,
                        total,
                        response.result!!
                    )
                )
                start += number
                if (start >= total || start >= request) {
                    break
                }
            }
            emitter.onComplete()
        }
    }

    companion object {
        const val NO_ERROR = 0
        private const val BROWSE = "Browse"
        private const val DESTROY_OBJECT = "DestroyObject"
        private const val OBJECT_ID = "ObjectID"
        private const val REQUEST_MAX = 10
    }
}
