//-----------------------------------------------------------
// Android SDL Sample App
//
// Copyright (c) 2015 Zebra Technologies
//-----------------------------------------------------------

package com.zebra.sdl;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.zebra.adc.decoder.BarCodeReader;

import org.mozilla.universalchardet.CharsetListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class SDLguiActivity extends Activity implements
		BarCodeReader.DecodeCallback, BarCodeReader.PictureCallback, BarCodeReader.PreviewCallback,
		SurfaceHolder.Callback, BarCodeReader.VideoCallback, BarCodeReader.ErrorCallback
{
	// ------------------------------------------------------
	static final private boolean saveSnapshot = false; // true = save snapshot to file
	static private boolean sigcapImage = true; // true = display signature capture
	static private boolean videoCapDisplayStarted = false;
	//states
	static final int STATE_IDLE 		= 0;
	static final int STATE_DECODE 		= 1;
	static final int STATE_HANDSFREE	= 2;	
	static final int STATE_PREVIEW		= 3;	//snapshot preview mode
	static final int STATE_SNAPSHOT		= 4;
	static final int STATE_VIDEO 		= 5;
	
	// -----------------------------------------------------
	// statics
	static SDLguiActivity app = null;

	// -----------------------------------------------------
	// ui
	private TextView tvStat = null;
	private TextView tvData = null;
	private EditText edPnum = null;
	private EditText edPval = null;
	private CheckBox chBeep = null;
	private CheckBox chBeep_RP 				= null;

	private ImageView image 					= null;	//snaphot image screen
	
	private SurfaceView surfaceView 			= null;	//video screen
	private SurfaceHolder surfaceHolder 	= null;
	private LayoutInflater controlInflater	= null;
	
	// system
	private ToneGenerator tg 		= null;

	// BarCodeReader specifics
	private BarCodeReader bcr 		= null;

	private boolean beepMode 		= true; 		// decode beep enable
	private int Mobile_reading_pane	= 716; 		// Mobile Phone reading Pane
	private int reading_pane_value  = 1; 
	private boolean snapPreview 	= false;		// snapshot preview mode enabled - true - calls viewfinder which gets handled by 
	private int trigMode			= BarCodeReader.ParamVal.LEVEL;
	private boolean atMain			= false;
	private int state				= STATE_IDLE;
	private int decodes 			= 0;
	
	private int motionEvents 		= 0;
	private int modechgEvents 	= 0;
	
	private int snapNum				= 0;		//saved snapshot #
	private String decodeDataString;
	private String decodeStatString;
	private static int decCount = 0;

	static
	{
		System.loadLibrary("IAL");
		System.loadLibrary("SDL");
		
		if(android.os.Build.VERSION.SDK_INT >= 19)
			System.loadLibrary("barcodereader44"); // Android 4.4
		else
			if(android.os.Build.VERSION.SDK_INT >= 18)
				System.loadLibrary("barcodereader43"); // Android 4.3
			else
				System.loadLibrary("barcodereader");   // Android 2.3 - Android 4.2
	}

	// ------------------------------------------------------
	public SDLguiActivity()
	{
		app = this;
	}

	// ------------------------------------------------------
	// Called with the activity is first created.
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mainScreen();
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);  		
		
		// sound
		tg = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);
		chBeep.setChecked(beepMode);
	}
	
	//-----------------------------------------------------
	@Override
	protected void onPause()
	{
		super.onPause();
		if (bcr != null)
		{
			setIdle();			
			bcr.release();
			bcr = null;
		}
	}

	// ------------------------------------------------------
	// Called when the activity is about to start interacting with the user.
	@Override
	protected void onResume()
	{
		super.onResume();
		state = STATE_IDLE;
		
		try
		{
			dspStat(getResources().getString(R.string.app_name) + " v" + this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName);
			if(android.os.Build.VERSION.SDK_INT >= 18)
				bcr = BarCodeReader.open(0, getApplicationContext()); // Android 4.3 and above 
			else
			   bcr = BarCodeReader.open(0); // Android 2.3
			
			if (bcr == null)
			{
				dspErr("open failed");
				return;
			}
			
			bcr.setDecodeCallback(this);
			
			bcr.setErrorCallback(this);
			
			// Set parameter - Uncomment for QC/MTK platforms
			 bcr.setParameter(765, 0); // For QC/MTK platforms
			 bcr.setParameter(764, 3);
			 //bcr.setParameter(137, 0);
			 //bcr.setParameter(8610, 1);
//			 Parameters  param = bcr.getParameters();
//			 Log.d("012", "scan_h:"+bcr.getNumProperty(BarCodeReader.PropertyNum.VERTICAL_RES)+", cam_h:"+param.getPreviewSize().height);
//			 if(bcr.getNumProperty(BarCodeReader.PropertyNum.VERTICAL_RES) != param.getPreviewSize().height){
//				 param.setPreviewSize(1360, bcr.getNumProperty(BarCodeReader.PropertyNum.VERTICAL_RES));
//				 param.setPreviewSize(1360,960);
//				 bcr.setParameters(param);
//			 }
						
			// Sample of how to setup OCR Related String Parameters
			// OCR Parameters
			// Enable OCR-B 
			//bcr.setParameter(681, 1);
						
			// Set OCR templates
			//String OCRSubSetString = "01234567890"; // Only numeric characters
			//String OCRSubSetString = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ!%"; // Only numeric characters
			// Parameter # 686 - OCR Subset
			//bcr.setParameter(686, OCRSubSetString);
			  			
			//String OCRTemplate = "54R"; // The D ignores all characters after the template
			// Parameter # 547 - OCR Template
			//bcr.setParameter(547, OCRTemplate);
			// Parameter # 689 - OCR Minimum characters
			//bcr.setParameter(689, 13);
			// Parameter # 690 - OCR Maximum characters
			//bcr.setParameter(690, 13); 
			  			
			// Set Orientation
			bcr.setParameter(687, 4); // 4 - omnidirectional
			  			
			// Sets OCR lines to decide 
			//bcr.setParameter(691, 2); // 2 - OCR 2 lines
						
			// End of OCR Parameter Sample
//			BarCodeReader.ReaderInfo readinfo = new BarCodeReader.ReaderInfo();	
//			BarCodeReader.getReaderInfo(0, readinfo);
//			Log.e("012", "face:"+readinfo.facing);
		}
		catch (Exception e)
		{
			dspErr("open excp:" + e);
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_F4) {
			doDecode();
		}
        return super.onKeyDown(keyCode, event);
    }

