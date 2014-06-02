package com.hamamatsu.android.dogeza;

public enum SceneStatus {
	/**
	 * 初期状態
	 */
	STATUS_INIT,
	/**
	 * 起立開始状態
	 */
	STATUS_RISE_START,
	/**
	 * 起立タイマー待ち状態
	 */
	STATUS_RISE_TIMER,
	/**
	 * 跪くタイマー待ち状態
	 */
	STATUS_DOWN_TIMER,
	/**
	 * 土下座状態
	 */
	STATUS_DOGEZA_TIMER,
	/**
	 * 面を上げ状態
	 */
	STATUS_RISE_FACE_TIMER,
	/**
	 * 判定状態
	 */
	STATUS_JUDGE

}
