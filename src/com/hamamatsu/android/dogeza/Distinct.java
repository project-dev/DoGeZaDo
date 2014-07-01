package com.hamamatsu.android.dogeza;

/**
 * 
 * @author 日本Androidの会 浜松支部
 */
public enum Distinct {
	Unknow(0xFFFF),
	ToDown(0x0001),
	ToUp(0x0002),
	ToLeft(0x0101),
	ToRight(0x0102);
	
	int dist;
	Distinct(int dist){
		this.dist = dist;
	}
}
