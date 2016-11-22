package org.tensorflow.demo.Activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.tensorflow.demo.AsynTask.SocialAsyncTask;
import org.tensorflow.demo.CameraActivity;
import org.tensorflow.demo.Interface.AsyncResponse;
import org.tensorflow.demo.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class Social extends Activity implements AsyncResponse {

    ImageView openCamera;
    LinearLayout celebrityLook,onlineFashion,lookbook;
    View cell;
    //SocialAsyncTask fetch = new SocialAsyncTask();

    private int[] images = {R.drawable.d1, R.drawable.d2, R.drawable.d3,
            R.drawable.d4, R.drawable.d5, R.drawable.d6};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_social);

        openCamera = (ImageView) findViewById(R.id.openCamera);
        celebrityLook = (LinearLayout) findViewById(R.id.celebrityLook);
        onlineFashion = (LinearLayout)findViewById(R.id.onlineStyle);
        lookbook = (LinearLayout) findViewById(R.id.lookbook);

        for(int i=0;i<images.length;i++) {
            cell = getLayoutInflater().inflate(R.layout.cell, null);
            final ImageView imageView = (ImageView) cell.findViewById(R.id.image);
            imageView.setImageResource(images[i]);
            celebrityLook.addView(cell);
        }
        //fetch.response = this;
        //fetch.execute("celebrity_sites");


        for(int i=images.length-1;i>=0;i--) {
            cell = getLayoutInflater().inflate(R.layout.cell, null);
            final ImageView imageView = (ImageView) cell.findViewById(R.id.image);
            imageView.setImageResource(images[i]);
            onlineFashion.addView(cell);
        }

        for(int i=0;i<images.length;i++) {
            cell = getLayoutInflater().inflate(R.layout.cell, null);
            final ImageView imageView = (ImageView) cell.findViewById(R.id.image);
            imageView.setImageResource(images[i]);
            lookbook.addView(cell);
        }

        openCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(),CameraActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    public void processFinish(String[] images) {
        for (int i = 0; i < images.length; i++) {
            cell = getLayoutInflater().inflate(R.layout.cell, null);
            final ImageView imageView = (ImageView) cell.findViewById(R.id.image);
            Bitmap bitmap = null;
            try ( InputStream is = new URL( Uri.parse(images[i]).toString() ).openStream() ) {
                bitmap = BitmapFactory.decodeStream( is );
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            imageView.setImageBitmap(bitmap);
            celebrityLook.addView(cell);
        }
    }
}
