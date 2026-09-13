package willits.jhenry.controller;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;

public final class WindowFocus {

	private interface User32Ext extends User32 {
		User32Ext INSTANCE = Native.load("user32", User32Ext.class);

		void SwitchToThisWindow(HWND hwnd, boolean unknown);
	}

	private WindowFocus() {
	}

	public static boolean focusByPid(long pid) {
		final HWND[] found = { null };
		User32.INSTANCE.EnumWindows((hwnd, data) -> {
			IntByReference owner = new IntByReference();
			User32.INSTANCE.GetWindowThreadProcessId(hwnd, owner);
			if (owner.getValue() != (int) pid) {
				return true;
			}
			if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
				return true;
			}
			if (User32.INSTANCE.GetWindowTextLength(hwnd) <= 0) {
				return true;
			}
			found[0] = hwnd;
			return false;
		}, null);

		if (found[0] == null) {
			return false;
		}

		User32.INSTANCE.ShowWindow(found[0], WinUser.SW_RESTORE);
		if (!User32.INSTANCE.SetForegroundWindow(found[0])) {
			User32Ext.INSTANCE.SwitchToThisWindow(found[0], true);
		}
		return true;
	}
}
