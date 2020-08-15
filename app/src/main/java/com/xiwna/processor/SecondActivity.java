package com.xiwna.processor;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xiwna.annotation.RouterAnnotation;

/**
 * @author xingping
 */
@RouterAnnotation("demo://second_activity")
public class SecondActivity extends AppCompatActivity  {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);
        ((TextView)findViewById(R.id.textName)).setText(getIntent().getStringExtra("name"));
    }
}
