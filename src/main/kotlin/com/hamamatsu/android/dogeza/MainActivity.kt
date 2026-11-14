package com.hamamatsu.android.dogeza

import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import java.util.Calendar
import java.util.Date
import java.util.Random
import kotlin.math.floor

/**
 * MainActivityクラス
 * @author 日本Androidの会 浜松支部
 */
class MainActivity : Activity(), SoundPool.OnLoadCompleteListener {

    companion object {
        // タイマー関連
        /**
         * タイマー更新間隔
         */
        private const val TIMER_UPDATE = 1 * 1000

        /**
         * 起立時間
         */
        private const val TIMER_RISE = 5 * 1000L

        /**
         * 跪く時間
         */
        private const val TIMER_DOWN = 5 * 1000L

        /**
         * 土下座時間
         */
        private const val TIMER_DOGEZA = 5 * 1000L

        /**
         * 面を上げ時間
         */
        private const val TIMER_RISE_FACE = 5 * 1000L

        private const val COUNT_DOGEZA = (TIMER_DOGEZA / TIMER_UPDATE).toInt()
    }

    private var mStartTime: Date = Date()
    private var mTimerOffset: Long = 0
    private var mLoadCnt: Int = 0
    private var mRankSEPlayed: Boolean = false

    /**
     * ゲーム状態
     */
    @Volatile
    private var mStatus: SceneStatus = SceneStatus.STATUS_INIT

    /**
     * タッチイベント処理
     */
    private var mGesDetector: GestureDetector? = null

    /**
     * 乱数
     */
    private var mRand: Random = Random()

    /**
     * 「土下座」推定クラス
     */
    private var mDogezaEstimation: DogezaEstimation? = null

    /**
     * シーンマップ
     */
    private val mSceneMap = HashMap<SceneStatus, () -> Unit>()

    /**
     * 制御用スレッド
     */
    @Volatile
    private var mThread: Thread? = null

    /**
     * 得点マップ
     */
    private val mScoreMap = LinkedHashMap<Float, Int>()
    private val mScoreImgMap = LinkedHashMap<Float, String>()

    /**
     * タップ有効フラグ
     */
    @Volatile
    private var mIsTapEnable: Boolean = true

    /**
     * BGM再生用
     */
    private var mPlayer: MediaPlayer? = null

    /**
     * 効果音再生
     */
    private var mSound: SoundPool? = null
    private val mSoundMap = HashMap<String, Int>()

    /**
     * スコア
     */
    private var mScore: Float = 0f

    /**
     * ランク表示
     */
    private var mRankImg: String = ""

