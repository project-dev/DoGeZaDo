package com.hamamatsu.android.dogeza;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import jp.epson.moverio.bt200.SensorControl;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

//--------------------------------------------------------------------------------
// MainActivityクラス
//--------------------------------------------------------------------------------
public class MainActivity extends Activity implements OnLoadCompleteListener{

	//----------------------------------------------------------
	// 定数定義
	//----------------------------------------------------------
	// タイマー関連
	/**
	 * タイマー更新間隔
	 */
	private final int TIMER_UPDATE = 1 * 1000;
	/**
	 * 起立時間
	 */
	private final int TIMER_RISE = 5 * 1000;
	/**
	 * 跪く時間
	 */
	private final int TIMER_DOWN = 5 * 1000;
	/**
	 * 土下座時間
	 */
	private final int TIMER_DOGEZA = 5 * 1000;
	/**
	 * 面を上げ時間
	 */
	private final int TIMER_RISE_FACE = 5 * 1000;
	
	//private final int COUNT_RISE = TIMER_RISE / TIMER_UPDATE;
	//private final int COUNT_DOWN = TIMER_DOWN / TIMER_UPDATE;
	private final int COUNT_DOGEZA = TIMER_DOGEZA / TIMER_UPDATE;
	//private final int COUNT_RISE_FACE = TIMER_RISE_FACE / TIMER_UPDATE;

	private Date mStartTime = null;
	private long mTimerOffset = 0;
	private int mLoadCnt = 0;
	private boolean mRankSEPlaied = false;

	//----------------------------------------------------------
	// 変数定義
	//----------------------------------------------------------
	/**
	 * ゲーム状態
	 */
	private SceneStatus mStatus = SceneStatus.STATUS_INIT;

	/**
	 * タッチイベント処理
	 */
	private GestureDetector mGesDetector = null;

	/**
	 * 乱数
	 */
	private Random mRand = null;

	/**
	 * 「土下座」推定クラス
	 */
	private DogezaEstimation mDogezaEstimation = null;

	/**
	 * シーンマップ
	 */
	private HashMap<SceneStatus,  Method> mSceneMap = null;


	/**
	 * 制御用スレッド
	 */
	private Thread mThread = null;
	
	/**
	 * 得点マップ
	 */
	private LinkedHashMap<Float, Integer> mScoreMap = null;
	private LinkedHashMap<Float, String> mScoreImgMap = null;

	/**
	 * 
	 */
	private boolean mIsTapEnable = true;
	
	/**
	 * BGM再生用
	 */
	private MediaPlayer mPlayer = null;

	/**
	 * 効果音再生
	 */
	private SoundPool mSound = null;
	private HashMap<String, Integer> mSoundMap = null;
	
	/**
	 * スコア
	 */
    private float mScore = 0;

    /**
     * ランク表示
     */
    private String mRankImg = "";

    
    
