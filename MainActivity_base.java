package com.hamamatsu.android.dogeza;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import jp.epson.moverio.bt200.SensorControl;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;

//--------------------------------------------------------------------------------
// MainActivityクラス
//--------------------------------------------------------------------------------
public class MainActivity_base extends Activity {

	// 定数定義
	// タイマー関連
	/**
	 * タイマー更新間隔
	 */
	private final int TIMER_UPDATE = 1000;
	/**
	 * 起立時間
	 */
	private final int TIMER_RISE = 3000;
	/**
	 * 跪く時間
	 */
	private final int TIMER_DOWN = 3000;
	/**
	 * 土下座時間
	 */
	private final int TIMER_DOGEZA = 5000;
	
	private final int COUNT_RISE = TIMER_RISE / TIMER_UPDATE;
	private final int COUNT_DOWN = TIMER_DOWN / TIMER_UPDATE;
	private final int COUNT_DOGEZA = TIMER_DOGEZA / TIMER_UPDATE;

	// 変数定義
	/**
	 * タイトルイメージ
	 */
	private ImageView mImgTitle = null;
	/**
	 * カウント表示テキスト
	 */
	private TextView mTxtCount = null;
	/**
	 * データ更新タイマー
	 */
	private Timer mTimerUpdate = null;
	/**
	 * ゲーム状態
	 */
	private SceneStatus mStatus = SceneStatus.STATUS_INIT;
	/**
	 * タイマー処理回数
	 */
	private int mTimerCount = 0;
	/**
	 * タッチイベント処理
	 */
	private GestureDetector mGesDetector = null;
	/**
	 * 乱数
	 */
	private Random mRand = null;
	/**
	 * 土下座カウント(5秒+5秒の乱数)
	 */
	private int mCountDogeza = 0;
	/**
	 * 「土下座」推定クラス
	 */
	private DogezaEstimation mDogezaEstimation = null;

	/**
	 * シーンマップ
	 */
	private HashMap<SceneStatus,  Method> mSceneMap = null;
	
	
	/**
	 * onCreate
	 * @param savedInstanceState
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// メイン画面設定
		setContentView(R.layout.activity_main);

		// 初期化処理
		try{
			init();
		}catch(Exception e){
			e.printStackTrace();
		}

	}

	/**
	 * onResume
	 */
	@Override
	public synchronized void onResume() {
		super.onResume();

		// ここに処理を追加

        // 「土下座」推定クラスの初期化処理
		mDogezaEstimation.initialize();
	}

	/**
	 * onPause
	 */
	@Override
	public synchronized void onPause() {
		super.onPause();

		// ここに処理を追加

        // 「土下座」推定クラスの終了処理
		mDogezaEstimation.finalize();
	}

	/**
	 * onStop
	 */
	@Override
	public void onStop() {
		super.onStop();

		// ここに処理を追加

	}

    /**
     * onDestroy
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

		// ここに処理を追加

        // データ更新タイマー停止
        stopTimerUpdate();

	}

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

    /**
     * 初期化処理
     */
    private void init() throws Exception{
    	
    	SensorControl sensor = new SensorControl(this.getApplicationContext());
    	sensor.setMode(SensorControl.SENSOR_MODE_HEADSET);
    	
    	// シーン設定
    	mSceneMap = new HashMap<SceneStatus, Method>();
    	registScene(SceneStatus.STATUS_INIT, "OnStatusInit");
    	registScene(SceneStatus.STATUS_RISE_START, "OnStatusRiseStart");
    	registScene(SceneStatus.STATUS_RISE_TIMER, "OnStatusRiseTimer");
    	registScene(SceneStatus.STATUS_DOWN_TIMER, "OnStatusDownTimer");
    	registScene(SceneStatus.STATUS_DOGEZA_TIMER, "OnStatusDogezaTimer");
    	registScene(SceneStatus.STATUS_JUDGE, "OnStatusJudge");
    	
    	// タイトルイメージ取得
    	mImgTitle = (ImageView)findViewById(R.id.imgTitle);
    	// カウント表示テキスト取得
    	mTxtCount = (TextView)findViewById(R.id.txtCount);
    	// タッチイベント処理設定
    	mGesDetector = new GestureDetector(this, onGestureListener);
    	// 乱数初期化
    	mRand = new Random();
    	// 初期状態へ設定
    	setStatus(SceneStatus.STATUS_INIT);
    	// ゲーム実行処理
    	proc();
        // 「土下座」推定クラスの生成
        mDogezaEstimation = new DogezaEstimation(this);
    }

