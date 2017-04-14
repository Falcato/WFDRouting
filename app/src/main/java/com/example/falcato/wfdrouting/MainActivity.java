package com.example.falcato.wfdrouting;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button cliBtn = (Button) findViewById(R.id.client_button);
        Button proBtn = (Button) findViewById(R.id.provider_button);

        new InternetCheck(this).isInternetConnectionAvailable(new InternetCheck.InternetCheckListener() {
            @Override
            public void onComplete(boolean connected) {
                if(connected) {
                    ((MyApplication) MainActivity.this.getApplication()).setHasNet(true);
                }else{
                    ((MyApplication) MainActivity.this.getApplication()).setHasNet(false);
                }
            }
        });

        proBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent ( MainActivity.this, ProviderActivity.class );
                startActivity(intent);
            }
        });

        cliBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent ( MainActivity.this, ClientActivity.class );
                startActivity(intent);
            }
        });
    }
}
