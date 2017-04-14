package com.example.falcato.wfdrouting;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class ClientActivity extends AppCompatActivity {

    EditText URLEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        Button go = (Button) findViewById(R.id.button);
        URLEdit = (EditText)findViewById(R.id.editText);

        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent ( ClientActivity.this, WebViewActivity.class );
                intent.putExtra ( "URLMainAct", URLEdit.getText().toString() );
                startActivity(intent);
            }
        });

        if( ((MyApplication) ClientActivity.this.getApplication()).getHasNet()) {

            TextView myTextView = (TextView)findViewById(R.id.textView);
            myTextView.setText("Connected!");

        }else{
            TextView myTextView = (TextView)findViewById(R.id.textView);
            myTextView.setText("Not connected!");
        }
    }
}
