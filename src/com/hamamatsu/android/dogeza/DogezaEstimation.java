package com.hamamatsu.android.dogeza;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.util.Log;

/**
 * 「土下座」推定クラス
 * @author 日本Androidの会 浜松支部
 */
public class DogezaEstimation implements SensorEventListener
{
	/*******************************************************************************
	 * 
	 * 定数
	 *
	 ******************************************************************************/
	
	// ローパスフィルターの平滑化係数
	private final float LOW_PASS_ALPHA = 0.2f;
	
	// 各軸の加速度を保存するバッファサイズ
	private final int ACCELERATION_BUFF_SIZE = 2048;
	
	// 各軸の加速度バッファを正規化したデータのサイズ
	private final int NORM_ACCELERATION_DATA_SIZE = 128;
	
	// 各軸の加速度を保存する閾値
	private final float THRESH_ACCELERATION = 0.1f;
	
	/*******************************************************************************
	 * 
	 * 変数
	 *
	 ******************************************************************************/
	/**
	 * ロギングする
	 */
	public boolean Logging = false;

	/**
	 * 2軸モード
	 */
	public boolean Axis2Mode = true;
	
	// 本クラスを生成したActivityオブジェクト
	private Context contextE = null;
	
	// SensorManagerオブジェクト
	private SensorManager sensorManagerE = null;

	// センサがアクティブかどうかのフラグ
	private boolean isSensorActiveE = false;

