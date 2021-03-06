/*
Copyright (c) 2009-2016, Andrew M. Martin
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
conditions are met:

 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following
   disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of Pandam nor the names of its contributors may be used to endorse or promote products derived from this
   software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.
*/
package org.pandcorps.pandam.android;

import java.io.*;

import android.opengl.*;
import android.os.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.util.*;
import android.view.*;
import org.pandcorps.core.*;
import org.pandcorps.core.Iotil.*;
import org.pandcorps.pandam.*;

/*
Title - res/values/strings.xml/app_name
*/

public abstract class PanActivity extends Activity {
	protected static PanActivity activity = null;
	protected static GLSurfaceView view = null;
	
	protected abstract Pangame newGame();
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		activity = this;
		ImgFactory.setFactory(new AndroidImgFactory());
		new AndroidPangine();
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int flags = WindowManager.LayoutParams.FLAG_FULLSCREEN; // | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        getWindow().setFlags(flags, flags);
		init();
        view = new PanSurfaceView(this);
        view.setRenderer(new PanRenderer());
        view.setRenderMode(PanSurfaceView.RENDERMODE_CONTINUOUSLY);
        
        System.setProperty("user.dir", getFilesDir().getAbsolutePath());
        Iotil.setWriterFactory(new AndroidWriterFactory());
        Iotil.setInputStreamFactory(new AndroidInputStreamFactory());
        Iotil.setResourceChecker(new AndroidResourceChecker());
        Iotil.setResourceDeleter(new AndroidResourceDeleter());
        Iotil.setResourceLister(new AndroidResourceLister());
        
        setSize();
        setTopOffset();
        setContentView(view);
	}
	
	protected final void init() {
		try {
			((WindowInitializer) Reftil.newInstance("org.pandcorps.pandam.android.NavigationHider")).init(getWindow());
		} catch (final Throwable e) {
			// Should be an older Android version that doesn't have a navigation bar
		}
	}
	
	private final void setSize() {
		if (AndroidPangine.desktopWidth > 0) {
			return;
		}
        try {
        	final Point size = new Point();
            getWindowManager().getDefaultDisplay().getRealSize(size); // API level 17
            AndroidPangine.desktopWidth = size.x;
            AndroidPangine.desktopHeight = size.y;
            if (AndroidPangine.desktopWidth > 0) {
            	return;
            }
        } catch (final Throwable e) {
            // Just try a technique below
        }
        final int w = view.getWidth();
        if (w > 0) {
        	AndroidPangine.desktopWidth = w;
        	AndroidPangine.desktopHeight = view.getHeight();
        } else {
        	final Resources r = getResources();
        	if (r != null) {
        		final DisplayMetrics dm = r.getDisplayMetrics();
        		if (dm != null) {
        			AndroidPangine.desktopWidth = dm.widthPixels;
        			AndroidPangine.desktopHeight = dm.heightPixels;
        		} else {
        			throw new RuntimeException("Cannot find device width/height");
        		}
        	}
        }
	}
	
	private final static boolean isSet(final String flagClassName, final boolean def) {
		try {
			return ((PanFlag) Reftil.newInstance(flagClassName)).isSet();
		} catch (final Throwable e) {
			return def;
		}
	}
	
	protected final static boolean isHideNavigationSafe() {
		return Build.VERSION.SDK_INT >= 19;
	}
	
	private final void setTopOffset() {
		if (isHideNavigationSafe()) {
			return;
		//} else if (KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)) {
			// Tested this on a tablet that returned true for a navigation bar back button instead of a real one
		//	return;
		} else if (isSet("org.pandcorps.pandam.android.MenuKeyFlag", true)) {
			return;
		}
		calculateTopOffset();
		AndroidPangine.engine.setZoomChangeHandler(new Runnable() {
			@Override public final void run() {
				calculateTopOffset();
			}});
	}
	
	private final void calculateTopOffset() {
		final int vh = view.getHeight(), off;
		if (vh >= 0) {
			off = AndroidPangine.engine.getDesktopHeight() - vh;
		} else {
			off = 12 * Math.round(AndroidPangine.engine.getZoom());
		}
		AndroidPangine.engine.setTopOffset(off);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		//getMenuInflater().inflate(R.menu.pan, menu);
		return true;
	}
	
	@Override
	protected final void onResume() {
		init();
		super.onResume();
		view.onResume();
	}

	@Override
	protected final void onPause() {
		super.onPause();
		view.onPause();
	}
	
	private final class AndroidWriterFactory implements WriterFactory {
		@Override
		public final Writer newWriter(final String location) throws Exception {
			return new OutputStreamWriter(openFileOutput(location, Context.MODE_PRIVATE));
		}
	}
	
	private final class AndroidInputStreamFactory implements InputStreamFactory {
		@Override
		public final InputStream newInputStream(final String location) throws Exception {
			return openFileInput(location);
		}
	}
	
	private final class AndroidResourceChecker implements ResourceChecker {
		@Override
		public final boolean exists(final String location) {
			for (final String f : fileList()) {
				if (f.equals(location)) {
					return true;
				}
			}
			return false;
		}
	}
	
	private final class AndroidResourceDeleter implements ResourceDeleter {
		@Override
		public final boolean delete(final String location) {
			return deleteFile(location);
		}
	}
	
	private final class AndroidResourceLister implements ResourceLister {
		@Override
		public final String[] list() {
			return getFilesDir().list();
		}
	}
	
	/*protected final File getFile(final String name) {
		for (final File f : getFilesDir().listFiles()) {
			info("Looking for " + name + "; checking " + f.getName());
			if (name.equals(f.getName())) {
				return f;
			}
		}
		info("Could not find " + name + "; returning null");
		return null;
	}*/
}