    private void registScene(SceneStatus status, String methodName) throws NoSuchMethodException{
    	Method method = MainActivity_base.class.getDeclaredMethod(methodName);
		mSceneMap.put(status, method);
    }
    
    /**
     * データ更新タイマー開始
     */
    private void startTimerUpdate() {
		// タイマー停止
    	stopTimerUpdate();
    	// タイマー生成
		mTimerUpdate = new Timer();
    	// タイマー設定
    	TimerTask task = new TimerTaskUpdate(this);
    	mTimerUpdate.schedule(task, TIMER_UPDATE, TIMER_UPDATE);
    }

    /**
     * データ更新タイマー開始
     */
    private void stopTimerUpdate() {
    	if(null != mTimerUpdate) {
    		// タイマー停止
    		mTimerUpdate.cancel();
    		mTimerUpdate = null;
    	}
		mTimerCount = 0;
    }

    /**
     * ゲーム状態設定
     * @param status
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
     * カウント表示
     */
    private void dispCount(int count) {
		// カウント表示
		String str = String.valueOf(count);
		mTxtCount.setText(str);
    }

    /**
     * ゲーム実行処理
     */
    private void proc() {
    	if(mSceneMap.containsKey(getStatus()) == true){
    		Method eventProc = mSceneMap.get(getStatus());
    		try {
    			// ステータスに関連づいたメソッドを呼び出す
				eventProc.invoke(this, (Object[])null);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
    	}
/*
    	// ゲーム状態で処理振り分け
    	switch(getStatus()) {
    	case STATUS_INIT:			// 初期状態 
    		mImgTitle.setImageResource(R.drawable.dogeza);
    		break;
    	case STATUS_RISE_START:		// 起立開始状態
    		// 起立タイマー待ち状態へ遷移
    		setStatus(SceneStatus.STATUS_RISE_TIMER);
    		// タイトル画面変更
    		mImgTitle.setImageResource(R.drawable.kiritsu);
    		// カウント表示
    		dispCount(COUNT_RISE - mTimerCount);
    		// データ更新タイマー開始
    		startTimerUpdate();

    		// ★ここへ起立開始時に必要な処理を記述する

    		// 「起立」推定開始
    		mDogezaEstimation.startKiritsuEstimation();
    		
    		break;
    	case STATUS_RISE_TIMER:		// 起立タイマー待ち状態
    		mTimerCount++;
    		if(COUNT_RISE <= mTimerCount) {
    			// 起立タイマー完了
    			// データ更新タイマー停止
    			stopTimerUpdate();
        		// 起立タイマー待ち状態へ遷移
        		setStatus(SceneStatus.STATUS_DOWN_TIMER);
        		// タイトル画面変更
        		mImgTitle.setImageResource(R.drawable.hizamaduke);
        		// カウント表示
        		dispCount(COUNT_DOWN - mTimerCount);
        		// データ更新タイマー開始
        		startTimerUpdate();

        		// ★ここへ跪くタイマー開始時に必要な処理を記述する

        		// 「起立」推定停止
        		mDogezaEstimation.stopKiritsuEstimation();
        		// 「跪け」推定開始
        		mDogezaEstimation.startHizamadukeEstimation();
        		
    		} else {
    			// 起立タイマー未完了
        		// カウント表示
        		dispCount(COUNT_RISE - mTimerCount);
    		}
    		break;
    	case STATUS_DOWN_TIMER:		// 跪くタイマー待ち状態
    		mTimerCount++;
    		if(COUNT_DOWN <= mTimerCount) {
    			// 起立タイマー完了
    			// データ更新タイマー停止
    			stopTimerUpdate();
        		// 土下座タイマー待ち状態へ遷移
        		setStatus(SceneStatus.STATUS_DOGEZA_TIMER);
        		// タイトル画面変更
        		mImgTitle.setImageResource(R.drawable.dogeza);
        		// 土下座カウント計算(5秒+5秒の乱数)
        		mCountDogeza = COUNT_DOGEZA + mRand.nextInt(COUNT_DOGEZA);
        		// カウント表示
        		dispCount(mCountDogeza - mTimerCount);
        		// データ更新タイマー開始
        		startTimerUpdate();

        		// ★ここへ土下座タイマー開始時に必要な処理を記述する

        		// 「跪け」推定停止
        		mDogezaEstimation.stopHizamadukeEstimation();
        		// 「土下座」推定開始
        		mDogezaEstimation.startDogezaEstimation();
        		
    		} else {
    			// 起立タイマー未完了
        		// カウント表示
        		dispCount(COUNT_DOWN - mTimerCount);
    		}
    		break;
    	case STATUS_DOGEZA_TIMER:		// 土下座タイマー待ち状態
    		mTimerCount++;
    		if(mCountDogeza <= mTimerCount) {
    			// 土下座タイマー完了
    			// データ更新タイマー停止
    			stopTimerUpdate();
        		// 土下座タイマー待ち状態へ遷移
        		setStatus(SceneStatus.STATUS_JUDGE);
        		// タイトル画面変更
        		mImgTitle.setImageResource(R.drawable.omotewoage);

        		// ★ここへ土下座タイマー完了時に必要な処理を記述する

        		// 「土下座」推定停止
        		mDogezaEstimation.stopDogezaEstimation();
        		// スコアの表示
        		String scoreText = "起立：" + String.valueOf(mDogezaEstimation.getScoreOfKiritsuE()) + "\n" +
        						   "跪け：" + String.valueOf(mDogezaEstimation.getScoreOfHizamadukeE()) + "\n" +
        						   "土下座：" + String.valueOf(mDogezaEstimation.getScoreOfDogezaE());
        		mTxtCount.setText(scoreText);
        		
    		} else {
    			// 起立タイマー未完了
        		// カウント表示
        		dispCount(mCountDogeza - mTimerCount);
    		}
    		break;
    	case STATUS_JUDGE:			// 判定状態
    		break;
    	default:
    		break;
    	}
 */
    }

    /**
     * 初期状態
     */
    protected void OnStatusInit(){
		mImgTitle.setImageResource(R.drawable.dogeza);
    }
    
    /**
     * 初期状態
     */
    private void OnStatusRiseStart(){
		// 起立タイマー待ち状態へ遷移
		setStatus(SceneStatus.STATUS_RISE_TIMER);
		// タイトル画面変更
		mImgTitle.setImageResource(R.drawable.kiritsu);
		// カウント表示
		dispCount(COUNT_RISE - mTimerCount);
		// データ更新タイマー開始
		startTimerUpdate();

		// ★ここへ起立開始時に必要な処理を記述する

		// 「起立」推定開始
		mDogezaEstimation.startKiritsuEstimation();
    }

    /**
     * 
     */
    private void OnStatusRiseTimer(){
		mTimerCount++;
		if(COUNT_RISE <= mTimerCount) {
			// 起立タイマー完了
			// データ更新タイマー停止
			stopTimerUpdate();
    		// 起立タイマー待ち状態へ遷移
    		setStatus(SceneStatus.STATUS_DOWN_TIMER);
    		// タイトル画面変更
    		mImgTitle.setImageResource(R.drawable.hizamaduke);
    		// カウント表示
    		dispCount(COUNT_DOWN - mTimerCount);
    		// データ更新タイマー開始
    		startTimerUpdate();

    		// ★ここへ跪くタイマー開始時に必要な処理を記述する

    		// 「起立」推定停止
    		mDogezaEstimation.stopKiritsuEstimation();
    		// 「跪け」推定開始
    		mDogezaEstimation.startHizamadukeEstimation();
    		
		} else {
			// 起立タイマー未完了
    		// カウント表示
    		dispCount(COUNT_RISE - mTimerCount);
		}
    }

    private void OnStatusDownTimer(){
		mTimerCount++;
		if(COUNT_DOWN <= mTimerCount) {
			// 起立タイマー完了
			// データ更新タイマー停止
			stopTimerUpdate();
    		// 土下座タイマー待ち状態へ遷移
    		setStatus(SceneStatus.STATUS_DOGEZA_TIMER);
    		// タイトル画面変更
    		mImgTitle.setImageResource(R.drawable.dogeza);
    		// 土下座カウント計算(5秒+5秒の乱数)
    		mCountDogeza = COUNT_DOGEZA + mRand.nextInt(COUNT_DOGEZA);
    		// カウント表示
    		dispCount(mCountDogeza - mTimerCount);
    		// データ更新タイマー開始
    		startTimerUpdate();

    		// ★ここへ土下座タイマー開始時に必要な処理を記述する

    		// 「跪け」推定停止
    		mDogezaEstimation.stopHizamadukeEstimation();
    		// 「土下座」推定開始
    		mDogezaEstimation.startDogezaEstimation();
    		
		} else {
			// 起立タイマー未完了
    		// カウント表示
    		dispCount(COUNT_DOWN - mTimerCount);
		}
    }

    private void OnStatusDogezaTimer(){
		mTimerCount++;
		if(mCountDogeza <= mTimerCount) {
			// 土下座タイマー完了
			// データ更新タイマー停止
			stopTimerUpdate();
    		// 土下座タイマー待ち状態へ遷移
    		setStatus(SceneStatus.STATUS_JUDGE);
    		// タイトル画面変更
    		mImgTitle.setImageResource(R.drawable.omotewoage);

    		// ★ここへ土下座タイマー完了時に必要な処理を記述する

    		// 「土下座」推定停止
    		mDogezaEstimation.stopDogezaEstimation();
    		// スコアの表示
    		String scoreText = "起立：" + String.valueOf(mDogezaEstimation.getScoreOfKiritsuE()) + "\n" +
    						   "跪け：" + String.valueOf(mDogezaEstimation.getScoreOfHizamadukeE()) + "\n" +
    						   "土下座：" + String.valueOf(mDogezaEstimation.getScoreOfDogezaE());
    		mTxtCount.setText(scoreText);
    		
		} else {
			// 起立タイマー未完了
    		// カウント表示
    		dispCount(mCountDogeza - mTimerCount);
		}
    }

    private void OnStatusJudge(){
    }
    //--------------------------------------------------------------------------------
    // タッチイベント処理
    //--------------------------------------------------------------------------------
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
    		switch(getStatus()) {
    		case STATUS_INIT:			// 初期状態
    			// 起立開始状態へ遷移
    	    	setStatus(SceneStatus.STATUS_RISE_START);
    	    	// ゲーム実行処理
    	    	proc();
    			return true;			// タッチ処理実行済を返す
    		case STATUS_JUDGE:			// 判定状態
    			// 初期状態へ遷移
    	    	setStatus(SceneStatus.STATUS_INIT);
    	    	// ゲーム実行処理
    	    	proc();
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

    //--------------------------------------------------------------------------------
    // 画面更新用タイマー関連
    //--------------------------------------------------------------------------------
    /**
     * データ更新用タイマータスク
     */
    public class TimerTaskUpdate extends TimerTask {
    	// メンバ変数
    	private Handler mHandlerUpdate = null;	// データ更新ハンドラ
    	private Context mContext = null;		// Context
    	
    	/**
    	 * コンストラクタ
    	 * @param context
    	 */
    	public TimerTaskUpdate(Context context) {
    		mHandlerUpdate = new Handler();
    		mContext = context;
    	}

    	/**
    	 * 実行処理
    	 */
		@Override
		public void run() {
			mHandlerUpdate.post(new Runnable() {
				@Override
				public void run() {
					// ゲーム実行処理
					proc();
				}
			});
		}
    };

}
