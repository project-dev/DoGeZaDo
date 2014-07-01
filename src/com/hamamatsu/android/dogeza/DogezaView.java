package com.hamamatsu.android.dogeza;

import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

/**
 * 土下座専用Viewクラス
 * @author TAKA@はままつ
 */
public class DogezaView extends SurfaceView implements SurfaceHolder.Callback{
	/**
	 * 
	 */
	private static float zoomVal = 0.0f;

	/**
	 * イメージマップ
	 */
	private static HashMap<String, Integer> m_imgMap = null;

	/**
	 * View
	 */
	private static DogezaView m_view = null;

	/**
	 * 描画用キャンバス
	 */
	private static Canvas m_canvas = null;

	/**
	 * バックバッファ
	 */
	private static Bitmap m_bkBuff = null;
	
	/**
	 * インスタンス生成
	 * DogezaViewのインスタンスは、アプリケーション内で一つだけでいい
	 * @param context
	 * @return
	 */
	public static DogezaView createView(Context context){
		m_view = new DogezaView(context);
		return m_view;
	}
	
	/**
	 * コンストラクタ
	 * @param context
	 */
	private DogezaView(Context context) {
		super(context);
		initalize();
	}

	/**
	 * コンストラクタ
	 * @param context
	 * @param attrs
	 */
	private DogezaView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initalize();
	}

	/**
	 * コンストラクタ
	 * @param context
	 * @param attrs
	 * @param defStyle
	 */
	private DogezaView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initalize();
	}

	/**
	 * 初期化
	 */
	private void initalize(){
		Log.d("dogeza", "initalize");
		getHolder().addCallback(this);
	}
	
	/**
	 * リソースのイメージをDrawManagerに登録します
	 * @param imgName
	 * @param id
	 */
	public static void registImage(String imgName, int id){
		if(m_imgMap == null){
			m_imgMap = new HashMap<String, Integer>();
		}
		m_imgMap.put(imgName, id);
	}	

	/**
	 * 描画準備
	 */
	public static void drawBegin(){
		try{
			m_canvas = null;
			m_view.calcZoom();
			if(m_bkBuff == null){
				m_bkBuff = Bitmap.createBitmap(m_view.getWidth(), m_view.getHeight(), Bitmap.Config.ARGB_8888);
			}
			if(m_canvas == null){
				m_canvas = new Canvas(m_bkBuff);
			}
			Paint paint = new Paint();
			paint.setColor(Color.BLACK);
			Rect r = new Rect(0, 0, m_view.getWidth(), m_view.getHeight());
			m_canvas.drawRect(r, paint);
		}catch(Exception e){
			
		}
	}

	/**
	 * 描画終了
	 */
	public static void drawEnd(){
		Canvas canvas = null;
		try{
			SurfaceHolder holder = m_view.getHolder();
			canvas = holder.lockCanvas();
			try{
				canvas.drawBitmap(m_bkBuff, 0, 0, new Paint());
			}finally{
				holder.unlockCanvasAndPost(canvas);
			}
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			m_canvas = null;
		}
	}
	
	/**
	 * イメージ描画
	 * @param imgName
	 * @param x
	 * @param y
	 * @param paint
	 */
	public static void drawImage(String imgName, int x, int y, Paint paint){
		if(m_canvas == null){
			return;
		}
		if(m_imgMap.containsKey(imgName) == false){
			return;
		}
		Context ctx = m_view.getContext();
		Resources res = ctx.getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, m_imgMap.get(imgName));
		if(bmp == null){
			return;
		}
		Rect src = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
		Rect dst = new Rect(x, y, bmp.getWidth(), bmp.getHeight());
		m_canvas.drawBitmap(bmp, src, dst, paint);
	}

	/**
	 * イメージ描画
	 * @param imgName
	 * @param x
	 * @param y
	 * @param width
	 * @param height
	 * @param paint
	 */
	public static void drawImage(String imgName, int x, int y, int width, int height, Paint paint){
		if(m_canvas == null){
			return;
		}
		if(m_imgMap.containsKey(imgName) == false){
			return;
		}
		Context ctx = m_view.getContext();
		Resources res = ctx.getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, m_imgMap.get(imgName));
		if(bmp == null){
			return;
		}
		int bmpWidth = bmp.getWidth();
		int bmpHeight = bmp.getHeight();
		Rect srcRect = new Rect(0, 0, bmpWidth, bmpHeight);
		Rect distRect = new Rect(x, y, (int)(width * zoomVal), (int)(height * zoomVal));
		m_canvas.drawBitmap( bmp, srcRect, distRect, paint);
	}
	public static void drawImageToCenter(String imgName, int cnt, int maxCnt, Distinct vDist, Distinct hDist, Paint paint){
		if(m_canvas == null){
			return;
		}
		if(m_imgMap.containsKey(imgName) == false){
			return;
		}

		Context ctx = m_view.getContext();
		Resources res = ctx.getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, m_imgMap.get(imgName));
		if(bmp == null){
			return;
		}
		
		int bmpW = bmp.getWidth();
		int bmpH = bmp.getHeight();
		
		int dispX = (m_view.getWidth() - bmpW) / 2;
		int dispY = (m_view.getHeight() - bmpH) / 2;

		int x = dispX;
		int y = dispY;
		if(maxCnt < cnt){
			cnt = maxCnt;
		}
		switch(vDist){
		case ToDown:
			y = (dispY + bmpH) / maxCnt * cnt - bmpH;
			break;
		case ToUp:
			y = m_view.getHeight() - dispY / maxCnt * cnt;
			break;
		default:
			// 何もしない
			break;
		}
		switch(hDist){
		case ToLeft:
			x = dispX / maxCnt * cnt;
			break;
		case ToRight:
			x = m_view.getWidth() - dispX / maxCnt * cnt;
			break;
		default:
			// 何もしない
			break;
		}
		
		
		m_canvas.drawBitmap(bmp, x, y, paint);
	}
	
	public static void drawImageCenter(String imgName, Paint paint, float zoom){
		if(m_canvas == null){
			return;
		}
		if(m_imgMap.containsKey(imgName) == false){
			return;
		}
		Context ctx = m_view.getContext();
		Resources res = ctx.getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, m_imgMap.get(imgName));
		if(bmp == null){
			return;
		}

		Matrix mtx = new Matrix();
		if(zoom == 0.0f){
			zoom = 0.01f;
		}
		mtx.postScale(zoom, zoom);
		Bitmap zoomMap = null;
		try{
			zoomMap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mtx, true);
			int dispX = (m_view.getWidth() - zoomMap.getWidth()) / 2;
			int dispY = (m_view.getHeight() - zoomMap.getHeight()) / 2;
			m_canvas.drawBitmap(zoomMap, dispX, dispY, paint);
		}catch(IllegalArgumentException iae){
			throw iae;
		}
	}
	
	/**
	 * 画像を画面中央に表示
	 * @param imgName
	 * @param paint
	 */
	public static void drawImageCenter(String imgName, Paint paint){
		if(m_canvas == null){
			return;
		}
		
		if(m_imgMap.containsKey(imgName) == false){
			return;
		}

		Context ctx = m_view.getContext();
		Resources res = ctx.getResources();
		Bitmap bmp = BitmapFactory.decodeResource(res, m_imgMap.get(imgName));
		if(bmp == null){
			return;
		}
	
		int dispX = (m_view.getWidth() - bmp.getWidth()) / 2;
		int dispY = (m_view.getHeight() - bmp.getHeight()) / 2;
		m_canvas.drawBitmap(bmp, dispX, dispY, paint);
	}
	
	/**
	 * 文字列を表示
	 * @param text
	 * @param x
	 * @param y
	 * @param paint
	 */
	public static void drawText(String text, int x, int y, Paint paint){
		if(m_canvas == null){
			return;
		}
		FontMetrics fm = paint.getFontMetrics();
		m_canvas.drawText(text, (int)(x * zoomVal), (int)(y * zoomVal) - fm.top, paint);
	}

	/**
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder, int, int, int)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		createBackBuffer(width, height);
	}

	/**
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		drawBackBuffer(this, holder);
	}

	private void calcZoom(){
		// ウィンドウマネージャのインスタンス取得
		WindowManager wm = (WindowManager)this.getContext().getSystemService(Activity.WINDOW_SERVICE);
		// 幅、高さを比較し、画像の拡縮率を求める
		DisplayMetrics metrics = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(metrics);
		
		zoomVal = 1.0f; 
		zoomVal = 1.0f / metrics.scaledDensity;
	}
	
	/**
	 * 
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		releaseBuffer();
	}

	/**
	 * 
	 * @param width
	 * @param height
	 */
	private static void createBackBuffer(int width, int height){
		m_bkBuff = null;
		m_bkBuff = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	}

	/**
	 * 
	 * @param view
	 * @param holder
	 */
	private static void drawBackBuffer(DogezaView view, SurfaceHolder holder){
		Canvas canvas = holder.lockCanvas();
		if(m_bkBuff == null){
			createBackBuffer(view.getWidth(), view.getHeight());
		}else{
			// ここに描画処理を記述する
			canvas.drawBitmap(m_bkBuff, 0, 0, new Paint());
		}
		holder.unlockCanvasAndPost(canvas);
	}

	/**
	 * 
	 */
	private static void releaseBuffer(){
		m_bkBuff = null;
	}
}
