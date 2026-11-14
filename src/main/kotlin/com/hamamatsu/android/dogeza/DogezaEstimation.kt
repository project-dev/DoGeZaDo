package com.hamamatsu.android.dogeza

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 「土下座」推定クラス
 * @author 日本Androidの会 浜松支部
 */
class DogezaEstimation(private val contextE: Context) : SensorEventListener {

    companion object {
        // ローパスフィルターの平滑化係数
        private const val LOW_PASS_ALPHA = 0.2f

        // 各軸の加速度を保存するバッファサイズ
        private const val ACCELERATION_BUFF_SIZE = 2048

        // 各軸の加速度バッファを正規化したデータのサイズ
        private const val NORM_ACCELERATION_DATA_SIZE = 128

        // 各軸の加速度を保存する閾値
        private const val THRESH_ACCELERATION = 0.1f
    }

    /**
     * ロギングする
     */
    var Logging: Boolean = false

    /**
     * 2軸モード
     */
    var Axis2Mode: Boolean = true

    // SensorManagerオブジェクト
    private val sensorManagerE: SensorManager =
        contextE.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // センサがアクティブかどうかのフラグ
    private var isSensorActiveE: Boolean = false

    // 「起立」の教師データ
    private val masterKiritsuAccXArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val masterKiritsuAccYArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val masterKiritsuAccZArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)

    // 「跪け」の教師データ
    private val masterHizamadukeAccXArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val masterHizamadukeAccYArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val masterHizamadukeAccZArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)

    // 「土下座」の教師データ
    private val masterDogezaAccXArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val masterDogezaAccYArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val masterDogezaAccZArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)

    // 各軸の加速度を保存するバッファ
    private val accelerationXBuffArrE = FloatArray(ACCELERATION_BUFF_SIZE)
    private val accelerationYBuffArrE = FloatArray(ACCELERATION_BUFF_SIZE)
    private val accelerationZBuffArrE = FloatArray(ACCELERATION_BUFF_SIZE)

    // 各軸の加速度を保存するバッファの使用数
    private var accelerationXBuffCntE: Int = 0
    private var accelerationYBuffCntE: Int = 0
    private var accelerationZBuffCntE: Int = 0

    // 各軸の加速度バッファを正規化したデータ
    private val normAccelerationXDataArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val normAccelerationYDataArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)
    private val normAccelerationZDataArrE = FloatArray(NORM_ACCELERATION_DATA_SIZE)

    // 加速度を取得した前回時間
    private var prevTimeOfAccelerationE: Long = 0

    // 各軸の加速度
    private var accelerationXE: Float = 0.0f
    private var accelerationYE: Float = 0.0f
    private var accelerationZE: Float = 0.0f

    // 各軸の速度
    private var velocityXE: Float = 0.0f
    private var velocityYE: Float = 0.0f
    private var velocityZE: Float = 0.0f

    // 各軸の移動距離
    private var distanceXE: Float = 0.0f
    private var distanceYE: Float = 0.0f
    private var distanceZE: Float = 0.0f

    // 「起立」スコア (0.0～1.0、値が大きいほど高得点)
    var scoreOfKiritsuE: Float = 0.0f
        private set

    // 「跪け」スコア (0.0～1.0、値が大きいほど高得点)
    var scoreOfHizamadukeE: Float = 0.0f
        private set

    // 「土下座」スコア (0.0～1.0、値が大きいほど高得点)
    var scoreOfDogezaE: Float = 0.0f
        private set

    init {
        // 教師データのロード
        loadMasterData(R.raw.master_kiritsu_acc_x, masterKiritsuAccXArrE)
        loadMasterData(R.raw.master_kiritsu_acc_y, masterKiritsuAccYArrE)
        loadMasterData(R.raw.master_kiritsu_acc_z, masterKiritsuAccZArrE)
        loadMasterData(R.raw.master_hizamaduke_acc_x, masterHizamadukeAccXArrE)
        loadMasterData(R.raw.master_hizamaduke_acc_y, masterHizamadukeAccYArrE)
        loadMasterData(R.raw.master_hizamaduke_acc_z, masterHizamadukeAccZArrE)
        loadMasterData(R.raw.master_dogeza_acc_x, masterDogezaAccXArrE)
        loadMasterData(R.raw.master_dogeza_acc_y, masterDogezaAccYArrE)
        loadMasterData(R.raw.master_dogeza_acc_z, masterDogezaAccZArrE)
    }

    /**
     * 初期化処理
     */
    fun initialize() {
        // 内部保持センサ値のリセット
        resetSensorValue()

        // センサ登録（直線加速度センサを優先し、非搭載端末では加速度センサにフォールバック）
        val linearSensors = sensorManagerE.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION)
        if (linearSensors.isNotEmpty()) {
            for (sensorL in linearSensors) {
                sensorManagerE.registerListener(this, sensorL, SensorManager.SENSOR_DELAY_UI)
                isSensorActiveE = true
            }
        } else {
            val accSensors = sensorManagerE.getSensorList(Sensor.TYPE_ACCELEROMETER)
            for (sensorL in accSensors) {
                sensorManagerE.registerListener(this, sensorL, SensorManager.SENSOR_DELAY_UI)
                isSensorActiveE = true
            }
        }
    }

    /**
     * 終了処理
     */
    fun release() {
        // センサ解除
        if (isSensorActiveE) {
            sensorManagerE.unregisterListener(this)
            isSensorActiveE = false
        }
    }

    override fun onAccuracyChanged(sensorA: Sensor?, accuracyA: Int) {
        // 何もしない
    }

    override fun onSensorChanged(eventA: SensorEvent?) {
        if (eventA == null || eventA.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }

        // 現在時刻の取得[ms]
        val currentTimeL = System.currentTimeMillis()

        // 直線加速度（またはフォールバックの加速度センサ）かチェック
        if (eventA.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION ||
            eventA.sensor.type == Sensor.TYPE_ACCELEROMETER
        ) {
            if (prevTimeOfAccelerationE != 0L) {
                // 前回取得からの経過時間
                val elapsedTimeL = (currentTimeL - prevTimeOfAccelerationE) / 1000.0f

                // ノイズ対策のため、各軸の加速度に対してローパスフィルターを適用
                accelerationXE += (eventA.values[0] - accelerationXE) * LOW_PASS_ALPHA
                accelerationYE += (eventA.values[1] - accelerationYE) * LOW_PASS_ALPHA
                accelerationZE += (eventA.values[2] - accelerationZE) * LOW_PASS_ALPHA

                // 各軸の速度
                velocityXE += accelerationXE * elapsedTimeL
                velocityYE += accelerationYE * elapsedTimeL
                velocityZE += accelerationZE * elapsedTimeL

                // 各軸の移動距離
                distanceXE += velocityXE * elapsedTimeL
                distanceYE += velocityYE * elapsedTimeL
                distanceZE += velocityZE * elapsedTimeL

                if (!Axis2Mode) {
                    // 各軸の加速度を保存
                    if (accelerationXBuffCntE < ACCELERATION_BUFF_SIZE && abs(accelerationXE) >= THRESH_ACCELERATION) {
                        accelerationXBuffArrE[accelerationXBuffCntE] = accelerationXE
                        accelerationXBuffCntE++
                    }
                }
                if (accelerationYBuffCntE < ACCELERATION_BUFF_SIZE && abs(accelerationYE) >= THRESH_ACCELERATION) {
                    accelerationYBuffArrE[accelerationYBuffCntE] = accelerationYE
                    accelerationYBuffCntE++
                }
                if (accelerationZBuffCntE < ACCELERATION_BUFF_SIZE && abs(accelerationZE) >= THRESH_ACCELERATION) {
                    accelerationZBuffArrE[accelerationZBuffCntE] = accelerationZE
                    accelerationZBuffCntE++
                }
            }

            // 加速度の前回取得時間を更新
            prevTimeOfAccelerationE = currentTimeL
        }
    }

    /**
     * 「起立」推定開始
     */
    fun startKiritsuEstimation() {
        resetSensorValue()
    }

    /**
     * 「起立」推定停止
     */
    fun stopKiritsuEstimation() {
        scoreOfKiritsuE = 0.0f
        if (!Axis2Mode) {
            scoreOfKiritsuE += calcSimilarityOfWave(
                accelerationXBuffArrE,
                accelerationXBuffCntE,
                masterKiritsuAccXArrE,
                NORM_ACCELERATION_DATA_SIZE,
                normAccelerationXDataArrE
            )
        }
        scoreOfKiritsuE += calcSimilarityOfWave(
            accelerationYBuffArrE,
            accelerationYBuffCntE,
            masterKiritsuAccYArrE,
            NORM_ACCELERATION_DATA_SIZE,
            normAccelerationYDataArrE
        )
        scoreOfKiritsuE += calcSimilarityOfWave(
            accelerationZBuffArrE,
            accelerationZBuffCntE,
            masterKiritsuAccZArrE,
            NORM_ACCELERATION_DATA_SIZE,
            normAccelerationZDataArrE
        )

        scoreOfKiritsuE /= if (Axis2Mode) 2.0f else 3.0f

        if (Logging) {
            saveData("Kiritsu")
        }
    }

    /**
     * 「跪け」推定開始
     */
    fun startHizamadukeEstimation() {
        resetSensorValue()
    }

    /**
     * 「跪け」推定停止
     */
    fun stopHizamadukeEstimation() {
        scoreOfHizamadukeE = 0.0f
        if (!Axis2Mode) {
            scoreOfHizamadukeE += calcSimilarityOfWave(
                accelerationXBuffArrE,
                accelerationXBuffCntE,
                masterHizamadukeAccXArrE,
                NORM_ACCELERATION_DATA_SIZE,
                normAccelerationXDataArrE
            )
        }
        scoreOfHizamadukeE += calcSimilarityOfWave(
            accelerationYBuffArrE,
            accelerationYBuffCntE,
            masterHizamadukeAccYArrE,
            NORM_ACCELERATION_DATA_SIZE,
            normAccelerationYDataArrE
        )
        scoreOfHizamadukeE += calcSimilarityOfWave(
            accelerationZBuffArrE,
            accelerationZBuffCntE,
            masterHizamadukeAccZArrE,
            NORM_ACCELERATION_DATA_SIZE,
            normAccelerationZDataArrE
        )

        scoreOfHizamadukeE /= if (Axis2Mode) 2.0f else 3.0f

        if (Logging) {
            saveData("Hizamaduke")
        }
    }

    /**
     * 「土下座」推定開始
     */
    fun startDogezaEstimation() {
        resetSensorValue()
    }

    /**
     * 「土下座」推定停止
     */
    fun stopDogezaEstimation() {
        scoreOfDogezaE = 0.0f
        if (!Axis2Mode) {
            scoreOfDogezaE += calcSimilarityOfWave(
                accelerationXBuffArrE,
                accelerationXBuffCntE,
                masterDogezaAccXArrE,
                NORM_ACCELERATION_DATA_SIZE,
                normAccelerationXDataArrE
            )
        }
        scoreOfDogezaE += calcSimilarityOfWave(
            accelerationYBuffArrE,
            accelerationYBuffCntE,
            masterDogezaAccYArrE,
            NORM_ACCELERATION_DATA_SIZE,
            normAccelerationYDataArrE
        )
        scoreOfDogezaE += calcSimilarityOfWave(
            accelerationZBuffArrE,
            accelerationZBuffCntE,
            masterDogezaAccZArrE,
            NORM_ACCELERATION_DATA_SIZE,
            normAccelerationZDataArrE
        )

        scoreOfDogezaE /= if (Axis2Mode) 2.0f else 3.0f

        if (Logging) {
            saveData("Dogeza")
        }
    }

    /**
     * 現在の加速度を取得
     */
    fun getAcceleration(accelerationArrA: FloatArray) {
        accelerationArrA[0] = accelerationXBuffCntE.toFloat()
        accelerationArrA[1] = accelerationYBuffCntE.toFloat()
        accelerationArrA[2] = accelerationZBuffCntE.toFloat()
    }

    /**
     * 現在の速度を取得
     */
    fun getVelocity(velocityArrA: FloatArray) {
        velocityArrA[0] = velocityXE
        velocityArrA[1] = velocityYE
        velocityArrA[2] = velocityZE
    }

    /**
     * 現在の移動距離を取得
     */
    fun getDistance(distanceArrA: FloatArray) {
        distanceArrA[0] = distanceXE
        distanceArrA[1] = distanceYE
        distanceArrA[2] = distanceZE
    }

    /**
     * 教師データのロード
     */
    private fun loadMasterData(resIdA: Int, masterDataArrA: FloatArray): Boolean {
        var resultL = false
        val resL = contextE.resources
        val inStreamL = resL.openRawResource(resIdA)

        try {
            BufferedReader(InputStreamReader(inStreamL, Charsets.UTF_8)).use { readerL ->
                var lineNumL = 0
                while (lineNumL < NORM_ACCELERATION_DATA_SIZE) {
                    val lineStrL = readerL.readLine() ?: break
                    masterDataArrA[lineNumL] = lineStrL.toFloat()
                    lineNumL++
                }
                if (lineNumL == NORM_ACCELERATION_DATA_SIZE) {
                    resultL = true
                }
            }
        } catch (_: Exception) {
        }

        if (resultL) {
            normalizeValue(masterDataArrA, masterDataArrA, NORM_ACCELERATION_DATA_SIZE)
        }

        return resultL
    }

    /**
     * 内部保持センサ値のリセット
     */
    private fun resetSensorValue() {
        accelerationXBuffCntE = 0
        accelerationYBuffCntE = 0
        accelerationZBuffCntE = 0

        prevTimeOfAccelerationE = 0

        accelerationXE = 0.0f
        accelerationYE = 0.0f
        accelerationZE = 0.0f

        velocityXE = 0.0f
        velocityYE = 0.0f
        velocityZE = 0.0f

        distanceXE = 0.0f
        distanceYE = 0.0f
        distanceZE = 0.0f
    }

    /**
     * センサ取得波形と教師データ波形の類似度を求める (結果は0.0～1.0、数値が大きいほど2つの波形は一致)
     */
    private fun calcSimilarityOfWave(
        sensorWaveArrA: FloatArray,
        sensorWaveSizeA: Int,
        masterWaveArrA: FloatArray,
        masterWaveSizeA: Int,
        normWaveArrA: FloatArray
    ): Float {
        // 波形の正規化
        normalizeWave(sensorWaveArrA, sensorWaveSizeA, normWaveArrA, masterWaveSizeA)

        // 正規化した波形の値を0.0～1.0に正規化
        normalizeValue(normWaveArrA, normWaveArrA, masterWaveSizeA)

        // 正規化波形と教師データ波形から相関係数を計算
        val correlationL = calcCorrelationOfWave(normWaveArrA, masterWaveArrA, masterWaveSizeA)

        // 相関係数を0.0～1.0に変換したものを類似度とする
        return (correlationL + 1.0f) / 2.0f
    }

    /**
     * 配列内の値を0.0～1.0に正規化
     */
    private fun normalizeValue(
        srcValueArrA: FloatArray,
        normValueArrA: FloatArray,
        arrSizeA: Int
    ) {
        var minValueL = Float.MAX_VALUE
        var maxValueL = Float.MIN_VALUE

        for (arrIdxL in 0 until arrSizeA) {
            if (srcValueArrA[arrIdxL] < minValueL) minValueL = srcValueArrA[arrIdxL]
            if (srcValueArrA[arrIdxL] > maxValueL) maxValueL = srcValueArrA[arrIdxL]
        }

        val valueRangeL = maxValueL - minValueL

        for (arrIdxL in 0 until arrSizeA) {
            normValueArrA[arrIdxL] = if (valueRangeL != 0f) {
                (srcValueArrA[arrIdxL] - minValueL) / valueRangeL
            } else {
                0f
            }
        }
    }

    /**
     * 波形の正規化
     */
    private fun normalizeWave(
        srcWaveArrA: FloatArray,
        srcWaveSizeA: Int,
        normWaveArrA: FloatArray,
        normWaveSizeA: Int
    ) {
        var srcStepL = 0f
        if (normWaveSizeA + 1 != 0) {
            srcStepL = srcWaveSizeA.toFloat() / (normWaveSizeA + 1).toFloat()
        }

        if (srcWaveSizeA == 0) {
            srcWaveArrA[0] = 0.0f
        }

        var srcIdxL = srcStepL
        for (dstIdxL in 0 until normWaveSizeA) {
            normWaveArrA[dstIdxL] = srcWaveArrA[srcIdxL.toInt()]
            srcIdxL += srcStepL
        }
    }

    /**
     * 2つの波形の相関係数を計算 (結果は-1.0～+1.0、数値が大きいほど2つの波形は一致)
     */
    private fun calcCorrelationOfWave(
        wave1ArrA: FloatArray,
        wave2ArrA: FloatArray,
        waveSizeA: Int
    ): Float {
        var wave1MeanL = 0.0f
        var wave2MeanL = 0.0f

        for (iL in 0 until waveSizeA) {
            wave1MeanL += wave1ArrA[iL]
            wave2MeanL += wave2ArrA[iL]
        }
        wave1MeanL /= waveSizeA.toFloat()
        wave2MeanL /= waveSizeA.toFloat()

        var covarianceL = 0.0f
        var standardDeviation1L = 0.0f
        var standardDeviation2L = 0.0f

        for (iL in 0 until waveSizeA) {
            val deviation1L = wave1ArrA[iL] - wave1MeanL
            val deviation2L = wave2ArrA[iL] - wave2MeanL

            covarianceL += deviation1L * deviation2L
            standardDeviation1L += deviation1L * deviation1L
            standardDeviation2L += deviation2L * deviation2L
        }

        return (covarianceL / (sqrt(standardDeviation1L.toDouble()) * sqrt(standardDeviation2L.toDouble()) + 0.000001)).toFloat()
    }

    /**
     * センサデータ保存
     */
    private fun saveData(prefixOfFileNameA: String) {
        val dateL = Date()
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.JAPAN)
        val prefixOfFileNameL = "${dateFormat.format(dateL)}-$prefixOfFileNameA"

        saveFloatData("${prefixOfFileNameL}RawAccX.txt", accelerationXBuffArrE, accelerationXBuffCntE)
        saveFloatData("${prefixOfFileNameL}RawAccY.txt", accelerationYBuffArrE, accelerationYBuffCntE)
        saveFloatData("${prefixOfFileNameL}RawAccZ.txt", accelerationZBuffArrE, accelerationZBuffCntE)

        saveFloatData("${prefixOfFileNameL}NormAccX.txt", normAccelerationXDataArrE, NORM_ACCELERATION_DATA_SIZE)
        saveFloatData("${prefixOfFileNameL}NormAccY.txt", normAccelerationYDataArrE, NORM_ACCELERATION_DATA_SIZE)
        saveFloatData("${prefixOfFileNameL}NormAccZ.txt", normAccelerationZDataArrE, NORM_ACCELERATION_DATA_SIZE)
    }

    /**
     * float配列データのファイル保存 (アプリ固有外部ストレージディレクトリに保存)
     */
    private fun saveFloatData(fileNameA: String, dataArrA: FloatArray, dataSizeA: Int) {
        try {
            val dir = contextE.getExternalFilesDir(null) ?: contextE.filesDir
            val outFile = File(dir, fileNameA)
            BufferedWriter(FileWriter(outFile)).use { bufferWriterL ->
                for (srcIdxL in 0 until dataSizeA) {
                    bufferWriterL.write(String.format(Locale.US, "%.6f\n", dataArrA[srcIdxL]))
                }
            }
            Log.v("Save SD OK!!", outFile.absolutePath)
        } catch (_: Exception) {
        }
    }
}

