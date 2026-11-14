package com.hamamatsu.android.dogeza

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.min

/**
 * 土下座専用Viewクラス
 * @author TAKA@はままつ
 */
class DogezaView : SurfaceView, SurfaceHolder.Callback {

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        initialize()
    }

    private fun initialize() {
        Log.d("dogeza", "initialize")
        holder.addCallback(this)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        createBackBuffer()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        drawBackBuffer(this, holder)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        releaseBuffer()
    }

    companion object {
        /**
         * 基準論理画面サイズ (BT-200の960x540をベースとし、より大きい画像があれば拡張)
         */
        private const val BASE_WIDTH = 960
        private const val BASE_HEIGHT = 540

        private var logicalWidth = BASE_WIDTH
        private var logicalHeight = BASE_HEIGHT

        private var zoomVal = 1.0f

        /**
         * イメージリソースIDマップ
         */
        private val mImgMap = HashMap<String, Int>()

        /**
         * デコード済みBitmapキャッシュ
         */
        private val mBitmapCache = HashMap<String, Bitmap>()

        /**
         * Viewインスタンス
         */
        private var mView: DogezaView? = null

        /**
         * 描画用キャンバス
         */
        private var mCanvas: Canvas? = null

        /**
         * バックバッファ（論理画面サイズ）
         */
        private var mBkBuff: Bitmap? = null

        /**
         * インスタンス生成
         * DogezaViewのインスタンスは、アプリケーション内で一つだけでいい
         */
        @JvmStatic
        fun createView(context: Context): DogezaView {
            val view = DogezaView(context)
            mView = view
            return view
        }

        /**
         * リソースのイメージを登録します
         */
        @JvmStatic
        fun registImage(imgName: String, id: Int) {
            mImgMap[imgName] = id
            mBitmapCache.remove(imgName)
        }

        /**
         * 元画像のピクセルサイズそのまま（非スケーリング）でBitmapを取得・キャッシュします
         */
        private fun getBitmap(imgName: String): Bitmap? {
            mBitmapCache[imgName]?.let {
                if (!it.isRecycled) return it
            }
            val view = mView ?: return null
            val resId = mImgMap[imgName] ?: return null
            val options = BitmapFactory.Options().apply {
                inScaled = false
            }
            val bmp = BitmapFactory.decodeResource(view.context.resources, resId, options)
            if (bmp != null) {
                bmp.density = Bitmap.DENSITY_NONE
                mBitmapCache[imgName] = bmp
                if (imgName == "title") {
                    logicalWidth = maxOf(BASE_WIDTH, bmp.width)
                    logicalHeight = maxOf(BASE_HEIGHT, bmp.height)
                }
            }
            return bmp
        }

        /**
         * 描画準備
         */
        @JvmStatic
        fun drawBegin() {
            try {
                mCanvas = null
                val view = mView ?: return
                if (view.width <= 0 || view.height <= 0) return

                // タイトル画像サイズに合わせて論理キャンバスサイズを確定
                if (mBitmapCache["title"] == null && mImgMap.containsKey("title")) {
                    getBitmap("title")
                }

                if (mBkBuff == null || mBkBuff?.width != logicalWidth || mBkBuff?.height != logicalHeight) {
                    mBkBuff = Bitmap.createBitmap(
                        logicalWidth,
                        logicalHeight,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        density = Bitmap.DENSITY_NONE
                    }
                }
                val bkBuff = mBkBuff ?: return
                val canvas = Canvas(bkBuff).apply {
                    density = Bitmap.DENSITY_NONE
                }
                mCanvas = canvas

                val paint = Paint().apply {
                    color = Color.BLACK
                }
                val r = Rect(0, 0, logicalWidth, logicalHeight)
                canvas.drawRect(r, paint)
            } catch (_: Exception) {
            }
        }

        /**
         * 描画終了（論理バックバッファから実画面へアスペクト比を維持して中央スケーリング描画）
         */
        @JvmStatic
        fun drawEnd() {
            var canvas: Canvas? = null
            try {
                val view = mView ?: return
                val bkBuff = mBkBuff ?: return
                val holder = view.holder
                canvas = holder.lockCanvas() ?: return
                try {
                    canvas.drawColor(Color.BLACK)
                    val targetW = if (view.width > 0) view.width else canvas.width
                    val targetH = if (view.height > 0) view.height else canvas.height
                    val srcRect = Rect(0, 0, bkBuff.width, bkBuff.height)
                    val dstRect = calcAspectFitRect(
                        bkBuff.width,
                        bkBuff.height,
                        targetW,
                        targetH
                    )
                    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(bkBuff, srcRect, dstRect, paint)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mCanvas = null
            }
        }

        private fun calcAspectFitRect(
            srcW: Int,
            srcH: Int,
            dstW: Int,
            dstH: Int
        ): Rect {
            if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) {
                return Rect(0, 0, dstW, dstH)
            }
            val scale = min(dstW.toFloat() / srcW.toFloat(), dstH.toFloat() / srcH.toFloat())
            val scaledW = (srcW * scale).toInt()
            val scaledH = (srcH * scale).toInt()
            val left = (dstW - scaledW) / 2
            val top = (dstH - scaledH) / 2
            return Rect(left, top, left + scaledW, top + scaledH)
        }

        /**
         * イメージ描画
         */
        @JvmStatic
        fun drawImage(imgName: String, x: Int, y: Int, paint: Paint) {
            val canvas = mCanvas ?: return
            val bmp = getBitmap(imgName) ?: return
            val src = Rect(0, 0, bmp.width, bmp.height)
            val dst = Rect(x, y, x + bmp.width, y + bmp.height)
            canvas.drawBitmap(bmp, src, dst, paint)
        }

        /**
         * イメージ描画（サイズ指定）
         */
        @JvmStatic
        fun drawImage(imgName: String, x: Int, y: Int, width: Int, height: Int, paint: Paint) {
            val canvas = mCanvas ?: return
            val bmp = getBitmap(imgName) ?: return
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            val distRect = Rect(x, y, (width * zoomVal).toInt(), (height * zoomVal).toInt())
            canvas.drawBitmap(bmp, srcRect, distRect, paint)
        }

        @JvmStatic
        fun drawImageToCenter(
            imgName: String,
            cnt: Int,
            maxCnt: Int,
            vDist: Distinct,
            hDist: Distinct,
            paint: Paint
        ) {
            val canvas = mCanvas ?: return
            val bmp = getBitmap(imgName) ?: return

            val bmpW = bmp.width
            val bmpH = bmp.height

            val dispX = (logicalWidth - bmpW) / 2
            val dispY = (logicalHeight - bmpH) / 2

            var x = dispX
            var y = dispY
            val clampedCnt = if (maxCnt < cnt) maxCnt else cnt

            when (vDist) {
                Distinct.ToDown -> y = (dispY + bmpH) / maxCnt * clampedCnt - bmpH
                Distinct.ToUp -> y = logicalHeight - dispY / maxCnt * clampedCnt
                else -> {}
            }
            when (hDist) {
                Distinct.ToLeft -> x = dispX / maxCnt * clampedCnt
                Distinct.ToRight -> x = logicalWidth - dispX / maxCnt * clampedCnt
                else -> {}
            }

            canvas.drawBitmap(bmp, x.toFloat(), y.toFloat(), paint)
        }

        @JvmStatic
        fun drawImageCenter(imgName: String, paint: Paint, zoom: Float) {
            val canvas = mCanvas ?: return
            val bmp = getBitmap(imgName) ?: return

            val safeZoom = if (zoom <= 0.0f) 0.01f else zoom
            val scaledW = maxOf(1, (bmp.width * safeZoom).toInt())
            val scaledH = maxOf(1, (bmp.height * safeZoom).toInt())
            val dispX = (logicalWidth - scaledW) / 2
            val dispY = (logicalHeight - scaledH) / 2
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            val dstRect = Rect(dispX, dispY, dispX + scaledW, dispY + scaledH)
            paint.isFilterBitmap = true
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }

        /**
         * 画像を画面中央に表示
         */
        @JvmStatic
        fun drawImageCenter(imgName: String, paint: Paint) {
            val canvas = mCanvas ?: return
            val bmp = getBitmap(imgName) ?: return

            val dispX = (logicalWidth - bmp.width) / 2
            val dispY = (logicalHeight - bmp.height) / 2
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            val dstRect = Rect(dispX, dispY, dispX + bmp.width, dispY + bmp.height)
            canvas.drawBitmap(bmp, srcRect, dstRect, paint)
        }

        /**
         * 文字列を表示
         */
        @JvmStatic
        fun drawText(text: String, x: Int, y: Int, paint: Paint) {
            val canvas = mCanvas ?: return
            val fm = paint.fontMetrics
            canvas.drawText(
                text,
                (x * zoomVal),
                (y * zoomVal) - fm.top,
                paint
            )
        }

        private fun createBackBuffer() {
            if (logicalWidth > 0 && logicalHeight > 0) {
                mBkBuff = Bitmap.createBitmap(
                    logicalWidth,
                    logicalHeight,
                    Bitmap.Config.ARGB_8888
                ).apply {
                    density = Bitmap.DENSITY_NONE
                }
            }
        }

        private fun drawBackBuffer(view: DogezaView, holder: SurfaceHolder) {
            val canvas = holder.lockCanvas() ?: return
            try {
                if (mBkBuff == null) {
                    createBackBuffer()
                }
                val bkBuff = mBkBuff
                if (bkBuff != null) {
                    canvas.drawColor(Color.BLACK)
                    val targetW = if (view.width > 0) view.width else canvas.width
                    val targetH = if (view.height > 0) view.height else canvas.height
                    val srcRect = Rect(0, 0, bkBuff.width, bkBuff.height)
                    val dstRect = calcAspectFitRect(
                        bkBuff.width,
                        bkBuff.height,
                        targetW,
                        targetH
                    )
                    canvas.drawBitmap(bkBuff, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun releaseBuffer() {
            mBkBuff = null
        }
    }
}

