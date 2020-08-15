package com.xiwna.processor;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xiwna.processor.router.Routers;

/**
 * @author xingping
 */
public class MainActivity extends AppCompatActivity  {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btnStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Routers.open(MainActivity.this, "demo://second_activity?name=xingping");
            }
        });
    }
}
