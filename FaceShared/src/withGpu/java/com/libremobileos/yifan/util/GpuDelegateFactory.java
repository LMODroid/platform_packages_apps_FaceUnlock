package com.libremobileos.yifan.util;

import org.tensorflow.lite.Delegate;
import org.tensorflow.lite.gpu.GpuDelegate;

public class GpuDelegateFactory {
	public static Delegate get() {
		return new GpuDelegate();
	}

	public static boolean isSupported() {
		return true;
	}
}