// === Android UI methods =======================================
	//-----------------------------------------------------
	// create main screen
	private void mainScreen()
	{
		if (atMain)
			return;
	
		atMain = true;
		
		setContentView(R.layout.main);		// Inflate our UI from its XML layout description.

		// Hook up button presses to the appropriate event handler.
		((Button) findViewById(R.id.buttonDec)).setOnClickListener(mDecodeListener);
		((Button) findViewById(R.id.buttonHF)).setOnClickListener(mHandsFreeListener);		
		((Button) findViewById(R.id.buttonSnap)).setOnClickListener(mSnapListener);
		((Button) findViewById(R.id.buttonVid)).setOnClickListener(mVidListener);
		((Button) findViewById(R.id.buttonGet)).setOnClickListener(mGetParamListener);
		((Button) findViewById(R.id.buttonSet)).setOnClickListener(mSetParamListener);
		((Button) findViewById(R.id.buttonDfl)).setOnClickListener(mDflParamListener);
		((Button) findViewById(R.id.buttonProp)).setOnClickListener(mPropListener);
		((Button) findViewById(R.id.buttonEnable)).setOnClickListener(mEnableAllListener);
		((Button) findViewById(R.id.buttonDisable)).setOnClickListener(mDisableAllListener);
		((Button) findViewById(R.id.buttonDecImage)).setOnClickListener(mGetDecodedImageListener);
		
		((CheckBox) findViewById(R.id.checkBeep)).setOnClickListener(mCheckBeepListener);
		((CheckBox) findViewById(R.id.checkReadingPane)).setOnClickListener(mCheckReadingPaneListener);
		
		// ui items
		tvStat = (TextView) findViewById(R.id.textStatus);
		tvData = (TextView) findViewById(R.id.textDecode);
		edPnum = (EditText) findViewById(R.id.editPnum);
		edPval = (EditText) findViewById(R.id.editPval);
		chBeep = (CheckBox) findViewById(R.id.checkBeep);
		chBeep.setChecked(beepMode);
		
		chBeep_RP = (CheckBox) findViewById(R.id.checkReadingPane);
		chBeep_RP.setChecked(false);

	}

	//-----------------------------------------------------	
	// create snapshot image screen
	private void snapScreen(Bitmap bmSnap)
	{
		atMain = false;
		setContentView(R.layout.image);

		image = (ImageView) findViewById(R.id.snap_image);		
		image.setOnClickListener(mImageClickListener);
		
		if (bmSnap != null)
			image.setImageBitmap(bmSnap);
	}
	
	//-----------------------------------------------------	
	// create preview/video screen
	private void vidScreen(boolean addButton)
	{
		atMain = false;
		setContentView(R.layout.surface);
		
		getWindow().setFormat(PixelFormat.UNKNOWN);
		surfaceView = (SurfaceView) findViewById(R.id.camerapreview);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		surfaceView.setOnClickListener(mImageClickListener);		
		if (addButton)
		{
   		controlInflater = LayoutInflater.from(getBaseContext());
   		View viewControl = controlInflater.inflate(R.layout.control, null);
   		LayoutParams layoutParamsControl = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
   		this.addContentView(viewControl, layoutParamsControl);
   		((Button) findViewById(R.id.takepicture)).setOnClickListener(mTakePicListener);
		}
	}

	//-----------------------------------------------------
	// SurfaceHolder callbacks	
	public void surfaceCreated(SurfaceHolder holder)
	{
		if (state == STATE_PREVIEW)
   		{
				//bcr.setPreviewDisplay(holder);
				bcr.startViewFinder(this);			//snapshot with preview mode
   		}
   		else //must be video	
   		{
				//bcr.setPreviewDisplay(holder);
				//bcr.startVideoCapture(this);			
   				bcr.startPreview();
   		}
	}

	//-----------------------------------------------------
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
	}

	//-----------------------------------------------------
	public void surfaceDestroyed(SurfaceHolder holder)
	{
	}
	
	// ------------------------------------------------------
	// Called when your activity's options menu needs to be created.
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);

		// menu.add(0, DECODE_ID, 0, R.string.decode).setShortcut('1', 'd');
		// menu.add(0, SNAP_ID, 0, R.string.snap).setShortcut('1', 's');
		return true;
	}

	// ------------------------------------------------------
	// Called right before your activity's option menu is displayed.
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);

		return true;
	}

	// ------------------------------------------------------
	// Called when a menu item is selected.
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		return super.onOptionsItemSelected(item);
	}

	// ------------------------------------------------------
	// callback for beep checkbox
	OnClickListener mCheckBeepListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			beepMode = ((CheckBox) v).isChecked();
		}
	};

	// ------------------------------------------------------
		// callback for beep checkbox
		OnClickListener mCheckReadingPaneListener = new OnClickListener()
		{
			public void onClick(View v)
			{

				if ( ((CheckBox) v).isChecked() )
				{
					chBeep_RP.setChecked(true);	
					reading_pane_value = 1;
					bcr.setParameter(Mobile_reading_pane, reading_pane_value);
					dspStat("Enabled mobile Phone Reading Pane");
				}
				else
				{
					chBeep_RP.setChecked(false);
					reading_pane_value = 0;
					bcr.setParameter(Mobile_reading_pane, reading_pane_value);
					dspStat("Disabled mobile Phone Reading Pane");
				}
			}
		};

	
	// ------------------------------------------------------
	// callback for decode button press
	OnClickListener mDecodeListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			doDecode();
		}
	};

	// ------------------------------------------------------
	// callback for HandsFree button press
	OnClickListener mHandsFreeListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			doHandsFree();
		}
	};

	// ------------------------------------------------------
	// callback for snapshot button press
	OnClickListener mSnapListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			doSnap();
		}
	};

	// ------------------------------------------------------
	// callback for video button press
	OnClickListener mVidListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			doVideo();
		}
	};

	// ------------------------------------------------------
	// callback for properties button press
	OnClickListener mPropListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			doGetProp();
		}
	};

	// ------------------------------------------------------	
	// callback for take-picture button on snap-preview screen
	OnClickListener mTakePicListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			doSnap1();
		}
	};
	
	// ------------------------------------------------------
	// callback for video screen click
	OnClickListener mImageClickListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			setIdle();
			mainScreen();
		}
	};

	// ------------------------------------------------------
	// callback for decode button press
	OnClickListener mDflParamListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			AlertDialog.Builder ad = new AlertDialog.Builder(app);		
	       ad.setMessage("Default ALL Parameters?")
	       .setCancelable(false)
	       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
	           public void onClick(DialogInterface dialog, int id) {
	               doDefaultParams();
	           }
	       })
	       .setNegativeButton("No", new DialogInterface.OnClickListener() {
	       public void onClick(DialogInterface dialog, int id) {
	           //just ignore it
	          }
	      });

	      Dialog dlg = ad.create();
			dlg.show();
		}
	};

	// ------------------------------------------------------
	// callback Get Param for button press
	OnClickListener mGetParamListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			getParam();
		}
	};

	// ------------------------------------------------------
	// callback enable all parameters for button press
	OnClickListener mEnableAllListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			dspStat("All Paramters Enabled");
			bcr.enableAllCodeTypes();
			
			/*String FilePath = "/mnt/sdcard/CAABVS00-002-R01D0.DAT";
			 
			boolean fIgnoreRelString = true;
			boolean fIgnoreSignature = false;
			
			int Status = bcr.FWUpdate(FilePath, fIgnoreRelString, fIgnoreSignature);

			if (Status == 0)
				dspStat("All Paramters Enabled\nFW Update Successful");
			else
				dspStat("All Paramters Enabled\nFW Update Unsuccessful");*/
		}
	};
	
	// ------------------------------------------------------
	// callback Disable all parameters for button press
	OnClickListener mDisableAllListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			dspStat("All Paramters Disabled");
			bcr.disableAllCodeTypes();
		}
	};
	
	// ------------------------------------------------------
	// callback Get Last Decoded image for button press
	OnClickListener mGetDecodedImageListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			//dspErr("LastImageDecodeComplete called");
			byte[] data = bcr.getLastDecImage();
			
			//String temp = "length " + data.length + " ";
			//dspStat(temp);
				
			if (data == null)
			{
				dspErr("LastImageDecodeComplete: data null - no image");
			}
						
			// display snapshot		
			Bitmap bmSnap = BitmapFactory.decodeByteArray(data, 0, data.length);
			snapScreen(bmSnap);
			
			if (bmSnap == null)
			{
				dspErr("LastImageDecodeComplete: no bitmap");
							
			}
			image.setImageBitmap(bmSnap);
			
		}
	};
	
	// ------------------------------------------------------
	// callback for Set Param button press
	OnClickListener mSetParamListener = new OnClickListener()
	{
		public void onClick(View v)
		{
			setParam();
		}
	};

	// ----------------------------------------
	// display status string
	private void dspStat(String s)
	{
		tvStat.setText(s);
	}

	// ----------------------------------------
	// display status resource id
	private void dspStat(int id)
	{
		tvStat.setText(id);
	}

	// ----------------------------------------
	// display error msg
	private void dspErr(String s)
	{
		tvStat.setText("ERROR" + s);
	}

	// ----------------------------------------
	// display status string
	private void dspData(String s)
	{
		tvData.setText(s);
	}

	// -----------------------------------------
	private void beep()
	{
		if (tg != null)
			tg.startTone(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING);
	}

	// ----------------------------------------
	private void getParam()
	{
		setIdle();		

		// get param #
		String s = edPnum.getText().toString();
		try
		{
			int num = Integer.parseInt(s);
			doGetParam(num);
		}
		catch (NumberFormatException nx)
		{
			dspStat("value ERROR");
		}
	}

	// ----------------------------------------
	private void setParam()
	{
		setIdle();
		
		// get param #
		String sn = edPnum.getText().toString();
		String sv = edPval.getText().toString();
		try
		{
			int num = Integer.parseInt(sn);
			int val = Integer.parseInt(sv);
			doSetParam(num, val);
		}
		catch (NumberFormatException nx)
		{
			dspStat("value ERROR");
		}
	}