	//----------------------------------------------------------
	// 初期化・終了
	//----------------------------------------------------------
	/**
	 * 初期化処理
	 */
	private void init() throws Exception{
		try{
			// 前画面表示 for Moverio
			Window win = getWindow();
			WindowManager.LayoutParams winParams = win.getAttributes();
			// WindowManager.LayoutParams.FLAG_SMARTFULLSCREEN
			winParams.flags |= 0x80000000;	
			win.setAttributes(winParams);

			// センサー初期化
			SensorControl sensor = new SensorControl(this.getApplicationContext());
			sensor.setMode(SensorControl.SENSOR_MODE_HEADSET);
		}catch(Exception e){
			Log.e("DoGeZa", e.getLocalizedMessage());
		}
		
		// シーン設定
		mSceneMap = new HashMap<SceneStatus, Method>();

		registScene(SceneStatus.STATUS_INIT, "OnStatusInit");
		registScene(SceneStatus.STATUS_RISE_START, "OnStatusRiseStart");
		registScene(SceneStatus.STATUS_RISE_TIMER, "OnStatusRiseTimer");
		registScene(SceneStatus.STATUS_DOWN_TIMER, "OnStatusDownTimer");
		registScene(SceneStatus.STATUS_DOGEZA_TIMER, "OnStatusDogezaTimer");
    	registScene(SceneStatus.STATUS_RISE_FACE_TIMER, "OnStatusRiseFaceTimer");
		registScene(SceneStatus.STATUS_JUDGE, "OnStatusJudge");

		// 得点設定
		mScoreMap = new LinkedHashMap<Float, Integer>();
		mScoreMap.put(90.0f, R.string.score_level_5);
		mScoreMap.put(70.0f, R.string.score_level_4);
		mScoreMap.put(50.0f, R.string.score_level_3);
		mScoreMap.put(30.0f, R.string.score_level_2);
		mScoreMap.put(-1.0f, R.string.score_level_1);

		mScoreImgMap = new LinkedHashMap<Float, String>();
		mScoreImgMap.put(90.0f, "lv5");
		mScoreImgMap.put(70.0f, "lv4");
		mScoreImgMap.put(50.0f, "lv3");
		mScoreImgMap.put(30.0f, "lv2");
		mScoreImgMap.put(-1.0f, "lv1");
		
		// イメージの登録
		DogezaView.registImage("title", R.drawable.title);
		DogezaView.registImage("kiritsu", R.drawable.kiritsu);
		DogezaView.registImage("hizamaduke", R.drawable.hizamaduke);
		DogezaView.registImage("dogeza", R.drawable.dogeza);
		DogezaView.registImage("omotewoage", R.drawable.omotewoage);
		DogezaView.registImage("0", R.drawable.num0);
		DogezaView.registImage("1", R.drawable.num1);
		DogezaView.registImage("2", R.drawable.num2);
		DogezaView.registImage("3", R.drawable.num3);
		DogezaView.registImage("4", R.drawable.num4);
		DogezaView.registImage("5", R.drawable.num5);
		DogezaView.registImage("6", R.drawable.num6);
		DogezaView.registImage("7", R.drawable.num7);
		DogezaView.registImage("8", R.drawable.num8);
		DogezaView.registImage("9", R.drawable.num9);
		DogezaView.registImage("lv1", R.drawable.lv1);
		DogezaView.registImage("lv2", R.drawable.lv2);
		DogezaView.registImage("lv3", R.drawable.lv3);
		DogezaView.registImage("lv4", R.drawable.lv4);
		DogezaView.registImage("lv5", R.drawable.lv5);

		// タッチイベント処理設定
		mGesDetector = new GestureDetector(this, onGestureListener);
		// 乱数初期化
		mRand = new Random();
		// 初期状態へ設定
		setStatus(SceneStatus.STATUS_INIT);

		// 「土下座」推定クラスの生成
		mDogezaEstimation = new DogezaEstimation(this);

		// スレッドの開始
		startApplication();
	}

