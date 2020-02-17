package com.example.feedx;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.example.feedx.image.ImageLoader;

public class LruActivity extends AppCompatActivity {

    private ImageLoader mImageLoader;

    private String url = "http://inews.gtimg.com/newsapp_bt/0/11194668198/1000.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lru);
        mImageLoader = new ImageLoader(this);

        final ImageView imageView = findViewById(R.id.image01);

        findViewById(R.id.btn01).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mImageLoader.displayImage(url, imageView);
            }
        });

    }
}