    /**
     * 初期化処理
     */
    private fun init() {
        // シーン設定
        mSceneMap.clear()
        mSceneMap[SceneStatus.STATUS_INIT] = ::onStatusInit
        mSceneMap[SceneStatus.STATUS_RISE_START] = ::onStatusRiseStart
        mSceneMap[SceneStatus.STATUS_RISE_TIMER] = ::onStatusRiseTimer
        mSceneMap[SceneStatus.STATUS_DOWN_TIMER] = ::onStatusDownTimer
        mSceneMap[SceneStatus.STATUS_DOGEZA_TIMER] = ::onStatusDogezaTimer
        mSceneMap[SceneStatus.STATUS_RISE_FACE_TIMER] = ::onStatusRiseFaceTimer
        mSceneMap[SceneStatus.STATUS_JUDGE] = ::onStatusJudge

        // 得点設定
        mScoreMap.clear()
        mScoreMap[90.0f] = R.string.score_level_5
        mScoreMap[70.0f] = R.string.score_level_4
        mScoreMap[50.0f] = R.string.score_level_3
        mScoreMap[30.0f] = R.string.score_level_2
        mScoreMap[-1.0f] = R.string.score_level_1

        mScoreImgMap.clear()
        mScoreImgMap[90.0f] = "lv5"
        mScoreImgMap[70.0f] = "lv4"
        mScoreImgMap[50.0f] = "lv3"
        mScoreImgMap[30.0f] = "lv2"
        mScoreImgMap[-1.0f] = "lv1"

        // イメージの登録
        DogezaView.registImage("title", R.drawable.title)
        DogezaView.registImage("kiritsu", R.drawable.kiritsu)
        DogezaView.registImage("hizamaduke", R.drawable.hizamaduke)
        DogezaView.registImage("dogeza", R.drawable.dogeza)
        DogezaView.registImage("omotewoage", R.drawable.omotewoage)
        DogezaView.registImage("0", R.drawable.num0)
        DogezaView.registImage("1", R.drawable.num1)
        DogezaView.registImage("2", R.drawable.num2)
        DogezaView.registImage("3", R.drawable.num3)
        DogezaView.registImage("4", R.drawable.num4)
        DogezaView.registImage("5", R.drawable.num5)
        DogezaView.registImage("6", R.drawable.num6)
        DogezaView.registImage("7", R.drawable.num7)
        DogezaView.registImage("8", R.drawable.num8)
        DogezaView.registImage("9", R.drawable.num9)
        DogezaView.registImage("lv1", R.drawable.lv1)
        DogezaView.registImage("lv2", R.drawable.lv2)
        DogezaView.registImage("lv3", R.drawable.lv3)
        DogezaView.registImage("lv4", R.drawable.lv4)
        DogezaView.registImage("lv5", R.drawable.lv5)

        // タッチイベント処理設定
        mGesDetector = GestureDetector(this, onGestureListener)
        // 乱数初期化
        mRand = Random()
        // 初期状態へ設定
        setStatus(SceneStatus.STATUS_INIT)

        // 「土下座」推定クラスの生成
        mDogezaEstimation = DogezaEstimation(this)

        // スレッドの開始
        startApplication()
    }

    /**
     * 開始処理
     */
    fun startApplication() {
        if (mThread != null) return
        val thread = Thread {
            while (mThread != null) {
                try {
                    Thread.sleep((1000 / 60).toLong())
                } catch (e: Exception) {
                    Log.e("DoGeZa", e.localizedMessage ?: "sleep error")
                }
                proc()
            }
        }
        mThread = thread
        thread.start()
    }