	// 「起立」の教師データ
	private float[] masterKiritsuAccXArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] masterKiritsuAccYArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] masterKiritsuAccZArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	
	// 「跪け」の教師データ
	private float[] masterHizamadukeAccXArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] masterHizamadukeAccYArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] masterHizamadukeAccZArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	
	// 「土下座」の教師データ
	private float[] masterDogezaAccXArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] masterDogezaAccYArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] masterDogezaAccZArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	
	// 各軸の加速度を保存するバッファ
	private float[] accelerationXBuffArrE = new float[ACCELERATION_BUFF_SIZE];
	private float[] accelerationYBuffArrE = new float[ACCELERATION_BUFF_SIZE];
	private float[] accelerationZBuffArrE = new float[ACCELERATION_BUFF_SIZE];

	// 各軸の加速度を保存するバッファの使用数
	private int accelerationXBuffCntE = 0;
	private int accelerationYBuffCntE = 0;
	private int accelerationZBuffCntE = 0;

	// 各軸の加速度バッファを正規化したデータ
	private float[] normAccelerationXDataArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] normAccelerationYDataArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	private float[] normAccelerationZDataArrE = new float[NORM_ACCELERATION_DATA_SIZE];
	
	// 加速度を取得した前回時間
	private long prevTimeOfAccelerationE = 0;
	
	// 各軸の加速度
	private float accelerationXE = 0.0f;
	private float accelerationYE = 0.0f;
	private float accelerationZE = 0.0f;
	
	// 各軸の速度
	private float velocityXE = 0.0f;
	private float velocityYE = 0.0f;
	private float velocityZE = 0.0f;
	
	// 各軸の移動距離
	private float distanceXE = 0.0f;
	private float distanceYE = 0.0f;
	private float distanceZE = 0.0f;
	
	// 「起立」スコア (0.0～1.0、値が大きいほど高得点)
	private float scoreOfKiritsuE = 0.0f;
	
	// 「跪け」スコア (0.0～1.0、値が大きいほど高得点)
	private float scoreOfHizamadukeE = 0.0f;
	
	// 「土下座」スコア (0.0～1.0、値が大きいほど高得点)
	private float scoreOfDogezaE = 0.0f;
	
	
	/*******************************************************************************
	 * 
	 * 関数
	 *
	 ******************************************************************************/
	
	/*******************************************************************************
	 * コンストラクタ
	 ******************************************************************************/
	public DogezaEstimation(Context contextA)
	{
		// 本クラスを生成したActivityオブジェクトを保持
		contextE = contextA;
		
		// SensorManagerオブジェクトを取得／保持
		sensorManagerE = (SensorManager)contextE.getSystemService(Context.SENSOR_SERVICE);
		
		 // 教師データのロード
		loadMastarData(R.raw.master_kiritsu_acc_x, masterKiritsuAccXArrE);
		loadMastarData(R.raw.master_kiritsu_acc_y, masterKiritsuAccYArrE);
		loadMastarData(R.raw.master_kiritsu_acc_z, masterKiritsuAccZArrE);
		loadMastarData(R.raw.master_hizamaduke_acc_x, masterHizamadukeAccXArrE);
		loadMastarData(R.raw.master_hizamaduke_acc_y, masterHizamadukeAccYArrE);
		loadMastarData(R.raw.master_hizamaduke_acc_z, masterHizamadukeAccZArrE);
		loadMastarData(R.raw.master_dogeza_acc_x, masterDogezaAccXArrE);
		loadMastarData(R.raw.master_dogeza_acc_y, masterDogezaAccYArrE);
		loadMastarData(R.raw.master_dogeza_acc_z, masterDogezaAccZArrE);
		
		return;
	}
	
	/*******************************************************************************
	 * 初期化処理
	 ******************************************************************************/
	public void initialize()
	{
		// 内部保持センサ値のリセット
		resetSendorValue();

		// センサ登録
		List<Sensor> sensorsL = sensorManagerE.getSensorList(Sensor.TYPE_ALL);
		for(Sensor sensorL : sensorsL)
		{
			if(sensorL.getType() == Sensor.TYPE_LINEAR_ACCELERATION)
			{
				sensorManagerE.registerListener(this, sensorL, SensorManager.SENSOR_DELAY_UI);
				isSensorActiveE = true;
			}
		}
		
		return;
	}
	
	/*******************************************************************************
	 * 終了処理
	 ******************************************************************************/
	public void finalize()
	{
		// センサ解除
		if(isSensorActiveE)
		{
			sensorManagerE.unregisterListener(this);
			isSensorActiveE = false;
		}
		
		return;
	}
	
	/*******************************************************************************
	 * センサ精度の更新
	 ******************************************************************************/
	@Override
	public void onAccuracyChanged(Sensor sensorA, int accuracyA)
	{
		return;
	}

	/*******************************************************************************
	 * センサ値の更新
	 ******************************************************************************/
	@Override
	public void onSensorChanged(SensorEvent eventA) 
	{
		if(eventA.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
		{
			return;
		}
		
		// 現在時刻の取得[Seconds 取得]
		long currentTimeL = System.currentTimeMillis();
		
		// 直線加速度かチェック
		if(eventA.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) 
		{
			if(prevTimeOfAccelerationE != 0)
			{
				// 前回取得からの経過時間
				float elapsedTimeL = (currentTimeL - prevTimeOfAccelerationE) / 1000.0f;
				
				// ノイズ対策のため、各軸の加速度に対してローパスフィルターを適用
				accelerationXE += (eventA.values[0] - accelerationXE) * LOW_PASS_ALPHA;
				accelerationYE += (eventA.values[1] - accelerationYE) * LOW_PASS_ALPHA;
				accelerationZE += (eventA.values[2] - accelerationZE) * LOW_PASS_ALPHA;
				
				// 各軸の速度
				velocityXE += (accelerationXE * elapsedTimeL);
				velocityYE += (accelerationYE * elapsedTimeL);
				velocityZE += (accelerationZE * elapsedTimeL);
				
				// 各軸の移動距離
				distanceXE += (velocityXE * elapsedTimeL);
				distanceYE += (velocityYE * elapsedTimeL);
				distanceZE += (velocityZE * elapsedTimeL);

				if(Axis2Mode == false){
					// 各軸の加速度を保存
					if(accelerationXBuffCntE < ACCELERATION_BUFF_SIZE && Math.abs(accelerationXE) >= THRESH_ACCELERATION)
					{
						accelerationXBuffArrE[accelerationXBuffCntE] = accelerationXE;
						accelerationXBuffCntE++;
					}
				}
				if(accelerationYBuffCntE < ACCELERATION_BUFF_SIZE && Math.abs(accelerationYE) >= THRESH_ACCELERATION)
				{
					accelerationYBuffArrE[accelerationYBuffCntE] = accelerationYE;
					accelerationYBuffCntE++;
				}
				if(accelerationZBuffCntE < ACCELERATION_BUFF_SIZE && Math.abs(accelerationZE) >= THRESH_ACCELERATION)
				{
					accelerationZBuffArrE[accelerationZBuffCntE] = accelerationZE;
					accelerationZBuffCntE++;
				}
			}
			
			// 加速度の前回取得時間を更新
			prevTimeOfAccelerationE = currentTimeL;
		}
		
		return;
	}
	
	/*******************************************************************************
	 * 「起立」推定開始
	 ******************************************************************************/
	public void startKiritsuEstimation()
	{
		// 内部保持センサ値のリセット
		resetSendorValue();
		
		return;
	}

	/*******************************************************************************
	 * 「起立」推定停止
	 ******************************************************************************/
	public void stopKiritsuEstimation()
	{
    	// 加速度センサ取得波形と教師データ波形の類似度を求める
		scoreOfKiritsuE = 0.0f;
		if(Axis2Mode == false){
			scoreOfKiritsuE += calcSimilarityOfWave(accelerationXBuffArrE, accelerationXBuffCntE, masterKiritsuAccXArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationXDataArrE);
		}
		scoreOfKiritsuE += calcSimilarityOfWave(accelerationYBuffArrE, accelerationYBuffCntE, masterKiritsuAccYArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationYDataArrE);
		scoreOfKiritsuE += calcSimilarityOfWave(accelerationZBuffArrE, accelerationZBuffCntE, masterKiritsuAccZArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationZDataArrE);
    	
		// スコアを保存 (2軸の類似度の平均値)
		scoreOfKiritsuE /= Axis2Mode ? 2.0f : 3.0f;
		
		// センサデータ保存
		if(Logging == true){
			saveData("Kiritsu");
		}
		return;
	}
	
	/*******************************************************************************
	 * 「跪け」推定開始
	 ******************************************************************************/
	public void startHizamadukeEstimation()
	{
		// 内部保持センサ値のリセット
		resetSendorValue();
		
		return;
	}
	
	/*******************************************************************************
	 * 「跪け」推定停止
	 ******************************************************************************/
	public void stopHizamadukeEstimation()
	{
    	// 加速度センサ取得波形と教師データ波形の類似度を求める
		scoreOfHizamadukeE = 0.0f;
		if(Axis2Mode == false){
			scoreOfHizamadukeE += calcSimilarityOfWave(accelerationXBuffArrE, accelerationXBuffCntE, masterHizamadukeAccXArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationXDataArrE);
		}
		scoreOfHizamadukeE += calcSimilarityOfWave(accelerationYBuffArrE, accelerationYBuffCntE, masterHizamadukeAccYArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationYDataArrE);
		scoreOfHizamadukeE += calcSimilarityOfWave(accelerationZBuffArrE, accelerationZBuffCntE, masterHizamadukeAccZArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationZDataArrE);
    	
		// スコアを保存 (3軸の類似度の平均値)
		scoreOfHizamadukeE /= Axis2Mode ? 2.0f : 3.0f;
		
		// センサデータ保存
		if(Logging == true){
			saveData("Hizamaduke");
		}
		
		return;
	}
	
	/*******************************************************************************
	 * 「土下座」推定開始
	 ******************************************************************************/
	public void startDogezaEstimation()
	{
		// 内部保持センサ値のリセット
		resetSendorValue();
		
		return;
	}
	
	/*******************************************************************************
	 * 「土下座」推定停止
	 ******************************************************************************/
	public void stopDogezaEstimation()
	{
    	// 加速度センサ取得波形と教師データ波形の類似度を求める
		scoreOfDogezaE = 0.0f;
		if(Axis2Mode == false){
			scoreOfDogezaE += calcSimilarityOfWave(accelerationXBuffArrE, accelerationXBuffCntE, masterDogezaAccXArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationXDataArrE);
		}
		scoreOfDogezaE += calcSimilarityOfWave(accelerationYBuffArrE, accelerationYBuffCntE, masterDogezaAccYArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationYDataArrE);
		scoreOfDogezaE += calcSimilarityOfWave(accelerationZBuffArrE, accelerationZBuffCntE, masterDogezaAccZArrE, NORM_ACCELERATION_DATA_SIZE, normAccelerationZDataArrE);
    	
		// スコアを保存 (3軸の類似度の平均値)
		scoreOfDogezaE /= Axis2Mode ? 2.0f : 3.0f;
		
		// センサデータ保存
		if(Logging == true){
			saveData("Dogeza");
		}
		
		return;
	}
	
	/*******************************************************************************
	 * 「起立」スコアを取得 (0.0～1.0、値が大きいほど高得点)
	 ******************************************************************************/
	public float getScoreOfKiritsuE()
	{
		return scoreOfKiritsuE;
	}
	
	/*******************************************************************************
	 * 「跪け」スコアを取得 (0.0～1.0、値が大きいほど高得点)
	 ******************************************************************************/
	public float getScoreOfHizamadukeE()
	{
		return scoreOfHizamadukeE;
	}
	
	/*******************************************************************************
	 * 「土下座」スコアを取得 (0.0～1.0、値が大きいほど高得点)
	 ******************************************************************************/
	public float getScoreOfDogezaE()
	{
		return scoreOfDogezaE;
	}
	
	/*******************************************************************************
	 * 現在の加速度を取得 (結果を引数に指定されたfloat型配列に保存、3個分の配列とすること、0：X軸 1：X軸 2：X軸)
	 ******************************************************************************/
	public void getAcceleration(float[] accelerationArrA)
	{
		accelerationArrA[0] = accelerationXBuffCntE;
		accelerationArrA[1] = accelerationYBuffCntE;
		accelerationArrA[2] = accelerationZBuffCntE;
		
		return;
	}
	
	/*******************************************************************************
	 * 現在の速度を取得 (結果を引数に指定されたfloat型配列に保存、3個分の配列とすること、0：X軸 1：X軸 2：X軸)
	 ******************************************************************************/
	public void getVelocity(float[] velocityArrA)
	{
		velocityArrA[0] = velocityXE;
		velocityArrA[1] = velocityYE;
		velocityArrA[2] = velocityZE;
		
		return;
	}
	
	/*******************************************************************************
	 * 現在の移動距離を取得 (結果を引数に指定されたfloat型配列に保存、3個分の配列とすること、0：X軸 1：X軸 2：X軸)
	 ******************************************************************************/
	public void getDistance(float[] distanceArrA)
	{
		distanceArrA[0] = distanceXE;
		distanceArrA[1] = distanceYE;
		distanceArrA[2] = distanceZE;
		
		return;
	}
	
	/*******************************************************************************
	 * 教師データのロード
	 ******************************************************************************/
	private boolean loadMastarData(int resIdA, float[] masterDataArrA)
	{
		boolean resultL = false;
		
		// リソースファイルオープン
		Resources resL = contextE.getResources();
		InputStream inStreamL = resL.openRawResource(resIdA);
		
        try {
        	BufferedReader readerL = new BufferedReader(new InputStreamReader(inStreamL, "UTF-8"));
        	String lineStrL;
        	int lineNumL = 0;
        	
    		// 教師データのロード
            while((lineStrL = readerL.readLine()) != null && lineNumL < NORM_ACCELERATION_DATA_SIZE)
            {
            	// 教師データを文字列からfloatに変換して保持
            	masterDataArrA[lineNumL] = Float.parseFloat(lineStrL);
            	
            	// 行数の更新
            	lineNumL++;
            }
            
            // ロード結果判定
            if(lineNumL == NORM_ACCELERATION_DATA_SIZE) resultL = true;
        }
        catch(Exception eA)
        {
        }
        
		// 教師データの値を0.0～1.0に正規化
        if(resultL == true)
        {
        	normalizeValue(masterDataArrA, masterDataArrA, NORM_ACCELERATION_DATA_SIZE);
        }
		
        return resultL;
	}
	
	/*******************************************************************************
	 * 内部保持センサ値のリセット
	 ******************************************************************************/
	private void resetSendorValue()
	{
		// 各軸の加速度を保存するバッファの使用数
		accelerationXBuffCntE = 0;
		accelerationYBuffCntE = 0;
		accelerationZBuffCntE = 0;
		
		// 加速度を取得した前回時間
		prevTimeOfAccelerationE = 0;
		
		// 各軸の加速度
		accelerationXE = 0.0f;
		accelerationYE = 0.0f;
		accelerationZE = 0.0f;
		
		// 各軸の速度
		velocityXE = 0.0f;
		velocityYE = 0.0f;
		velocityZE = 0.0f;
		
		// 各軸の移動距離
		distanceXE = 0.0f;
		distanceYE = 0.0f;
		distanceZE = 0.0f;
		
		return;
	}

	/*******************************************************************************
	 * センサ取得波形と教師データ波形の類似度を求める (結果は0.0～1.0、数値が大きいほど2つの波形は一致)
	 ******************************************************************************/
	private float calcSimilarityOfWave(float[] sensorWaveArrA, int sensorWaveSizeA,	// センサ取得波形
									   float[] masterWaveArrA, int masterWaveSizeA,	// 教師データ波形
									   float[] normWaveArrA)						// 正規化した波形の保存先 (配列サイズは教師データ波形と同一)
	{
    	// 波形の正規化
		normalizeWave(sensorWaveArrA, sensorWaveSizeA, normWaveArrA, masterWaveSizeA);
		
		// 正規化した波形の値を0.0～1.0に正規化
		normalizeValue(normWaveArrA, normWaveArrA, masterWaveSizeA);
    	
    	// 正規化波形と教師データ波形から相関係数を計算
    	float correlationL = calcCorrelationOfWave(normWaveArrA, masterWaveArrA, masterWaveSizeA);
    	
    	// 相関係数を0.0～1.0に変換したものを類似度とする
		return (correlationL + 1.0f) / 2.0f;
	}

	/*******************************************************************************
	 * 配列内の値を0.0～1.0に正規化
	 ******************************************************************************/
	private void normalizeValue(float[] srcValueArrA, float[] normValueArrA, int arrSizeA)
	{
		int arrIdxL;
		float minValueL = Float.MAX_VALUE;
		float maxValueL = Float.MIN_VALUE;
		
		// 最小値／最大値を検索
		for(arrIdxL = 0; arrIdxL < arrSizeA; arrIdxL++)
		{
			if(srcValueArrA[arrIdxL] < minValueL) minValueL = srcValueArrA[arrIdxL]; 
			if(srcValueArrA[arrIdxL] > maxValueL) maxValueL = srcValueArrA[arrIdxL]; 
		}
		
		// 値の範囲
		float valueRangeL = maxValueL - minValueL;
		
		// 値の正規化
		for(arrIdxL = 0; arrIdxL < arrSizeA; arrIdxL++)
		{
			if(valueRangeL != 0){
				normValueArrA[arrIdxL] = (srcValueArrA[arrIdxL] - minValueL) / valueRangeL;
			}else{
				//TODO:暫定。0で割れないときの対応
				normValueArrA[arrIdxL] = 0;
			}
		}
		return;
	}

	/*******************************************************************************
	 * 波形の正規化
	 ******************************************************************************/
	private void normalizeWave(float[] srcWaveArrA, int srcWaveSizeA, float[] normWaveArrA, int normWaveSizeA)
	{
		int dstIdxL;
		float srcIdxL;
		float srcStepL = 0;
		//TODO:0で割らないようにする対応
		if(normWaveSizeA + 1 != 0){
			srcStepL = (float)srcWaveSizeA / (float)(normWaveSizeA + 1);
		}

		// 加速度が保存されたバッファ個数が0の場合の初期化
		if(srcWaveSizeA == 0) srcWaveArrA[0] = 0.0f;
		
		// 加速度が保存されたバッファを正規化
		for(dstIdxL = 0, srcIdxL = srcStepL; dstIdxL < normWaveSizeA; dstIdxL++, srcIdxL += srcStepL)
		{
			normWaveArrA[dstIdxL] = srcWaveArrA[(int)srcIdxL];
		}
		
		return;
	}
	
	/*******************************************************************************
	 * 2つの波形の相関係数を計算 (結果は-1.0～+1.0、数値が大きいほど2つの波形は一致)
	 ******************************************************************************/
	private float calcCorrelationOfWave(float[] wave1ArrA, float[] wave2ArrA, int waveSizeA)
	{
		int iL;

		// 波形1、2の平均値を計算
		float wave1MeanL = 0.0f;
		float wave2MeanL = 0.0f;
		
		// 平均値の計算ループ
		for(iL = 0; iL < waveSizeA; iL++)
		{
			wave1MeanL += wave1ArrA[iL];
			wave2MeanL += wave2ArrA[iL];
		}
		wave1MeanL /= (float)waveSizeA;
		wave2MeanL /= (float)waveSizeA;
		
		// 波形1、2の共分散／標準偏差を計算
		float covarianceL = 0.0f;
		float standardDeviation1L = 0.0f; 
		float standardDeviation2L = 0.0f;
		
		// 共分散／標準偏差の計算ループ
		for(iL = 0; iL < waveSizeA; iL++)
		{
			// 平均値との偏差
			float deviation1L = wave1ArrA[iL] - wave1MeanL;
			float deviation2L = wave2ArrA[iL] - wave2MeanL;
			
			// 共分散
			covarianceL += (deviation1L * deviation2L);
			
			// 標準偏差
			standardDeviation1L += (deviation1L * deviation1L); 
			standardDeviation2L += (deviation2L * deviation2L); 
		}
		
		// 相関係数の計算 (zero除算防止のため小さな実数を足しておく)
		float correlationL = (float)(covarianceL / (Math.sqrt(standardDeviation1L) * Math.sqrt(standardDeviation2L) + 0.000001)); 
        
		return correlationL;
	}

	/*******************************************************************************
	 * センサデータ保存
	 ******************************************************************************/
	private void saveData(String prefixOfFileNameA)
	{
		 Date dateL = new Date();
		 SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.JAPAN);
		 String prefixOfFileNameL = dateFormat.format(dateL) + "-" + prefixOfFileNameA;
    
    	// 加速度の生データを保存する
    	saveFloatData(prefixOfFileNameL + "RawAccX.txt", accelerationXBuffArrE, accelerationXBuffCntE);
    	saveFloatData(prefixOfFileNameL + "RawAccY.txt", accelerationYBuffArrE, accelerationYBuffCntE);
    	saveFloatData(prefixOfFileNameL + "RawAccZ.txt", accelerationZBuffArrE, accelerationZBuffCntE);
    	
    	// 加速度の正規化データを保存する
    	saveFloatData(prefixOfFileNameL + "NormAccX.txt", normAccelerationXDataArrE, NORM_ACCELERATION_DATA_SIZE);
    	saveFloatData(prefixOfFileNameL + "NormAccY.txt", normAccelerationYDataArrE, NORM_ACCELERATION_DATA_SIZE);
    	saveFloatData(prefixOfFileNameL + "NormAccZ.txt", normAccelerationZDataArrE, NORM_ACCELERATION_DATA_SIZE);
    	
    	return;
	}
	
	/*******************************************************************************
	 * float配列データのファイル保存 (外部ストレージのrootディレクトリに保存)
	 ******************************************************************************/
	private void saveFloatData(String fileNameA, float[] dataArrA, int dataSizeA)
	{
	    try
	    {  
	        // SDカードフォルダのパスを取得
	        String sdPathL = Environment.getExternalStorageDirectory().getPath();
	    	
            FileWriter fileWriterL = new FileWriter(sdPathL + "/" + fileNameA);  
            BufferedWriter bufferWriterL = new BufferedWriter(fileWriterL);

            // float配列データを書き込み
			for(int srcIdxL = 0; srcIdxL < dataSizeA; srcIdxL++)
			{
				bufferWriterL.write(String.format("%.6f", dataArrA[srcIdxL]) + "\n");
			}
            
	        bufferWriterL.close();
	        
	        Log.v("Save SD OK!!", fileNameA);
	    }
	    catch(Exception eA)
	    {  
	    }
	    
	    return;
	}
}
