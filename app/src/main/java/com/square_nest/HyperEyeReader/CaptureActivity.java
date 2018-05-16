package com.square_nest.HyperEyeReader;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.square_nest.hypereyedecodelib.HyperEyeDecoder;

import io.fotoapparat.Fotoapparat;
import io.fotoapparat.parameter.ScaleType;
import io.fotoapparat.preview.Frame;
import io.fotoapparat.preview.FrameProcessor;
import io.fotoapparat.selector.LensPositionSelectorsKt;
import io.fotoapparat.selector.ResolutionSelectorsKt;
import io.fotoapparat.view.CameraView;


public class CaptureActivity extends AppCompatActivity
{

    CameraView mCameraView;
    Fotoapparat mFotoapparat;
    FrameProcessor mFrameProcessor;
    Handler mHandler;
    Size mBestSize;
    RelativeLayout mFrame;
    Context mContext;
    Intent intent;
    TextView lbCode;

    private static int DECODE_WIDTH = 360;
    private static int DECODE_HEIGHT = 360;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);

        lbCode = (TextView) findViewById(R.id.textView);

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case 1:
                        onFPS(msg.getData().getLong("fps"));
                        break;
                    case 2:
                        onRecognize(msg.getData().getLong("res"));
                        break;
                    case -1:
                        mBestSize = new Size(msg.arg1, msg.arg2);
                        onWindowFocusChanged(true);
                        break;
                    default:
                        break;
                }
            }
        };

        intent = getIntent();
        Log.i("MY", intent.getAction());
        mContext = this;

        mCameraView = (CameraView) findViewById(R.id.camera_view);

        mFrameProcessor = new FrameProcessor() {
            long previous_index = 0;

            @Override
            public void process(Frame frame) {
                byte[] data = frame.getImage();

                //  Sending size to the main thread
                Message msgSize = new Message();
                msgSize.what = -1;
                msgSize.arg1 = frame.getSize().width;
                msgSize.arg2 = frame.getSize().height;
                mHandler.sendMessage(msgSize);

                //  Decoding
                int width = frame.getSize().width;
                int height = frame.getSize().height;
                int length = data.length;

                long msec = System.currentTimeMillis();
                int x = (width - DECODE_WIDTH) / 2;
                int y = (height - DECODE_HEIGHT) / 2;
                long res = HyperEyeDecoder.HEDecodeNV21(data, length, width, height, x,y,DECODE_WIDTH,DECODE_HEIGHT);


                //  Sending FPS to the main thread
                Message msgFPS = new Message();
                msgFPS.what = 1;
                Bundle bundleFPS = new Bundle();
                bundleFPS.putLong("fps", System.currentTimeMillis() - msec);
                msgFPS.setData(bundleFPS);
                mHandler.sendMessage(msgFPS);

                //  Sending result to the main thread
                if(res != previous_index) {
                    previous_index = res;

                    Message msgRes = new Message();
                    msgRes.what = 2;
                    Bundle bundleRes = new Bundle();
                    bundleRes.putLong("res", res);
                    msgRes.setData(bundleRes);
                    mHandler.sendMessage(msgRes);
                }
            }
        };

        mFotoapparat =
                Fotoapparat
                    .with(mContext)
                    .into(mCameraView)
                    .previewScaleType(ScaleType.CenterCrop)
                    .photoResolution(ResolutionSelectorsKt.highestResolution())
                    .lensPosition(LensPositionSelectorsKt.back())
                    .frameProcessor(mFrameProcessor)
                    .build();

        mBestSize = new Size(1, 1);

        mFrame = (RelativeLayout) findViewById(R.id.layout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mFotoapparat.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFotoapparat.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_capture, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (mFrame == null) return;

        int layoutWidth = mFrame.getWidth();
        int layoutHeight = mFrame.getHeight();

        int newWidth = layoutHeight * mBestSize.getHeight() / mBestSize.getWidth();
//        int newHeight = layoutWidth * mBestSize.getWidth() / mBestSize.getHeight();
        float k;
        if (newWidth >= layoutWidth) {
            k = (float)layoutWidth / mBestSize.getHeight();
        } else {
            k = (float)layoutHeight / mBestSize.getWidth();
        }

        ImageView border = (ImageView) findViewById(R.id.rectimage);
        border.getLayoutParams().width = (int)(k * CaptureActivity.DECODE_WIDTH);
        border.getLayoutParams().height = (int)(k * CaptureActivity.DECODE_HEIGHT);
        border.requestLayout();
    }

    public void onFPS(long msec)
    {
        // Show FPS delegate
        getSupportActionBar().setTitle("HyperEyeReader: " + Long.toString(msec) + "ms");
    }

    public void onRecognize(long code)
    {
        // Recognize HyperEye delegate
        if (code <= 0) {
            lbCode.setText("");
        } else {
            lbCode.setText(String.valueOf(code));
        }
    }
}
