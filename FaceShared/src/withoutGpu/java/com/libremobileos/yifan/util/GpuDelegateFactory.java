package com.libremobileos.yifan.util;

import org.tensorflow.lite.Delegate;

public class GpuDelegateFactory {
	public static Delegate get() {
		throw new UnsupportedOperationException("compiled without GPU library, can't create GPU delegate");
	}

	public static boolean isSupported() {
		return false;
	}
}