	/**
	 * 開始処理
	 */
	public void startApplication(){
		// 制御スレッドの生成と開始
		mThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while(mThread != null){
					try{
						Thread.sleep(1000/60);
					}catch(Exception e){
						Log.e("DoGeZa", e.getLocalizedMessage());
					}
					proc();
				}
			}
		});
		mThread.start();
	}

	/**
	 * 終了処理
	 */
	public void endApplication(){
		try{
			mPlayer.stop();
			mPlayer.release();
			mPlayer = null;
		}catch(Exception e){
			Log.e("DoGeZa", e.getLocalizedMessage());
		}
		
		try{
			mSound.release();
			mSound = null;
		}catch(Exception e){
			Log.e("DoGeZa", e.getLocalizedMessage());
		}
		mThread = null;
	}

	//----------------------------------------------------------
	// イベント
	//----------------------------------------------------------
    
    /**
	 * onCreate
	 * アクシビティ生成時のイベント
	 * @param savedInstanceState
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// BGM再生
		//mPlayer = MediaPlayer.create(this, R.raw.dogeza);
		//mPlayer.setVolume(0.5f, 0.5f);
		//mPlayer.setLooping(true);
		// onResumで再生するのでここでは再生しない
		//mPlayer.start();

		mSoundMap = new HashMap<String, Integer>();
		
		HashMap<String, Integer> selist = new HashMap<String, Integer>();
		selist.put("kiritsu", R.raw.v01_kiritsu);
		selist.put("hizamazuke", R.raw.v02_hizamazuke);
		selist.put("dogeza", R.raw.v03_dogeza);
		selist.put("omotewoage", R.raw.v04_omotewoage);
		selist.put("lv1", R.raw.v10_zakowa);
		selist.put("lv2", R.raw.v11_kutihodo);
		selist.put("lv3", R.raw.v12_tedare);
		selist.put("lv4", R.raw.v13_henotuppari);
		selist.put("lv5", R.raw.v14_appare);
		selist.put("taiko", R.raw.taiko);
		
		
		// 効果音
		mSound = new SoundPool(selist.size(), AudioManager.STREAM_MUSIC, 0);
		mSound.setOnLoadCompleteListener(this);
		
		Set<String> keys = selist.keySet();
		Iterator<String> keyIte = keys.iterator();
		while(keyIte.hasNext()){
			String key = keyIte.next();
			registSound(key, selist.get(key));
		}

		// メイン画面設定
		setContentView(DogezaView.createView(getApplicationContext()));

		// 初期化処理
		try{
			init();
		}catch(Exception e){
			Log.e("DoGeZa", e.getLocalizedMessage());
		}

	}

	/**
	 * onResume
	 * アクシビティ再表示時のイベント
	 */
	@Override
	public synchronized void onResume() {
		super.onResume();

		mStatus = SceneStatus.STATUS_INIT;		
		
		// ここに処理を追加
		if(mPlayer != null){
			mPlayer.stop();
			mPlayer.release();
			mPlayer = null;
		}
		mPlayer = MediaPlayer.create(this, R.raw.dogeza);
		mPlayer.setVolume(0.5f, 0.5f);
		mPlayer.setLooping(true);
		mPlayer.start();

		// 「土下座」推定クラスの初期化処理
		mDogezaEstimation.initialize();
	}

	/**
	 * onPause
	 * アクシビティ一時停止時
	 */
	@Override
	public synchronized void onPause() {
		super.onPause();

		mPlayer.stop();
		mPlayer.release();
		mPlayer = null;
		// ここに処理を追加

		// 「土下座」推定クラスの終了処理
		mDogezaEstimation.finalize();
	}

	/**
	 * onStop
	 * アクシビティ停止
	 */
	@Override
	public void onStop() {
		super.onStop();

		// ここに処理を追加

	}

	/**
	 * onDestroy
	 * アクシビティ破棄
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();

		// ここに処理を追加
		endApplication(); 
	}
	
	//----------------------------------------------------------
	// サウンド関係
	//----------------------------------------------------------
	/**
	 * 
	 * @param key
	 * @param resId
	 */
	private void registSound(String key, int resId){
		int soundId = mSound.load(this, resId, 0);
		mSoundMap.put(key, soundId);
	}
	
	/**
	 * 
	 * @param key
	 * @param leftVolume
	 * @param rightVolume
	 * @param priority
	 */
	private void playSe(String key, float leftVolume, float rightVolume, int priority){
		mSound.play(mSoundMap.get(key), leftVolume, rightVolume, priority, 0, 1);
	}
	
	/**
	 * 
	 * @see android.media.SoundPool.OnLoadCompleteListener#onLoadComplete(android.media.SoundPool, int, int)
	 */
	@Override
	public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
		mLoadCnt++;
		if(mSoundMap.size() == mLoadCnt){
			mIsTapEnable =true;
		}
	}

	//----------------------------------------------------------
	// ゲーム制御関連
	//----------------------------------------------------------
	/**
	 * ゲーム実行処理
	 */
	private void proc() {
		if(mSceneMap.containsKey(getStatus()) == true){
			Method eventProc = mSceneMap.get(getStatus());
			try {
				// 描画処理準備
				DogezaView.drawBegin();
				// ステータスに関連づいたメソッドを呼び出す
				eventProc.invoke(this, (Object[])null);
				// 描画処理終了
				DogezaView.drawEnd();
			} catch (IllegalArgumentException e) {
				Log.e("DoGeZa", e.getLocalizedMessage());
			} catch (IllegalAccessException e) {
				Log.e("DoGeZa", e.getLocalizedMessage());
			} catch (InvocationTargetException e) {
				Log.e("DoGeZa", e.getLocalizedMessage());
			}
		}
	}

	/**
	 * シーン登録
	 * @param status　状態フラグ
	 * @param methodName シーン処理メソッド
	 * @throws NoSuchMethodException
	 */
	private void registScene(SceneStatus status, String methodName) throws NoSuchMethodException{
		Method method = MainActivity.class.getDeclaredMethod(methodName);
		mSceneMap.put(status, method);
	}
	
	/**
	 * ゲーム状態設定
	 * @param status 状態
	 */
	private void setStatus(SceneStatus status) {
		mStatus = status; 
	}

	/**
	 * ゲーム状態取得
	 */
	private SceneStatus getStatus() {
		return mStatus; 
	}

	/**
	 * 時間を保持します
	 */
	private void setSaveTime(){
		mStartTime = Calendar.getInstance().getTime();
	}
	
	/**
	 * setSaveTimeで保持した時間から引数msecで指定したミリ秒経過しているかを
	 * チェックします
	 * @param msec
	 * @return 引数の時間を超えている場合true
	 */
	private boolean IsOverTime(long msec ){
		Date cur = Calendar.getInstance().getTime();
		long curTime = cur.getTime();
		long stTime = mStartTime.getTime();
		return curTime - stTime >= msec;
	}
	
	/**
	 * setSaveTime()からの経過時間をミリ秒で取得します
	 * @return
	 */
	private long getSpanMSec(){
		Date cur = Calendar.getInstance().getTime();
		long curTime = cur.getTime();
		long stTime = mStartTime.getTime();
		return curTime - stTime;
	}
	
	/**
	 * setSaveTime()からの経過時間をミリ秒で取得します
	 * @return
	 */
	private long getSpanSec(){
		Date cur = Calendar.getInstance().getTime();
		long curTime = cur.getTime();
		long stTime = mStartTime.getTime();
		double span = (curTime - stTime) / 1000.0;
		return (int)Math.floor(span);
	}


	//----------------------------------------------------------
	// シーン別サブルーチン
	//----------------------------------------------------------
	/**
	 * 初期状態
	 */
	protected void OnStatusInit(){
		mIsTapEnable = true;
		Paint pt = new Paint();
		DogezaView.drawImageCenter("title", pt);
	}
	
	/**
	 * 初期状態
	 */
	@SuppressWarnings("unused")
	private void OnStatusRiseStart(){
		// 開始時間の保持
		setSaveTime();

		// 起立タイマー待ち状態へ遷移
		setStatus(SceneStatus.STATUS_RISE_TIMER);
		// タイトル画面変更
		Paint pt = new Paint();
		DogezaView.drawImageCenter("kiritsu", pt);

		//// カウント表示
		//dispCount(COUNT_RISE - mTimerCount);

		// ★ここへ起立開始時に必要な処理を記述する

		// 「起立」推定開始
		mDogezaEstimation.startKiritsuEstimation();
		
		playSe("kiritsu", 1, 1, 0);
	}

	
	/**
	 * 起立
	 */
	@SuppressWarnings("unused")
	private void OnStatusRiseTimer(){
		Paint pt = new Paint();
		
		// 登場アニメーション
		DogezaView.drawImageCenter("kiritsu", pt);

		if(IsOverTime(TIMER_RISE)){
			// 起立タイマー完了
			// 起立タイマー待ち状態へ遷移
			setStatus(SceneStatus.STATUS_DOWN_TIMER);

			// 「起立」推定停止
			mDogezaEstimation.stopKiritsuEstimation();
			// 「跪け」推定開始
			mDogezaEstimation.startHizamadukeEstimation();

    		// 開始時間の保持
			setSaveTime();
			
			playSe("hizamazuke", 1, 1, 0);

		} else {
			Paint paint = new Paint();
			float zoom = ((float)(getSpanMSec() - getSpanSec() * 1000)) / 1000.0f;
			DogezaView.drawImageCenter(Long.toString(3 - getSpanSec()), paint, zoom);
		}
	}

	/**
	 * 跪け
	 */
	@SuppressWarnings("unused")
	private void OnStatusDownTimer(){
		Paint pt = new Paint();
		DogezaView.drawImageCenter("hizamaduke", pt);

		if(IsOverTime(TIMER_DOWN)){
			// 起立タイマー完了
			// 土下座タイマー待ち状態へ遷移
			setStatus(SceneStatus.STATUS_DOGEZA_TIMER);

			// 土下座カウント計算(5秒+5秒の乱数)
			mTimerOffset = mRand.nextInt(COUNT_DOGEZA) * 1000;

			//// カウント表示
			//dispCount(mCountDogeza - mTimerCount);

			// ★ここへ土下座タイマー開始時に必要な処理を記述する

			// 「跪け」推定停止
			mDogezaEstimation.stopHizamadukeEstimation();

			// 「土下座」推定開始
			mDogezaEstimation.startDogezaEstimation();

			// 跪けの開始時間保持
			setSaveTime();

			playSe("dogeza", 1, 1, 0);
		
		} else {
			Paint paint = new Paint();
			float zoom = ((float)(getSpanMSec() - getSpanSec() * 1000)) / 1000.0f;
			DogezaView.drawImageCenter(Long.toString(3 - getSpanSec()), paint, zoom);
		}
	}

	/**
	 * 土下座
	 */
	@SuppressWarnings("unused")
	private void OnStatusDogezaTimer(){
		Paint pt = new Paint();
		DogezaView.drawImageCenter("dogeza", pt);

		if(IsOverTime(TIMER_DOGEZA + mTimerOffset)){
			// 土下座タイマー完了
			setSaveTime();

			// 土下座タイマー待ち状態へ遷移
			setStatus(SceneStatus.STATUS_RISE_FACE_TIMER);

			// ★ここへ土下座タイマー完了時に必要な処理を記述する

			// 「土下座」推定停止
			mDogezaEstimation.stopDogezaEstimation();
//	  		mTxtCount.setText(scoreText);

			playSe("omotewoage", 1, 1, 0);

		} else {
		}
	}

	/**
	 * 判定処理
	 */
	@SuppressWarnings("unused")
    private void OnStatusRiseFaceTimer(){
		Paint pt = new Paint();
		DogezaView.drawImageCenter("omotewoage", pt);

		if(IsOverTime(TIMER_RISE_FACE)){
			// 面を上げタイマー完了
 		
    		// ★ここへ面を上げタイマー完了時に必要な処理を記述する
    		setSaveTime();

    		// 判定状態へ遷移
    		setStatus(SceneStatus.STATUS_JUDGE);
    		mRankSEPlaied = false;
		}
    }

    /**
     * 判定
     */
	@SuppressWarnings("unused")
	private void OnStatusJudge(){
		// スコアの表示
    	/*    		
		String scoreText = "起立：" + String.valueOf(mDogezaEstimation.getScoreOfKiritsuE()) + "\n" +
						   "跪け：" + String.valueOf(mDogezaEstimation.getScoreOfHizamadukeE()) + "\n" +
						   "土下座：" + String.valueOf(mDogezaEstimation.getScoreOfDogezaE());
    	*/
		Paint pt = new Paint();
		//DogezaView.drawImageCenter("", pt);
		pt.setColor(Color.WHITE);
		pt.setTextSize(60);

		if(mRankImg == ""){
			// スコア計算
			calcScore();
			for(Map.Entry<Float, String> e : mScoreImgMap.entrySet()) {
				if(e.getKey() < mScore) {
					mRankImg = e.getValue();
					break;
				}
			}
		}

		if(!mRankSEPlaied){
			mRankSEPlaied = true;
			playSe(mRankImg, 1, 1, 0);
		}
		
		
		if(IsOverTime(3 * 1000) == true){
			// タップ可能
			mIsTapEnable = true;

		}
		// ランク画像表示
		DogezaView.drawImageCenter(mRankImg, new Paint());
	
		// 得点文字列生成
//		String scoreText = String.format(getString(R.string.score_format2), mScore);
		String scoreText = String.format(getString(R.string.score_format2), (int)Math.floor(mScore));
		DogezaView.drawText(scoreText, 100, 30, pt);
	}

	/**
	 * スコア計算
	 */
	private void calcScore() {
		// 得点計算
		mScore = 0.0f;
		mScore += 100.0f * 0.1f * mDogezaEstimation.getScoreOfKiritsuE();		// 起立
		mScore += 100.0f * 0.2f * mDogezaEstimation.getScoreOfHizamadukeE();	// 跪け
		mScore += 100.0f * 0.7f * mDogezaEstimation.getScoreOfDogezaE();		// 土下座
//		mScore += 20.0f;		// 20点かさ上げ
		mScore -= 50.0f;
		mScore *= 3.6f;
		
		if(mScore >= 100.0f) {
			mScore = 100.0f;
		}else if(mScore < 0.0f){
			mScore = 0.0f;
		}
	}

	//--------------------------------------------------------------------------------
	// タッチイベント処理
	//--------------------------------------------------------------------------------

	/**
	 * onTouchEvent
	 * @param event
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// タッチイベント処理
		mGesDetector.onTouchEvent(event);
		return false;
	}

	private final SimpleOnGestureListener onGestureListener = new SimpleOnGestureListener() {
		@Override
		public boolean onDoubleTap(MotionEvent event) {
			return super.onDoubleTap(event);
		}

		@Override
		public boolean onDoubleTapEvent(MotionEvent event) {
			return super.onDoubleTapEvent(event);
		}

		@Override
		public boolean onDown(MotionEvent event) {
			return super.onDown(event);
		}

		@Override
		public boolean onFling(MotionEvent event1, MotionEvent event2, float velocityX, float velocityY) {
			return super.onFling(event1, event2, velocityX, velocityY);
		}

		@Override
		public void onLongPress(MotionEvent event) {
			super.onLongPress(event);
		}

		@Override
		public boolean onScroll(MotionEvent event1, MotionEvent event2, float distanceX, float distanceY) {
			return super.onScroll(event1, event2, distanceX, distanceY);
		}

		@Override
		public void onShowPress(MotionEvent event) {
			super.onShowPress(event);
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent event) {
			if(mIsTapEnable == false){
				return false;
			}
			switch(getStatus()) {
			case STATUS_INIT:
				// 起立開始状態へ遷移
				setStatus(SceneStatus.STATUS_RISE_START);
				mIsTapEnable = false;
				playSe("taiko", 1, 1, 0);
				return true;			// タッチ処理実行済を返す
			case STATUS_JUDGE:
				// 初期状態へ遷移
				setStatus(SceneStatus.STATUS_INIT);
				mIsTapEnable = false;
				mRankImg = "";
				return true;			// タッチ処理実行済を返す
			default:
				break;
			}
			return super.onSingleTapConfirmed(event);
		}

		@Override
		public boolean onSingleTapUp(MotionEvent event) {
			return super.onSingleTapUp(event);
		}
	};


}
