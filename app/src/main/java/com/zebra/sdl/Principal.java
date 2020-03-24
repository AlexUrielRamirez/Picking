//-----------------------------------------------------------
// Android SDL Sample App
//
// Copyright (c) 2015 Zebra Technologies
//-----------------------------------------------------------

package com.zebra.sdl;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.widget.TextView;

import com.zebra.adc.decoder.BarCodeReader;

public class Principal extends Activity implements
        BarCodeReader.DecodeCallback,
        BarCodeReader.PreviewCallback,
        SurfaceHolder.Callback,
        BarCodeReader.VideoCallback,
        BarCodeReader.ErrorCallback {
    // ------------------------------------------------------
    static final private boolean saveSnapshot = false; // true = save snapshot to file
    static private boolean sigcapImage = true; // true = display signature capture
    static private boolean videoCapDisplayStarted = false;
    //states
    static final int STATE_IDLE = 0;
    static final int STATE_DECODE = 1;
    static final int STATE_HANDSFREE = 2;
    static final int STATE_PREVIEW = 3;    //snapshot preview mode
    static final int STATE_SNAPSHOT = 4;
    static final int STATE_VIDEO = 5;

    // -----------------------------------------------------
    // statics
    static Principal app = null;



    // system
    private ToneGenerator tg = null;

    // BarCodeReader specifics
    private BarCodeReader bcr = null;

    private boolean beepMode = true;
    private boolean snapPreview = false;        // snapshot preview mode enabled - true - calls viewfinder which gets handled by
    private int trigMode = BarCodeReader.ParamVal.LEVEL;

    private int state = STATE_IDLE;
    private String decodeDataString;
    private static int decCount = 0;


    private long mStartTime;
    private long mBarcodeCount = 0;
    private long mConsumTime;


    static {
        System.loadLibrary("IAL");
        System.loadLibrary("SDL");

        if (android.os.Build.VERSION.SDK_INT >= 19)
            System.loadLibrary("barcodereader44"); // Android 4.4
        else if (android.os.Build.VERSION.SDK_INT >= 18)
            System.loadLibrary("barcodereader43"); // Android 4.3
        else
            System.loadLibrary("barcodereader");   // Android 2.3 - Android 4.2
    }

    public Principal()
    {
        app = this;
    }

    TextView txt;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_principal);

        txt = findViewById(R.id.txt);
        tg = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);

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

            int num = BarCodeReader.getNumberOfReaders();
            Log.e("ReaderNumber", num+"");
            if(android.os.Build.VERSION.SDK_INT >= 18)
                bcr = BarCodeReader.open(num, getApplicationContext()); // Android 4.3 and above
            else
                bcr = BarCodeReader.open(num); // Android 2.3

            bcr.setDecodeCallback(this);

            bcr.setErrorCallback(this);

            bcr.setParameter(765, 0); // For QC/MTK platforms
            bcr.setParameter(764, 3);

            bcr.setParameter(687, 4);
        }
        catch (Exception e) {

        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_F4) {
            doDecode();
        }
        return super.onKeyDown(keyCode, event);
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
        if (state == STATE_PREVIEW)
        {
            bcr.startViewFinder(this);
        }
        else{
            bcr.startPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void beep() {
        if (tg != null)
            tg.startTone(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING);
    }

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
        return ret;
    }

    // ----------------------------------------
    // set Default params
    private void doDefaultParams()
    {
        setIdle();
        bcr.setDefaultParameters();
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
    private void doDecode() {
        if (setIdle() != STATE_IDLE)
            return;

        state = STATE_DECODE;
        decCount = 0;
        decodeDataString = new String("");
       // decodeStatString = new String("");
        try
        {
            mStartTime = System.currentTimeMillis();
            bcr.startDecode(); // start decode (callback gets results)
        }
        catch (Exception e) { }

    }

    public void onDecodeComplete(int symbology, int length, byte[] data, BarCodeReader reader)
    {
        if (state == STATE_DECODE)
            state = STATE_IDLE;

        if(length == BarCodeReader.DECODE_STATUS_MULTI_DEC_COUNT)
            decCount = symbology;

        if (length > 0)
        {
            if (isHandsFree()==false && isAutoAim()==false)
                bcr.stopDecode();

            //++decodes;

            if (symbology == 0x69)	// signature capture
            {
                if (sigcapImage)
                {
                    Bitmap bmSig = null;
                    int scHdr = 6;
                    if (length > scHdr)
                        bmSig = BitmapFactory.decodeByteArray(data, scHdr, length-scHdr);
                }


                decodeDataString += new String(data);

                mBarcodeCount++;
                long consum = System.currentTimeMillis() - mStartTime;
                mConsumTime += consum;
                decodeDataString += "\n\r" + "本次消耗时间:" + consum + "毫秒" + "\n\r" + "平均速度:" + (mConsumTime / mBarcodeCount) + "毫秒/个";



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

                decodeDataString += new String(data);
                //add for test speed
                mBarcodeCount++;
                long consum = System.currentTimeMillis() - mStartTime;
                mConsumTime += consum;
                decodeDataString += "\n\r" + "本次消耗时间:" + consum + "毫秒" + "\n\r" +  "平均速度:" + (mConsumTime / mBarcodeCount) + "毫秒/个";
                Log.e("peashepe--->",decodeDataString);
                txt.setText(new String(data));


                if(decCount > 1) // Add the next line only if multiple decode
                {

                    decodeDataString += new String(" ; ");
                }
                else
                {
                    decodeDataString = new String("");

                }
            }

            if (beepMode)
                beep();
        }
        else	// no-decode
        {
            switch (length)
            {
                case BarCodeReader.DECODE_STATUS_TIMEOUT:

                    break;

                case BarCodeReader.DECODE_STATUS_CANCELED:

                    break;

                case BarCodeReader.DECODE_STATUS_ERROR:
                default:

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
        if (snapPreview)		//snapshot-preview mode?
        {
            state = STATE_PREVIEW;
            videoCapDisplayStarted = false;
            bcr.startViewFinder(this);
        }
        else
        {
            state = STATE_SNAPSHOT;

        }
    }
    private void doSnap1() {
        if (state == STATE_PREVIEW) {
            bcr.stopPreview();
            state = STATE_SNAPSHOT;
        }
        else{
            setIdle();
        }
    }

    // ----------------------------------------
    public void onPreviewFrame(byte[] data, BarCodeReader bcreader)
    {
    }

    //------------------------------------------
    private int setIdle() {
        int prevState = state;
        int ret = prevState;		//for states taking time to chg/end

        state = STATE_IDLE;
        switch (prevState) {
            case STATE_HANDSFREE:
                resetTrigger();
                //fall thru
            case STATE_DECODE:
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


                break;

            case BarCodeReader.BCRDR_EVENT_MOTION_DETECTED:


                break;

            case BarCodeReader.BCRDR_EVENT_SCANNER_RESET:

                break;

            default:
                // process any other events here
                break;
        }
    }

    @Override
    public void onVideoFrame(int format, int width, int height, byte[] data, BarCodeReader reader) {

    }

    @Override
    public void onError(int error, BarCodeReader reader) {

    }
}