    /**
     * 終了処理
     */
    fun endApplication() {
        mThread = null
        try {
            mPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mPlayer = null
        } catch (e: Exception) {
            Log.e("DoGeZa", e.localizedMessage ?: "player release error")
        }

        try {
            mSound?.release()
            mSound = null
        } catch (e: Exception) {
            Log.e("DoGeZa", e.localizedMessage ?: "sound release error")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 画面常時点灯
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // メイン画面設定
        try {
            setContentView(DogezaView.createView(applicationContext))
        } catch (e: Exception) {
            Log.d("DoGeZa", e.message ?: "createView error")
        }

        hideSystemUI()

        mSoundMap.clear()
        val selist = hashMapOf(
            "kiritsu" to R.raw.v01_kiritsu,
            "hizamazuke" to R.raw.v02_hizamazuke,
            "dogeza" to R.raw.v03_dogeza,
            "omotewoage" to R.raw.v04_omotewoage,
            "lv1" to R.raw.v10_zakowa,
            "lv2" to R.raw.v11_kutihodo,
            "lv3" to R.raw.v12_tedare,
            "lv4" to R.raw.v13_henotuppari,
            "lv5" to R.raw.v14_appare,
            "taiko" to R.raw.taiko
        )

        // 効果音 (AudioAttributes を用いたモダンな SoundPool 構築)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        mSound = SoundPool.Builder()
            .setMaxStreams(selist.size)
            .setAudioAttributes(audioAttributes)
            .build().apply {
                setOnLoadCompleteListener(this@MainActivity)
            }

        for ((key, resId) in selist) {
            registSound(key, resId)
        }

        // 初期化処理
        try {
            init()
        } catch (e: Exception) {
            Log.e("DoGeZa", e.localizedMessage ?: "init error")
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        hideSystemUI()

        mStatus = SceneStatus.STATUS_INIT

        try {
            mPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mPlayer = null
        } catch (_: Exception) {
        }

        mPlayer = MediaPlayer.create(this, R.raw.dogeza)?.apply {
            setVolume(0.5f, 0.5f)
            isLooping = true
            start()
        }

        // 「土下座」推定クラスの初期化処理
        mDogezaEstimation?.initialize()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        try {
            mPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mPlayer = null
        } catch (_: Exception) {
        }

        // 「土下座」推定クラスの終了処理
        mDogezaEstimation?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        endApplication()
    }

    //----------------------------------------------------------
    // サウンド関係
    //----------------------------------------------------------
    private fun registSound(key: String, resId: Int) {
        val soundPool = mSound ?: return
        val soundId = soundPool.load(this, resId, 0)
        mSoundMap[key] = soundId
    }

    private fun playSe(key: String, leftVolume: Float, rightVolume: Float, priority: Int) {
        val soundPool = mSound ?: return
        val soundId = mSoundMap[key] ?: return
        soundPool.play(soundId, leftVolume, rightVolume, priority, 0, 1f)
    }

    override fun onLoadComplete(soundPool: SoundPool?, sampleId: Int, status: Int) {
        mLoadCnt++
        if (mSoundMap.size == mLoadCnt) {
            mIsTapEnable = true
        }
    }

    //----------------------------------------------------------
    // ゲーム制御関連
    //----------------------------------------------------------
    private fun proc() {
        val eventProc = mSceneMap[getStatus()] ?: return
        try {
            DogezaView.drawBegin()
            eventProc.invoke()
            DogezaView.drawEnd()
        } catch (e: Exception) {
            Log.e("DoGeZa", e.localizedMessage ?: "proc error")
        }
    }

    private fun setStatus(status: SceneStatus) {
        mStatus = status
    }

    private fun getStatus(): SceneStatus {
        return mStatus
    }

    private fun setSaveTime() {
        mStartTime = Calendar.getInstance().time
    }

    private fun isOverTime(msec: Long): Boolean {
        val cur = Calendar.getInstance().time
        return cur.time - mStartTime.time >= msec
    }

    private fun getSpanMSec(): Long {
        val cur = Calendar.getInstance().time
        return cur.time - mStartTime.time
    }

    private fun getSpanSec(): Long {
        val cur = Calendar.getInstance().time
        val span = (cur.time - mStartTime.time) / 1000.0
        return floor(span).toLong()
    }

    //----------------------------------------------------------
    // シーン別サブルーチン
    //----------------------------------------------------------
    /**
     * 初期状態
     */
    private fun onStatusInit() {
        mIsTapEnable = true
        val pt = Paint()
        DogezaView.drawImageCenter("title", pt)
    }

    /**
     * 起立開始状態
     */
    private fun onStatusRiseStart() {
        setSaveTime()
        setStatus(SceneStatus.STATUS_RISE_TIMER)
        val pt = Paint()
        DogezaView.drawImageCenter("kiritsu", pt)

        // 「起立」推定開始
        mDogezaEstimation?.startKiritsuEstimation()

        playSe("kiritsu", 1f, 1f, 0)
    }

    /**
     * 起立
     */
    private fun onStatusRiseTimer() {
        val pt = Paint()
        DogezaView.drawImageCenter("kiritsu", pt)

        if (isOverTime(TIMER_RISE)) {
            setStatus(SceneStatus.STATUS_DOWN_TIMER)

            // 「起立」推定停止
            mDogezaEstimation?.stopKiritsuEstimation()
            // 「跪け」推定開始
            mDogezaEstimation?.startHizamadukeEstimation()

            setSaveTime()
            playSe("hizamazuke", 1f, 1f, 0)
        } else {
            val paint = Paint()
            val zoom = (getSpanMSec() - getSpanSec() * 1000).toFloat() / 1000.0f
            DogezaView.drawImageCenter((3 - getSpanSec()).toString(), paint, zoom)
        }
    }

    /**
     * 跪け
     */
    private fun onStatusDownTimer() {
        val pt = Paint()
        DogezaView.drawImageCenter("hizamaduke", pt)

        if (isOverTime(TIMER_DOWN)) {
            setStatus(SceneStatus.STATUS_DOGEZA_TIMER)

            // 土下座カウント計算(5秒+5秒の乱数)
            mTimerOffset = (mRand.nextInt(COUNT_DOGEZA) * 1000).toLong()

            // 「跪け」推定停止
            mDogezaEstimation?.stopHizamadukeEstimation()
            // 「土下座」推定開始
            mDogezaEstimation?.startDogezaEstimation()

            setSaveTime()
            playSe("dogeza", 1f, 1f, 0)
        } else {
            val paint = Paint()
            val zoom = (getSpanMSec() - getSpanSec() * 1000).toFloat() / 1000.0f
            DogezaView.drawImageCenter((3 - getSpanSec()).toString(), paint, zoom)
        }
    }

    /**
     * 土下座
     */
    private fun onStatusDogezaTimer() {
        val pt = Paint()
        DogezaView.drawImageCenter("dogeza", pt)

        if (isOverTime(TIMER_DOGEZA + mTimerOffset)) {
            setSaveTime()
            setStatus(SceneStatus.STATUS_RISE_FACE_TIMER)

            // 「土下座」推定停止
            mDogezaEstimation?.stopDogezaEstimation()

            playSe("omotewoage", 1f, 1f, 0)
        }
    }

    /**
     * 面を上げ
     */
    private fun onStatusRiseFaceTimer() {
        val pt = Paint()
        DogezaView.drawImageCenter("omotewoage", pt)

        if (isOverTime(TIMER_RISE_FACE)) {
            setSaveTime()
            setStatus(SceneStatus.STATUS_JUDGE)
            mRankSEPlayed = false
        }
    }

    /**
     * 判定
     */
    private fun onStatusJudge() {
        val pt = Paint().apply {
            color = Color.WHITE
            textSize = 60f
            isAntiAlias = true
        }

        if (mRankImg.isEmpty()) {
            calcScore()
            for ((threshold, rankImg) in mScoreImgMap) {
                if (threshold < mScore) {
                    mRankImg = rankImg
                    break
                }
            }
        }

        if (!mRankSEPlayed) {
            mRankSEPlayed = true
            playSe(mRankImg, 1f, 1f, 0)
        }

        if (isOverTime(3 * 1000L)) {
            mIsTapEnable = true
        }

        // ランク画像表示
        DogezaView.drawImageCenter(mRankImg, Paint())

        // 得点文字列生成
        val scoreText = String.format(getString(R.string.score_format2), floor(mScore.toDouble()).toInt())
        DogezaView.drawText(scoreText, 100, 30, pt)
    }

    /**
     * スコア計算
     */
    private fun calcScore() {
        val estimation = mDogezaEstimation ?: return
        mScore = 0.0f
        mScore += 100.0f * 0.1f * estimation.scoreOfKiritsuE      // 起立
        mScore += 100.0f * 0.2f * estimation.scoreOfHizamadukeE   // 跪け
        mScore += 100.0f * 0.7f * estimation.scoreOfDogezaE       // 土下座
        mScore -= 50.0f
        mScore *= 3.6f

        if (mScore >= 100.0f) {
            mScore = 100.0f
        } else if (mScore < 0.0f) {
            mScore = 0.0f
        }
    }

    //--------------------------------------------------------------------------------
    // タッチイベント処理
    //--------------------------------------------------------------------------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        mGesDetector?.onTouchEvent(event)
        return true
    }

    private val onGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            if (!mIsTapEnable) {
                return false
            }
            when (getStatus()) {
                SceneStatus.STATUS_INIT -> {
                    setStatus(SceneStatus.STATUS_RISE_START)
                    mIsTapEnable = false
                    playSe("taiko", 1f, 1f, 0)
                    return true
                }
                SceneStatus.STATUS_JUDGE -> {
                    setStatus(SceneStatus.STATUS_INIT)
                    mIsTapEnable = false
                    mRankImg = ""
                    return true
                }
                else -> {}
            }
            return super.onSingleTapConfirmed(event)
        }
    }
}