// ==== SDL methods =====================
	
	// ----------------------------------------	
	private boolean isHandsFree()
	{
		return (trigMode == BarCodeReader.ParamVal.HANDSFREE);
	}

	// ----------------------------------------	
	private boolean isAutoAim()
	{
		return (trigMode == BarCodeReader.ParamVal.AUTO_AIM);		
	}

	// ----------------------------------------
	// reset Level trigger mode
	void resetTrigger()
	{
		doSetParam(BarCodeReader.ParamNum.PRIM_TRIG_MODE, BarCodeReader.ParamVal.LEVEL); 
		trigMode = BarCodeReader.ParamVal.LEVEL;		
	}
	
	
	// ----------------------------------------
	// get param
	private int doGetParam(int num)
	{
		int val = bcr.getNumParameter(num);		
		if (val != BarCodeReader.BCR_ERROR)
		{
			dspStat("Get # " + num + " = " + val);
			edPval.setText(Integer.toString(val));
		}
		else
		{
			dspStat("Get # " + num + " FAILED (" + val + ")");
			edPval.setText(Integer.toString(val));
		}
		return val;
	}

	// ----------------------------------------
	// set param
	private int doSetParam(int num, int val)
	{
		String s= "";
		int ret = bcr.setParameter(num, val);
		if (ret != BarCodeReader.BCR_ERROR)
		{
			if (num == BarCodeReader.ParamNum.PRIM_TRIG_MODE)
			{
				trigMode = val;
				if (val == BarCodeReader.ParamVal.HANDSFREE)
				{
					s = "HandsFree";
				}
				else if (val == BarCodeReader.ParamVal.AUTO_AIM)
				{
					s = "AutoAim";
					ret = bcr.startHandsFreeDecode(BarCodeReader.ParamVal.AUTO_AIM);
					if (ret != BarCodeReader.BCR_SUCCESS)
					{
						dspErr("AUtoAIm start FAILED");
					}
				}
				else if (val == BarCodeReader.ParamVal.LEVEL)
				{
					s = "Level";
				}
			}
			else if (num == BarCodeReader.ParamNum.IMG_VIDEOVF)
			{
				if ( snapPreview=(val == 1) )
					s = "SnapPreview";				
			}
		}
		else
			s = " FAILED (" + ret +")";
			
		dspStat("Set #" + num + " to " + val + " " + s);			
		return ret;
	}

	// ----------------------------------------
	// set Default params
	private void doDefaultParams()
   {
		setIdle();		
   	bcr.setDefaultParameters();
   	dspStat("Parameters Defaulted");

   	// reset modes
  		snapPreview = false;		   	
   	int val = bcr.getNumParameter(BarCodeReader.ParamNum.PRIM_TRIG_MODE);
  		if (val != BarCodeReader.BCR_ERROR)  
   		trigMode = val;
   }
		
	// ----------------------------------------
	// get properties
	private void doGetProp()
	{
		setIdle();
		String sMod = bcr.getStrProperty(BarCodeReader.PropertyNum.MODEL_NUMBER).trim();
		String sSer = bcr.getStrProperty(BarCodeReader.PropertyNum.SERIAL_NUM).trim();
		String sImg = bcr.getStrProperty(BarCodeReader.PropertyNum.IMGKIT_VER).trim();
		String sEng = bcr.getStrProperty(BarCodeReader.PropertyNum.ENGINE_VER).trim();
		String sBTLD = bcr.getStrProperty(BarCodeReader.PropertyNum.BTLD_FW_VER).trim();
		
		int buf = bcr.getNumProperty(BarCodeReader.PropertyNum.MAX_FRAME_BUFFER_SIZE);
		int hRes = bcr.getNumProperty(BarCodeReader.PropertyNum.HORIZONTAL_RES);
		int vRes = bcr.getNumProperty(BarCodeReader.PropertyNum.VERTICAL_RES);

		String s = "Model:\t\t" + sMod + "\n";
		s += "Serial:\t\t" + sSer + "\n";
		s += "Bytes:\t\t" + buf + "\n";
		s += "V-Res:\t\t" + vRes + "\n";
		s += "H-Res:\t\t" + hRes + "\n";
		s += "ImgKit:\t\t" + sImg + "\n";
		s += "Engine:\t" + sEng + "\n";
		s += "FW BTLD:\t" + sBTLD + "\n";
		
		AlertDialog.Builder dlg = new AlertDialog.Builder(this);
		if (dlg != null)
		{
			dlg.setTitle("SDL Properties");
			dlg.setMessage(s);
			dlg.setPositiveButton("ok", null);
			dlg.show();
		}
	}

	// ----------------------------------------
	// start a decode session
	private void doDecode()
	{
		if (setIdle() != STATE_IDLE)		
			return;
		
		state = STATE_DECODE;	
		decCount = 0;
		decodeDataString = new String("");
		decodeStatString = new String("");
		dspData("");
		dspStat(R.string.decoding);
		
		try
		{
			bcr.startDecode(); // start decode (callback gets results)					
		}
		catch (Exception e)
		{
			dspErr("open excp:" + e);
		}
	
	}

	// ----------------------------------------
	// start HandFree decode session
	private void doHandsFree()
	{
		if (setIdle() != STATE_IDLE)		
			return;
		
		int ret = bcr.startHandsFreeDecode(BarCodeReader.ParamVal.HANDSFREE);
		if (ret != BarCodeReader.BCR_SUCCESS)
			dspStat("startHandFree FAILED");
		else
		{
			trigMode = BarCodeReader.ParamVal.HANDSFREE;			
			state = STATE_HANDSFREE;

			decodeDataString = new String("");
			decodeStatString = new String("");
			dspData("");
			dspStat("HandsFree decoding");
		}	
	}
	
	// ----------------------------------------
	// BarCodeReader.DecodeCallback override
	public void onDecodeComplete(int symbology, int length, byte[] data, BarCodeReader reader)
	{
		if (state == STATE_DECODE)
			state = STATE_IDLE;
		
		// Get the decode count
		if(length == BarCodeReader.DECODE_STATUS_MULTI_DEC_COUNT)
			decCount = symbology;
		
		if (length > 0)
		{
			if (isHandsFree()==false && isAutoAim()==false)
				bcr.stopDecode();

			++decodes;
			
			if (symbology == 0x69)	// signature capture
			{
				if (sigcapImage)
     			{
   				Bitmap bmSig = null;
   				int scHdr = 6;
   				if (length > scHdr)
   					bmSig = BitmapFactory.decodeByteArray(data, scHdr, length-scHdr);
   				
   				if (bmSig != null)
   					snapScreen(bmSig);

   				else
   					dspErr("OnDecodeComplete: SigCap no bitmap");
     			}
				decodeStatString += new String("[" + decodes + "] type: " + symbology + " len: " + length);
				decodeDataString += new String(data);
				/*try {
					decodeDataString += new String(data,charsetName(data));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}*/
			}
			else
			{ 	
				

			if (symbology == 0x99)	//type 99?
			{
				symbology = data[0];
				int n = data[1];
				int s = 2;
				int d = 0;
				int len = 0;
				byte d99[] = new byte[data.length];					
				for (int i=0; i<n; ++i)
				{
					s += 2;
					len = data[s++];
					System.arraycopy(data, s, d99, d, len);
					s += len;
					d += len;
				}
				d99[d] = 0;
				data = d99;
			}
			
			Log.d("012", "ret="+byte2hex(data));
			decodeStatString += new String("[" + decodes + "] type: " + symbology + " len: " + length);
			decodeDataString += new String(data);
				/*try {
					decodeDataString += new String(data,charsetName(data));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}*/
				dspStat(decodeStatString);
			dspData(decodeDataString);
			
			if(decCount > 1) // Add the next line only if multiple decode
			{
				decodeStatString += new String(" ; ");
				decodeDataString += new String(" ; ");
			}
			else
			{
				decodeDataString = new String("");
				decodeStatString = new String("");
			}
			}
			
			if (beepMode)
				beep();
		}
      else	// no-decode
      {
      	dspData("");
      	switch (length)
      	{
      	case BarCodeReader.DECODE_STATUS_TIMEOUT:
      		dspStat("decode timed out");
      		break;
      		
      	case BarCodeReader.DECODE_STATUS_CANCELED:
      		dspStat("decode cancelled");
      		break;
      		
     		case BarCodeReader.DECODE_STATUS_ERROR:
      	default:
      		dspStat("decode failed");  
//      		Log.d("012", "decode failed length= " + length);
      		break;
      	}
      }
		
	//}	
	}
	private String byte2hex(byte [] buffer){  
        String h = "";  
          
        for(int i = 0; i < buffer.length; i++){  
            String temp = Integer.toHexString(buffer[i] & 0xFF);  
            if(temp.length() == 1){  
                temp = "0" + temp;  
            }  
            h = h + " "+ temp;  
        }  
          
        return h;  
          
    }  
	// ----------------------------------------
	// start a snap/preview session
	private void doSnap()
	{
		if (setIdle() != STATE_IDLE)		
			return;

		resetTrigger();
		dspData("");		
		if (snapPreview)		//snapshot-preview mode?
		{
			state = STATE_PREVIEW;
			videoCapDisplayStarted = false;
			dspStat("Snapshot Preview");
			bcr.startViewFinder(this); 
		}
		else
		{
			state = STATE_SNAPSHOT;
			snapScreen(null);
			bcr.takePicture(app);
		}
	}

	// ----------------------------------------
	// take snapshot   
	private void doSnap1()
	{
		if (state == STATE_PREVIEW)
		{
			bcr.stopPreview();
			state = STATE_SNAPSHOT;
		}
		if (state == STATE_SNAPSHOT)
		{
			snapScreen(null);
			bcr.takePicture(app);
		}
		else //unexpected state - reset mode
		{
			setIdle();
			mainScreen();
		}
	}

	// ----------------------------------------
	public void onPictureTaken(int format, int width, int height, byte[] abData, BarCodeReader reader)
	{
		if ( image == null)
			return;			

		// display snapshot		
		Bitmap bmSnap = BitmapFactory.decodeByteArray(abData, 0, abData.length);
		if (bmSnap == null)
		{
			dspErr("OnPictureTaken: no bitmap");
			return;			
		}
		image.setImageBitmap(rotated(bmSnap));
	
		// Save snapshot to the SD card
		if (saveSnapshot)
		{
			String snapFmt = "bin";			
			switch (bcr.getNumParameter(BarCodeReader.ParamNum.IMG_FILE_FORMAT))
			{
			case BarCodeReader.ParamVal.IMG_FORMAT_BMP:
				snapFmt = "bmp";
				break;
			
			case BarCodeReader.ParamVal.IMG_FORMAT_JPEG:
				snapFmt = "jpg";				
				break;
			
			case BarCodeReader.ParamVal.IMG_FORMAT_TIFF:
				snapFmt = "tif";	
				break;
			}
			
			File filFSpec = null;
			try
			{
				String strFile = String.format("se4500_img_%d.%s", snapNum, snapFmt);
				File filRoot = Environment.getExternalStorageDirectory();
				File filPath = new File(filRoot.getAbsolutePath() + "/DCIM/Camera");
				filPath.mkdirs();
				filFSpec = new File(filPath, strFile);
				FileOutputStream fos = new FileOutputStream(filFSpec);
				fos.write(abData);
				fos.close();
				
				++snapNum;
			}
			catch (Throwable thrw)
			{
				dspErr("Create '" + filFSpec.getAbsolutePath() + "' failed");
				dspErr("Error=" + thrw.getMessage());
			}
		}
	}
	
	// ----------------------------------------
	public void onPreviewFrame(byte[] data, BarCodeReader bcreader)
	{
	}

	// ----------------------------------------
	// start video session
	private void doVideo()
	{
		if (setIdle() != STATE_IDLE)		
			return;
	
		resetTrigger();
		dspData("");
		dspStat("video started");
		state = STATE_VIDEO;
		videoCapDisplayStarted = false;
		bcr.startVideoCapture(this);
		//bcr.startPreview();
	}

	//------------------------------------------	
	private int setIdle()
	{
		int prevState = state;
		int ret = prevState;		//for states taking time to chg/end
		
		state = STATE_IDLE;
		switch (prevState)
		{
		case STATE_HANDSFREE:
			resetTrigger();
			//fall thru
		case STATE_DECODE:
			dspStat("decode stopped");
			bcr.stopDecode();
			break;
		
		case STATE_VIDEO:			
			bcr.stopPreview();
			break;
		
		case STATE_SNAPSHOT:
			ret = STATE_IDLE;			
			break;
			
		default:
			ret = STATE_IDLE;			
		}
		return ret;
	}

	// ----------------------------------------
	public void onEvent(int event, int info, byte[] data, BarCodeReader reader)
	{
		switch (event)
		{
		case BarCodeReader.BCRDR_EVENT_SCAN_MODE_CHANGED:
			++modechgEvents;
			dspStat("Scan Mode Changed Event (#" + modechgEvents + ")");
			break;

		case BarCodeReader.BCRDR_EVENT_MOTION_DETECTED:
			++motionEvents;
			dspStat("Motion Detect Event (#" + motionEvents + ")");
			break;
			
		case BarCodeReader.BCRDR_EVENT_SCANNER_RESET:
			dspStat("Reset Event");
			break;

		default:
			// process any other events here
			break;
		}
	}

	//-------------------------------------------------------
	private Bitmap rotated(Bitmap bmSnap)
	{
		Matrix matrix = new Matrix();
		if (matrix != null)
		{
			matrix.postRotate(90); 	  
			// create new bitmap from orig tranformed by matrix  
			Bitmap bmr = Bitmap.createBitmap(bmSnap , 0, 0, bmSnap.getWidth(), bmSnap.getHeight(), matrix, true);
			if (bmr != null)
				return bmr;
		}

		return bmSnap;		//when all else fails
	}

	public void onVideoFrame(int format, int width, int height, byte[] data,
			BarCodeReader reader) {
		// display snapshot		
		Bitmap bmSnap = BitmapFactory.decodeByteArray(data, 0, data.length);
		
		if(videoCapDisplayStarted == false)
		{
			atMain = false;
			videoCapDisplayStarted = true;
			setContentView(R.layout.image);
			image = (ImageView) findViewById(R.id.snap_image);		
			
			// This handles snapshot with viewfinder
			if(state == STATE_PREVIEW)
			{
				controlInflater = LayoutInflater.from(getBaseContext());
				View viewControl = controlInflater.inflate(R.layout.control, null);
				LayoutParams layoutParamsControl = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
				this.addContentView(viewControl, layoutParamsControl);
				((Button) findViewById(R.id.takepicture)).setOnClickListener(mTakePicListener);
			}
			else
			{
				image.setOnClickListener(mImageClickListener);
			}
		}
		
		if (bmSnap != null)
			image.setImageBitmap(bmSnap);		
	}

	public void onError(int error, BarCodeReader reader) {
		// TODO Auto-generated method stub
		
	}

	//add by myself
	/**
	 * The encode formate of data scanned by scnner;
	 * @param date the source data
	 * @return the encode formate;
	 * @throws IOException decode exception;
	 */
	private  String charsetName(byte[] date){
		return guessEncoding(date);
		/*Charset charset = null;
		InputStream is = new ByteArrayInputStream(date);
		CodepageDetectorProxy detectorProxy = CodepageDetectorProxy.getInstance();
		detectorProxy.add(new ParsingDetector(false));
		detectorProxy.add(JChardetFacade.getInstance());
		detectorProxy.add(ASCIIDetector.getInstance());
		detectorProxy.add(UnicodeDetector.getInstance());
		try {
			charset = detectorProxy.detectCodepage(is,date.length);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (charset != null) {
			return charset.name();
		} else {
			return guessEncoding(date);
		}*/
	}

	/**
	 * juniversalchardet guess the encode type
	 * @param bytes
	 * @return
	 */
	private  String guessEncoding(byte[] bytes) {
		String DEFAULT_ENCODING = "UTF-8";
		org.mozilla.universalchardet.UniversalDetector detector =
				new org.mozilla.universalchardet.UniversalDetector(new CharsetListener() {
					@Override
					public void report(String s) {
						Log.d("SDLguiActivity","Charset Name:" + s);
					}
				});
		detector.handleData(bytes, 0, bytes.length);
		detector.dataEnd();
		String encoding = detector.getDetectedCharset();
		detector.reset();
		if (encoding == null) {
			encoding = DEFAULT_ENCODING;
		}
		return encoding;
	}

}//end-class
